package com.lightbox.batch.ui

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.lightbox.batch.LightBoxApp
import com.lightbox.batch.data.PhotoEntity
import com.lightbox.batch.data.PresetEntity
import com.lightbox.batch.model.BatchState
import com.lightbox.batch.model.PresetParameters
import com.lightbox.batch.preset.LightroomPresetParser
import com.lightbox.batch.processing.BatchImageProcessor
import com.lightbox.batch.worker.MassExportWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

class BatchViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val TAG = "BatchViewModel"
        private const val EXPORT_WORK_NAME = "lightbox_mass_export"
        val DEFAULT_PRESET = PresetParameters.BUILT_IN_PRESETS.first()
        val DEFAULT_PRESET_ID = getPresetId(DEFAULT_PRESET.name)

        private fun getPresetId(name: String): String {
            val clean = name.lowercase()
                .replace("á", "a").replace("é", "e").replace("í", "i").replace("ó", "o").replace("ú", "u")
                .replace(" ", "_")
                .replace("-", "_")
                .replace("&", "and")
            return "builtin_$clean"
        }
    }

    private val app = application as LightBoxApp
    private val database = app.database
    private val processor = BatchImageProcessor(application)

    // Current batch state
    private val _batchState = MutableStateFlow(BatchState(
        batchId = UUID.randomUUID().toString(),
        selectedPresetId = DEFAULT_PRESET_ID
    ))
    val batchState: StateFlow<BatchState> = _batchState.asStateFlow()

    // Photos in current batch - dynamically reacts to batchId changes
    @OptIn(ExperimentalCoroutinesApi::class)
    val photos: StateFlow<List<PhotoEntity>> = _batchState
        .map { it.batchId }
        .distinctUntilChanged()
        .flatMapLatest { batchId ->
            database.photoDao().getPhotosByBatch(batchId)
        }
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // 3 Sample photos for live filter preview across different scenarios
    private val _previewPhotos = MutableStateFlow<List<PhotoEntity>>(emptyList())
    val previewPhotos: StateFlow<List<PhotoEntity>> = _previewPhotos.asStateFlow()

    fun shufflePreviewSamples() {
        val all = photos.value
        if (all.isNotEmpty()) {
            _previewPhotos.value = all.shuffled().take(3)
        }
    }

    // Available presets
    val presets = database.presetDao()
        .getAllPresets()
        .stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    // Selected preset parameters (cached)
    private val _selectedPreset = MutableStateFlow<PresetParameters?>(DEFAULT_PRESET)
    val selectedPreset: StateFlow<PresetParameters?> = _selectedPreset.asStateFlow()

    // Effective adjustment parameters combining selected preset + auto-correct
    val effectiveAdjustmentParams: StateFlow<PresetParameters?> = kotlinx.coroutines.flow.combine(
        _selectedPreset,
        _batchState
    ) { preset, state ->
        when {
            preset != null && state.autoCorrectEnabled -> preset.combineWith(PresetParameters.AUTO_CORRECT)
            preset != null -> preset
            state.autoCorrectEnabled -> PresetParameters.AUTO_CORRECT
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    // Export progress from WorkManager
    private val _exportProgress = MutableStateFlow<WorkProgress?>(null)
    val exportProgress: StateFlow<WorkProgress?> = _exportProgress.asStateFlow()

    init {
        createNotificationChannel()
        seedBuiltInPresets()

        // Keep 3 random preview samples synced whenever photos list changes
        viewModelScope.launch {
            photos.collect { list ->
                if (list.isEmpty()) {
                    _previewPhotos.value = emptyList()
                } else {
                    val current = _previewPhotos.value
                    if (current.isEmpty() || !current.all { p -> list.any { it.id == p.id } }) {
                        _previewPhotos.value = list.shuffled().take(3)
                    }
                }
            }
        }
    }

    private fun seedBuiltInPresets() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val assetManager = getApplication<LightBoxApp>().assets
                val files = assetManager.list("presets") ?: emptyArray()

                // Also seed any BUILT_IN_PRESETS not covered by asset files
                val seededNames = mutableSetOf<String>()

                files.forEach { fileName ->
                    try {
                        assetManager.open("presets/$fileName").use { inputStream ->
                            val params = if (fileName.endsWith(".xmp", true) || fileName.endsWith(".lrtemplate", true)) {
                                LightroomPresetParser.parseXmp(inputStream, fileName)
                            } else {
                                val jsonStr = inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
                                PresetParameters.fromJson(jsonStr)
                            }

                            val id = getPresetId(params.name)
                            seededNames.add(params.name)

                            val existing = database.presetDao().getPresetById(id)
                            if (existing == null) {
                                database.presetDao().insert(
                                    PresetEntity(
                                        id = id,
                                        name = params.name,
                                        filePath = "asset:presets/$fileName",
                                        parametersJson = params.toJson()
                                    )
                                )
                                Log.d(TAG, "Seeded preset from asset: ${params.name}")
                            } else if (existing.filePath.startsWith("asset:") || existing.filePath == "builtin") {
                                database.presetDao().update(id, params.name, params.toJson())
                                Log.d(TAG, "Updated preset from asset: ${params.name}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to load preset asset: $fileName", e)
                    }
                }

                // Seed any built-in presets that aren't covered by assets
                PresetParameters.BUILT_IN_PRESETS.forEach { params ->
                    if (params.name !in seededNames) {
                        val id = getPresetId(params.name)
                        val existing = database.presetDao().getPresetById(id)
                        if (existing == null) {
                            database.presetDao().insert(
                                PresetEntity(
                                    id = id,
                                    name = params.name,
                                    filePath = "builtin",
                                    parametersJson = params.toJson()
                                )
                            )
                            Log.d(TAG, "Seeded built-in preset: ${params.name}")
                        } else if (existing.filePath.startsWith("asset:") || existing.filePath == "builtin") {
                            database.presetDao().update(id, params.name, params.toJson())
                        }
                    }
                }

                // Refresh selected preset in memory if already selected
                val currentSelectedId = _batchState.value.selectedPresetId
                if (currentSelectedId != null) {
                    val updated = database.presetDao().getPresetById(currentSelectedId)
                    if (updated != null) {
                        _selectedPreset.value = PresetParameters.fromJson(updated.parametersJson)
                    }
                } else {
                    // Find the "Corrida" preset or fall back to first one
                    val allPresets = database.presetDao().getAllPresetsOnce()
                    val defaultPreset = allPresets.firstOrNull { it.name.contains("Corrida", true) }
                        ?: allPresets.firstOrNull()
                    defaultPreset?.let { selectPreset(it.id) }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to seed built-in presets", e)
            }
        }
    }

    // ========================
    // IMPORT
    // ========================

    /**
     * Import JPEGs from a selected directory (SAF tree URI).
     * Rapidly streams files, extracts metadata, and generates cached thumbnails.
     */
    fun importFromDirectory(treeUri: Uri) {
        viewModelScope.launch {
            _batchState.value = _batchState.value.copy(isImporting = true, errorMessage = null)

            try {
                // Persist Uri permission across operations
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    getApplication<LightBoxApp>().contentResolver.takePersistableUriPermission(treeUri, flags)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not take persistable Uri permission for treeUri: $treeUri", e)
                }

                withContext(Dispatchers.IO) {
                    val batchId = _batchState.value.batchId
                    val documentFile = DocumentFile.fromTreeUri(getApplication(), treeUri)
                        ?: throw IllegalStateException("Cannot access directory")

                    val jpegFiles = documentFile.listFiles().filter { file ->
                        file.isFile && file.name?.let { name ->
                            name.endsWith(".jpg", true) || name.endsWith(".jpeg", true)
                        } == true
                    }

                    Log.i(TAG, "Found ${jpegFiles.size} JPEG files")

                    // Batch insert in chunks for memory efficiency
                    val thumbnailDir = File(getApplication<LightBoxApp>().cacheDir, "thumbnails").apply { mkdirs() }

                    jpegFiles.chunked(100).forEachIndexed { chunkIndex, chunk ->
                        val entities = chunk.mapNotNull { file ->
                            try {
                                val fileName = file.name ?: return@mapNotNull null
                                val fileSize = file.length()

                                // Generate thumbnail (v2: upright with EXIF orientation baked in)
                                val thumbnailFile = File(thumbnailDir, "thumb_v2_${file.uri.hashCode()}.jpg")
                                if (!thumbnailFile.exists()) {
                                    try {
                                        processor.generateThumbnail(file.uri, thumbnailFile, 256)
                                    } catch (e: Exception) {
                                        Log.w(TAG, "Thumbnail generation failed for $fileName", e)
                                    }
                                }

                                PhotoEntity(
                                    uri = file.uri.toString(),
                                    fileName = fileName,
                                    fileSize = fileSize,
                                    batchId = batchId,
                                    thumbnailPath = if (thumbnailFile.exists()) thumbnailFile.absolutePath else null
                                )
                            } catch (e: Exception) {
                                Log.w(TAG, "Failed to process file", e)
                                null
                            }
                        }

                        database.photoDao().insertAll(entities)

                        val totalImported = (chunkIndex + 1) * 100
                        _batchState.value = _batchState.value.copy(
                            totalPhotos = minOf(totalImported, jpegFiles.size)
                        )
                    }

                    val finalCount = database.photoDao().getPhotoCount(batchId)
                    _batchState.value = _batchState.value.copy(
                        totalPhotos = finalCount,
                        isImporting = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Import failed", e)
                _batchState.value = _batchState.value.copy(
                    isImporting = false,
                    errorMessage = "Import failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Import individual JPEG files (from file picker).
     */
    fun importFiles(uris: List<Uri>) {
        viewModelScope.launch {
            _batchState.value = _batchState.value.copy(isImporting = true, errorMessage = null)

            try {
                uris.forEach { uri ->
                    try {
                        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                        getApplication<LightBoxApp>().contentResolver.takePersistableUriPermission(uri, flags)
                    } catch (e: Exception) {
                        Log.w(TAG, "Could not take persistable Uri permission for uri: $uri", e)
                    }
                }

                withContext(Dispatchers.IO) {
                    val batchId = _batchState.value.batchId
                    val thumbnailDir = File(getApplication<LightBoxApp>().cacheDir, "thumbnails").apply { mkdirs() }

                    val entities = uris.mapNotNull { uri ->
                        try {
                            val fileName = getFileName(uri) ?: "unknown.jpg"
                            if (!fileName.endsWith(".jpg", true) && !fileName.endsWith(".jpeg", true)) {
                                return@mapNotNull null
                            }

                            val thumbnailFile = File(thumbnailDir, "thumb_v2_${uri.hashCode()}.jpg")
                            if (!thumbnailFile.exists()) {
                                try {
                                    processor.generateThumbnail(uri, thumbnailFile, 256)
                                } catch (e: Exception) {
                                    Log.w(TAG, "Thumbnail failed for $fileName", e)
                                }
                            }

                            PhotoEntity(
                                uri = uri.toString(),
                                fileName = fileName,
                                fileSize = 0,
                                batchId = batchId,
                                thumbnailPath = if (thumbnailFile.exists()) thumbnailFile.absolutePath else null
                            )
                        } catch (e: Exception) {
                            Log.w(TAG, "Failed to process URI: $uri", e)
                            null
                        }
                    }

                    database.photoDao().insertAll(entities)

                    val finalCount = database.photoDao().getPhotoCount(batchId)
                    _batchState.value = _batchState.value.copy(
                        totalPhotos = finalCount,
                        isImporting = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "File import failed", e)
                _batchState.value = _batchState.value.copy(
                    isImporting = false,
                    errorMessage = "Import failed: ${e.message}"
                )
            }
        }
    }

    // ========================
    // PRESET MANAGEMENT
    // ========================

    /**
     * Import a Lightroom preset file (.xmp or .lrtemplate).
     */
    fun importPreset(uri: Uri, fileName: String) {
        viewModelScope.launch {
            try {
                try {
                    val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION
                    getApplication<LightBoxApp>().contentResolver.takePersistableUriPermission(uri, flags)
                } catch (e: Exception) {
                    Log.w(TAG, "Could not take persistable Uri permission for preset uri: $uri", e)
                }

                val actualFileName = getFileName(uri) ?: fileName
                val params = withContext(Dispatchers.IO) {
                    LightroomPresetParser.parse(getApplication(), uri, actualFileName)
                }

                val entity = PresetEntity(
                    id = UUID.randomUUID().toString(),
                    name = params.name,
                    filePath = uri.toString(),
                    parametersJson = params.toJson()
                )

                database.presetDao().insert(entity)
                selectPreset(entity.id)
                Log.i(TAG, "Imported preset: ${params.name}")
            } catch (e: Exception) {
                Log.e(TAG, "Preset import failed", e)
                _batchState.value = _batchState.value.copy(
                    errorMessage = "Preset import failed: ${e.message}"
                )
            }
        }
    }

    /**
     * Select a preset for batch application.
     */
    fun selectPreset(presetId: String?) {
        viewModelScope.launch {
            if (presetId == null) {
                _selectedPreset.value = null
                _batchState.value = _batchState.value.copy(selectedPresetId = null)
                return@launch
            }

            val entity = database.presetDao().getPresetById(presetId)
            if (entity != null) {
                _selectedPreset.value = PresetParameters.fromJson(entity.parametersJson)
                _batchState.value = _batchState.value.copy(selectedPresetId = presetId)
            }
        }
    }

    /**
     * Rename an existing preset.
     */
    fun renamePreset(presetId: String, newName: String) {
        viewModelScope.launch {
            val entity = database.presetDao().getPresetById(presetId) ?: return@launch
            val params = try {
                PresetParameters.fromJson(entity.parametersJson).copy(name = newName)
            } catch (_: Exception) {
                PresetParameters(name = newName)
            }
            database.presetDao().update(presetId, newName, params.toJson())
            if (_batchState.value.selectedPresetId == presetId) {
                _selectedPreset.value = params
            }
        }
    }

    /**
     * Delete an existing preset.
     */
    fun deletePreset(presetId: String) {
        viewModelScope.launch {
            database.presetDao().delete(presetId)
            if (_batchState.value.selectedPresetId == presetId) {
                selectPreset(null)
            }
        }
    }

    // ========================
    // AUTO-CORRECT
    // ========================

    fun toggleAutoCorrect() {
        _batchState.value = _batchState.value.copy(
            autoCorrectEnabled = !_batchState.value.autoCorrectEnabled
        )
    }

    // ========================
    // SELECTION
    // ========================

    fun togglePhotoSelection(photoId: Long) {
        viewModelScope.launch {
            val photo = database.photoDao().getPhotoById(photoId) ?: return@launch
            database.photoDao().setSelected(photoId, !photo.isSelected)
        }
    }

    fun selectAll() {
        viewModelScope.launch {
            database.photoDao().setAllSelected(_batchState.value.batchId, true)
        }
    }

    fun deselectAll() {
        viewModelScope.launch {
            database.photoDao().setAllSelected(_batchState.value.batchId, false)
        }
    }

    // ========================
    // EXPORT
    // ========================

    fun setExportQuality(quality: Int) {
        _batchState.value = _batchState.value.copy(
            exportQuality = quality.coerceIn(50, 100)
        )
    }

    /**
     * Launch mass export via WorkManager.
     */
    fun startExport(outputDirUri: Uri) {
        // Persist Uri permission for background WorkManager execution
        try {
            val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            getApplication<LightBoxApp>().contentResolver.takePersistableUriPermission(outputDirUri, flags)
        } catch (e: Exception) {
            Log.w(TAG, "Could not take persistable Uri permission for export dir: $outputDirUri", e)
        }

        viewModelScope.launch {
            val state = _batchState.value
            var preset = _selectedPreset.value
            if (preset == null && state.selectedPresetId != null) {
                val entity = withContext(Dispatchers.IO) {
                    database.presetDao().getPresetById(state.selectedPresetId)
                }
                if (entity != null) {
                    preset = PresetParameters.fromJson(entity.parametersJson)
                    _selectedPreset.value = preset
                }
            }

            val presetJson = preset?.toJson()
            Log.d(TAG, "startExport: presetId=${state.selectedPresetId}, preset=${preset?.name}, " +
                    "jsonLen=${presetJson?.length}, autoCorrect=${state.autoCorrectEnabled}")

            val inputData = workDataOf(
                MassExportWorker.KEY_BATCH_ID to state.batchId,
                MassExportWorker.KEY_OUTPUT_DIR_URI to outputDirUri.toString(),
                MassExportWorker.KEY_QUALITY to state.exportQuality,
                MassExportWorker.KEY_PRESET_ID to state.selectedPresetId,
                MassExportWorker.KEY_PRESET_JSON to presetJson,
                MassExportWorker.KEY_AUTO_CORRECT to state.autoCorrectEnabled
            )

            val exportRequest = OneTimeWorkRequestBuilder<MassExportWorker>()
                .setInputData(inputData)
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .addTag("export")
                .build()

            val workManager = WorkManager.getInstance(getApplication())
            workManager.enqueueUniqueWork(
                EXPORT_WORK_NAME,
            ExistingWorkPolicy.REPLACE,
            exportRequest
        )

        _batchState.value = state.copy(isExporting = true)

        // Observe work progress
        viewModelScope.launch {
            workManager.getWorkInfoByIdFlow(exportRequest.id).collect { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.RUNNING -> {
                        val current = workInfo.progress.getInt(MassExportWorker.KEY_PROGRESS_CURRENT, 0)
                        val total = workInfo.progress.getInt(MassExportWorker.KEY_PROGRESS_TOTAL, 0)
                        val failed = workInfo.progress.getInt(MassExportWorker.KEY_PROGRESS_FAILED, 0)
                        val batchInfo = workInfo.progress.getString(MassExportWorker.KEY_BATCH_INFO)
                        _exportProgress.value = WorkProgress(current, total, failed, batchInfo = batchInfo)
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        val total = workInfo.outputData.getInt(MassExportWorker.KEY_PROGRESS_TOTAL, 0)
                        val failed = workInfo.outputData.getInt(MassExportWorker.KEY_PROGRESS_FAILED, 0)
                        val lastError = workInfo.outputData.getString(MassExportWorker.KEY_LAST_ERROR)
                        _exportProgress.value = WorkProgress(total, total, failed, isComplete = true, lastError = lastError)
                        _batchState.value = _batchState.value.copy(isExporting = false)
                    }
                    WorkInfo.State.FAILED -> {
                        _exportProgress.value = null
                        val errorMsg = try {
                            workInfo.outputData.getString("error") ?: "Unknown error"
                        } catch (_: Exception) {
                            "Unknown error"
                        }
                        _batchState.value = _batchState.value.copy(
                            isExporting = false,
                            errorMessage = "Export failed: $errorMsg"
                        )
                    }
                    WorkInfo.State.CANCELLED -> {
                        _exportProgress.value = null
                        _batchState.value = _batchState.value.copy(isExporting = false)
                    }
                    else -> { /* ENQUEUED, BLOCKED */ }
                }
            }
        }
      }
    }

    fun cancelExport() {
        WorkManager.getInstance(getApplication()).cancelUniqueWork(EXPORT_WORK_NAME)
        _batchState.value = _batchState.value.copy(isExporting = false)
        _exportProgress.value = null
    }

    // ========================
    // NEW BATCH
    // ========================

    fun startNewBatch() {
        cancelExport()
        val newBatchId = UUID.randomUUID().toString()
        val currentPreset = _selectedPreset.value
        val presetId = currentPreset?.let { getPresetId(it.name) }
        _batchState.value = BatchState(
            batchId = newBatchId,
            selectedPresetId = presetId,
            totalPhotos = 0
        )
        _previewPhotos.value = emptyList()
        _exportProgress.value = null
        viewModelScope.launch(Dispatchers.IO) {
            try {
                database.photoDao().deleteAllPhotos()
            } catch (e: Exception) {
                Log.w(TAG, "Failed to clear photos DB on new batch", e)
            }
            try {
                File(getApplication<LightBoxApp>().cacheDir, "thumbnails").deleteRecursively()
            } catch (_: Exception) {}
        }
    }

    fun dismissError() {
        _batchState.value = _batchState.value.copy(errorMessage = null)
    }

    // ========================
    // HELPERS
    // ========================

    private fun getFileName(uri: Uri): String? {
        val cursor = getApplication<LightBoxApp>().contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null
        )
        return cursor?.use {
            if (it.moveToFirst()) it.getString(0) else null
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            "lightbox_export",
            "LightBox Export",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Batch export progress"
            setShowBadge(false)
        }
        val nm = getApplication<LightBoxApp>()
            .getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
    }
}

data class WorkProgress(
    val current: Int,
    val total: Int,
    val failed: Int = 0,
    val isComplete: Boolean = false,
    val lastError: String? = null,
    val batchInfo: String? = null
) {
    val percentage: Float
        get() = if (total > 0) current.toFloat() / total else 0f
}

package com.lightbox.batch.worker

import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.net.toUri
import androidx.documentfile.provider.DocumentFile
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lightbox.batch.LightBoxApp
import com.lightbox.batch.model.PresetParameters
import com.lightbox.batch.processing.AutoToneAnalyzer
import com.lightbox.batch.processing.BatchImageProcessor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicInteger

/**
 * WorkManager-based batch export worker that processes and exports JPEGs
 * directly to SAF without intermediate disk writes, utilizing bounded concurrency.
 *
 * Runs as a foreground service with progress notification for long-running
 * batch operations (thousands of images).
 */
class MassExportWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val TAG = "MassExportWorker"

        // Input data keys
        const val KEY_BATCH_ID = "batch_id"
        const val KEY_OUTPUT_DIR_URI = "output_dir_uri"
        const val KEY_QUALITY = "quality"
        const val KEY_PRESET_JSON = "preset_json"
        const val KEY_PRESET_ID = "preset_id"
        const val KEY_AUTO_CORRECT = "auto_correct"

        // Progress data keys
        const val KEY_PROGRESS_CURRENT = "progress_current"
        const val KEY_PROGRESS_TOTAL = "progress_total"
        const val KEY_PROGRESS_FAILED = "progress_failed"
        const val KEY_LAST_ERROR = "last_error"
        const val KEY_BATCH_INFO = "batch_info"

        // Notification
        private const val NOTIFICATION_ID = 1001
        private const val CHANNEL_ID = "lightbox_export"

        // Concurrent image tasks: 5 parallel workers on Snapdragon 8 Gen 3 (12GB+ RAM)
        // Each worker slices rows internally across CPU cores in applyAdjustments()
        private const val MAX_CONCURRENT = 5

        // Mini-batches of 200 images for bounded progress and memory management
        private const val MINI_BATCH_SIZE = 200
    }

    private val processor = BatchImageProcessor(applicationContext)
    private val database = (applicationContext as LightBoxApp).database

    override suspend fun doWork(): Result = withContext(Dispatchers.Default) {
        val batchId = inputData.getString(KEY_BATCH_ID)
            ?: return@withContext Result.failure(workDataOf("error" to "Identificador do lote não informado."))
        val outputDirUriStr = inputData.getString(KEY_OUTPUT_DIR_URI)
            ?: return@withContext Result.failure(workDataOf("error" to "Pasta de destino não selecionada."))
        val quality = inputData.getInt(KEY_QUALITY, 92)
        val presetId = inputData.getString(KEY_PRESET_ID)
        val presetJson = inputData.getString(KEY_PRESET_JSON)
        val autoCorrect = inputData.getBoolean(KEY_AUTO_CORRECT, false)

        val outputDirUri = outputDirUriStr.toUri()

        // 1. Validate output directory before starting work
        val outputDir = withContext(Dispatchers.IO) {
            try {
                DocumentFile.fromTreeUri(applicationContext, outputDirUri)
            } catch (e: Exception) {
                Log.e(TAG, "Cannot access output directory tree", e)
                null
            }
        }

        if (outputDir == null || !outputDir.exists() || !outputDir.canWrite()) {
            return@withContext Result.failure(
                workDataOf("error" to "A pasta de destino não possui permissão de escrita ou é inacessível. Por favor, selecione outra pasta.")
            )
        }

        // 2. Resolve preset: prefer DB lookup by ID (avoids WorkManager 10KB Data limit)
        val presetParams: PresetParameters? = run {
            if (presetId != null) {
                try {
                    database.presetDao().getPresetById(presetId)
                        ?.let { PresetParameters.fromJson(it.parametersJson) }
                } catch (e: Exception) {
                    Log.w(TAG, "DB preset lookup failed for id=$presetId", e)
                    null
                }
            } else null
        } ?: run {
            presetJson?.let {
                try { PresetParameters.fromJson(it) } catch (e: Exception) { null }
            }
        }

        Log.d(TAG, "Export: presetId=$presetId, preset=${presetParams?.name}, autoCorrect=$autoCorrect, quality=$quality")

        try {
            // Set up foreground notification safely
            try {
                setForeground(createForegroundInfo("Preparando exportação...", 0, 0))
            } catch (e: Exception) {
                Log.w(TAG, "Could not set foreground service notification", e)
            }

            // Get selected or all photos in batch
            val selectedPhotos = database.photoDao().getSelectedPhotos(batchId)
            val allPhotos = database.photoDao().getPhotosByBatchList(batchId)
            val photos = if (selectedPhotos.isNotEmpty()) selectedPhotos else allPhotos

            if (photos.isEmpty()) {
                return@withContext Result.failure(workDataOf("error" to "Nenhuma foto encontrada no lote atual para exportação."))
            }

            val totalCount = photos.size
            val processedCount = AtomicInteger(0)
            val failedCount = AtomicInteger(0)
            val semaphore = Semaphore(MAX_CONCURRENT)
            var lastFailureReason: String? = null

            val miniBatches = photos.chunked(MINI_BATCH_SIZE)
            val totalMiniBatches = miniBatches.size

            // Process photos partitioned into 200-image mini batches
            miniBatches.forEachIndexed { batchIndex, miniBatch ->
                ensureActive()
                val batchNum = batchIndex + 1
                val startIdx = batchIndex * MINI_BATCH_SIZE + 1
                val endIdx = minOf(batchIndex * MINI_BATCH_SIZE + miniBatch.size, totalCount)
                val batchDesc = "Mini lote $batchNum de $totalMiniBatches (fotos $startIdx a $endIdx)"

                Log.i(TAG, "Iniciando $batchDesc com ${miniBatch.size} imagens")
                updateProgress(processedCount.get(), totalCount, failedCount.get(), batchDesc)

                coroutineScope {
                    val jobs = miniBatch.map { photo ->
                        async(Dispatchers.Default) {
                            semaphore.acquire()
                            try {
                                ensureActive()

                                val inputUri = photo.uri.toUri()
                                val outputFileName = buildExportFileName(photo.fileName)

                                // Create SAF target document directly
                                val outputDoc = outputDir.createFile("image/jpeg", outputFileName)
                                    ?: throw IllegalStateException("Não foi possível criar o arquivo de destino: $outputFileName")

                                // Determine adjustment parameters
                                val adjustParams = when {
                                    presetParams != null && autoCorrect -> {
                                        val autoParams = AutoToneAnalyzer.analyze(applicationContext, inputUri)
                                        combineParameters(presetParams, autoParams)
                                    }
                                    presetParams != null -> presetParams
                                    autoCorrect -> {
                                        AutoToneAnalyzer.analyze(applicationContext, inputUri)
                                    }
                                    else -> PresetParameters()
                                }

                                // Direct SAF processing: Multi-core row slicing + in-place transform + EXIF baking
                                processor.processImageToUri(
                                    inputUri = inputUri,
                                    outputDocUri = outputDoc.uri,
                                    params = adjustParams,
                                    quality = quality
                                )

                                // Mark as exported in database
                                database.photoDao().markExported(photo.id)

                                val current = processedCount.incrementAndGet()
                                updateProgress(current, totalCount, failedCount.get(), batchDesc)

                                Log.d(TAG, "Exported directly to SAF: ${photo.fileName} ($current/$totalCount)")

                            } catch (e: Exception) {
                                val errorMsg = "${photo.fileName}: ${e.localizedMessage ?: e.message ?: "Erro desconhecido"}"
                                Log.e(TAG, "Failed to export: $errorMsg", e)
                                lastFailureReason = errorMsg
                                failedCount.incrementAndGet()
                                val current = processedCount.incrementAndGet()
                                updateProgress(current, totalCount, failedCount.get(), batchDesc)
                            } finally {
                                semaphore.release()
                            }
                        }
                    }
                    jobs.awaitAll()
                }

                // Mini-batch boundary: clear heap and garbage collect between 200-image batches
                System.gc()
            }

            // Final progress update
            val finalProcessed = processedCount.get()
            val finalFailed = failedCount.get()
            val successCount = finalProcessed - finalFailed

            Log.i(TAG, "Export complete: $successCount succeeded, $finalFailed failed out of $totalCount")

            // If ALL photos failed, report as failure with reason
            if (successCount == 0 && totalCount > 0) {
                return@withContext Result.failure(
                    workDataOf(
                        "error" to (lastFailureReason ?: "Falha ao exportar fotos"),
                        KEY_PROGRESS_CURRENT to finalProcessed,
                        KEY_PROGRESS_TOTAL to totalCount,
                        KEY_PROGRESS_FAILED to finalFailed,
                        KEY_BATCH_INFO to "Falha total"
                    )
                )
            }

            return@withContext Result.success(
                workDataOf(
                    KEY_PROGRESS_CURRENT to finalProcessed,
                    KEY_PROGRESS_TOTAL to totalCount,
                    KEY_PROGRESS_FAILED to finalFailed,
                    KEY_LAST_ERROR to (lastFailureReason ?: ""),
                    KEY_BATCH_INFO to "Concluído ($totalMiniBatches mini lotes)"
                )
            )

        } catch (e: Exception) {
            Log.e(TAG, "Export failed catastrophically", e)
            return@withContext Result.failure(workDataOf("error" to (e.message ?: "Erro desconhecido")))
        }
    }

    /**
     * Combine two PresetParameters by averaging their values.
     * Preset values take priority (60/40 weight).
     */
    private fun combineParameters(
        preset: PresetParameters,
        auto: PresetParameters
    ): PresetParameters {
        return preset.combineWith(auto)
    }

    /** Build export file name with _LB suffix */
    private fun buildExportFileName(originalName: String): String {
        val baseName = originalName.substringBeforeLast(".")
        val extension = originalName.substringAfterLast(".", "jpg")
        return "${baseName}_LB.$extension"
    }

    /** Update progress data and notification */
    private suspend fun updateProgress(current: Int, total: Int, failed: Int, batchDesc: String? = null) {
        setProgress(workDataOf(
            KEY_PROGRESS_CURRENT to current,
            KEY_PROGRESS_TOTAL to total,
            KEY_PROGRESS_FAILED to failed,
            KEY_BATCH_INFO to (batchDesc ?: "")
        ))
        try {
            val title = if (!batchDesc.isNullOrBlank()) "LightBox Export • $batchDesc" else "LightBox Export"
            val text = "Exportando: $current / $total" + if (failed > 0) " ($failed falharam)" else ""
            setForeground(createForegroundInfo(text, current, total, title))
        } catch (_: Exception) {
            // Ignore foreground service notification update failures
        }
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        return createForegroundInfo("LightBox está exportando fotos...", 0, 0, "LightBox Export")
    }

    /** Create foreground notification for long-running export */
    private fun createForegroundInfo(message: String, current: Int, total: Int, title: String = "LightBox Export"): ForegroundInfo {
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(message)
            .setSmallIcon(android.R.drawable.ic_menu_save)
            .setOngoing(true)
            .setSilent(true)
            .apply {
                if (total > 0) {
                    setProgress(total, current, false)
                } else {
                    setProgress(0, 0, true)
                }
            }
            .build()

        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        )
    }
}

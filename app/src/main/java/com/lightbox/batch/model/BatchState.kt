package com.lightbox.batch.model

data class BatchState(
    val batchId: String = "",
    val totalPhotos: Int = 0,
    val processedCount: Int = 0,
    val exportedCount: Int = 0,
    val isImporting: Boolean = false,
    val isProcessing: Boolean = false,
    val isExporting: Boolean = false,
    val autoCorrectEnabled: Boolean = false,
    val selectedPresetId: String? = null,
    val exportQuality: Int = 92,
    val errorMessage: String? = null
)

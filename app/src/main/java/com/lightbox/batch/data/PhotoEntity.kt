package com.lightbox.batch.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "photos")
data class PhotoEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val uri: String,
    val fileName: String,
    val fileSize: Long,
    val width: Int = 0,
    val height: Int = 0,
    val dateTaken: Long = 0,
    val dateImported: Long = System.currentTimeMillis(),
    val batchId: String,
    val thumbnailPath: String? = null,
    val isSelected: Boolean = false,
    val isProcessed: Boolean = false,
    val isExported: Boolean = false,
    val appliedPresetId: String? = null,
    val autoCorrectApplied: Boolean = false
)

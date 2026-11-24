package com.lightbox.batch.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "presets")
data class PresetEntity(
    @PrimaryKey val id: String,
    val name: String,
    val filePath: String,
    val dateImported: Long = System.currentTimeMillis(),
    val parametersJson: String // serialized PresetParameters
)

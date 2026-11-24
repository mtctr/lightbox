package com.lightbox.batch.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface PresetDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(preset: PresetEntity)

    @Query("SELECT * FROM presets ORDER BY dateImported DESC")
    fun getAllPresets(): Flow<List<PresetEntity>>

    @Query("SELECT * FROM presets ORDER BY dateImported DESC")
    suspend fun getAllPresetsOnce(): List<PresetEntity>

    @Query("SELECT * FROM presets WHERE id = :id")
    suspend fun getPresetById(id: String): PresetEntity?

    @Query("DELETE FROM presets WHERE id = :id")
    suspend fun delete(id: String)

    @Query("UPDATE presets SET name = :newName WHERE id = :id")
    suspend fun updateName(id: String, newName: String)

    @Query("UPDATE presets SET name = :name, parametersJson = :parametersJson WHERE id = :id")
    suspend fun update(id: String, name: String, parametersJson: String)
}

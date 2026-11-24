package com.lightbox.batch.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PhotoDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(photos: List<PhotoEntity>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(photo: PhotoEntity): Long

    @Update
    suspend fun update(photo: PhotoEntity)

    @Query("SELECT * FROM photos WHERE batchId = :batchId ORDER BY dateImported DESC")
    fun getPhotosByBatch(batchId: String): Flow<List<PhotoEntity>>

    @Query("SELECT * FROM photos WHERE batchId = :batchId ORDER BY dateImported DESC")
    suspend fun getPhotosByBatchList(batchId: String): List<PhotoEntity>

    @Query("SELECT * FROM photos WHERE id = :id")
    suspend fun getPhotoById(id: Long): PhotoEntity?

    @Query("SELECT * FROM photos WHERE batchId = :batchId AND isSelected = 1")
    suspend fun getSelectedPhotos(batchId: String): List<PhotoEntity>

    @Query("UPDATE photos SET isSelected = :selected WHERE batchId = :batchId")
    suspend fun setAllSelected(batchId: String, selected: Boolean)

    @Query("UPDATE photos SET isSelected = :selected WHERE id = :id")
    suspend fun setSelected(id: Long, selected: Boolean)

    @Query("UPDATE photos SET isProcessed = 1, appliedPresetId = :presetId WHERE id = :id")
    suspend fun markProcessed(id: Long, presetId: String?)

    @Query("UPDATE photos SET isExported = 1 WHERE id = :id")
    suspend fun markExported(id: Long)

    @Query("UPDATE photos SET autoCorrectApplied = :applied WHERE batchId = :batchId")
    suspend fun setAutoCorrectForBatch(batchId: String, applied: Boolean)

    @Query("SELECT COUNT(*) FROM photos WHERE batchId = :batchId")
    suspend fun getPhotoCount(batchId: String): Int

    @Query("SELECT COUNT(*) FROM photos WHERE batchId = :batchId AND isExported = 1")
    suspend fun getExportedCount(batchId: String): Int

    @Query("DELETE FROM photos WHERE batchId = :batchId")
    suspend fun deleteByBatch(batchId: String)

    @Query("DELETE FROM photos")
    suspend fun deleteAllPhotos()

    @Query("SELECT * FROM photos WHERE batchId = :batchId ORDER BY RANDOM() LIMIT :limit")
    suspend fun getRandomSamplePhotos(batchId: String, limit: Int = 3): List<PhotoEntity>
}

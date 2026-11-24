package com.lightbox.batch.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [PhotoEntity::class, PresetEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LightBoxDatabase : RoomDatabase() {
    abstract fun photoDao(): PhotoDao
    abstract fun presetDao(): PresetDao

    companion object {
        fun create(context: Context): LightBoxDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                LightBoxDatabase::class.java,
                "lightbox.db"
            )
                .fallbackToDestructiveMigration()
                .build()
        }
    }
}

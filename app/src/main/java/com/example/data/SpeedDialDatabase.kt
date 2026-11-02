package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [SpeedDialEntity::class], version = 1, exportSchema = false)
abstract class SpeedDialDatabase : RoomDatabase() {
    abstract fun dao(): SpeedDialDao

    companion object {
        @Volatile
        private var INSTANCE: SpeedDialDatabase? = null

        fun getDatabase(context: Context): SpeedDialDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    SpeedDialDatabase::class.java,
                    "speed_dial_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}

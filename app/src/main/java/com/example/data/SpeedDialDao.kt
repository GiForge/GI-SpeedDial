package com.example.data

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeedDialDao {
    @Query("SELECT * FROM speed_dial_items ORDER BY orderIndex ASC")
    fun getAllItems(): Flow<List<SpeedDialEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: SpeedDialEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItems(items: List<SpeedDialEntity>)

    @Query("DELETE FROM speed_dial_items WHERE id = :id")
    suspend fun deleteItemById(id: String)

    @Query("DELETE FROM speed_dial_items WHERE parentId = :parentId")
    suspend fun deleteByParentId(parentId: String)

    @Query("DELETE FROM speed_dial_items")
    suspend fun clearAll()

    @Update
    suspend fun updateItem(item: SpeedDialEntity)
}

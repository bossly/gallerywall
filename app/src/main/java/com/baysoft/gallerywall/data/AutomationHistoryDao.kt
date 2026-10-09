package com.baysoft.gallerywall.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface AutomationHistoryDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: AutomationHistoryEntity): Long

    @Update
    suspend fun update(record: AutomationHistoryEntity)

    @Query("SELECT * FROM automation_history ORDER BY timestamp DESC")
    fun getAllHistoryFlow(): Flow<List<AutomationHistoryEntity>>

    @Query("SELECT * FROM automation_history ORDER BY timestamp DESC LIMIT :limit")
    suspend fun getRecentHistory(limit: Int = 100): List<AutomationHistoryEntity>

    @Query("DELETE FROM automation_history")
    suspend fun clearAll()

    @Query("DELETE FROM automation_history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM automation_history WHERE timestamp < :cutoffMillis")
    suspend fun deleteOlderThan(cutoffMillis: Long)
}

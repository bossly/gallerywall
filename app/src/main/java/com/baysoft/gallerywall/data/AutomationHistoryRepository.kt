package com.baysoft.gallerywall.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AutomationHistoryRepository(private val dao: AutomationHistoryDao) {

    val allHistory: Flow<List<AutomationHistoryEntity>> = dao.getAllHistoryFlow()

    suspend fun getRecentHistory(limit: Int = 100): List<AutomationHistoryEntity> =
        withContext(Dispatchers.IO) {
            dao.getRecentHistory(limit)
        }

    suspend fun logRecord(record: AutomationHistoryEntity): Long =
        withContext(Dispatchers.IO) {
            dao.insert(record)
        }

    suspend fun updateRecord(record: AutomationHistoryEntity) =
        withContext(Dispatchers.IO) {
            dao.update(record)
        }

    suspend fun clearAll() = withContext(Dispatchers.IO) {
        dao.clearAll()
    }

    suspend fun deleteById(id: Long) = withContext(Dispatchers.IO) {
        dao.deleteById(id)
    }

    suspend fun cleanupOldRecords() = withContext(Dispatchers.IO) {
        val thirtyDaysAgo = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000
        dao.deleteOlderThan(thirtyDaysAgo)
    }
}

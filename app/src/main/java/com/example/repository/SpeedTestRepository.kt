package com.example.repository

import com.example.db.SpeedTestDao
import com.example.db.SpeedTestResultEntity
import kotlinx.coroutines.flow.Flow

class SpeedTestRepository(private val dao: SpeedTestDao) {
    val allResults: Flow<List<SpeedTestResultEntity>> = dao.getAllResults()

    suspend fun saveResult(result: SpeedTestResultEntity): Long {
        return dao.insertResult(result)
    }

    suspend fun deleteResult(id: Int) {
        dao.deleteById(id)
    }

    suspend fun clearAll() {
        dao.clearAll()
    }
}

package com.example.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface SpeedTestDao {
    @Query("SELECT * FROM speed_test_results ORDER BY timestamp DESC")
    fun getAllResults(): Flow<List<SpeedTestResultEntity>>

    @Query("SELECT * FROM speed_test_results WHERE testType = :testType ORDER BY timestamp DESC")
    fun getResultsByType(testType: String): Flow<List<SpeedTestResultEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertResult(result: SpeedTestResultEntity): Long

    @Query("DELETE FROM speed_test_results WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM speed_test_results")
    suspend fun clearAll()
}

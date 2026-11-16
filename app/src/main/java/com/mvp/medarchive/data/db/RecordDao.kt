package com.mvp.medarchive.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

data class RecordCount(val memberId: Long, val cnt: Int)

@Dao
interface RecordDao {
    @Query("SELECT * FROM records WHERE memberId = :id ORDER BY dateEpochDay DESC, createdAt DESC")
    fun observeByMember(id: Long): Flow<List<Record>>

    @Query("SELECT * FROM records WHERE memberId = :id ORDER BY dateEpochDay DESC, createdAt DESC")
    suspend fun byMember(id: Long): List<Record>

    @Query("SELECT * FROM records WHERE id = :id")
    suspend fun get(id: Long): Record?

    @Insert
    suspend fun insert(r: Record): Long

    @Update
    suspend fun update(r: Record)

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT memberId, COUNT(*) AS cnt FROM records GROUP BY memberId")
    fun observeRecordCounts(): Flow<List<RecordCount>>
}

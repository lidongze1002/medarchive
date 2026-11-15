package com.mvp.medarchive.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

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

    @Query("DELETE FROM records WHERE id = :id")
    suspend fun delete(id: Long)
}

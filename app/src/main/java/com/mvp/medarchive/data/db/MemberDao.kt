package com.mvp.medarchive.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface MemberDao {
    @Query("SELECT * FROM members ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<Member>>

    @Query("SELECT * FROM members WHERE id = :id")
    suspend fun get(id: Long): Member?

    @Insert
    suspend fun insert(m: Member): Long

    @Query("DELETE FROM members WHERE id = :id")
    suspend fun delete(id: Long)
}

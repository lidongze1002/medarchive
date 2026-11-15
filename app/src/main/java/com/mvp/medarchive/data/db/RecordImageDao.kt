package com.mvp.medarchive.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface RecordImageDao {
    @Query("SELECT * FROM record_images WHERE recordId = :id ORDER BY sortOrder")
    fun observeByRecord(id: Long): Flow<List<RecordImage>>

    @Query("SELECT * FROM record_images WHERE recordId = :id")
    suspend fun byRecord(id: Long): List<RecordImage>

    @Insert
    suspend fun insert(img: RecordImage)

    @Query("DELETE FROM record_images WHERE recordId = :id")
    suspend fun deleteByRecord(id: Long)
}

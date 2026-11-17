package com.mvp.medarchive.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MedReminderDao {

    @Query("SELECT * FROM med_reminders WHERE memberId = :memberId ORDER BY createdAt")
    fun observeByMember(memberId: Long): Flow<List<MedReminder>>

    @Query("SELECT * FROM med_reminders WHERE memberId = :memberId ORDER BY createdAt")
    suspend fun byMember(memberId: Long): List<MedReminder>

    @Query("SELECT * FROM med_reminders WHERE enabled = 1")
    suspend fun allEnabled(): List<MedReminder>

    @Query("SELECT * FROM med_reminders WHERE id = :id")
    suspend fun get(id: Long): MedReminder?

    @Insert
    suspend fun insert(r: MedReminder): Long

    @Update
    suspend fun update(r: MedReminder)

    @Query("DELETE FROM med_reminders WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM med_reminders WHERE memberId = :memberId")
    suspend fun deleteByMember(memberId: Long)
}

@Dao
interface MedDoseDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(dose: MedDose)

    @Query("SELECT * FROM med_doses WHERE memberId = :memberId AND dateEpochDay BETWEEN :from AND :to")
    fun observeRange(memberId: Long, from: Long, to: Long): Flow<List<MedDose>>

    @Query("SELECT * FROM med_doses WHERE memberId = :memberId AND dateEpochDay = :day")
    suspend fun byDay(memberId: Long, day: Long): List<MedDose>

    @Query("DELETE FROM med_doses WHERE reminderId = :reminderId AND dateEpochDay = :day AND time = :time")
    suspend fun deleteOne(reminderId: Long, day: Long, time: String)

    @Query("DELETE FROM med_doses WHERE reminderId = :reminderId")
    suspend fun deleteByReminder(reminderId: Long)

    @Query("DELETE FROM med_doses WHERE memberId = :memberId")
    suspend fun deleteByMember(memberId: Long)
}

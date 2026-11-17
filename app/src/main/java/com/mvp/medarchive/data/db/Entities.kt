package com.mvp.medarchive.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "members")
data class Member(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val birthdate: String = "",
    val bloodType: String = "",
    val allergies: String = "",
    val notes: String = "",
    @ColumnInfo(defaultValue = "") val avatarPath: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "records", indices = [Index("memberId")])
data class Record(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val type: String,
    val title: String,
    val dateEpochDay: Long,
    val hospital: String = "",
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Entity(tableName = "record_images", indices = [Index("recordId")])
data class RecordImage(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val recordId: Long,
    val path: String,
    val sortOrder: Int = 0
)

@Entity(tableName = "med_reminders", indices = [Index("memberId")])
data class MedReminder(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val memberId: Long,
    val drug: String,
    val dosage: String = "",
    /** 每天提醒时间，21:30 逗号分隔 */
    val times: String = "08:00",
    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis()
)

/** 打卡记录：某天某个时间点是否已服用。 */
@Entity(
    tableName = "med_doses",
    indices = [
        Index(value = ["reminderId", "dateEpochDay", "time"], unique = true),
        Index("memberId")
    ]
)
data class MedDose(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val reminderId: Long,
    val memberId: Long,
    val dateEpochDay: Long,
    /** HH:mm */
    val time: String,
    /** taken / skipped */
    val status: String = "taken",
    val takenAt: Long = System.currentTimeMillis()
)

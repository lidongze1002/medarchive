package com.mvp.medarchive.data.db

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

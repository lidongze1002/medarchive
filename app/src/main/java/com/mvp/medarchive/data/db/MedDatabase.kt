package com.mvp.medarchive.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [Member::class, Record::class, RecordImage::class],
    version = 1,
    exportSchema = false
)
abstract class MedDatabase : RoomDatabase() {
    abstract fun memberDao(): MemberDao
    abstract fun recordDao(): RecordDao
    abstract fun recordImageDao(): RecordImageDao
}

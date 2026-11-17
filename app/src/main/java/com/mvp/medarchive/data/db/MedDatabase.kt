package com.mvp.medarchive.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE members ADD COLUMN avatarPath TEXT NOT NULL DEFAULT ''")
    }
}

@Database(
    entities = [Member::class, Record::class, RecordImage::class],
    version = 2,
    exportSchema = false
)
abstract class MedDatabase : RoomDatabase() {
    abstract fun memberDao(): MemberDao
    abstract fun recordDao(): RecordDao
    abstract fun recordImageDao(): RecordImageDao
}

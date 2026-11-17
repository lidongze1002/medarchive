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

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `med_reminders` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`memberId` INTEGER NOT NULL, " +
                "`drug` TEXT NOT NULL, " +
                "`dosage` TEXT NOT NULL, " +
                "`times` TEXT NOT NULL, " +
                "`enabled` INTEGER NOT NULL, " +
                "`createdAt` INTEGER NOT NULL)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_med_reminders_memberId` ON `med_reminders` (`memberId`)")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "CREATE TABLE IF NOT EXISTS `med_doses` (" +
                "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`reminderId` INTEGER NOT NULL, " +
                "`memberId` INTEGER NOT NULL, " +
                "`dateEpochDay` INTEGER NOT NULL, " +
                "`time` TEXT NOT NULL, " +
                "`status` TEXT NOT NULL, " +
                "`takenAt` INTEGER NOT NULL)"
        )
        db.execSQL(
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_med_doses_reminderId_dateEpochDay_time` " +
                "ON `med_doses` (`reminderId`, `dateEpochDay`, `time`)"
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS `index_med_doses_memberId` ON `med_doses` (`memberId`)")
    }
}

@Database(
    entities = [Member::class, Record::class, RecordImage::class, MedReminder::class, MedDose::class],
    version = 4,
    exportSchema = false
)
abstract class MedDatabase : RoomDatabase() {
    abstract fun memberDao(): MemberDao
    abstract fun recordDao(): RecordDao
    abstract fun recordImageDao(): RecordImageDao
    abstract fun medReminderDao(): MedReminderDao
    abstract fun medDoseDao(): MedDoseDao
}

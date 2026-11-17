package com.mvp.medarchive.di

import android.content.Context
import androidx.room.Room
import com.mvp.medarchive.data.ImageStore
import com.mvp.medarchive.data.MedRepository
import com.mvp.medarchive.data.db.MedDatabase
import com.mvp.medarchive.data.db.MIGRATION_1_2
import com.mvp.medarchive.data.db.MIGRATION_2_3

class AppContainer(context: Context) {
    private val app = context.applicationContext
    val imageStore = ImageStore(app)
    private val database: MedDatabase = Room.databaseBuilder(
        app,
        MedDatabase::class.java,
        "med.db"
    ).addMigrations(MIGRATION_1_2, MIGRATION_2_3).build()

    val repository = MedRepository(database, imageStore)
}

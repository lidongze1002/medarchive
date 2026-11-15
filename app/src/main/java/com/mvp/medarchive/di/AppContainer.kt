package com.mvp.medarchive.di

import android.content.Context
import androidx.room.Room
import com.mvp.medarchive.data.ImageStore
import com.mvp.medarchive.data.MedRepository
import com.mvp.medarchive.data.db.MedDatabase

class AppContainer(context: Context) {
    private val app = context.applicationContext
    val imageStore = ImageStore(app)
    private val database: MedDatabase = Room.databaseBuilder(
        app,
        MedDatabase::class.java,
        "med.db"
    ).build()

    val repository = MedRepository(database, imageStore)
}

package com.mvp.medarchive

import android.app.Application
import com.mvp.medarchive.di.AppContainer

class MedArchiveApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

package com.mvp.medarchive

import android.app.Application
import com.mvp.medarchive.di.AppContainer
import com.mvp.medarchive.reminder.ReminderNotifier
import com.mvp.medarchive.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MedArchiveApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderNotifier.ensureChannel(this)
        CoroutineScope(Dispatchers.IO).launch {
            runCatching {
                ReminderScheduler.scheduleAll(this@MedArchiveApp, container.repository.allEnabledReminders())
            }
        }
    }
}

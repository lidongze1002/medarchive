package com.mvp.medarchive.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.mvp.medarchive.data.db.MedReminder
import java.time.LocalTime
import java.time.ZonedDateTime

object ReminderScheduler {

    fun parseTimes(times: String): List<LocalTime> =
        times.split(',', '，')
            .mapNotNull { runCatching { LocalTime.parse(it.trim()) }.getOrNull() }
            .distinct()
            .sorted()

    fun schedule(context: Context, reminder: MedReminder) {
        cancel(context, reminder)
        if (!reminder.enabled) return
        parseTimes(reminder.times).forEachIndexed { index, time ->
            scheduleAt(context, reminder, time, index)
        }
    }

    fun scheduleAll(context: Context, reminders: List<MedReminder>) {
        reminders.forEach { schedule(context, it) }
    }

    fun cancel(context: Context, reminder: MedReminder) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        parseTimes(reminder.times).forEachIndexed { index, time ->
            manager.cancel(pendingIntent(context, reminder.id, time, index, reminder))
        }
    }

    private fun scheduleAt(context: Context, reminder: MedReminder, time: LocalTime, index: Int) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val pi = pendingIntent(context, reminder.id, time, index, reminder)
        val trigger = nextTriggerMillis(time)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !manager.canScheduleExactAlarms()) {
                manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            } else {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
            }
        } catch (e: SecurityException) {
            manager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pi)
        }
    }

    fun nextTriggerMillis(time: LocalTime, now: ZonedDateTime = ZonedDateTime.now()): Long {
        var target = now.toLocalDate().atTime(time).atZone(now.zone)
        if (!target.isAfter(now)) target = target.plusDays(1)
        return target.toInstant().toEpochMilli()
    }

    private fun pendingIntent(
        context: Context,
        reminderId: Long,
        time: LocalTime,
        index: Int,
        reminder: MedReminder
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_ID, reminderId)
            putExtra(ReminderReceiver.EXTRA_DRUG, reminder.drug)
            putExtra(ReminderReceiver.EXTRA_DOSAGE, reminder.dosage)
            putExtra(ReminderReceiver.EXTRA_TIME, time.toString())
        }
        val requestCode = (reminderId * 100 + index).toInt()
        return PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }
}

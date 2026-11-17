package com.mvp.medarchive.ui.reminder

import com.mvp.medarchive.data.db.MedDose
import com.mvp.medarchive.data.db.MedReminder
import com.mvp.medarchive.reminder.ReminderScheduler
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

data class Adherence(val taken: Int, val scheduled: Int) {
    val text: String get() = "近 7 天 $taken/$scheduled 次"
}

/**
 * 近 7 天依从率：从提醒创建日（或 7 天前，取较晚者）算到今天的应服次数。
 */
fun adherenceFor(reminder: MedReminder, doses: List<MedDose>, today: LocalDate): Adherence {
    val times = ReminderScheduler.parseTimes(reminder.times)
    if (times.isEmpty()) return Adherence(0, 0)
    val createdDay = runCatching {
        Instant.ofEpochMilli(reminder.createdAt).atZone(ZoneId.systemDefault()).toLocalDate()
    }.getOrDefault(today)
    val start = maxOf(today.minusDays(6), createdDay)
    if (start.isAfter(today)) return Adherence(0, 0)
    val days = ChronoUnit.DAYS.between(start, today).toInt() + 1
    val scheduled = times.size * days
    val taken = doses.count {
        it.reminderId == reminder.id &&
            it.status == "taken" &&
            it.dateEpochDay in start.toEpochDay()..today.toEpochDay()
    }.coerceAtMost(scheduled)
    return Adherence(taken, scheduled)
}

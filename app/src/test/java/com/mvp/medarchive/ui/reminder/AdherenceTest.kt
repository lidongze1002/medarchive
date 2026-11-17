package com.mvp.medarchive.ui.reminder

import com.mvp.medarchive.data.db.MedDose
import com.mvp.medarchive.data.db.MedReminder
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AdherenceTest {

    private val today = LocalDate.of(2026, 11, 17)

    private fun createdDaysAgo(days: Long): Long =
        today.minusDays(days).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()

    private fun reminder(times: String, createdDaysAgo: Long = 6): MedReminder =
        MedReminder(
            id = 1,
            memberId = 1,
            drug = "缬沙坦",
            times = times,
            createdAt = createdDaysAgo(createdDaysAgo)
        )

    private fun dose(day: LocalDate, time: String, reminderId: Long = 1): MedDose =
        MedDose(
            reminderId = reminderId,
            memberId = 1,
            dateEpochDay = day.toEpochDay(),
            time = time,
            status = "taken"
        )

    @Test
    fun countsTwoTimesForSevenDays() {
        val r = reminder("08:00,20:00")
        val doses = listOf(dose(today, "08:00"), dose(today.minusDays(1), "20:00"))
        val a = adherenceFor(r, doses, today)
        assertEquals(2, a.taken)
        assertEquals(14, a.scheduled)
        assertEquals("近 7 天 2/14 次", a.text)
    }

    @Test
    fun scheduledStartsFromCreationDay() {
        val r = reminder("08:00", createdDaysAgo = 2)
        assertEquals(3, adherenceFor(r, emptyList(), today).scheduled)
    }

    @Test
    fun ignoresOtherRemindersAndOlderDoses() {
        val r = reminder("08:00")
        val doses = listOf(
            dose(today, "08:00"),
            dose(today, "08:00", reminderId = 2),
            dose(today.minusDays(10), "08:00")
        )
        assertEquals(1, adherenceFor(r, doses, today).taken)
    }

    @Test
    fun neverExceedsScheduled() {
        val r = reminder("08:00", createdDaysAgo = 0)
        val doses = List(5) { dose(today, "08:00") }
        val a = adherenceFor(r, doses, today)
        assertEquals(1, a.scheduled)
        assertEquals(1, a.taken)
    }

    @Test
    fun emptyTimesHasZeroScheduled() {
        assertEquals(0, adherenceFor(reminder(""), emptyList(), today).scheduled)
    }
}

package com.mvp.medarchive.reminder

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

class ReminderSchedulerTest {

    private val zone: ZoneId = ZoneId.of("Asia/Shanghai")

    @Test
    fun parseTimesSortsAndDeduplicates() {
        val times = ReminderScheduler.parseTimes("20:00, 08:00,08:00")
        assertEquals(listOf(LocalTime.of(8, 0), LocalTime.of(20, 0)), times)
    }

    @Test
    fun parseTimesIgnoresGarbage() {
        assertTrue(ReminderScheduler.parseTimes("abc,,").isEmpty())
    }

    @Test
    fun nextTriggerIsTodayWhenTimeStillAhead() {
        val now = ZonedDateTime.of(2026, 11, 17, 7, 30, 0, 0, zone)
        val trigger = ReminderScheduler.nextTriggerMillis(LocalTime.of(8, 0), now)
        val expected = ZonedDateTime.of(2026, 11, 17, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, trigger)
    }

    @Test
    fun nextTriggerIsTomorrowWhenTimeAlreadyPassed() {
        val now = ZonedDateTime.of(2026, 11, 17, 9, 0, 0, 0, zone)
        val trigger = ReminderScheduler.nextTriggerMillis(LocalTime.of(8, 0), now)
        val expected = ZonedDateTime.of(2026, 11, 18, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, trigger)
    }

    @Test
    fun nextTriggerIsTomorrowAtExactSameMinute() {
        val now = ZonedDateTime.of(2026, 11, 17, 8, 0, 0, 0, zone)
        val trigger = ReminderScheduler.nextTriggerMillis(LocalTime.of(8, 0), now)
        val expected = ZonedDateTime.of(2026, 11, 18, 8, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(expected, trigger)
    }
}

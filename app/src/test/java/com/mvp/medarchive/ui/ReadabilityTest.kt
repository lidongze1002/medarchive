package com.mvp.medarchive.ui

import com.mvp.medarchive.data.db.Record
import com.mvp.medarchive.ui.vitals.extractVitals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ReadabilityTest {

    @Test
    fun humanDateUsesRelativeWords() {
        val today = LocalDate.of(2026, 11, 17)
        assertEquals("今天", humanDate(today.toEpochDay(), today))
        assertEquals("昨天", humanDate(today.minusDays(1).toEpochDay(), today))
        assertEquals("3天前", humanDate(today.minusDays(3).toEpochDay(), today))
        assertEquals("10月1日", humanDate(LocalDate.of(2026, 10, 1).toEpochDay(), today))
        assertEquals("2025年12月31日", humanDate(LocalDate.of(2025, 12, 31).toEpochDay(), today))
    }

    @Test
    fun ageIsComputedFromBirthdate() {
        val today = LocalDate.of(2026, 11, 17)
        assertEquals(36, ageFromBirthdate("1990-01-01", today))
        assertEquals(36, ageFromBirthdate("1990/1/1", today))
        assertEquals(68, ageFromBirthdate("1958年3月12日", today))
        assertNull(ageFromBirthdate("", today))
        assertNull(ageFromBirthdate("不是日期", today))
    }

    @Test
    fun parsesBloodPressureFromNotes() {
        val r = Record(
            memberId = 1,
            type = "检查单",
            title = "复查",
            dateEpochDay = 100,
            notes = "血压 152/96，空腹血糖 6.4"
        )
        val series = extractVitals(listOf(r))
        val bp = series.first { it.metric.key == "bp" }
        assertEquals(152f, bp.points.single().primary, 0.01f)
        assertEquals(96f, bp.points.single().secondary!!, 0.01f)
    }

    @Test
    fun ignoresDateLikeSlashesWithoutBloodPressureContext() {
        val r = Record(memberId = 1, type = "病历", title = "感冒", dateEpochDay = 100, notes = "2026-11-15 复诊")
        assertTrue(extractVitals(listOf(r)).none { it.metric.key == "bp" })
    }

    @Test
    fun parsesGlucoseAndHemoglobin() {
        val r = Record(
            memberId = 1,
            type = "检查单",
            title = "体检",
            dateEpochDay = 200,
            notes = "空腹血糖 5.6 mmol/L，血红蛋白 135 g/L"
        )
        val series = extractVitals(listOf(r))
        assertEquals(5.6f, series.first { it.metric.key == "glucose" }.points.single().primary, 0.01f)
        assertEquals(135f, series.first { it.metric.key == "hb" }.points.single().primary, 0.01f)
    }

    @Test
    fun sortsPointsAndDropsDuplicatesOnSameDay() {
        val rs = listOf(
            Record(memberId = 1, type = "检查单", title = "复查", dateEpochDay = 300, notes = "血压 128/82"),
            Record(memberId = 1, type = "检查单", title = "复查", dateEpochDay = 100, notes = "血压 150/95"),
            Record(memberId = 1, type = "检查单", title = "复查", dateEpochDay = 300, notes = "血压 128/82")
        )
        val bp = extractVitals(rs).first { it.metric.key == "bp" }
        assertEquals(listOf(100L, 300L), bp.points.map { it.epochDay })
    }

    @Test
    fun rejectsOutOfRangeValues() {
        val r = Record(memberId = 1, type = "检查单", title = "异常", dateEpochDay = 100, notes = "血压 999/999 血糖 99")
        assertTrue(extractVitals(listOf(r)).isEmpty())
    }
}

package com.mvp.medarchive.ui.recordform

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class OcrParserTest {

    private val today = LocalDate.of(2026, 11, 17)

    @Test
    fun extractsDateHospitalAndTitle() {
        val text = """
            市第一人民医院
            血常规检验报告
            检查日期：2026-11-10
            白细胞 6.2
        """.trimIndent()
        val fields = OcrParser.parse(text, today)
        assertEquals(LocalDate.of(2026, 11, 10).toEpochDay(), fields.dateEpochDay)
        assertEquals("市第一人民医院", fields.hospital)
        assertEquals("血常规检验报告", fields.title)
    }

    @Test
    fun parsesChineseDateFormat() {
        val fields = OcrParser.parse("体检报告\n2026年11月10日", today)
        assertEquals(LocalDate.of(2026, 11, 10).toEpochDay(), fields.dateEpochDay)
    }

    @Test
    fun ignoresImpossibleDate() {
        assertNull(OcrParser.parse("2026-13-40 报告", today).dateEpochDay)
        assertNull(OcrParser.parse("1899-01-01", today).dateEpochDay)
    }

    @Test
    fun hospitalAfterColon() {
        val fields = OcrParser.parse("医院：市第一医院\nCT报告", today)
        assertEquals("市第一医院", fields.hospital)
    }

    @Test
    fun doesNotUseDateLineAsTitle() {
        val fields = OcrParser.parse("检查日期：2026-11-10\n超声报告", today)
        assertEquals("超声报告", fields.title)
    }

    @Test
    fun doesNotUseHospitalAsTitle() {
        val fields = OcrParser.parse("市第一人民医院\n复诊", today)
        assertEquals("市第一人民医院", fields.hospital)
        assertNull(fields.title)
    }

    @Test
    fun noFieldsWhenTextHasNoHints() {
        val fields = OcrParser.parse("今天感觉好多了", today)
        assertNull(fields.dateEpochDay)
        assertNull(fields.hospital)
        assertNull(fields.title)
    }
}

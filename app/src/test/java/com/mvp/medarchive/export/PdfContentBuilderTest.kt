package com.mvp.medarchive.export

import com.mvp.medarchive.data.db.Member
import com.mvp.medarchive.data.db.Record
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PdfContentBuilderTest {

    @Test
    fun emptyRecordsStillHasMemberBlock() {
        val m = Member(name = "张三", allergies = "青霉素")
        val c = PdfContentBuilder.build(m, emptyList())
        assertEquals("张三", c.title)
        assertTrue(c.headerLines.any { it.contains("青霉素") })
        assertTrue(c.items.isEmpty())
    }

    @Test
    fun ordersRecordsNewestFirst() {
        val m = Member(name = "张三")
        val r1 = Record(memberId = 1, type = "病历", title = "感冒", dateEpochDay = 100)
        val r2 = Record(memberId = 1, type = "病历", title = "发烧", dateEpochDay = 200)
        val c = PdfContentBuilder.build(m, listOf(r1, r2))
        assertEquals(listOf("发烧", "感冒"), c.items.map { it.title })
    }

    @Test
    fun includesAllergies() {
        val m = Member(name = "张三", allergies = "青霉素")
        val c = PdfContentBuilder.build(m, emptyList())
        assertTrue(c.headerLines.contains("过敏史：青霉素"))
    }

    @Test
    fun eachRecordHasTypeAndTitle() {
        val m = Member(name = "张三")
        val r = Record(memberId = 1, type = "检查单", title = "血常规", dateEpochDay = 100)
        val c = PdfContentBuilder.build(m, listOf(r))
        val item = c.items.first()
        assertTrue(item.title.contains("血常规"))
        assertTrue(item.type.contains("检查单"))
    }
}

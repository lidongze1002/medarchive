package com.mvp.medarchive.ui.recordform

import java.time.LocalDate

data class OcrFields(
    val dateEpochDay: Long? = null,
    val hospital: String? = null,
    val title: String? = null
)

/**
 * 从 OCR 全文里抽取结构化字段：日期、医院、报告标题。
 * 只做“高置信度”抽取，宁可留空，也不乱填。
 */
object OcrParser {

    private val DATE_PATTERN =
        Regex("""(\d{4})\s*[-/.年]\s*(\d{1,2})\s*[-/.月]\s*(\d{1,2})""")

    private val HOSPITAL_KEYWORDS = listOf(
        "医院", "卫生院", "诊所", "卫生服务中心", "体检中心", "医疗中心"
    )

    private val TITLE_KEYWORDS = listOf(
        "报告", "检查", "化验", "检验", "超声", "彩超", "心电图",
        "CT", "核磁", "磁共振", "处方", "门诊", "出院", "诊断", "病历"
    )

    fun parse(text: String, today: LocalDate = LocalDate.now()): OcrFields {
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }

        val date = DATE_PATTERN.find(text)?.let { m ->
            val year = m.groupValues[1].toIntOrNull() ?: return@let null
            val month = m.groupValues[2].toIntOrNull() ?: return@let null
            val day = m.groupValues[3].toIntOrNull() ?: return@let null
            if (year !in 1900..today.year + 1) return@let null
            runCatching { LocalDate.of(year, month, day).toEpochDay() }.getOrNull()
        }

        val hospital = lines.firstNotNullOfOrNull { extractHospital(it) }
        val title = lines.firstNotNullOfOrNull { extractTitle(it, hospital) }

        return OcrFields(date, hospital, title)
    }

    private fun extractHospital(line: String): String? {
        val body = when {
            line.contains("：") -> line.substringAfter("：")
            line.contains(":") -> line.substringAfter(":")
            else -> line
        }.trim()
        val keyword = HOSPITAL_KEYWORDS.firstOrNull { body.contains(it) } ?: return null
        val end = body.indexOf(keyword) + keyword.length
        val start = (end - 14).coerceAtLeast(0)
        val value = body.substring(start, end).trim()
        return value.takeIf { it.length in 3..24 }
    }

    private fun extractTitle(line: String, hospital: String?): String? {
        if (hospital != null && line.contains(hospital)) return null
        if (DATE_PATTERN.containsMatchIn(line)) return null
        if (line.length !in 2..24) return null
        return line.takeIf { l -> TITLE_KEYWORDS.any { l.contains(it, ignoreCase = true) } }
    }
}

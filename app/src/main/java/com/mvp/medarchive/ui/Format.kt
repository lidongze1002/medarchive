package com.mvp.medarchive.ui

import com.mvp.medarchive.ui.i18n.T
import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Date
import java.util.Locale

fun formatEpochDay(day: Long): String =
    LocalDate.ofEpochDay(day).format(DateTimeFormatter.ISO_LOCAL_DATE)

fun formatTime(ms: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

private val monthFormatterZh = DateTimeFormatter.ofPattern("yyyy年M月", Locale.CHINA)
private val monthFormatterEn = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)
private val fullDateFormatterZh = DateTimeFormatter.ofPattern("yyyy年M月d日", Locale.CHINA)
private val fullDateFormatterEn = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH)
private val shortDateFormatterEn = DateTimeFormatter.ofPattern("MMM d", Locale.ENGLISH)

/** 用于分组标题，如 2026年11月 / Nov 2026 */
fun monthLabel(epochDay: Long): String = runCatching {
    val d = LocalDate.ofEpochDay(epochDay)
    d.format(if (com.mvp.medarchive.ui.i18n.Lang.current == "en") monthFormatterEn else monthFormatterZh)
}.getOrDefault("")

/** 详情页的大日期：2026年11月15日 周六 / Sun, Nov 15, 2026 */
fun fullDateWithWeekday(epochDay: Long): String = runCatching {
    val d = LocalDate.ofEpochDay(epochDay)
    if (com.mvp.medarchive.ui.i18n.Lang.current == "en") {
        val weekday = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.ENGLISH)
        "$weekday, ${d.format(fullDateFormatterEn)}"
    } else {
        val weekday = d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.CHINA)
        "${d.format(fullDateFormatterZh)} $weekday"
    }
}.getOrDefault("")

/** 列表里的“人话”日期：今天 / 昨天 / 3天前 / 12月31日 */
fun humanDate(epochDay: Long, today: LocalDate = LocalDate.now()): String = runCatching {
    val d = LocalDate.ofEpochDay(epochDay)
    val days = ChronoUnit.DAYS.between(d, today)
    if (com.mvp.medarchive.ui.i18n.Lang.current == "en") {
        when {
            days == 0L -> "Today"
            days == 1L -> "Yesterday"
            days in 2..6 -> "$days days ago"
            d.year == today.year -> d.format(shortDateFormatterEn)
            else -> d.format(fullDateFormatterEn)
        }
    } else {
        when {
            days == 0L -> "今天"
            days == 1L -> "昨天"
            days in 2..6 -> "${days}天前"
            d.year == today.year -> "${d.monthValue}月${d.dayOfMonth}日"
            else -> d.format(fullDateFormatterZh)
        }
    }
}.getOrDefault(formatEpochDay(epochDay))

/** 从生日算年龄；解析失败返回 null。支持 1990-01-01 / 1990/1/1 / 1990年1月1日 */
fun ageFromBirthdate(birthdate: String, today: LocalDate = LocalDate.now()): Int? {
    val text = birthdate.trim()
    if (text.isEmpty()) return null
    val normalized = text.replace("年", "-").replace("月", "-").replace("日", "")
        .replace("/", "-").replace(".", "-")
    val parts = normalized.split("-").filter { it.isNotBlank() }
    if (parts.size < 3) return null
    val (y, m, d) = runCatching {
        Triple(parts[0].toInt(), parts[1].toInt(), parts[2].toInt())
    }.getOrNull() ?: return null
    val birth = runCatching { LocalDate.of(y, m, d) }.getOrNull() ?: return null
    if (birth.isAfter(today)) return null
    return ChronoUnit.YEARS.between(birth, today).toInt()
}

fun ageText(age: Int): String = T("$age 岁", "$age yrs")

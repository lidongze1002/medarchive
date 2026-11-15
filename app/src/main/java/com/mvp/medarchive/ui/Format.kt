package com.mvp.medarchive.ui

import java.text.SimpleDateFormat
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale

fun formatEpochDay(day: Long): String =
    LocalDate.ofEpochDay(day).format(DateTimeFormatter.ISO_LOCAL_DATE)

fun formatTime(ms: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()).format(Date(ms))

package com.mvp.medarchive.ui.vitals

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mvp.medarchive.data.db.Record
import com.mvp.medarchive.ui.common.AppCard
import java.time.LocalDate

data class VitalMetric(val key: String, val label: String, val unit: String)

data class VitalPoint(val epochDay: Long, val primary: Float, val secondary: Float? = null)

data class VitalSeries(val metric: VitalMetric, val points: List<VitalPoint>)

private val BP = VitalMetric("bp", "血压", "mmHg")
private val GLUCOSE = VitalMetric("glucose", "血糖", "mmol/L")
private val HEMOGLOBIN = VitalMetric("hb", "血红蛋白", "g/L")

private val BP_PAIR = Regex("""(\d{2,3})\s*[/／]\s*(\d{2,3})""")
private val GLUCOSE_VALUE = Regex("""(?:血糖|空腹血糖|餐后血糖)[^\d]{0,6}(\d{1,2}(?:[.．]\d+)?)""")
private val HB_VALUE = Regex("""(?:血红蛋白|Hb|HGB)[^\d]{0,6}(\d{2,3})""", RegexOption.IGNORE_CASE)

/**
 * 从记录标题/备注里抽取血压、血糖、血红蛋白，生成可画趋势的数据。
 * 例：「血压 135/85」「空腹血糖 5.6」「血红蛋白 130」。
 */
fun extractVitals(records: List<Record>): List<VitalSeries> {
    val bp = mutableListOf<VitalPoint>()
    val glucose = mutableListOf<VitalPoint>()
    val hb = mutableListOf<VitalPoint>()

    records.forEach { r ->
        val text = r.title + "\n" + r.notes
        val day = r.dateEpochDay

        if (text.contains("血压") || text.contains("BP")) {
            BP_PAIR.find(text)?.let { m ->
                val sys = m.groupValues[1].toFloatOrNull()
                val dia = m.groupValues[2].toFloatOrNull()
                if (sys != null && dia != null && sys in 70f..250f && dia in 40f..150f) {
                    bp += VitalPoint(day, sys, dia)
                }
            }
        }
        GLUCOSE_VALUE.find(text)?.let { m ->
            val v = m.groupValues[1].replace('．', '.').toFloatOrNull()
            if (v != null && v in 2f..30f) glucose += VitalPoint(day, v)
        }
        HB_VALUE.find(text)?.let { m ->
            val v = m.groupValues[1].toFloatOrNull()
            if (v != null && v in 30f..220f) hb += VitalPoint(day, v)
        }
    }

    fun series(metric: VitalMetric, list: List<VitalPoint>): VitalSeries? =
        list.sortedBy { it.epochDay }
            .distinctBy { it.epochDay to it.primary }
            .takeIf { it.isNotEmpty() }
            ?.let { VitalSeries(metric, it) }

    return listOfNotNull(series(BP, bp), series(GLUCOSE, glucose), series(HEMOGLOBIN, hb))
}

@Composable
fun VitalsCard(records: List<Record>) {
    val series = remember(records) { extractVitals(records) }
    var selectedKey by remember(series) { mutableStateOf(series.firstOrNull()?.metric?.key) }
    val current = series.firstOrNull { it.metric.key == selectedKey } ?: series.firstOrNull()

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                "健康趋势",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            if (current != null) {
                Text(
                    "共 ${current.points.size} 次",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (current == null) {
            Spacer(Modifier.height(6.dp))
            Text(
                "在备注里写「血压 135/85」「血糖 5.6」或「血红蛋白 130」，这里会自动生成趋势。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                series.forEach { s ->
                    FilterChip(
                        selected = s.metric.key == current.metric.key,
                        onClick = { selectedKey = s.metric.key },
                        label = { Text(s.metric.label) }
                    )
                }
            }

            val last = current.points.last()
            val prev = current.points.getOrNull(current.points.size - 2)
            Spacer(Modifier.height(10.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    formatValue(last.primary),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                if (last.secondary != null) {
                    Text(
                        " / ${formatValue(last.secondary)}",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(
                    current.metric.unit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 3.dp)
                )
                Spacer(Modifier.weight(1f))
                if (prev != null) {
                    val diff = last.primary - prev.primary
                    if (kotlin.math.abs(diff) >= 0.05f) {
                        Text(
                            (if (diff > 0) "较上次 +" else "较上次 ") + formatValue(diff),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            VitalsChart(current)
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth()) {
                val shown = current.points.takeLast(MAX_POINTS)
                Text(
                    shortDate(shown.first().epochDay),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.weight(1f))
                if (last.secondary != null) {
                    LegendDot(MaterialTheme.colorScheme.primary, "收缩压")
                    Spacer(Modifier.width(10.dp))
                    LegendDot(MaterialTheme.colorScheme.tertiary, "舒张压")
                    Spacer(Modifier.weight(1f))
                }
                Text(
                    shortDate(shown.last().epochDay),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

private const val MAX_POINTS = 12

@Composable
private fun LegendDot(color: androidx.compose.ui.graphics.Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        androidx.compose.foundation.layout.Box(
            Modifier
                .size(7.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun VitalsChart(series: VitalSeries) {
    val points = series.points.takeLast(MAX_POINTS)
    val values = points.map { it.primary } + points.mapNotNull { it.secondary }
    var min = values.minOrNull() ?: 0f
    var max = values.maxOrNull() ?: 1f
    if (max - min < 1f) {
        min -= 1f
        max += 1f
    }
    val pad = (max - min) * 0.18f
    min -= pad
    max += pad

    val lineColor = MaterialTheme.colorScheme.primary
    val secondColor = MaterialTheme.colorScheme.tertiary
    val gridColor = MaterialTheme.colorScheme.outlineVariant

    Canvas(
        Modifier
            .fillMaxWidth()
            .height(132.dp)
    ) {
        val top = 6.dp.toPx()
        val bottom = size.height - 6.dp.toPx()
        val n = points.size
        fun x(i: Int): Float =
            if (n <= 1) size.width / 2f else i * size.width / (n - 1)

        fun y(v: Float): Float =
            bottom - (v - min) / (max - min) * (bottom - top)

        repeat(3) { i ->
            val gy = top + (bottom - top) * i / 2f
            drawLine(
                color = gridColor,
                start = Offset(0f, gy),
                end = Offset(size.width, gy),
                strokeWidth = 1.dp.toPx()
            )
        }

        fun drawSeries(selector: (VitalPoint) -> Float?, color: androidx.compose.ui.graphics.Color) {
            val path = Path()
            var started = false
            points.forEachIndexed { i, p ->
                val v = selector(p) ?: return@forEachIndexed
                val px = x(i)
                val py = y(v)
                if (!started) {
                    path.moveTo(px, py)
                    started = true
                } else {
                    path.lineTo(px, py)
                }
                drawCircle(color = color, radius = 3.dp.toPx(), center = Offset(px, py))
            }
            if (started) {
                drawPath(
                    path = path,
                    color = color,
                    style = Stroke(width = 2.5.dp.toPx(), cap = StrokeCap.Round)
                )
            }
        }

        if (points.any { it.secondary != null }) {
            drawSeries({ it.secondary }, secondColor)
        }
        drawSeries({ it.primary }, lineColor)
    }
}

private fun formatValue(v: Float): String =
    if (kotlin.math.abs(v - v.toInt()) < 0.05f) v.toInt().toString() else String.format("%.1f", v)

private fun shortDate(epochDay: Long): String = runCatching {
    LocalDate.ofEpochDay(epochDay).let { "${it.monthValue}/${it.dayOfMonth}" }
}.getOrDefault("")

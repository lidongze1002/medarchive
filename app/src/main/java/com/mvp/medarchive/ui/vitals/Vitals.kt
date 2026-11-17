package com.mvp.medarchive.ui.vitals

import com.mvp.medarchive.ui.i18n.T

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
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.mvp.medarchive.data.db.Record
import com.mvp.medarchive.health.HealthConnectSource
import com.mvp.medarchive.ui.common.AppCard
import kotlinx.coroutines.launch
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
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val source = remember { HealthConnectSource(ctx) }
    var healthSeries by remember { mutableStateOf<List<VitalSeries>>(emptyList()) }
    var syncing by remember { mutableStateOf(false) }
    var healthMessage by remember { mutableStateOf<String?>(null) }

    suspend fun loadHealth() {
        syncing = true
        runCatching { source.readLast(30) }
            .onSuccess { list ->
                healthSeries = list
                healthMessage = if (list.isEmpty()) T(
                    "已连接健康 App，但最近 30 天没有读到数据",
                    "Connected, but no health data in the last 30 days"
                ) else null
            }
            .onFailure { e ->
                healthMessage = if (e is SecurityException) T(
                    "没有读取健康数据的权限",
                    "No permission to read health data"
                ) else T("读取健康数据失败", "Failed to read health data")
            }
        syncing = false
    }

    val permissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.health.connect.client.PermissionController.createRequestPermissionResultContract()
    ) { granted ->
        if (granted.containsAll(source.permissions)) {
            scope.launch { loadHealth() }
        } else {
            syncing = false
            healthMessage = T("未获得健康数据权限", "Health data permission not granted")
        }
    }

    LaunchedEffect(Unit) {
        if (source.isAvailable()) {
            val granted = source.grantedPermissions()
            if (granted.containsAll(source.permissions)) loadHealth()
        }
    }

    val baseSeries = remember(records) { extractVitals(records) }
    val series = remember(baseSeries, healthSeries) { baseSeries + healthSeries }
    var selectedKey by remember { mutableStateOf<String?>(null) }
    val current = series.firstOrNull { it.metric.key == selectedKey } ?: series.firstOrNull()

    AppCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(T("健康趋势", "Health trends"),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    if (!source.isAvailable()) {
                        healthMessage = T(
                            "需要「健康连接」：Android 14+ 系统自带，旧系统需单独安装",
                            "Health Connect required: built in on Android 14+, install it on older versions"
                        )
                        return@TextButton
                    }
                    scope.launch {
                        val granted = source.grantedPermissions()
                        if (granted.containsAll(source.permissions)) {
                            loadHealth()
                        } else {
                            syncing = true
                            permissionLauncher.launch(source.permissions)
                        }
                    }
                },
                enabled = !syncing
            ) { Text(if (syncing) T("同步中…", "Syncing…") else T("同步手环", "Sync band")) }
        }

        if (current != null) {
            Text(
                T(
                    "共 ${current.points.size} 次 · 点上方指标切换",
                    "${current.points.size} readings · tap a metric above"
                ),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        healthMessage?.let { message ->
            Spacer(Modifier.height(2.dp))
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error
            )
        }

        if (current == null) {
            Spacer(Modifier.height(6.dp))
            Text(T("在备注里写「血压 135/85」「血糖 5.6」或「血红蛋白 130」会自动生成趋势；也可以点右上角「同步手环」读取体脂秤/手环数据。", "Write “BP 135/85”, “glucose 5.6” or “hemoglobin 130” in notes to build trends, or tap “Sync band” to import scale/band data."),
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
                            (if (diff > 0) T("较上次 +", "vs last +") else T("较上次 ", "vs last ")) + formatValue(diff),
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
            if (HealthConnectSource.SOURCE_KEYS.contains(current.metric.key)) {
                Spacer(Modifier.height(4.dp))
                Text(T("数据来自健康 App（体脂秤/手环），最近 30 天", "From health apps (scale/band), last 30 days"),
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

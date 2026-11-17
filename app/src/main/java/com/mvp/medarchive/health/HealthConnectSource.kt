package com.mvp.medarchive.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BloodGlucoseRecord
import androidx.health.connect.client.records.BloodPressureRecord
import androidx.health.connect.client.records.HeartRateRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import com.mvp.medarchive.ui.vitals.VitalMetric
import com.mvp.medarchive.ui.vitals.VitalPoint
import com.mvp.medarchive.ui.vitals.VitalSeries
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * 从 Health Connect 读取小米体脂秤 / 华为手环等健康 App 同步过来的数据。
 * Android 14+ 系统自带；旧系统需要单独安装「健康连接」。
 */
class HealthConnectSource(private val context: Context) {

    fun isAvailable(): Boolean =
        runCatching {
            HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
        }.getOrDefault(false)

    val permissions: Set<String> = setOf(
        HealthPermission.getReadPermission(WeightRecord::class),
        HealthPermission.getReadPermission(HeartRateRecord::class),
        HealthPermission.getReadPermission(BloodPressureRecord::class),
        HealthPermission.getReadPermission(BloodGlucoseRecord::class)
    )

    suspend fun grantedPermissions(): Set<String> =
        runCatching {
            HealthConnectClient.getOrCreate(context).permissionController.getGrantedPermissions()
        }.getOrDefault(emptySet())

    suspend fun readLast(days: Long = 30): List<VitalSeries> {
        val client = HealthConnectClient.getOrCreate(context)
        val end = Instant.now()
        val start = end.minusSeconds(days * 24 * 3600)
        val filter = TimeRangeFilter.between(start, end)
        val zone = ZoneId.systemDefault()
        val result = mutableListOf<VitalSeries>()

        val weights = client.readRecords(
            ReadRecordsRequest(WeightRecord::class, timeRangeFilter = filter)
        ).records
        val weightPoints = weights
            .groupBy { LocalDate.ofInstant(it.time, zone) }
            .mapNotNull { (day, list) ->
                val latest = list.maxByOrNull { it.time } ?: return@mapNotNull null
                VitalPoint(day.toEpochDay(), latest.weight.inKilograms.toFloat())
            }
            .sortedBy { it.epochDay }
        if (weightPoints.isNotEmpty()) result += VitalSeries(HC_WEIGHT, weightPoints)

        val heartRates = client.readRecords(
            ReadRecordsRequest(HeartRateRecord::class, timeRangeFilter = filter)
        ).records
        val heartPoints = heartRates
            .groupBy { LocalDate.ofInstant(it.startTime, zone) }
            .mapNotNull { (day, list) ->
                val samples = list.flatMap { record -> record.samples }.map { it.beatsPerMinute }
                if (samples.isEmpty()) null
                else VitalPoint(day.toEpochDay(), samples.average().toFloat())
            }
            .sortedBy { it.epochDay }
        if (heartPoints.isNotEmpty()) result += VitalSeries(HC_HEART_RATE, heartPoints)

        val bloodPressures = client.readRecords(
            ReadRecordsRequest(BloodPressureRecord::class, timeRangeFilter = filter)
        ).records
        val bpPoints = bloodPressures
            .groupBy { LocalDate.ofInstant(it.time, zone) }
            .mapNotNull { (day, list) ->
                val latest = list.maxByOrNull { it.time } ?: return@mapNotNull null
                VitalPoint(
                    day.toEpochDay(),
                    latest.systolic.inMillimetersOfMercury.toFloat(),
                    latest.diastolic.inMillimetersOfMercury.toFloat()
                )
            }
            .sortedBy { it.epochDay }
        if (bpPoints.isNotEmpty()) result += VitalSeries(HC_BLOOD_PRESSURE, bpPoints)

        val glucose = client.readRecords(
            ReadRecordsRequest(BloodGlucoseRecord::class, timeRangeFilter = filter)
        ).records
        val glucosePoints = glucose
            .groupBy { LocalDate.ofInstant(it.time, zone) }
            .mapNotNull { (day, list) ->
                val latest = list.maxByOrNull { it.time } ?: return@mapNotNull null
                VitalPoint(day.toEpochDay(), latest.level.inMillimolesPerLiter.toFloat())
            }
            .sortedBy { it.epochDay }
        if (glucosePoints.isNotEmpty()) result += VitalSeries(HC_GLUCOSE, glucosePoints)

        return result
    }

    companion object {
        val HC_WEIGHT = VitalMetric("hc_weight", "体重·手环", "kg")
        val HC_HEART_RATE = VitalMetric("hc_hr", "心率·手环", "bpm")
        val HC_BLOOD_PRESSURE = VitalMetric("hc_bp", "血压·手环", "mmHg")
        val HC_GLUCOSE = VitalMetric("hc_glucose", "血糖·手环", "mmol/L")

        val SOURCE_KEYS = setOf(
            HC_WEIGHT.key, HC_HEART_RATE.key, HC_BLOOD_PRESSURE.key, HC_GLUCOSE.key
        )
    }
}

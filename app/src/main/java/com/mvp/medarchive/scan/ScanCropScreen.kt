package com.mvp.medarchive.scan

import android.graphics.Bitmap
import android.graphics.PointF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * 扫描校正页：拖动四个角对齐纸张边缘，确认后做透视校正 + 增强，得到平整、清晰的扫描件。
 */
@Composable
fun ScanCropScreen(
    imagePath: String,
    onCancel: () -> Unit,
    onDone: (ByteArray) -> Unit
) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val bitmap = remember(imagePath) { ScanProcessor.loadForPreview(imagePath) }
    var corners by remember {
        mutableStateOf(
            listOf(
                PointF(0.08f, 0.08f),
                PointF(0.92f, 0.08f),
                PointF(0.92f, 0.92f),
                PointF(0.08f, 0.92f)
            )
        )
    }
    var enhance by remember { mutableStateOf(true) }
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    var dragging by remember { mutableStateOf(-1) }
    var processing by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = onCancel) { Text("取消", color = Color.White) }
            Spacer(Modifier.weight(1f))
            Text(
                "拖动四角对齐纸张",
                color = Color.White.copy(alpha = 0.8f),
                style = MaterialTheme.typography.bodySmall
            )
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = {
                    val bmp = bitmap ?: return@TextButton
                    processing = true
                    scope.launch {
                        val bytes = withContext(Dispatchers.Default) {
                            // 用更高分辨率重载一次再校正，保证报告文字清晰
                            val full = ScanProcessor.loadForPreview(imagePath, 2600)
                            if (full != null) {
                                val out = ScanProcessor.process(full, corners, enhance)
                                full.recycle()
                                out
                            } else {
                                null
                            }
                        }
                        processing = false
                        if (bytes != null) {
                            onDone(bytes)
                        } else {
                            android.widget.Toast.makeText(ctx, "处理失败，请重试", android.widget.Toast.LENGTH_SHORT).show()
                        }
                    }
                },
                enabled = bitmap != null && !processing
            ) {
                Text(if (processing) "处理中…" else "完成", color = Color(0xFF5EEAD4))
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            if (bitmap == null) {
                Text(
                    "图片加载失败",
                    color = Color.White,
                    modifier = Modifier.align(Alignment.Center)
                )
            } else {
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .onSizeChanged { canvasSize = it }
                        .pointerInput(bitmap, canvasSize) {
                            detectDragGestures(
                                onDragStart = { pos ->
                                    dragging = nearestCorner(pos, corners, canvasSize, bitmap)
                                },
                                onDragEnd = { dragging = -1 },
                                onDragCancel = { dragging = -1 }
                            ) { change, drag ->
                                val index = dragging
                                val rect = ScanProcessor.fitRect(
                                    canvasSize.width, canvasSize.height,
                                    bitmap.width, bitmap.height
                                )
                                if (index >= 0 && rect.width() > 0f) {
                                    change.consume()
                                    val dx = drag.x / rect.width()
                                    val dy = drag.y / rect.height()
                                    corners = corners.toMutableList().also { list ->
                                        val c = list[index]
                                        list[index] = PointF(
                                            (c.x + dx).coerceIn(0f, 1f),
                                            (c.y + dy).coerceIn(0f, 1f)
                                        )
                                    }
                                }
                            }
                        }
                ) {
                    val rect = ScanProcessor.fitRect(
                        size.width.roundToInt(), size.height.roundToInt(),
                        bitmap.width, bitmap.height
                    )
                    drawImage(
                        image = bitmap.asImageBitmap(),
                        dstOffset = IntOffset(rect.left.roundToInt(), rect.top.roundToInt()),
                        dstSize = IntSize(rect.width().roundToInt(), rect.height().roundToInt())
                    )

                    val points = corners.map {
                        Offset(
                            rect.left + it.x * rect.width(),
                            rect.top + it.y * rect.height()
                        )
                    }
                    val path = Path().apply {
                        moveTo(points[0].x, points[0].y)
                        lineTo(points[1].x, points[1].y)
                        lineTo(points[2].x, points[2].y)
                        lineTo(points[3].x, points[3].y)
                        close()
                    }
                    drawPath(path, Color(0xFF5EEAD4), style = Stroke(width = 2.dp.toPx()))
                    points.forEachIndexed { i, p ->
                        drawCircle(Color.White, 9.dp.toPx(), p)
                        drawCircle(
                            if (dragging == i) Color(0xFF0E9384) else Color(0xFF0E9384).copy(alpha = 0.85f),
                            6.dp.toPx(),
                            p
                        )
                    }
                }
            }
        }

        Row(
            Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("增强对比（推荐）", color = Color.White)
            Spacer(Modifier.weight(1f))
            Switch(checked = enhance, onCheckedChange = { enhance = it })
        }
    }
}

private fun nearestCorner(
    pos: Offset,
    corners: List<PointF>,
    canvasSize: IntSize,
    bitmap: Bitmap
): Int {
    val rect = ScanProcessor.fitRect(canvasSize.width, canvasSize.height, bitmap.width, bitmap.height)
    if (rect.width() <= 0f) return -1
    var best = -1
    var bestDistance = 90f
    corners.forEachIndexed { i, c ->
        val x = rect.left + c.x * rect.width()
        val y = rect.top + c.y * rect.height()
        val d = hypot(pos.x - x, pos.y - y)
        if (d < bestDistance) {
            bestDistance = d
            best = i
        }
    }
    return best
}

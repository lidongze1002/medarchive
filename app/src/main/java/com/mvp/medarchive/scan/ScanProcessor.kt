package com.mvp.medarchive.scan

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.Matrix
import android.graphics.RectF
import java.io.ByteArrayOutputStream
import java.io.File
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * 离线“扫描件”处理：
 * 1) 从拍到的照片按四角做透视校正（把斜着拍的纸拉正）；
 * 2) 去色 + 提对比度，让纸质报告更接近扫描仪输出。
 * 不依赖任何在线服务。
 */
object ScanProcessor {

    fun loadForPreview(path: String, maxSide: Int = 1800): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(path, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (bounds.outWidth / sample > maxSide || bounds.outHeight / sample > maxSide) {
            sample *= 2
        }
        return BitmapFactory.decodeFile(path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    /**
     * @param corners 归一化坐标（0..1），顺序：左上、右上、右下、左下
     */
    fun process(source: Bitmap, corners: List<PointF>, enhance: Boolean = true): ByteArray? {
        if (corners.size != 4) return null
        val src = corners.map {
            PointF(it.x * source.width, it.y * source.height)
        }

        val width = max(distance(src[0], src[1]), distance(src[3], src[2]))
        val height = max(distance(src[0], src[3]), distance(src[1], src[2]))
        if (width < 32f || height < 32f) return null

        val outW = width.roundToInt().coerceIn(64, 3000)
        val outH = height.roundToInt().coerceIn(64, 3000)

        val dst = floatArrayOf(
            0f, 0f,
            outW.toFloat(), 0f,
            outW.toFloat(), outH.toFloat(),
            0f, outH.toFloat()
        )
        val from = floatArrayOf(
            src[0].x, src[0].y,
            src[1].x, src[1].y,
            src[2].x, src[2].y,
            src[3].x, src[3].y
        )
        val matrix = Matrix()
        if (!matrix.setPolyToPoly(from, 0, dst, 0, 4)) return null

        val warped = Bitmap.createBitmap(outW, outH, Bitmap.Config.ARGB_8888)
        Canvas(warped).drawBitmap(source, matrix, Paint(Paint.FILTER_BITMAP_FLAG))
        val result = if (enhance) ImageEnhancer.enhanceBitmap(warped) else warped
        if (result !== warped) warped.recycle()
        val bytes = ImageEnhancer.toJpeg(result)
        result.recycle()
        return bytes
    }

    private fun distance(a: PointF, b: PointF): Float = hypot(a.x - b.x, a.y - b.y)

    /** 在给定画布内按图片比例计算绘制区域（居中、留边）。 */
    fun fitRect(canvasWidth: Int, canvasHeight: Int, imageWidth: Int, imageHeight: Int): RectF {
        val cw = canvasWidth.toFloat()
        val ch = canvasHeight.toFloat()
        if (cw <= 0f || ch <= 0f || imageWidth <= 0 || imageHeight <= 0) return RectF()
        val scale = minOf(cw / imageWidth, ch / imageHeight)
        val w = imageWidth * scale
        val h = imageHeight * scale
        val left = (cw - w) / 2f
        val top = (ch - h) / 2f
        return RectF(left, top, left + w, top + h)
    }
}

object ImageEnhancer {

    fun enhanceFile(file: File, contrast: Float = 1.35f): ByteArray? {
        val src = runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull() ?: return null
        val out = enhanceBitmap(src, contrast)
        if (out !== src) src.recycle()
        val bytes = toJpeg(out)
        out.recycle()
        return bytes
    }

    fun enhanceBitmap(src: Bitmap, contrast: Float = 1.35f): Bitmap {
        val output = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            colorFilter = ColorMatrixColorFilter(colorMatrix(contrast))
        }
        Canvas(output).drawBitmap(src, 0f, 0f, paint)
        return output
    }

    private fun colorMatrix(contrast: Float): ColorMatrix {
        val gray = ColorMatrix().apply { setSaturation(0f) }
        val offset = (1f - contrast) * 128f
        gray.postConcat(
            ColorMatrix(
                floatArrayOf(
                    contrast, 0f, 0f, 0f, offset,
                    0f, contrast, 0f, 0f, offset,
                    0f, 0f, contrast, 0f, offset,
                    0f, 0f, 0f, 1f, 0f
                )
            )
        )
        return gray
    }

    fun toJpeg(bitmap: Bitmap, quality: Int = 90): ByteArray {
        val out = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }
}

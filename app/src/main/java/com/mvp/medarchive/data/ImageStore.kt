package com.mvp.medarchive.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.File

class ImageStore(context: Context) {

    private val dir: File = File(context.filesDir, "media").apply { mkdirs() }

    /** 入库统一压缩 + 纠正方向，避免大图占空间、列表解码卡顿。 */
    fun save(bytes: ByteArray): String {
        val normalized = runCatching { compress(bytes) }.getOrNull() ?: bytes
        return saveRaw(normalized)
    }

    /** 已经处理/压缩过的图片直接落盘，避免二次压缩掉画质（扫描件、增强件）。 */
    fun saveRaw(bytes: ByteArray): String {
        val name = "img_${System.currentTimeMillis()}_${bytes.contentHashCode()}.jpg"
        File(dir, name).writeBytes(bytes)
        return name
    }

    private fun compress(bytes: ByteArray): ByteArray {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return bytes

        var sample = 1
        while (bounds.outWidth / sample > MAX_SIDE || bounds.outHeight / sample > MAX_SIDE) {
            sample *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val decoded = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return bytes

        val rotation = runCatching {
            when (
                ExifInterface(bytes.inputStream()).getAttributeInt(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL
                )
            ) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
        }.getOrDefault(0f)

        val fixed = if (rotation == 0f) {
            decoded
        } else {
            Bitmap.createBitmap(
                decoded, 0, 0, decoded.width, decoded.height,
                Matrix().apply { postRotate(rotation) }, true
            ).also { if (it !== decoded) decoded.recycle() }
        }

        val out = ByteArrayOutputStream()
        fixed.compress(Bitmap.CompressFormat.JPEG, QUALITY, out)
        fixed.recycle()
        return out.toByteArray()
    }

    fun file(rel: String): File = File(dir, rel)

    fun delete(rel: String) {
        File(dir, rel).delete()
    }

    companion object {
        private const val MAX_SIDE = 2400
        private const val QUALITY = 88
    }
}

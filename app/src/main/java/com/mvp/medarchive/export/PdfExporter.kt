package com.mvp.medarchive.export

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.io.FileOutputStream

class PdfExporter(private val context: Context) {

    fun export(content: PdfContent, isPro: Boolean, imageResolver: (Long) -> List<Bitmap>): Uri {
        val doc = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()

        var page = doc.startPage(pageInfo)
        var canvas = page.canvas
        var y = 40f

        fun watermark(c: Canvas) {
            if (isPro) return
            val p = Paint().apply {
                color = Color.parseColor("#99AAAAAA")
                textSize = 10f
            }
            c.drawText("由「家庭病历本」免费版生成 · 升级 Pro 可去水印", 40f, 822f, p)
        }

        watermark(canvas)

        fun newPage() {
            doc.finishPage(page)
            page = doc.startPage(pageInfo)
            canvas = page.canvas
            y = 40f
            watermark(canvas)
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > 800f) newPage()
        }

        val titlePaint = Paint().apply {
            color = Color.BLACK
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
        }
        val headerPaint = Paint().apply { color = Color.BLACK; textSize = 12f }
        val itemPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
        }
        val notePaint = Paint().apply { color = Color.DKGRAY; textSize = 11f }

        canvas.drawText(content.title, 40f, y, titlePaint)
        y += 30f
        content.headerLines.forEach {
            ensureSpace(20f)
            canvas.drawText(it, 40f, y, headerPaint)
            y += 20f
        }
        y += 10f

        content.items.forEach { item ->
            ensureSpace(40f)
            canvas.drawText("• ${item.date}  ${item.type}  ${item.title}", 40f, y, itemPaint)
            y += 18f
            if (item.notes.isNotBlank()) {
                ensureSpace(18f)
                canvas.drawText(item.notes, 40f, y, notePaint)
                y += 16f
            }
            imageResolver(item.recordId).forEach { bmp ->
                val w = 100f
                val h = if (bmp.width > 0) w * bmp.height / bmp.width else 100f
                ensureSpace(h + 8f)
                canvas.drawBitmap(bmp, null, RectF(40f, y, 40f + w, y + h), null)
                y += h + 8f
            }
            y += 6f
        }

        doc.finishPage(page)

        val file = File(context.cacheDir, "member_${System.currentTimeMillis()}.pdf")
        doc.writeTo(FileOutputStream(file))
        doc.close()

        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
    }
}

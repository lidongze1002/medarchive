package com.mvp.medarchive.data

import android.content.Context
import java.io.File

class ImageStore(context: Context) {

    private val dir: File = File(context.filesDir, "media").apply { mkdirs() }

    fun save(bytes: ByteArray): String {
        val name = "img_${System.currentTimeMillis()}_${bytes.contentHashCode()}.jpg"
        File(dir, name).writeBytes(bytes)
        return name
    }

    fun file(rel: String): File = File(dir, rel)

    fun delete(rel: String) {
        File(dir, rel).delete()
    }
}

package com.mvp.medarchive.data

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.mvp.medarchive.data.db.Member
import com.mvp.medarchive.data.db.Record
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class BackupManager(
    private val context: Context,
    private val repository: MedRepository
) {
    companion object {
        const val JSON_NAME = "data.json"
    }

    suspend fun exportZip(): Uri {
        val members = repository.allMembers()
        val root = JSONObject().put("app", "medarchive").put("version", 1)
        val membersArr = JSONArray()
        for (m in members) {
            val mo = JSONObject().apply {
                put("name", m.name)
                put("birthdate", m.birthdate)
                put("bloodType", m.bloodType)
                put("allergies", m.allergies)
                put("notes", m.notes)
                put("avatar", m.avatarPath)
            }
            val recsArr = JSONArray()
            for (r in repository.recordsList(m.id)) {
                val ro = JSONObject().apply {
                    put("type", r.type)
                    put("title", r.title)
                    put("date", LocalDate.ofEpochDay(r.dateEpochDay).toString())
                    put("hospital", r.hospital)
                    put("notes", r.notes)
                }
                val imgs = JSONArray()
                repository.imagePaths(r.id).forEach { imgs.put(it) }
                ro.put("images", imgs)
                recsArr.put(ro)
            }
            mo.put("records", recsArr)
            membersArr.put(mo)
        }
        root.put("members", membersArr)

        val zip = File(context.cacheDir, "medarchive_backup.zip")
        ZipOutputStream(FileOutputStream(zip)).use { zos ->
            zos.putNextEntry(ZipEntry(JSON_NAME))
            zos.write(root.toString().toByteArray(Charsets.UTF_8))
            zos.closeEntry()
            val added = mutableSetOf<String>()
            for (m in members) {
                if (m.avatarPath.isNotBlank() && added.add(m.avatarPath)) {
                    val f = repository.imageStore.file(m.avatarPath)
                    if (f.exists()) {
                        zos.putNextEntry(ZipEntry("media/${m.avatarPath}"))
                        zos.write(f.readBytes())
                        zos.closeEntry()
                    }
                }
                for (r in repository.recordsList(m.id)) {
                    for (p in repository.imagePaths(r.id)) {
                        if (!added.add(p)) continue
                        val f = repository.imageStore.file(p)
                        if (f.exists()) {
                            zos.putNextEntry(ZipEntry("media/$p"))
                            zos.write(f.readBytes())
                            zos.closeEntry()
                        }
                    }
                }
            }
        }
        return FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", zip)
    }

    suspend fun importZip(uri: Uri): Int {
        var imported = 0
        val mediaMap = mutableMapOf<String, String>()
        var json: String? = null

        context.contentResolver.openInputStream(uri)?.use { input ->
            ZipInputStream(input).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    when {
                        entry.name == JSON_NAME -> json = zis.readBytes().toString(Charsets.UTF_8)
                        entry.name.startsWith("media/") -> {
                            val orig = entry.name.removePrefix("media/")
                            val bytes = zis.readBytes()
                            mediaMap[orig] = repository.imageStore.save(bytes)
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }
        }

        val raw = json ?: return 0
        val root = JSONObject(raw)
        val members = root.optJSONArray("members") ?: return 0
        for (i in 0 until members.length()) {
            val mo = members.getJSONObject(i)
            val avatar = mo.optString("avatar")
            val memberId = repository.addMember(
                Member(
                    name = mo.optString("name"),
                    birthdate = mo.optString("birthdate"),
                    bloodType = mo.optString("bloodType"),
                    allergies = mo.optString("allergies"),
                    notes = mo.optString("notes"),
                    avatarPath = mediaMap[avatar] ?: ""
                )
            )
            val recs = mo.optJSONArray("records") ?: continue
            for (j in 0 until recs.length()) {
                val ro = recs.getJSONObject(j)
                val epochDay = runCatching {
                    LocalDate.parse(ro.optString("date")).toEpochDay()
                }.getOrDefault(LocalDate.now().toEpochDay())
                val paths = mutableListOf<String>()
                ro.optJSONArray("images")?.let { imgs ->
                    for (k in 0 until imgs.length()) {
                        mediaMap[imgs.getString(k)]?.let { paths.add(it) }
                    }
                }
                repository.addRecord(
                    Record(
                        memberId = memberId,
                        type = ro.optString("type"),
                        title = ro.optString("title"),
                        dateEpochDay = epochDay,
                        hospital = ro.optString("hospital"),
                        notes = ro.optString("notes")
                    ),
                    paths
                )
                imported++
            }
        }
        return imported
    }
}

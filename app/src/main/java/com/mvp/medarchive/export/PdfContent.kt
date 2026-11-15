package com.mvp.medarchive.export

import com.mvp.medarchive.data.db.Member
import com.mvp.medarchive.data.db.Record
import java.time.LocalDate

data class PdfItem(
    val recordId: Long,
    val title: String,
    val type: String,
    val date: String,
    val notes: String
)

data class PdfContent(
    val title: String,
    val headerLines: List<String>,
    val items: List<PdfItem>
)

object PdfContentBuilder {
    fun build(member: Member, records: List<Record>): PdfContent {
        val sorted = records.sortedWith(
            compareByDescending<Record> { it.dateEpochDay }.thenByDescending { it.createdAt }
        )
        val headers = mutableListOf<String>()
        if (member.birthdate.isNotBlank()) headers += "生日：${member.birthdate}"
        if (member.bloodType.isNotBlank()) headers += "血型：${member.bloodType}"
        if (member.allergies.isNotBlank()) headers += "过敏史：${member.allergies}"
        if (member.notes.isNotBlank()) headers += "备注：${member.notes}"

        val items = sorted.map { r ->
            PdfItem(
                recordId = r.id,
                title = r.title,
                type = r.type,
                date = LocalDate.ofEpochDay(r.dateEpochDay).toString(),
                notes = r.notes
            )
        }
        return PdfContent(title = member.name, headerLines = headers, items = items)
    }
}

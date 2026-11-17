package com.mvp.medarchive.data

import com.mvp.medarchive.data.db.MedDatabase
import com.mvp.medarchive.data.db.Member
import com.mvp.medarchive.data.db.MedDose
import com.mvp.medarchive.data.db.MedReminder
import com.mvp.medarchive.data.db.Record
import com.mvp.medarchive.data.db.RecordCount
import com.mvp.medarchive.data.db.RecordImage
import kotlinx.coroutines.flow.Flow

class MedRepository(
    private val db: MedDatabase,
    val imageStore: ImageStore
) {

    fun members(): Flow<List<Member>> = db.memberDao().observeAll()

    suspend fun allMembers(): List<Member> = db.memberDao().getAll()

    suspend fun member(id: Long): Member? = db.memberDao().get(id)

    suspend fun addMember(m: Member): Long = db.memberDao().insert(m)

    suspend fun updateMember(m: Member) {
        db.memberDao().update(m)
    }

    suspend fun deleteMember(id: Long) {
        db.memberDao().get(id)?.let { m ->
            if (m.avatarPath.isNotBlank()) imageStore.delete(m.avatarPath)
        }
        db.recordDao().byMember(id).forEach { deleteRecord(it.id) }
        db.medReminderDao().deleteByMember(id)
        db.medDoseDao().deleteByMember(id)
        db.memberDao().delete(id)
    }

    fun remindersOf(memberId: Long): Flow<List<MedReminder>> =
        db.medReminderDao().observeByMember(memberId)

    suspend fun reminders(memberId: Long): List<MedReminder> = db.medReminderDao().byMember(memberId)

    suspend fun allEnabledReminders(): List<MedReminder> = db.medReminderDao().allEnabled()

    suspend fun addReminder(r: MedReminder): Long = db.medReminderDao().insert(r)

    suspend fun updateReminder(r: MedReminder) = db.medReminderDao().update(r)

    suspend fun deleteReminder(id: Long) {
        db.medReminderDao().delete(id)
        db.medDoseDao().deleteByReminder(id)
    }

    suspend fun findReminder(id: Long): MedReminder? = db.medReminderDao().get(id)

    fun dosesSince(memberId: Long, fromDay: Long): Flow<List<MedDose>> =
        db.medDoseDao().observeRange(memberId, fromDay, Long.MAX_VALUE)

    suspend fun todayDoses(memberId: Long, day: Long): List<MedDose> =
        db.medDoseDao().byDay(memberId, day)

    suspend fun markDoseTaken(reminderId: Long, memberId: Long, time: String, day: Long) {
        db.medDoseDao().upsert(
            MedDose(
                reminderId = reminderId,
                memberId = memberId,
                dateEpochDay = day,
                time = time,
                status = "taken"
            )
        )
    }

    suspend fun unmarkDose(reminderId: Long, day: Long, time: String) {
        db.medDoseDao().deleteOne(reminderId, day, time)
    }

    fun recordsOf(memberId: Long): Flow<List<Record>> = db.recordDao().observeByMember(memberId)

    fun allRecords(): Flow<List<Record>> = db.recordDao().observeAll()

    suspend fun recordsList(memberId: Long): List<Record> = db.recordDao().byMember(memberId)

    suspend fun record(id: Long): Record? = db.recordDao().get(id)

    suspend fun addRecord(r: Record, images: List<String>): Long {
        val id = db.recordDao().insert(r)
        images.forEachIndexed { i, path ->
            db.recordImageDao().insert(RecordImage(recordId = id, path = path, sortOrder = i))
        }
        return id
    }

    suspend fun updateRecord(r: Record) {
        db.recordDao().update(r)
    }

    suspend fun appendRecordImages(recordId: Long, images: List<String>) {
        if (images.isEmpty()) return
        val existing = db.recordImageDao().byRecord(recordId)
        val start = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1
        images.forEachIndexed { i, path ->
            db.recordImageDao().insert(RecordImage(recordId = recordId, path = path, sortOrder = start + i))
        }
    }

    fun recordCounts(): Flow<List<RecordCount>> = db.recordDao().observeRecordCounts()

    suspend fun deleteRecord(id: Long) {
        val imgs = db.recordImageDao().byRecord(id)
        imgs.forEach { imageStore.delete(it.path) }
        db.recordImageDao().deleteByRecord(id)
        db.recordDao().delete(id)
    }

    fun recordImages(recordId: Long): Flow<List<RecordImage>> =
        db.recordImageDao().observeByRecord(recordId)

    suspend fun imagePaths(recordId: Long): List<String> =
        db.recordImageDao().byRecord(recordId).map { it.path }
}

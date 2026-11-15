package com.mvp.medarchive.data

import com.mvp.medarchive.data.db.MedDatabase
import com.mvp.medarchive.data.db.Member
import com.mvp.medarchive.data.db.Record
import com.mvp.medarchive.data.db.RecordImage
import kotlinx.coroutines.flow.Flow

class MedRepository(
    private val db: MedDatabase,
    val imageStore: ImageStore
) {

    fun members(): Flow<List<Member>> = db.memberDao().observeAll()

    suspend fun member(id: Long): Member? = db.memberDao().get(id)

    suspend fun addMember(m: Member): Long = db.memberDao().insert(m)

    suspend fun deleteMember(id: Long) {
        db.memberDao().delete(id)
    }

    fun recordsOf(memberId: Long): Flow<List<Record>> = db.recordDao().observeByMember(memberId)

    suspend fun recordsList(memberId: Long): List<Record> = db.recordDao().byMember(memberId)

    suspend fun record(id: Long): Record? = db.recordDao().get(id)

    suspend fun addRecord(r: Record, images: List<String>): Long {
        val id = db.recordDao().insert(r)
        images.forEachIndexed { i, path ->
            db.recordImageDao().insert(RecordImage(recordId = id, path = path, sortOrder = i))
        }
        return id
    }

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

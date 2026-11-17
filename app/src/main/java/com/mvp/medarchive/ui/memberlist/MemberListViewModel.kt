package com.mvp.medarchive.ui.memberlist

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mvp.medarchive.data.BackupManager
import com.mvp.medarchive.data.MedRepository
import com.mvp.medarchive.data.db.Member
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MemberItem(val member: Member, val recordCount: Int)

class MemberListViewModel(
    application: Application,
    private val repository: MedRepository
) : AndroidViewModel(application) {

    val items: StateFlow<List<MemberItem>> = combine(
        repository.members(),
        repository.recordCounts()
    ) { members, counts ->
        val countMap = counts.associate { it.memberId to it.cnt }
        members.map { MemberItem(it, countMap[it.id] ?: 0) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(name: String, birthdate: String, bloodType: String, allergies: String, notes: String) =
        viewModelScope.launch {
            repository.addMember(
                Member(
                    name = name.trim(),
                    birthdate = birthdate.trim(),
                    bloodType = bloodType.trim(),
                    allergies = allergies.trim(),
                    notes = notes.trim()
                )
            )
        }

    fun delete(id: Long) = viewModelScope.launch { repository.deleteMember(id) }

    suspend fun exportBackup(): Uri =
        BackupManager(getApplication(), repository).exportZip()

    suspend fun importBackup(uri: Uri): Int =
        BackupManager(getApplication(), repository).importZip(uri)

    companion object {
        fun factory(app: Application, repository: MedRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { MemberListViewModel(app, repository) }
        }
    }
}

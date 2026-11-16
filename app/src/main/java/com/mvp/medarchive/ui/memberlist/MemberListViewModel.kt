package com.mvp.medarchive.ui.memberlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mvp.medarchive.data.MedRepository
import com.mvp.medarchive.data.db.Member
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MemberItem(val member: Member, val recordCount: Int)

class MemberListViewModel(private val repository: MedRepository) : ViewModel() {

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

    companion object {
        fun factory(repository: MedRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { MemberListViewModel(repository) }
        }
    }
}

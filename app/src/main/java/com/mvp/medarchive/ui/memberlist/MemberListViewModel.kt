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
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MemberListViewModel(private val repository: MedRepository) : ViewModel() {

    val members: StateFlow<List<Member>> = repository.members()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun add(name: String, bloodType: String, allergies: String) = viewModelScope.launch {
        repository.addMember(
            Member(name = name.trim(), bloodType = bloodType.trim(), allergies = allergies.trim())
        )
    }

    fun delete(id: Long) = viewModelScope.launch { repository.deleteMember(id) }

    companion object {
        fun factory(repository: MedRepository): ViewModelProvider.Factory = viewModelFactory {
            initializer { MemberListViewModel(repository) }
        }
    }
}

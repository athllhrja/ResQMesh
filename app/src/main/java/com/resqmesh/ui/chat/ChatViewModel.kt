package com.resqmesh.ui.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.resqmesh.core.MeshConfig
import com.resqmesh.domain.MeshRepository
import com.resqmesh.domain.model.Message
import com.resqmesh.domain.model.NodeId
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class ChatViewModel(
    private val repository: MeshRepository,
    private val peerId: NodeId,
) : ViewModel() {

    private val draft = MutableStateFlow("")

    val input: StateFlow<String> = draft.asStateFlow()

    val canSend: StateFlow<Boolean> = draft
        .map { it.isNotBlank() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    val messages: StateFlow<List<Message>> = repository.observeConversation(peerId)
        .map { list -> list.reversed() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setDraft(value: String) {
        draft.value = value
    }

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    fun send() {
        val text = draft.value.trim()
        if (text.isEmpty()) return
        _error.value = null
        viewModelScope.launch {
            runCatching { repository.sendTo(peerId, text, MeshConfig.DEFAULT_TTL) }
                .onFailure { _error.value = it.message }
        }
        draft.value = ""
    }

    fun clearError() {
        _error.value = null
    }

    companion object {
        const val KEY_PEER_ID = "peerId"

        fun factory(repository: MeshRepository, peerId: NodeId): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
                    ChatViewModel(repository, peerId) as T
            }
    }
}

package com.resqmesh.ui.nodes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.resqmesh.domain.MeshRepository
import com.resqmesh.domain.model.Node
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

class NodesViewModel(
    repository: MeshRepository,
) : ViewModel() {

    val neighbors: StateFlow<List<Node>> = repository.observeNeighbors()
        .map { list -> list.sortedByDescending { it.lastSeenAt } }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = emptyList(),
        )

    companion object {
        fun factory(repository: MeshRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    NodesViewModel(repository) as T
            }
    }
}

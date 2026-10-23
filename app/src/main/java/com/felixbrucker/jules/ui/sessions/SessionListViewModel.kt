package com.felixbrucker.jules.ui.sessions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.jules.data.repository.JulesRepository
import com.felixbrucker.jules.data.database.SessionEntity
import com.felixbrucker.jules.network.api.model.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import timber.log.Timber
import javax.inject.Inject

@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val julesRepository: JulesRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _selectedStates = MutableStateFlow(setOf<SessionState>())
    val selectedStates: StateFlow<Set<SessionState>> = _selectedStates.asStateFlow()
    private val _includeArchived = MutableStateFlow(false)
    val includeArchived: StateFlow<Boolean> = _includeArchived.asStateFlow()

    var isRefreshing by mutableStateOf(false)
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    val sessions: StateFlow<List<SessionEntity>> = combine(
        searchQuery,
        selectedStates,
        includeArchived,
    ) { searchQuery, selectedStates, includeArchived ->
        SessionFilterParams(searchQuery, selectedStates, includeArchived)
    }
        .flatMapLatest {
            julesRepository.getSessionsFlow(
                searchQuery = it.searchQuery,
                states = it.selectedStates.toList(),
                includeArchived = it.includeArchived
            )
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        syncSessions()
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleStateFilter(state: SessionState) {
        _selectedStates.value = if (_selectedStates.value.contains(state)) {
            _selectedStates.value - state
        } else {
            _selectedStates.value + state
        }
    }

    fun toggleIncludeArchived(include: Boolean) {
        _includeArchived.value = include
    }

    fun syncSessions() {
        viewModelScope.launch {
            isRefreshing = true
            try {
                julesRepository.syncSessions()
            } catch (e: Exception) {
                Timber.e(e, "Failed to sync sessions")
            } finally {
                isRefreshing = false
            }
        }
    }

    fun deleteSession(sessionId: String) {
        viewModelScope.launch {
            try {
                julesRepository.deleteSession(sessionId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to delete session $sessionId")
            }
        }
    }

    fun archiveSession(sessionId: String) {
        viewModelScope.launch {
            try {
                julesRepository.archiveSession(sessionId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to archive session $sessionId")
            }
        }
    }

    fun unarchiveSession(sessionId: String) {
        viewModelScope.launch {
            try {
                julesRepository.unarchiveSession(sessionId)
            } catch (e: Exception) {
                Timber.e(e, "Failed to unarchive session $sessionId")
            }
        }
    }
}

data class SessionFilterParams(
    val searchQuery: String,
    val selectedStates: Set<SessionState>,
    val includeArchived: Boolean
)

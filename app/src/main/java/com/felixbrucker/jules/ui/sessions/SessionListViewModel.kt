package com.felixbrucker.jules.ui.sessions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.jules.data.datastore.AppSettingsRepository
import com.felixbrucker.jules.data.repository.JulesRepository
import com.felixbrucker.jules.data.database.SessionWithPullRequest
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

enum class SessionFilterChip(val label: String) {
    AWAITING_FEEDBACK("Awaiting Feedback"),
    IN_PROGRESS("In Progress"),
    COMPLETED("Completed")
}

@HiltViewModel
class SessionListViewModel @Inject constructor(
    private val julesRepository: JulesRepository,
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()
    private val _selectedChips = MutableStateFlow(setOf<SessionFilterChip>())
    val selectedChips: StateFlow<Set<SessionFilterChip>> = _selectedChips.asStateFlow()
    private val _includeArchived = MutableStateFlow(false)
    val includeArchived: StateFlow<Boolean> = _includeArchived.asStateFlow()

    val dailySessionCount = julesRepository.getDailySessionCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val dailySessionLimit = appSettingsRepository.dailySessionLimitFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 15)

    var isRefreshing by mutableStateOf(false)
        private set

    @OptIn(ExperimentalCoroutinesApi::class)
    val sessions: StateFlow<List<SessionWithPullRequest>> = combine(
        searchQuery,
        selectedChips,
        includeArchived,
    ) { searchQuery, selectedChips, includeArchived ->
        val states = selectedChips.flatMap { chip ->
            when (chip) {
                SessionFilterChip.AWAITING_FEEDBACK -> listOf(
                    SessionState.AWAITING_PLAN_APPROVAL,
                    SessionState.AWAITING_USER_FEEDBACK,
                    SessionState.FAILED
                )
                SessionFilterChip.IN_PROGRESS -> listOf(SessionState.IN_PROGRESS)
                SessionFilterChip.COMPLETED -> listOf(
                    SessionState.COMPLETED
                )
            }
        }
        SessionFilterParams(searchQuery, states, includeArchived)
    }
        .flatMapLatest {
            julesRepository.getSessionsFlow(
                searchQuery = it.searchQuery,
                states = it.states,
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

    fun toggleFilterChip(chip: SessionFilterChip) {
        _selectedChips.value = if (_selectedChips.value.contains(chip)) {
            _selectedChips.value - chip
        } else {
            _selectedChips.value + chip
        }
    }

    fun toggleIncludeArchived(include: Boolean) {
        _includeArchived.value = include
    }

    fun syncSessions() {
        viewModelScope.launch {
            isRefreshing = true
            try {
                julesRepository.sync()
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to sync sessions and pull requests")
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
                Timber.tag(TAG).e(e, "Failed to delete session $sessionId")
            }
        }
    }

    fun archiveSession(sessionId: String) {
        viewModelScope.launch {
            try {
                julesRepository.archiveSession(sessionId)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to archive session $sessionId")
            }
        }
    }

    fun unarchiveSession(sessionId: String) {
        viewModelScope.launch {
            try {
                julesRepository.unarchiveSession(sessionId)
            } catch (e: Exception) {
                Timber.tag(TAG).e(e, "Failed to unarchive session $sessionId")
            }
        }
    }

    companion object {
        private const val TAG = "SessionListViewModel"
    }
}

data class SessionFilterParams(
    val searchQuery: String,
    val states: List<SessionState>,
    val includeArchived: Boolean
)

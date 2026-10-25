package com.felixbrucker.jules.ui.sessions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.felixbrucker.jules.data.database.SessionDraftEntity
import com.felixbrucker.jules.data.database.SessionDraftWithSource
import com.felixbrucker.jules.data.database.SessionEntity
import com.felixbrucker.jules.data.database.SessionWithPullRequest
import com.felixbrucker.jules.data.database.SourceEntity
import com.felixbrucker.jules.data.database.DispatchableWorkflowEntity
import com.felixbrucker.jules.data.datastore.AppSettingsRepository
import com.felixbrucker.jules.data.repository.JulesRepository
import com.felixbrucker.jules.network.api.model.SessionState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
    private val _includeDrafts = MutableStateFlow(true)
    val includeDrafts: StateFlow<Boolean> = _includeDrafts.asStateFlow()

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()
    private val _activeDraft = MutableStateFlow<SessionDraftEntity?>(null)
    val activeDraft: StateFlow<SessionDraftEntity?> = _activeDraft.asStateFlow()
    private val _isCreatingSession = MutableStateFlow(false)
    val isCreatingSession: StateFlow<Boolean> = _isCreatingSession.asStateFlow()
    private val _createSessionError = MutableStateFlow<String?>(null)
    val createSessionError: StateFlow<String?> = _createSessionError.asStateFlow()

    private val _dispatchDialogState = MutableStateFlow<DispatchDialogState?>(null)
    val dispatchDialogState: StateFlow<DispatchDialogState?> = _dispatchDialogState.asStateFlow()

    private val _userError = MutableStateFlow<String?>(null)
    val userError: StateFlow<String?> = _userError.asStateFlow()

    fun clearUserError() {
        _userError.value = null
    }

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

    @OptIn(ExperimentalCoroutinesApi::class)
    val drafts: StateFlow<List<SessionDraftWithSource>> = combine(
        searchQuery,
        selectedChips,
        includeDrafts
    ) { query, chips, incDrafts ->
        DraftFilterParams(query = query, hasStatusFilters = chips.isNotEmpty(), includeDrafts = incDrafts)
    }
        .flatMapLatest { params ->
            if (!params.includeDrafts || params.hasStatusFilters) {
                flowOf(emptyList())
            } else {
                julesRepository.getDraftsFlow(params.query)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val sources: StateFlow<List<SourceEntity>> = julesRepository.getSourcesFlow()
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

    fun toggleIncludeDrafts(include: Boolean) {
        _includeDrafts.value = include
    }

    fun openCreateDialog(draft: SessionDraftEntity? = null) {
        viewModelScope.launch {
            val initialDraft = if (draft != null && draft.sourceId != null) {
                draft
            } else {
                val lastSourceId = appSettingsRepository.lastSelectedSourceIdFlow.first()
                val targetSourceId = lastSourceId?.takeIf { id ->
                    sources.value.any { it.id == id }
                } ?: sources.value.firstOrNull()?.id
                (draft ?: SessionDraftEntity()).copy(sourceId = targetSourceId)
            }
            _activeDraft.value = initialDraft
            _createSessionError.value = null
            _showCreateDialog.value = true
        }
    }

    fun dismissCreateDialog() {
        _showCreateDialog.value = false
        _activeDraft.value = null
        _createSessionError.value = null
        _isCreatingSession.value = false
    }

    fun saveDraft(draft: SessionDraftEntity) {
        viewModelScope.launch {
            try {
                draft.sourceId?.let { appSettingsRepository.saveLastSelectedSourceId(it) }
                julesRepository.saveDraft(draft)
                dismissCreateDialog()
            } catch (e: Exception) {
                _userError.value = e.message ?: "Failed to save draft"
            }
        }
    }

    fun deleteDraft(draftId: String) {
        viewModelScope.launch {
            try {
                julesRepository.deleteDraft(draftId)
            } catch (e: Exception) {
                _userError.value = e.message ?: "Failed to delete draft"
            }
        }
    }

    fun createSessionFromDraft(draft: SessionDraftEntity) {
        viewModelScope.launch {
            _isCreatingSession.value = true
            _createSessionError.value = null
            try {
                draft.sourceId?.let { appSettingsRepository.saveLastSelectedSourceId(it) }
                julesRepository.createSessionFromDraft(draft)
                dismissCreateDialog()
            } catch (e: Exception) {
                _createSessionError.value = e.message ?: "Failed to create session"
            } finally {
                _isCreatingSession.value = false
            }
        }
    }

    fun syncSessions() {
        viewModelScope.launch {
            isRefreshing = true
            try {
                julesRepository.sync()
            } catch (e: Exception) {
                _userError.value = e.message ?: "Failed to sync"
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
                _userError.value = e.message ?: "Failed to delete session"
            }
        }
    }

    fun archiveSession(sessionId: String) {
        viewModelScope.launch {
            try {
                julesRepository.archiveSession(sessionId)
            } catch (e: Exception) {
                _userError.value = e.message ?: "Failed to archive session"
            }
        }
    }

    fun unarchiveSession(sessionId: String) {
        viewModelScope.launch {
            try {
                julesRepository.unarchiveSession(sessionId)
            } catch (e: Exception) {
                _userError.value = e.message ?: "Failed to unarchive session"
            }
        }
    }

    fun openDispatchDialog(session: SessionEntity, workflow: DispatchableWorkflowEntity) {
        _dispatchDialogState.value = DispatchDialogState(session = session, workflow = workflow)
    }

    fun dismissDispatchDialog() {
        _dispatchDialogState.value = null
    }

    fun dispatchWorkflow(
        session: SessionEntity,
        workflow: DispatchableWorkflowEntity,
        branch: String,
        inputs: Map<String, String>
    ) {
        val repo = session.repo ?: return
        viewModelScope.launch {
            _dispatchDialogState.value = _dispatchDialogState.value?.copy(isDispatching = true, errorMessage = null)
            try {
                julesRepository.dispatchWorkflow(
                    owner = repo.owner,
                    repo = repo.name,
                    workflowId = workflow.workflowId.toString(),
                    ref = branch,
                    inputs = inputs
                )
                dismissDispatchDialog()
            } catch (e: Exception) {
                _dispatchDialogState.value = _dispatchDialogState.value?.copy(
                    isDispatching = false,
                    errorMessage = e.message ?: "Failed to dispatch workflow"
                )
            }
        }
    }
}

data class DraftFilterParams(
    val query: String,
    val hasStatusFilters: Boolean,
    val includeDrafts: Boolean
)

data class SessionFilterParams(
    val searchQuery: String,
    val states: List<SessionState>,
    val includeArchived: Boolean
)

data class DispatchDialogState(
    val session: SessionEntity,
    val workflow: DispatchableWorkflowEntity,
    val isDispatching: Boolean = false,
    val errorMessage: String? = null
)

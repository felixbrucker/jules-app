package com.felixbrucker.jules.ui.sessions

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.time.Instant
import com.felixbrucker.jules.data.database.ScheduledTaskEntity
import com.felixbrucker.jules.data.database.ScheduledTaskRepo
import com.felixbrucker.jules.data.database.SessionDraftEntity
import com.felixbrucker.jules.data.database.SessionDraftWithSource
import com.felixbrucker.jules.data.database.SessionEntity
import com.felixbrucker.jules.data.database.SessionWithPullRequest
import com.felixbrucker.jules.data.database.SourceEntity
import com.felixbrucker.jules.data.database.DispatchableWorkflowEntity
import com.felixbrucker.jules.data.datastore.AppSettingsRepository
import com.felixbrucker.jules.data.repository.JulesRepository
import com.felixbrucker.jules.network.api.model.SessionState
import com.felixbrucker.jules.util.periodicTickerFlow
import kotlinx.coroutines.Job
import kotlin.time.Duration.Companion.minutes
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
import kotlinx.coroutines.flow.map
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
    val selectedChips: StateFlow<Set<SessionFilterChip>> = appSettingsRepository.selectedFilterChipsFlow
        .map { names ->
            names.mapNotNull { name ->
                try {
                    SessionFilterChip.valueOf(name)
                } catch (_: Exception) {
                    null
                }
            }.toSet()
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptySet()
        )
    val includeArchived: StateFlow<Boolean> = appSettingsRepository.includeArchivedFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )
    val includeDrafts: StateFlow<Boolean> = appSettingsRepository.includeDraftsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = true
        )
    val includeScheduled: StateFlow<Boolean> = appSettingsRepository.includeScheduledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

    private val _showCreateDialog = MutableStateFlow(false)
    val showCreateDialog: StateFlow<Boolean> = _showCreateDialog.asStateFlow()
    private val _activeDraft = MutableStateFlow<SessionDraftEntity?>(null)
    val activeDraft: StateFlow<SessionDraftEntity?> = _activeDraft.asStateFlow()
    private val _isCreatingSession = MutableStateFlow(false)
    val isCreatingSession: StateFlow<Boolean> = _isCreatingSession.asStateFlow()
    private val _createSessionError = MutableStateFlow<String?>(null)
    val createSessionError: StateFlow<String?> = _createSessionError.asStateFlow()

    private val _showScheduledTaskDialog = MutableStateFlow(false)
    val showScheduledTaskDialog: StateFlow<Boolean> = _showScheduledTaskDialog.asStateFlow()
    private val _activeScheduledTask = MutableStateFlow<ScheduledTaskEntity?>(null)
    val activeScheduledTask: StateFlow<ScheduledTaskEntity?> = _activeScheduledTask.asStateFlow()
    private val _isSavingScheduledTask = MutableStateFlow(false)
    val isSavingScheduledTask: StateFlow<Boolean> = _isSavingScheduledTask.asStateFlow()
    private val _scheduledTaskError = MutableStateFlow<String?>(null)
    val scheduledTaskError: StateFlow<String?> = _scheduledTaskError.asStateFlow()

    private val _dispatchDialogState = MutableStateFlow<DispatchDialogState?>(null)
    val dispatchDialogState: StateFlow<DispatchDialogState?> = _dispatchDialogState.asStateFlow()

    private val _userFeedback = MutableStateFlow<String?>(null)
    val userFeedback: StateFlow<String?> = _userFeedback.asStateFlow()

    fun clearUserFeedback() {
        _userFeedback.value = null
    }

    val dailySessionCount = julesRepository.getDailySessionCountFlow()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)

    val dailySessionLimit = appSettingsRepository.dailySessionLimitFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 15)

    val workflowDispatchingEnabled: StateFlow<Boolean> = appSettingsRepository.workflowDispatchingEnabledFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = false
        )

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
                SessionFilterChip.IN_PROGRESS -> listOf(
                    SessionState.QUEUED,
                    SessionState.PLANNING,
                    SessionState.IN_PROGRESS
                )
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

    @OptIn(ExperimentalCoroutinesApi::class)
    val scheduledTasks: StateFlow<List<ScheduledTaskEntity>> = combine(
        searchQuery,
        selectedChips,
        includeScheduled
    ) { query, chips, incScheduled ->
        ScheduledFilterParams(query = query, hasStatusFilters = chips.isNotEmpty(), includeScheduled = incScheduled)
    }
        .flatMapLatest { params ->
            if (!params.includeScheduled || params.hasStatusFilters) {
                flowOf(emptyList())
            } else {
                julesRepository.getScheduledTasksFlow(params.query)
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

    private var periodicSyncJob: Job? = null

    init {
        syncSessions()
    }

    fun startPeriodicSync() {
        if (periodicSyncJob != null) return
        periodicSyncJob = viewModelScope.launch {
            periodicTickerFlow(5.minutes).collect {
                try {
                    julesRepository.sync()
                } catch (_: Exception) {
                }
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun toggleFilterChip(chip: SessionFilterChip) {
        val current = selectedChips.value
        val updatedSet = if (current.contains(chip)) {
            current - chip
        } else {
            current + chip
        }
        viewModelScope.launch {
            appSettingsRepository.saveSelectedFilterChips(updatedSet.map { it.name }.toSet())
        }
    }

    fun toggleIncludeArchived(include: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setIncludeArchived(include)
        }
    }

    fun toggleIncludeDrafts(include: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setIncludeDrafts(include)
        }
    }

    fun toggleIncludeScheduled(include: Boolean) {
        viewModelScope.launch {
            appSettingsRepository.setIncludeScheduled(include)
        }
    }

    fun openScheduledTaskDialog(task: ScheduledTaskEntity? = null) {
        viewModelScope.launch {
            val initialTask = if (task != null) {
                task
            } else {
                val lastSourceId = appSettingsRepository.lastSelectedSourceIdFlow.first()
                val targetSourceId = lastSourceId?.takeIf { id ->
                    sources.value.any { it.id == id }
                } ?: sources.value.firstOrNull()?.id
                val defaultRepo = targetSourceId?.let { id ->
                    val src = sources.value.firstOrNull { it.id == id }
                    val branch = src?.githubRepo?.defaultBranchName
                        ?: src?.githubRepo?.branches?.firstOrNull()?.displayName
                        ?: "main"
                    listOf(ScheduledTaskRepo(id, branch))
                } ?: emptyList()
                ScheduledTaskEntity(repos = defaultRepo, requirePlanApproval = false)
            }
            _activeScheduledTask.value = initialTask
            _scheduledTaskError.value = null
            _showScheduledTaskDialog.value = true
        }
    }

    fun dismissScheduledTaskDialog() {
        _showScheduledTaskDialog.value = false
        _activeScheduledTask.value = null
        _scheduledTaskError.value = null
        _isSavingScheduledTask.value = false
    }

    fun saveScheduledTask(task: ScheduledTaskEntity) {
        viewModelScope.launch {
            _isSavingScheduledTask.value = true
            _scheduledTaskError.value = null
            try {
                task.repos.firstOrNull()?.sourceId?.let { appSettingsRepository.saveLastSelectedSourceId(it) }
                julesRepository.saveScheduledTask(task)
                dismissScheduledTaskDialog()
            } catch (e: Exception) {
                _scheduledTaskError.value = e.message ?: "Failed to save scheduled task"
            } finally {
                _isSavingScheduledTask.value = false
            }
        }
    }

    fun toggleScheduledTaskEnabled(task: ScheduledTaskEntity, enabled: Boolean) {
        viewModelScope.launch {
            try {
                val updated = task.copy(enabled = enabled, updatedAt = Instant.now())
                julesRepository.saveScheduledTask(updated)
            } catch (e: Exception) {
                _userFeedback.value = e.message ?: "Failed to update scheduled task"
            }
        }
    }

    fun deleteScheduledTask(taskId: String) {
        viewModelScope.launch {
            try {
                julesRepository.deleteScheduledTask(taskId)
            } catch (e: Exception) {
                _userFeedback.value = e.message ?: "Failed to delete scheduled task"
            }
        }
    }

    fun executeScheduledTaskNow(taskId: String) {
        viewModelScope.launch {
            try {
                julesRepository.executeScheduledTask(taskId)
                _userFeedback.value = "Scheduled task triggered successfully"
            } catch (e: Exception) {
                _userFeedback.value = e.message ?: "Failed to execute scheduled task"
            }
        }
    }

    fun openCreateDialog(draft: SessionDraftEntity? = null) {
        viewModelScope.launch {
            val initialDraft = if (draft?.sourceId != null) {
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
            initialDraft.sourceId?.let { syncSourceIfNeeded(it) }
        }
    }

    fun syncSourceIfNeeded(sourceId: String) {
        viewModelScope.launch {
            try {
                julesRepository.syncSource(sourceId)
            } catch (_: Exception) {
            }
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
                _userFeedback.value = e.message ?: "Failed to save draft"
            }
        }
    }

    fun deleteDraft(draftId: String) {
        viewModelScope.launch {
            try {
                julesRepository.deleteDraft(draftId)
            } catch (e: Exception) {
                _userFeedback.value = e.message ?: "Failed to delete draft"
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
                _userFeedback.value = e.message ?: "Failed to sync"
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
                _userFeedback.value = e.message ?: "Failed to delete session"
            }
        }
    }

    fun archiveSession(sessionId: String) {
        viewModelScope.launch {
            try {
                julesRepository.archiveSession(sessionId)
            } catch (e: Exception) {
                _userFeedback.value = e.message ?: "Failed to archive session"
            }
        }
    }

    fun unarchiveSession(sessionId: String) {
        viewModelScope.launch {
            try {
                julesRepository.unarchiveSession(sessionId)
            } catch (e: Exception) {
                _userFeedback.value = e.message ?: "Failed to unarchive session"
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
                    sessionId = session.id,
                    owner = repo.owner,
                    repo = repo.name,
                    workflowId = workflow.workflowId.toString(),
                    workflowName = workflow.name,
                    ref = branch,
                    inputs = inputs
                )
                dismissDispatchDialog()
                _userFeedback.value = "Workflow '${workflow.name}' dispatched successfully"
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

data class ScheduledFilterParams(
    val query: String,
    val hasStatusFilters: Boolean,
    val includeScheduled: Boolean
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

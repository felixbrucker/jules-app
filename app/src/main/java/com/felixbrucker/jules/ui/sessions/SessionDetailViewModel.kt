package com.felixbrucker.jules.ui.sessions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.felixbrucker.jules.data.database.ActivityEntity
import com.felixbrucker.jules.data.database.SessionWithPullRequest
import com.felixbrucker.jules.data.repository.JulesRepository
import com.felixbrucker.jules.network.api.model.Activity
import com.felixbrucker.jules.network.api.model.ParsedFile
import com.felixbrucker.jules.network.api.model.SessionState
import com.felixbrucker.jules.network.api.util.UnidiffParser
import com.felixbrucker.jules.util.periodicTickerFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

sealed interface ActivityUiItem {
    val id: String

    data class ActivityItem(
        override val id: String,
        val activity: Activity,
        val isLatest: Boolean
    ) : ActivityUiItem

    data class RelativeTimeDivider(
        override val id: String,
        val relativeTimeText: String
    ) : ActivityUiItem
}

data class CurrentChangesetDiff(
    val unidiffPatch: String,
    val parsedFiles: List<ParsedFile>,
    val totalAdditions: Int,
    val totalDeletions: Int
)

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    private val julesRepository: JulesRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    val sessionWithPullRequestFlow: Flow<SessionWithPullRequest?> =
        julesRepository.getSessionFlow(sessionId)

    private val latestActivityWithChangesetFlow: Flow<ActivityEntity?> =
        julesRepository.getLatestActivityWithChangesetFlow(sessionId)

    val currentChangesetDiffFlow: Flow<CurrentChangesetDiff?> = combine(
        sessionWithPullRequestFlow,
        latestActivityWithChangesetFlow
    ) { sessionWithPr, latestActivityEntity ->
        val sessionEntity = sessionWithPr?.session
        val sessionChangeSet = sessionEntity?.changeSet
        val sessionUpdateTime = sessionEntity?.updateTime ?: sessionEntity?.createTime

        val activityPatch = latestActivityEntity?.unidiffPatch
        val activityTime = latestActivityEntity?.createTime

        if (activityPatch != null && (sessionUpdateTime == null || activityTime?.isAfter(sessionUpdateTime) == true)) {
            val parsed = UnidiffParser.parse(activityPatch)
            CurrentChangesetDiff(
                unidiffPatch = activityPatch,
                parsedFiles = parsed,
                totalAdditions = parsed.sumOf { it.additions },
                totalDeletions = parsed.sumOf { it.deletions }
            )
        } else if (sessionChangeSet != null && sessionChangeSet.unidiffPatch.isNotBlank()) {
            val parsed = UnidiffParser.parse(sessionChangeSet.unidiffPatch)
            CurrentChangesetDiff(
                unidiffPatch = sessionChangeSet.unidiffPatch,
                parsedFiles = parsed,
                totalAdditions = sessionChangeSet.totalAdditions,
                totalDeletions = sessionChangeSet.totalDeletions
            )
        } else if (activityPatch != null) {
            val parsed = UnidiffParser.parse(activityPatch)
            CurrentChangesetDiff(
                unidiffPatch = activityPatch,
                parsedFiles = parsed,
                totalAdditions = parsed.sumOf { it.additions },
                totalDeletions = parsed.sumOf { it.deletions }
            )
        } else {
            null
        }
    }

    val activitiesPagingDataFlow: Flow<PagingData<ActivityUiItem>> =
        julesRepository.getActivitiesPagingDataFlow(sessionId)
            .map { pagingData ->
                pagingData.map { activity ->
                    ActivityUiItem.ActivityItem(
                        id = activity.id,
                        activity = activity,
                        isLatest = false
                    )
                }.insertSeparators { before: ActivityUiItem.ActivityItem?, after: ActivityUiItem.ActivityItem? ->
                    val beforeTime = before?.activity?.createTime
                    val afterTime = after?.activity?.createTime

                    if (beforeTime != null && afterTime != null) {
                        val gapMinutes = Duration.between(afterTime, beforeTime).abs().toMinutes()
                        if (gapMinutes >= 15) {
                            val timeStr = formatRelativeOrAbsoluteTime(beforeTime)
                            ActivityUiItem.RelativeTimeDivider(
                                id = "time_sep_${before.id}_${after.id}",
                                relativeTimeText = timeStr
                            )
                        } else null
                    } else if (beforeTime != null && after == null) {
                        val timeStr = formatRelativeOrAbsoluteTime(beforeTime)
                        ActivityUiItem.RelativeTimeDivider(
                            id = "time_sep_top_${before.id}",
                            relativeTimeText = timeStr
                        )
                    } else null
                }
            }
            .cachedIn(viewModelScope)

    private val _splitRatio = MutableStateFlow(0.5f)
    val splitRatio: StateFlow<Float> = _splitRatio.asStateFlow()

    private val _isSendingMessage = MutableStateFlow(false)
    val isSendingMessage: StateFlow<Boolean> = _isSendingMessage.asStateFlow()

    private val _isApprovingPlan = MutableStateFlow(false)
    val isApprovingPlan: StateFlow<Boolean> = _isApprovingPlan.asStateFlow()

    private val _errorEvents = MutableSharedFlow<String>()
    val errorEvents: SharedFlow<String> = _errorEvents.asSharedFlow()

    private var pollingJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                julesRepository.syncActivities(sessionId, pageSize = 30)
                julesRepository.syncSession(sessionId)
            } catch (_: Exception) {
            }
        }
    }

    fun startPolling() {
        if (pollingJob != null) return
        pollingJob = viewModelScope.launch {
            periodicTickerFlow(10.seconds).collect {
                try {
                    julesRepository.syncActivities(sessionId, pageSize = 5)

                    val currentSession = sessionWithPullRequestFlow.first()?.session
                    if (currentSession != null && currentSession.state in listOf(
                            SessionState.QUEUED,
                            SessionState.PLANNING,
                            SessionState.IN_PROGRESS,
                            SessionState.AWAITING_PLAN_APPROVAL,
                            SessionState.AWAITING_USER_FEEDBACK
                        )
                    ) {
                        julesRepository.syncSession(sessionId)
                    }
                } catch (_: Exception) {
                }
            }
        }
    }

    fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    fun updateSplitRatio(ratio: Float) {
        val clamped = when {
            ratio <= 0.08f -> 0.0f
            ratio >= 0.92f -> 1.0f
            else -> ratio.coerceIn(0.0f, 1.0f)
        }
        _splitRatio.value = clamped
    }

    fun sendMessage(prompt: String) {
        if (prompt.isBlank() || _isSendingMessage.value) return
        viewModelScope.launch {
            _isSendingMessage.value = true
            try {
                julesRepository.sendMessage(sessionId, prompt)
            } catch (e: Exception) {
                _errorEvents.emit(e.message ?: "Failed to send message")
            } finally {
                _isSendingMessage.value = false
            }
        }
    }

    fun approvePlan() {
        if (_isApprovingPlan.value) return
        viewModelScope.launch {
            _isApprovingPlan.value = true
            try {
                julesRepository.approvePlan(sessionId)
            } catch (e: Exception) {
                _errorEvents.emit(e.message ?: "Failed to approve plan")
            } finally {
                _isApprovingPlan.value = false
            }
        }
    }

    fun archiveSession() {
        viewModelScope.launch {
            try {
                julesRepository.archiveSession(sessionId)
            } catch (e: Exception) {
                _errorEvents.emit(e.message ?: "Failed to archive session")
            }
        }
    }

    fun unarchiveSession() {
        viewModelScope.launch {
            try {
                julesRepository.unarchiveSession(sessionId)
            } catch (e: Exception) {
                _errorEvents.emit(e.message ?: "Failed to unarchive session")
            }
        }
    }

    fun deleteSession(onDeleted: () -> Unit) {
        viewModelScope.launch {
            try {
                julesRepository.deleteSession(sessionId)
                onDeleted()
            } catch (e: Exception) {
                _errorEvents.emit(e.message ?: "Failed to delete session")
            }
        }
    }

    private fun formatRelativeOrAbsoluteTime(instant: Instant): String {
        val now = Instant.now()
        val duration = Duration.between(instant, now)
        return when {
            duration.toMinutes() < 1 -> "Just now"
            duration.toMinutes() < 60 -> "${duration.toMinutes()}m ago"
            duration.toHours() < 24 -> "${duration.toHours()}h ago"
            else -> DateTimeFormatter.ofPattern("MMM d, HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(instant)
        }
    }
}

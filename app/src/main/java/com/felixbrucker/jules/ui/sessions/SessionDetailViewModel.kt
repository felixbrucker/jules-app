package com.felixbrucker.jules.ui.sessions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.felixbrucker.jules.data.database.ActivityBasedStateEntity
import com.felixbrucker.jules.data.database.AgentState
import com.felixbrucker.jules.data.database.ChatElementEntity
import com.felixbrucker.jules.data.database.ChatStateEntity
import com.felixbrucker.jules.data.database.DispatchableWorkflowEntity
import com.felixbrucker.jules.data.database.SessionEntity
import com.felixbrucker.jules.data.database.SessionWithPullRequest
import com.felixbrucker.jules.data.repository.JulesRepository
import com.felixbrucker.jules.network.api.model.ParsedFile
import com.felixbrucker.jules.util.periodicTickerFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Duration
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import javax.inject.Inject
import kotlin.time.Duration.Companion.seconds

sealed interface ChatElementUiItem {
    val id: String

    data class ElementItem(
        override val id: String,
        val elementEntity: ChatElementEntity
    ) : ChatElementUiItem

    data class RelativeTimeDivider(
        override val id: String,
        val relativeTimeText: String
    ) : ChatElementUiItem
}

enum class DiffLineType {
    CONTEXT,
    ADDITION,
    DELETION
}

data class ParsedDiffLine(
    val oldLineNumber: Int?,
    val newLineNumber: Int?,
    val content: String,
    val type: DiffLineType
)

sealed interface FileTreeNode {
    val name: String
    val fullPath: String

    data class Directory(
        override val name: String,
        override val fullPath: String,
        val children: List<FileTreeNode>
    ) : FileTreeNode

    data class File(
        override val name: String,
        override val fullPath: String,
        val parsedFile: ParsedFile
    ) : FileTreeNode
}

@HiltViewModel
class SessionDetailViewModel @Inject constructor(
    private val julesRepository: JulesRepository,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    val sessionId: String = checkNotNull(savedStateHandle["sessionId"])

    val sessionWithPullRequestFlow: Flow<SessionWithPullRequest?> =
        julesRepository.getSessionFlow(sessionId)

    val chatStateFlow: StateFlow<ChatStateEntity?> = julesRepository.getChatStateFlow(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val activityBasedStateFlow: StateFlow<ActivityBasedStateEntity?> = julesRepository.getActivityBasedStateFlow(sessionId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    val fileTreeFlow: StateFlow<List<FileTreeNode>> = activityBasedStateFlow
        .map { state ->
            val parsedFiles = state?.latestDiff?.parsedFilesMap?.values?.toList() ?: emptyList()
            if (parsedFiles.isEmpty()) emptyList() else buildFileTree(parsedFiles)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fileDiffLinesMapFlow: StateFlow<Map<String, List<ParsedDiffLine>>> = activityBasedStateFlow
        .map { state ->
            state?.latestDiff?.fileDiffLinesMap ?: emptyMap()
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    val chatElementsPagingDataFlow: Flow<PagingData<ChatElementUiItem>> =
        julesRepository.getChatElementsPagingDataFlow(sessionId)
            .map { pagingData ->
                pagingData.map { entity ->
                    ChatElementUiItem.ElementItem(
                        id = entity.id,
                        elementEntity = entity
                    )
                }.insertSeparators { before: ChatElementUiItem.ElementItem?, after: ChatElementUiItem.ElementItem? ->
                    val beforeTime = before?.elementEntity?.createdAt
                    val afterTime = after?.elementEntity?.createdAt

                    if (beforeTime != null && afterTime != null) {
                        val gapMinutes = Duration.between(afterTime, beforeTime).abs().toMinutes()
                        if (gapMinutes >= 15) {
                            val timeStr = formatRelativeOrAbsoluteTime(beforeTime)
                            ChatElementUiItem.RelativeTimeDivider(
                                id = "time_sep_${before.id}_${after.id}",
                                relativeTimeText = timeStr
                            )
                        } else null
                    } else if (beforeTime != null && after == null) {
                        val timeStr = formatRelativeOrAbsoluteTime(beforeTime)
                        ChatElementUiItem.RelativeTimeDivider(
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

    private val _dispatchDialogState = MutableStateFlow<DispatchDialogState?>(null)
    val dispatchDialogState: StateFlow<DispatchDialogState?> = _dispatchDialogState.asStateFlow()

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

    private val _isInitialSyncing = MutableStateFlow(true)
    val isInitialSyncing: StateFlow<Boolean> = _isInitialSyncing.asStateFlow()

    private var pollingJob: Job? = null

    init {
        viewModelScope.launch {
            try {
                julesRepository.syncActivities(sessionId, pageSize = 30)
                julesRepository.syncSession(sessionId)
            } catch (_: Exception) {
            } finally {
                _isInitialSyncing.value = false
            }
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    fun startPolling() {
        if (pollingJob != null) return
        pollingJob = viewModelScope.launch {
            chatStateFlow.map { it?.agentState ?: AgentState.Idle }
                .distinctUntilChanged()
                .flatMapLatest { agentState ->
                    val interval = if (agentState != AgentState.Idle) 5.seconds else 15.seconds
                    periodicTickerFlow(interval)
                }
                .collect {
                    try {
                        julesRepository.syncActivities(sessionId, pageSize = 30)
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
            ratio <= 0.03f -> 0.0f
            ratio >= 0.97f -> 1.0f
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
                julesRepository.syncSession(sessionId)
                julesRepository.syncActivities(sessionId, pageSize = 30)
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
                julesRepository.syncSession(sessionId)
                julesRepository.syncActivities(sessionId, pageSize = 30)
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

    private fun buildFileTree(parsedFiles: List<ParsedFile>): List<FileTreeNode> {
        class DirBuilder(val name: String, val fullPath: String) {
            val subDirs = mutableMapOf<String, DirBuilder>()
            val files = mutableListOf<ParsedFile>()

            fun toTreeNode(): FileTreeNode.Directory {
                val childrenList = mutableListOf<FileTreeNode>()
                subDirs.values.sortedBy { it.name }.forEach { dir ->
                    childrenList.add(dir.toTreeNode())
                }
                files.sortedBy { it.path.substringAfterLast('/') }.forEach { file ->
                    childrenList.add(
                        FileTreeNode.File(
                            name = file.path.substringAfterLast('/'),
                            fullPath = file.path,
                            parsedFile = file
                        )
                    )
                }
                return FileTreeNode.Directory(name, fullPath, childrenList)
            }
        }

        val rootDirs = mutableMapOf<String, DirBuilder>()
        val rootFiles = mutableListOf<ParsedFile>()

        for (file in parsedFiles) {
            val parts = file.path.split('/')
            if (parts.size == 1) {
                rootFiles.add(file)
            } else {
                val topLevelDirName = parts[0]
                val topLevelDir = rootDirs.getOrPut(topLevelDirName) {
                    DirBuilder(topLevelDirName, topLevelDirName)
                }
                var currentDir = topLevelDir
                var currentPath = topLevelDirName
                for (i in 1 until parts.size - 1) {
                    val dirName = parts[i]
                    currentPath = "$currentPath/$dirName"
                    currentDir = currentDir.subDirs.getOrPut(dirName) {
                        DirBuilder(dirName, currentPath)
                    }
                }
                currentDir.files.add(file)
            }
        }

        val result = mutableListOf<FileTreeNode>()
        rootDirs.values.sortedBy { it.name }.forEach { dir ->
            result.add(dir.toTreeNode())
        }
        rootFiles.sortedBy { it.path }.forEach { file ->
            result.add(
                FileTreeNode.File(
                    name = file.path,
                    fullPath = file.path,
                    parsedFile = file
                )
            )
        }
        return result
    }
}

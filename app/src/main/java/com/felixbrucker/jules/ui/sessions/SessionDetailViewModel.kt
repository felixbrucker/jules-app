package com.felixbrucker.jules.ui.sessions

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.felixbrucker.jules.data.database.ActivityEntity
import com.felixbrucker.jules.data.database.DispatchableWorkflowEntity
import com.felixbrucker.jules.data.database.SessionEntity
import com.felixbrucker.jules.data.database.SessionWithPullRequest
import com.felixbrucker.jules.data.repository.JulesRepository
import com.felixbrucker.jules.network.api.model.Activity
import com.felixbrucker.jules.network.api.model.ParsedFile
import com.felixbrucker.jules.network.api.util.UnidiffParser
import com.felixbrucker.jules.util.periodicTickerFlow
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
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
    }.flowOn(Dispatchers.Default)

    val fileTreeFlow: StateFlow<List<FileTreeNode>> = currentChangesetDiffFlow
        .map { diff ->
            if (diff == null || diff.parsedFiles.isEmpty()) emptyList()
            else buildFileTree(diff.parsedFiles)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val fileDiffLinesMapFlow: StateFlow<Map<String, List<ParsedDiffLine>>> = currentChangesetDiffFlow
        .map { diff ->
            if (diff == null || diff.unidiffPatch.isBlank()) emptyMap()
            else extractAllFileDiffLines(diff.unidiffPatch)
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

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
                julesRepository.syncActivities(sessionId, pageSize = 5)
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
                julesRepository.syncActivities(sessionId, pageSize = 5)
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

    private fun extractAllFileDiffLines(unidiffPatch: String): Map<String, List<ParsedDiffLine>> {
        val blocks = unidiffPatch.split(DIFF_GIT_REGEX)
        val resultMap = mutableMapOf<String, List<ParsedDiffLine>>()

        for (block in blocks) {
            if (block.isBlank()) continue
            val lines = block.lines()
            val headerLine = lines.firstOrNull() ?: continue
            val match = PATH_REGEX.find(headerLine)
            val path = match?.groupValues?.get(1) ?: continue

            val fileLines = mutableListOf<ParsedDiffLine>()
            var oldLine = 1
            var newLine = 1
            var inHunk = false

            for (line in lines) {
                if (line.startsWith("@@ ")) {
                    inHunk = true
                    val hunkMatch = HUNK_REGEX.find(line)
                    if (hunkMatch != null) {
                        oldLine = hunkMatch.groupValues[1].toInt()
                        newLine = hunkMatch.groupValues[2].toInt()
                    }
                    continue
                }

                if (!inHunk) continue

                when {
                    line.startsWith("+") && !line.startsWith("+++") -> {
                        fileLines.add(
                            ParsedDiffLine(
                                oldLineNumber = null,
                                newLineNumber = newLine,
                                content = line,
                                type = DiffLineType.ADDITION
                            )
                        )
                        newLine++
                    }
                    line.startsWith("-") && !line.startsWith("---") -> {
                        fileLines.add(
                            ParsedDiffLine(
                                oldLineNumber = oldLine,
                                newLineNumber = null,
                                content = line,
                                type = DiffLineType.DELETION
                            )
                        )
                        oldLine++
                    }
                    line.startsWith(" ") || line.isEmpty() -> {
                        fileLines.add(
                            ParsedDiffLine(
                                oldLineNumber = oldLine,
                                newLineNumber = newLine,
                                content = line,
                                type = DiffLineType.CONTEXT
                            )
                        )
                        oldLine++
                        newLine++
                    }
                }
            }
            resultMap[path] = fileLines
        }
        return resultMap
    }

    companion object {
        private val DIFF_GIT_REGEX = Regex("^diff --git ", RegexOption.MULTILINE)
        private val PATH_REGEX = Regex("""b/([^\s]+)""")
        private val HUNK_REGEX = Regex("""@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@""")
    }
}

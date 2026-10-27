package com.felixbrucker.jules.data.repository

import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import com.felixbrucker.jules.data.database.ActivityBasedStateDao
import com.felixbrucker.jules.data.database.ActivityBasedStateEntity
import com.felixbrucker.jules.data.database.AgentState
import com.felixbrucker.jules.data.database.ChatElement
import com.felixbrucker.jules.data.database.ChatElementDao
import com.felixbrucker.jules.data.database.ChatElementEntity
import com.felixbrucker.jules.data.database.ChatStateDao
import com.felixbrucker.jules.data.database.ChatStateEntity
import com.felixbrucker.jules.data.database.ComputedDiff
import com.felixbrucker.jules.data.database.DispatchableWorkflowEntity
import com.felixbrucker.jules.data.database.DispatchableWorkflowDao
import com.felixbrucker.jules.data.database.PullRequestDao
import com.felixbrucker.jules.data.database.PullRequestEntity
import com.felixbrucker.jules.data.database.SessionDao
import com.felixbrucker.jules.data.database.SessionDraftDao
import com.felixbrucker.jules.data.database.SessionDraftEntity
import com.felixbrucker.jules.data.database.SessionDraftWithSource
import com.felixbrucker.jules.data.database.SessionEntity
import com.felixbrucker.jules.data.database.SessionWithPullRequest
import com.felixbrucker.jules.data.database.SourceDao
import com.felixbrucker.jules.data.database.SourceEntity
import com.felixbrucker.jules.data.database.SourceSyncMetadataDao
import com.felixbrucker.jules.data.database.toEntity
import com.felixbrucker.jules.data.datastore.AppSettingsRepository
import com.felixbrucker.jules.data.datastore.SyncMetadataRepository
import com.felixbrucker.jules.network.api.GitHubApi
import com.felixbrucker.jules.network.api.JulesApi
import com.felixbrucker.jules.network.api.filter.ActivityFilter
import com.felixbrucker.jules.network.api.model.Activity
import com.felixbrucker.jules.network.api.model.AutomationMode
import com.felixbrucker.jules.network.api.model.CreateSessionRequest
import com.felixbrucker.jules.network.api.model.GitHubRepoContext
import com.felixbrucker.jules.network.api.model.JulesApiException
import com.felixbrucker.jules.network.api.model.ParsedFile
import com.felixbrucker.jules.network.api.model.SessionState
import com.felixbrucker.jules.network.api.model.SourceContext
import com.felixbrucker.jules.network.api.util.UnidiffParser
import com.felixbrucker.jules.network.api.util.WorkflowDispatchParser
import com.felixbrucker.jules.notification.JulesNotificationManager
import com.felixbrucker.jules.ui.sessions.DiffLineType
import com.felixbrucker.jules.ui.sessions.ParsedDiffLine
import com.felixbrucker.jules.util.periodicTickerFlow
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Duration
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.minutes

@Singleton
class JulesRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val pullRequestDao: PullRequestDao,
    private val sourceDao: SourceDao,
    private val sessionDraftDao: SessionDraftDao,
    private val dispatchableWorkflowDao: DispatchableWorkflowDao,
    private val sourceSyncMetadataDao: SourceSyncMetadataDao,
    private val chatElementDao: ChatElementDao,
    private val chatStateDao: ChatStateDao,
    private val activityBasedStateDao: ActivityBasedStateDao,
    private val syncMetadataRepository: SyncMetadataRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val notificationManager: JulesNotificationManager,
    private val julesApi: JulesApi,
    private val gitHubApi: GitHubApi
) {

    private val sessionMutexes = ConcurrentHashMap<String, Mutex>()

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getDailySessionCountFlow(): Flow<Int> {
        return periodicTickerFlow(10.minutes)
            .flatMapLatest { sessionDao.getDailySessionCountFlow() }
    }

    fun getSessionsFlow(
        searchQuery: String,
        states: List<SessionState>,
        includeArchived: Boolean
    ): Flow<List<SessionWithPullRequest>> {
        val formattedQuery = if (searchQuery.isBlank()) "%%" else "%${searchQuery.trim()}%"
        return sessionDao.getSessions(
            searchQuery = formattedQuery,
            states = states,
            statesIsEmpty = states.isEmpty(),
            includeArchived = includeArchived
        )
    }

    fun getSessionFlow(sessionId: String): Flow<SessionWithPullRequest?> =
        sessionDao.getSessionByIdFlow(sessionId)

    fun getChatElementsPagingDataFlow(sessionId: String): Flow<PagingData<ChatElementEntity>> {
        return Pager(
            config = PagingConfig(pageSize = 30, enablePlaceholders = false)
        ) {
            chatElementDao.getChatElementsPagingSource(sessionId)
        }.flow.flowOn(Dispatchers.Default)
    }

    fun getChatStateFlow(sessionId: String): Flow<ChatStateEntity?> =
        chatStateDao.getChatStateFlow(sessionId)

    fun getActivityBasedStateFlow(sessionId: String): Flow<ActivityBasedStateEntity?> =
        activityBasedStateDao.getActivityBasedStateFlow(sessionId)

    fun getDraftsFlow(searchQuery: String): Flow<List<SessionDraftWithSource>> {
        val formattedQuery = if (searchQuery.isBlank()) "%%" else "%${searchQuery.trim()}%"
        return sessionDraftDao.getDrafts(formattedQuery)
    }

    suspend fun saveDraft(draft: SessionDraftEntity) = withContext(Dispatchers.IO) {
        sessionDraftDao.upsertDraft(draft)
    }

    suspend fun deleteDraft(draftId: String) = withContext(Dispatchers.IO) {
        sessionDraftDao.deleteDraft(draftId)
    }

    suspend fun createSessionFromDraft(draft: SessionDraftEntity) = withContext(Dispatchers.IO) {
        val sourceContext = draft.sourceId?.let { srcId ->
            SourceContext(
                source = if (srcId.startsWith("sources/")) srcId else "sources/$srcId",
                githubRepoContext = draft.branch?.let { GitHubRepoContext(startingBranch = it) }
            )
        }
        val request = CreateSessionRequest(
            prompt = draft.prompt,
            title = draft.title?.ifBlank { null },
            sourceContext = sourceContext,
            requirePlanApproval = draft.requirePlanApproval,
            automationMode = AutomationMode.AUTOMATION_MODE_UNSPECIFIED
        )
        val created = julesApi.createSession(request)
        val fetched = julesApi.getSession(created.id)
        saveSessions(listOf(fetched.toEntity()), isBackground = false)
        sessionDraftDao.deleteDraft(draft.id)
    }

    fun getSourcesFlow(): Flow<List<SourceEntity>> = sourceDao.getAllSourcesFlow()

    suspend fun validateApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        julesApi.validateApiKey(apiKey = apiKey)
    }

    suspend fun validateGithubAuthToken(token: String) = withContext(Dispatchers.IO) {
        gitHubApi.validateAuthToken(token)
    }

    suspend fun sync(isBackground: Boolean = false, force: Boolean = false) = withContext(Dispatchers.IO) {
        syncSources(force = force)
        syncSessions(isBackground = isBackground)
        syncArchivedSessions(isBackground = isBackground, force = force)
        syncPullRequests()
        syncWorkflows(force = force)
    }

    suspend fun syncActivities(sessionId: String, pageSize: Int = 30) = withContext(Dispatchers.IO) {
        val mutex = sessionMutexes.getOrPut(sessionId) { Mutex() }
        if (!mutex.tryLock()) return@withContext

        try {
            var chatState = chatStateDao.getChatState(sessionId)
                ?: ChatStateEntity(sessionId = sessionId, agentState = AgentState.Idle)
            var activityBasedState = activityBasedStateDao.getActivityBasedState(sessionId)
                ?: ActivityBasedStateEntity(sessionId = sessionId)

            val initialFilter = activityBasedState.latestActivityCreatedAt?.let { instant ->
                ActivityFilter.CreatedAfter(instant.plusMillis(100))
            }
            var pageToken: String? = null
            do {
                val response = julesApi.getActivities(
                    sessionId = sessionId,
                    filter = initialFilter,
                    pageSize = pageSize,
                    pageToken = pageToken
                )

                val newChatElements = mutableListOf<ChatElementEntity>()
                var updatedUnidiffAt: Instant? = null

                for (activity in response.activitiesList) {
                    val actTime = activity.createTime
                    val lastActTime = activityBasedState.latestActivityCreatedAt

                    when (activity) {
                        is Activity.UserMessaged -> {
                            chatState = chatState.copy(agentState = AgentState.Thinking)
                            newChatElements.add(
                                ChatElementEntity(
                                    id = activity.id,
                                    sessionId = sessionId,
                                    createdAt = actTime,
                                    element = ChatElement.UserMessageElement(activity.userMessage)
                                )
                            )
                        }
                        is Activity.AgentMessaged -> {
                            val prevTime = lastActTime ?: actTime
                            val durationMillis = Duration.between(prevTime, actTime).toMillis().coerceAtLeast(0)
                            newChatElements.add(
                                ChatElementEntity(
                                    id = activity.id,
                                    sessionId = sessionId,
                                    createdAt = actTime,
                                    element = ChatElement.AgentThoughtElement(
                                        thoughts = activity.agentMessage,
                                        thoughtDurationMillis = durationMillis
                                    )
                                )
                            )
                        }
                        is Activity.ProgressUpdated -> {
                            var title = activity.title
                            var desc = activity.description

                            if (!title.isNullOrBlank() && !desc.isNullOrBlank()) {
                                val cleanTitle = title.trimEnd('.', '…').trim()
                                if (cleanTitle.isNotEmpty() && desc.startsWith(cleanTitle)) {
                                    title = desc
                                    desc = null
                                }
                            }

                            val element = when {
                                !title.isNullOrBlank() -> ChatElement.AgentMessageBlockElement(title, desc)
                                !desc.isNullOrBlank() -> ChatElement.AgentMessageBlockElement("Progress Update", desc)
                                else -> null
                            }
                            if (element != null) {
                                newChatElements.add(
                                    ChatElementEntity(
                                        id = activity.id,
                                        sessionId = sessionId,
                                        createdAt = actTime,
                                        element = element
                                    )
                                )
                            }
                        }
                        is Activity.PlanGenerated -> {
                            newChatElements.add(
                                ChatElementEntity(
                                    id = activity.id,
                                    sessionId = sessionId,
                                    createdAt = actTime,
                                    element = ChatElement.PlanElement(activity.plan.steps)
                                )
                            )
                        }
                        is Activity.PlanApproved -> {
                            chatState = chatState.copy(hasApprovedPlan = true)
                            newChatElements.add(
                                ChatElementEntity(
                                    id = activity.id,
                                    sessionId = sessionId,
                                    createdAt = actTime,
                                    element = ChatElement.PlanApprovedElement()
                                )
                            )
                        }
                        is Activity.SessionFailed -> {
                            newChatElements.add(
                                ChatElementEntity(
                                    id = activity.id,
                                    sessionId = sessionId,
                                    createdAt = actTime,
                                    element = ChatElement.SessionFailedElement(activity.reason ?: "Session failed")
                                )
                            )
                        }
                        is Activity.SessionCompleted -> {
                            val cs = activity.artifacts.firstNotNullOfOrNull { it.changeSet }
                            val gitPatch = cs?.gitPatch
                            val unidiff = gitPatch?.unidiffPatch
                            val parsedFiles = UnidiffParser.parse(unidiff)
                            val totalAdditions = parsedFiles.sumOf { it.additions }
                            val totalDeletions = parsedFiles.sumOf { it.deletions }
                            val commitMsg = gitPatch?.suggestedCommitMessage

                            newChatElements.add(
                                ChatElementEntity(
                                    id = activity.id,
                                    sessionId = sessionId,
                                    createdAt = actTime,
                                    element = ChatElement.SessionCompletedElement(
                                        totalAdditions = totalAdditions,
                                        totalDeletions = totalDeletions,
                                        suggestedCommitMessage = commitMsg
                                    )
                                )
                            )
                        }
                    }

                    if (activity is Activity.SessionCompleted || activity is Activity.SessionFailed) {
                        chatState = chatState.copy(agentState = AgentState.Idle)
                    } else if (activity is Activity.PlanGenerated) {
                        if (!chatState.hasApprovedPlan) {
                            chatState = chatState.copy(agentState = AgentState.Idle)
                        }
                    } else if (activity !is Activity.UserMessaged) {
                        chatState = chatState.copy(agentState = AgentState.Working)
                    }

                    val patch = when (activity) {
                        is Activity.ProgressUpdated -> activity.artifacts.firstNotNullOfOrNull { it.changeSet }?.gitPatch?.unidiffPatch
                        is Activity.SessionCompleted -> activity.artifacts.firstNotNullOfOrNull { it.changeSet }?.gitPatch?.unidiffPatch
                        is Activity.SessionFailed -> activity.artifacts.firstNotNullOfOrNull { it.changeSet }?.gitPatch?.unidiffPatch
                        else -> null
                    }

                    if (!patch.isNullOrBlank()) {
                        val changedFiles = computeChangedFiles(activityBasedState.latestUnidiff, patch)
                        if (changedFiles.isNotEmpty()) {
                            newChatElements.add(
                                ChatElementEntity(
                                    id = "cf_${activity.id}",
                                    sessionId = sessionId,
                                    createdAt = actTime,
                                    element = ChatElement.ChangedFilesElement(changedFiles)
                                )
                            )
                        }
                        activityBasedState = activityBasedState.copy(latestUnidiff = patch)
                        updatedUnidiffAt = actTime
                    }

                    activityBasedState = activityBasedState.copy(latestActivityCreatedAt = actTime)
                }

                if (updatedUnidiffAt != null) {
                    val patch = activityBasedState.latestUnidiff ?: ""
                    val parsedFiles = UnidiffParser.parse(patch)
                    val fileDiffLinesMap = extractAllFileDiffLines(patch)
                    val parsedFilesMap = parsedFiles.associateBy { it.path }
                    val computedDiff = ComputedDiff(
                        fileDiffLinesMap = fileDiffLinesMap,
                        parsedFilesMap = parsedFilesMap,
                        updatedAt = updatedUnidiffAt
                    )
                    activityBasedState = activityBasedState.copy(latestDiff = computedDiff)
                }

                chatStateDao.upsertChatState(chatState)
                activityBasedStateDao.upsertState(activityBasedState)
                if (newChatElements.isNotEmpty()) {
                    chatElementDao.upsertElements(newChatElements)
                }

                pageToken = response.nextPageToken
            } while (!pageToken.isNullOrBlank())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag(TAG).e(e, "Error syncing activities for session $sessionId")
            throw e
        } finally {
            mutex.unlock()
        }
    }

    suspend fun syncSession(sessionId: String) = withContext(Dispatchers.IO) {
        try {
            val fetched = julesApi.getSession(sessionId)
            saveSessions(listOf(fetched.toEntity()), isBackground = false)

            val patch = fetched.changeSets.firstOrNull()?.gitPatch?.unidiffPatch
            if (!patch.isNullOrBlank()) {
                val activityBasedState = activityBasedStateDao.getActivityBasedState(sessionId)
                    ?: ActivityBasedStateEntity(sessionId = sessionId)
                val updateTime = fetched.updateTime ?: fetched.createTime ?: Instant.now()

                if (activityBasedState.latestDiff == null || updateTime.isAfter(activityBasedState.latestDiff.updatedAt)) {
                    val parsedFiles = UnidiffParser.parse(patch)
                    val fileDiffLinesMap = extractAllFileDiffLines(patch)
                    val parsedFilesMap = parsedFiles.associateBy { it.path }
                    val computedDiff = ComputedDiff(
                        fileDiffLinesMap = fileDiffLinesMap,
                        parsedFilesMap = parsedFilesMap,
                        updatedAt = updateTime
                    )
                    activityBasedStateDao.upsertState(
                        activityBasedState.copy(
                            latestUnidiff = patch,
                            latestDiff = computedDiff
                        )
                    )
                }
            }

            syncPullRequests()
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag(TAG).e(e, "Error syncing session $sessionId")
            throw e
        }
    }

    suspend fun sendMessage(sessionId: String, prompt: String) = withContext(Dispatchers.IO) {
        julesApi.sendMessage(sessionId = sessionId, prompt = prompt)
        syncActivities(sessionId = sessionId)
        syncSession(sessionId = sessionId)
    }

    suspend fun approvePlan(sessionId: String) = withContext(Dispatchers.IO) {
        julesApi.approvePlan(sessionId = sessionId)
        val chatState = chatStateDao.getChatState(sessionId)
            ?: ChatStateEntity(sessionId = sessionId, agentState = AgentState.Idle)
        chatStateDao.upsertChatState(chatState.copy(hasApprovedPlan = true))
        syncActivities(sessionId = sessionId)
        syncSession(sessionId = sessionId)
    }

    suspend fun syncSources(force: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            if (!force) {
                val lastSyncedAt = syncMetadataRepository.lastSourcesSyncedAtFlow.first()
                if (lastSyncedAt != null && Duration.between(lastSyncedAt, Instant.now()).toHours() < 24) {
                    return@withContext
                }
            }

            val fetchedSourceIds = mutableSetOf<String>()
            var pageToken: String? = null
            val pageSize = 100

            do {
                val response = julesApi.getSources(
                    pageSize = pageSize,
                    pageToken = pageToken
                )
                val entities = response.sources.map { it.toEntity() }
                sourceDao.upsertSources(entities)
                response.sources.forEach { fetchedSourceIds.add(it.id) }
                pageToken = response.nextPageToken
            } while (!pageToken.isNullOrBlank())

            if (fetchedSourceIds.isNotEmpty()) {
                sourceDao.deleteSourcesNotIn(fetchedSourceIds.toList())
            }
            syncMetadataRepository.saveLastSourcesSyncedAt(Instant.now())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag(TAG).e(e, "Error syncing sources")
            throw e
        }
    }

    private suspend fun saveSessions(entities: List<SessionEntity>, isBackground: Boolean = false) {
        if (entities.isEmpty()) return

        ensureSourcesExist(entities)

        val sessionIds = entities.map { it.id }
        val previousSessionsMap = sessionDao.getSessionsByIds(sessionIds).associateBy { it.id }
        val existingSessionIds = previousSessionsMap.keys

        val (toInsert, toUpdate) = entities.partition { !existingSessionIds.contains(it.id) }

        if (toInsert.isNotEmpty()) {
            sessionDao.insertSessions(toInsert)
            val initialElements = toInsert.mapNotNull { session ->
                if (session.prompt.isNotBlank()) {
                    ChatElementEntity(
                        id = "init_prompt_${session.id}",
                        sessionId = session.id,
                        createdAt = session.createTime ?: Instant.now(),
                        element = ChatElement.UserMessageElement(session.prompt)
                    )
                } else null
            }
            if (initialElements.isNotEmpty()) {
                chatElementDao.upsertElements(initialElements)
            }
        }
        if (toUpdate.isNotEmpty()) {
            sessionDao.updateSessions(toUpdate)
        }

        if (isBackground) {
            val feedbackNotificationsEnabled = appSettingsRepository.feedbackNotificationsEnabledFlow.first()
            val completionNotificationsEnabled = appSettingsRepository.completionNotificationsEnabledFlow.first()

            for (newSession in entities) {
                val previousSession = previousSessionsMap[newSession.id]
                val stateChanged = (previousSession == null || previousSession.state != newSession.state)

                if (stateChanged) {
                    if (feedbackNotificationsEnabled && newSession.state.requiresFeedback) {
                        notificationManager.sendTaskFeedbackNotification(newSession)
                    } else if (completionNotificationsEnabled && newSession.state == SessionState.COMPLETED) {
                        notificationManager.sendTaskCompletedNotification(newSession)
                    }
                }
            }
        }
    }

    private suspend fun ensureSourcesExist(entities: List<SessionEntity>) = coroutineScope {
        val requiredSourceIds = entities.mapNotNull { it.sourceId }.distinct()
        if (requiredSourceIds.isEmpty()) return@coroutineScope

        val existingSourceIds = sourceDao.getAllSourceIds().toSet()
        val missingSourceIds = requiredSourceIds.filter { !existingSourceIds.contains(it) }
        if (missingSourceIds.isEmpty()) return@coroutineScope

        val semaphore = Semaphore(4)
        val fetchedSources = missingSourceIds.map { sourceId ->
            async {
                semaphore.withPermit {
                    try {
                        val source = julesApi.getSource(sourceId)
                        source.toEntity()
                    } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        Timber.tag(TAG).e(e, "Failed to retrieve missing source $sourceId")
                        null
                    }
                }
            }
        }.awaitAll().filterNotNull()

        if (fetchedSources.isNotEmpty()) {
            sourceDao.upsertSources(fetchedSources)
        }
    }

    suspend fun syncSessions(isBackground: Boolean = false) = withContext(Dispatchers.IO) {
        val fetchedSessionIds = mutableSetOf<String>()
        var pageToken: String? = null
        val pageSize = 30

        try {
            do {
                val response = julesApi.getSessions(
                    pageSize = pageSize,
                    pageToken = pageToken
                )
                val entities = response.sessions.map { it.toEntity() }
                saveSessions(entities, isBackground = isBackground)
                response.sessions.forEach { fetchedSessionIds.add(it.id) }
                pageToken = response.nextPageToken
            } while (!pageToken.isNullOrBlank())

            val localActiveIds = sessionDao.getAllActiveSessionIds()
            val missingIds = localActiveIds.filter { !fetchedSessionIds.contains(it) }

            if (missingIds.isNotEmpty()) {
                val semaphore = Semaphore(4)
                val entities = missingIds.map { missingId ->
                    async {
                        semaphore.withPermit {
                            try {
                                val session = julesApi.getSession(missingId)
                                session.toEntity()
                            } catch (e: JulesApiException) {
                                if (e.httpStatusCode == 404) {
                                    sessionDao.deleteSession(missingId)
                                } else {
                                    Timber.tag(TAG).e(e, "Failed to fetch missing active session $missingId")
                                }
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
                saveSessions(entities, isBackground = isBackground)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag(TAG).e(e, "Error syncing sessions")
            throw e
        }
    }

    suspend fun syncPullRequests() = withContext(Dispatchers.IO) {
        try {
            val githubToken = appSettingsRepository.githubAuthTokenFlow.first()
            val includePrivate = !githubToken.isNullOrBlank()
            val activeSessions = sessionDao.getActiveSessionsWithPullRequests(includePrivate = includePrivate)

            val sessionPrTuples = activeSessions.mapNotNull { session ->
                val prUrl = session.pullRequest?.url ?: return@mapNotNull null
                session.id to prUrl
            }

            if (sessionPrTuples.isEmpty()) return@withContext

            val semaphore = Semaphore(4)
            val prEntities = sessionPrTuples.map { (sessionId, prUrl) ->
                async {
                    semaphore.withPermit {
                        try {
                            val state = gitHubApi.getPullRequestState(prUrl)
                            PullRequestEntity(sessionId = sessionId, url = prUrl, state = state)
                        } catch (e: Exception) {
                            if (e is CancellationException) throw e
                            Timber.tag(TAG).e(e, "Failed to sync pull request state for $prUrl")
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()

            if (prEntities.isNotEmpty()) {
                pullRequestDao.upsertPullRequests(prEntities)
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag(TAG).e(e, "Error syncing pull requests")
            throw e
        }
    }

    suspend fun syncArchivedSessions(isBackground: Boolean = false, force: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            if (!force) {
                val lastSyncedAt = syncMetadataRepository.lastArchivedSessionsSyncedAtFlow.first()
                if (lastSyncedAt != null && Duration.between(lastSyncedAt, Instant.now()).toHours() < 24) {
                    return@withContext
                }
            }

            val fetchedArchivedIds = mutableSetOf<String>()
            var pageToken: String? = null
            val pageSize = 30

            do {
                val response = julesApi.getArchivedSessions(
                    pageSize = pageSize,
                    pageToken = pageToken
                )
                val entities = response.sessions.map { it.toEntity() }
                saveSessions(entities, isBackground = isBackground)
                response.sessions.forEach { fetchedArchivedIds.add(it.id) }
                pageToken = response.nextPageToken
            } while (!pageToken.isNullOrBlank())

            val localArchivedIds = sessionDao.getAllArchivedSessionIds()
            val missingIds = localArchivedIds.filter { !fetchedArchivedIds.contains(it) }

            if (missingIds.isNotEmpty()) {
                val semaphore = Semaphore(4)
                val entities = missingIds.map { missingId ->
                    async {
                        semaphore.withPermit {
                            try {
                                val session = julesApi.getSession(missingId)
                                session.toEntity()
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Timber.tag(TAG).e(e, "Failed to fetch missing archived session $missingId")
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
                saveSessions(entities, isBackground = isBackground)
            }
            syncMetadataRepository.saveLastArchivedSessionsSyncedAt(Instant.now())
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag(TAG).e(e, "Error syncing archived sessions")
            throw e
        }
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        try {
            julesApi.deleteSession(sessionId)
            sessionDao.deleteSession(sessionId)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag(TAG).e(e, "Failed to delete session $sessionId")
            throw e
        }
    }

    suspend fun archiveSession(sessionId: String) = withContext(Dispatchers.IO) {
        try {
            julesApi.archiveSession(sessionId)
            sessionDao.updateArchived(sessionId, isArchived = true)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag(TAG).e(e, "Failed to archive session $sessionId")
            throw e
        }
    }

    suspend fun unarchiveSession(sessionId: String) = withContext(Dispatchers.IO) {
        try {
            julesApi.unarchiveSession(sessionId)
            sessionDao.updateArchived(sessionId, isArchived = false)
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag(TAG).e(e, "Failed to unarchive session $sessionId")
            throw e
        }
    }

    suspend fun syncWorkflows(force: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            val githubToken = appSettingsRepository.githubAuthTokenFlow.first()
            if (githubToken.isNullOrBlank()) {
                return@withContext
            }

            val thresholdEpochMillis = Instant.now().minus(Duration.ofDays(14)).toEpochMilli()
            val usableSources = sourceDao.getSourcesNeedingWorkflowSync(
                thresholdEpochMillis = thresholdEpochMillis,
                force = force
            )

            if (usableSources.isEmpty()) {
                return@withContext
            }

            val semaphore = Semaphore(4)
            coroutineScope {
                usableSources.map { source ->
                    async {
                        semaphore.withPermit {
                            try {
                                val repo = source.githubRepo!!
                                val activeWorkflows = gitHubApi.getWorkflows(owner = repo.owner, repo = repo.repo)
                                    .filter { it.state.equals("active", ignoreCase = true) }

                                val entitiesForSource = mutableListOf<DispatchableWorkflowEntity>()
                                for (wf in activeWorkflows) {
                                    try {
                                        val content = gitHubApi.getWorkflowContent(owner = repo.owner, repo = repo.repo, path = wf.path)
                                        val parsed = WorkflowDispatchParser.parseWorkflowDispatch(content)
                                        if (parsed.hasWorkflowDispatch) {
                                            entitiesForSource.add(
                                                DispatchableWorkflowEntity(
                                                    id = "${source.id}_${wf.id}",
                                                    sourceId = source.id,
                                                    workflowId = wf.id,
                                                    name = wf.name,
                                                    inputs = parsed.inputs
                                                )
                                            )
                                        }
                                    } catch (e: Exception) {
                                        if (e is CancellationException) throw e
                                        Timber.tag(TAG).e(e, "Failed to fetch/parse workflow content for ${wf.path} in ${source.id}")
                                    }
                                }

                                if (entitiesForSource.isNotEmpty()) {
                                    dispatchableWorkflowDao.upsertWorkflows(entitiesForSource)
                                }
                                sourceSyncMetadataDao.updateLastWorkflowSyncedAtTargeted(
                                    sourceId = source.id,
                                    timestamp = Instant.now()
                                )
                            } catch (e: Exception) {
                                if (e is CancellationException) throw e
                                Timber.tag(TAG).e(e, "Failed to fetch workflows for source ${source.id}")
                            }
                        }
                    }
                }.awaitAll()
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            Timber.tag(TAG).e(e, "Error syncing workflows")
            throw e
        }
    }

    suspend fun dispatchWorkflow(
        owner: String,
        repo: String,
        workflowId: String,
        ref: String,
        inputs: Map<String, String>? = null
    ) = withContext(Dispatchers.IO) {
        gitHubApi.dispatchWorkflow(
            owner = owner,
            repo = repo,
            workflowId = workflowId,
            ref = ref,
            inputs = inputs
        )
    }

    private fun computeChangedFiles(unidiff1: String?, unidiff2: String): List<String> {
        val files2 = UnidiffParser.parse(unidiff2)
        if (unidiff1.isNullOrBlank()) {
            return files2.map { it.path }
        }
        val files1Map = UnidiffParser.parse(unidiff1).associateBy { it.path }
        return files2.filter { file2 ->
            val file1 = files1Map[file2.path]
            file1 == null || file1.additions != file2.additions || file1.deletions != file2.deletions || file1.changeType != file2.changeType
        }.map { it.path }
    }

    private fun extractAllFileDiffLines(unidiffPatch: String): Map<String, List<ParsedDiffLine>> {
        if (unidiffPatch.isBlank()) return emptyMap()
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
        private const val TAG = "JulesRepository"
        private val DIFF_GIT_REGEX = Regex("^diff --git ", RegexOption.MULTILINE)
        private val PATH_REGEX = Regex("""b/([^\s]+)""")
        private val HUNK_REGEX = Regex("""@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@""")
    }
}

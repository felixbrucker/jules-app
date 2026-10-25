package com.felixbrucker.jules.data.repository

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
import com.felixbrucker.jules.data.database.SourceSyncMetadataEntity
import com.felixbrucker.jules.data.database.WorkflowDispatchDao
import com.felixbrucker.jules.data.database.DispatchableWorkflowEntity
import com.felixbrucker.jules.data.database.toEntity
import com.felixbrucker.jules.network.api.util.WorkflowDispatchParser
import com.felixbrucker.jules.network.api.model.AutomationMode
import com.felixbrucker.jules.network.api.model.CreateSessionRequest
import com.felixbrucker.jules.network.api.model.GitHubRepoContext
import com.felixbrucker.jules.network.api.model.SourceContext
import com.felixbrucker.jules.data.datastore.AppSettingsRepository
import com.felixbrucker.jules.data.datastore.SyncMetadataRepository
import com.felixbrucker.jules.notification.JulesNotificationManager
import com.felixbrucker.jules.network.api.GitHubApi
import com.felixbrucker.jules.network.api.JulesApi
import com.felixbrucker.jules.network.api.model.JulesApiException
import com.felixbrucker.jules.network.api.model.SessionState
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
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.time.Duration.Companion.minutes

@Singleton
class JulesRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val pullRequestDao: PullRequestDao,
    private val sourceDao: SourceDao,
    private val sessionDraftDao: SessionDraftDao,
    private val workflowDispatchDao: WorkflowDispatchDao,
    private val sourceSyncMetadataDao: SourceSyncMetadataDao,
    private val syncMetadataRepository: SyncMetadataRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val notificationManager: JulesNotificationManager,
    private val julesApi: JulesApi,
    private val gitHubApi: GitHubApi
) {

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
        val sessionIds = entities.map { it.id }
        val previousSessionsMap = sessionDao.getSessionsByIds(sessionIds).associateBy { it.id }
        val existingSessionIds = previousSessionsMap.keys

        val (toInsert, toUpdate) = entities.partition { !existingSessionIds.contains(it.id) }

        if (toInsert.isNotEmpty()) {
            ensureSourcesExist(toInsert)
            sessionDao.insertSessions(toInsert)
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
            // Step 1: Paginate through regular (non-archived) sessions
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

            // Step 2: Check local active session IDs not returned in sync and fetch them in parallel (max 4)
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
                                // The session was deleted remotely, remove from local cache
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
                                    workflowDispatchDao.upsertWorkflows(entitiesForSource)
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

    companion object {
        private const val TAG = "JulesRepository"
    }
}

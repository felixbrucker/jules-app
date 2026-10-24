package com.felixbrucker.jules.data.repository

import com.felixbrucker.jules.data.database.PullRequestDao
import com.felixbrucker.jules.data.database.PullRequestEntity
import com.felixbrucker.jules.data.database.SessionDao
import com.felixbrucker.jules.data.database.SessionEntity
import com.felixbrucker.jules.data.database.SessionWithPullRequest
import com.felixbrucker.jules.data.database.SourceDao
import com.felixbrucker.jules.data.database.toEntity
import com.felixbrucker.jules.data.datastore.AppSettingsRepository
import com.felixbrucker.jules.data.datastore.SyncMetadataRepository
import com.felixbrucker.jules.network.api.GitHubApi
import com.felixbrucker.jules.network.api.JulesApi
import com.felixbrucker.jules.network.api.model.JulesApiException
import com.felixbrucker.jules.network.api.model.SessionState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class JulesRepository @Inject constructor(
    private val sessionDao: SessionDao,
    private val pullRequestDao: PullRequestDao,
    private val sourceDao: SourceDao,
    private val syncMetadataRepository: SyncMetadataRepository,
    private val appSettingsRepository: AppSettingsRepository,
    private val julesApi: JulesApi,
    private val gitHubApi: GitHubApi
) {

    @OptIn(ExperimentalCoroutinesApi::class)
    fun getDailySessionCountFlow(): Flow<Int> {
        return periodicTickerFlow(10 * 60 * 1000L)
            .flatMapLatest { sessionDao.getDailySessionCountFlow() }
    }

    private fun periodicTickerFlow(periodMillis: Long): Flow<Unit> = flow {
        while (true) {
            emit(Unit)
            delay(periodMillis)
        }
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

    suspend fun validateApiKey(apiKey: String) = withContext(Dispatchers.IO) {
        julesApi.validateApiKey(apiKey = apiKey)
    }

    suspend fun validateGithubAuthToken(token: String) = withContext(Dispatchers.IO) {
        gitHubApi.validateAuthToken(token)
    }

    suspend fun sync(force: Boolean = false) = withContext(Dispatchers.IO) {
        syncSources(force = force)
        syncSessions()
        syncPullRequests()
    }

    suspend fun syncSources(force: Boolean = false) = withContext(Dispatchers.IO) {
        try {
            if (!force) {
                val sourceCount = sourceDao.getSourceCount()
                val lastSyncedAt = syncMetadataRepository.lastSourcesSyncedAtFlow.first()
                if (sourceCount > 0 && lastSyncedAt != null) {
                    val hoursSinceLastSync = Duration.between(lastSyncedAt, Instant.now()).toHours()
                    if (hoursSinceLastSync < 24) {
                        return@withContext
                    }
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
            Timber.e(e, "Error syncing sources")
            throw e
        }
    }

    private suspend fun saveSessions(entities: List<SessionEntity>) {
        if (entities.isEmpty()) return
        val existingSessionIds = sessionDao.getAllSessionIds().toSet()
        val (toInsert, toUpdate) = entities.partition { !existingSessionIds.contains(it.id) }

        if (toInsert.isNotEmpty()) {
            ensureSourcesExist(toInsert)
            sessionDao.insertSessions(toInsert)
        }
        if (toUpdate.isNotEmpty()) {
            sessionDao.updateSessions(toUpdate)
        }
    }

    private suspend fun ensureSourcesExist(entities: List<SessionEntity>) {
        val requiredSourceIds = entities.mapNotNull { it.sourceId }.distinct()
        if (requiredSourceIds.isEmpty()) return

        val existingSourceIds = sourceDao.getAllSourceIds().toSet()
        val missingSourceIds = requiredSourceIds.filter { !existingSourceIds.contains(it) }

        for (sourceId in missingSourceIds) {
            try {
                val source = julesApi.getSource(sourceId)
                sourceDao.upsertSources(listOf(source.toEntity()))
            } catch (e: Exception) {
                Timber.e(e, "Failed to retrieve missing source $sourceId")
            }
        }
    }

    suspend fun syncSessions() = withContext(Dispatchers.IO) {
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
                saveSessions(entities)
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
                                    Timber.e(e, "Failed to fetch missing active session $missingId")
                                }
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
                saveSessions(entities)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error syncing sessions")
            throw e
        }
    }

    suspend fun syncPullRequests() = withContext(Dispatchers.IO) {
        try {
            val githubToken = appSettingsRepository.githubAuthTokenFlow.first()
            val includePrivate = !githubToken.isNullOrBlank()
            val activeSessions = sessionDao.getActiveSessionsWithPullRequests(includePrivate = includePrivate)
            val prUrls = activeSessions.mapNotNull { it.pullRequest?.url }.distinct()

            if (prUrls.isEmpty()) return@withContext

            val semaphore = Semaphore(4)
            val prEntities = prUrls.map { prUrl ->
                async {
                    semaphore.withPermit {
                        try {
                            val state = gitHubApi.getPullRequestState(prUrl)
                            PullRequestEntity(url = prUrl, state = state)
                        } catch (e: Exception) {
                            Timber.e(e, "Failed to sync pull request state for $prUrl")
                            null
                        }
                    }
                }
            }.awaitAll().filterNotNull()

            if (prEntities.isNotEmpty()) {
                pullRequestDao.upsertPullRequests(prEntities)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error syncing pull requests")
            throw e
        }
    }

    suspend fun syncArchivedSessions() = withContext(Dispatchers.IO) {
        val fetchedArchivedIds = mutableSetOf<String>()
        var pageToken: String? = null
        val pageSize = 30

        try {
            do {
                val response = julesApi.getArchivedSessions(
                    pageSize = pageSize,
                    pageToken = pageToken
                )
                val entities = response.sessions.map { it.toEntity() }
                saveSessions(entities)
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
                                Timber.e(e, "Failed to fetch missing archived session $missingId")
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
                saveSessions(entities)
            }
        } catch (e: Exception) {
            Timber.e(e, "Error syncing archived sessions")
            throw e
        }
    }

    suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        try {
            julesApi.deleteSession(sessionId)
            sessionDao.deleteSession(sessionId)
        } catch (e: Exception) {
            Timber.e(e, "Failed to delete session $sessionId")
            throw e
        }
    }

    suspend fun archiveSession(sessionId: String) = withContext(Dispatchers.IO) {
        try {
            julesApi.archiveSession(sessionId)
            sessionDao.updateArchived(sessionId, isArchived = true)
        } catch (e: Exception) {
            Timber.e(e, "Failed to archive session $sessionId")
            throw e
        }
    }

    suspend fun unarchiveSession(sessionId: String) = withContext(Dispatchers.IO) {
        try {
            julesApi.unarchiveSession(sessionId)
            sessionDao.updateArchived(sessionId, isArchived = false)
        } catch (e: Exception) {
            Timber.e(e, "Failed to unarchive session $sessionId")
            throw e
        }
    }
}

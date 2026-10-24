package com.felixbrucker.jules.network.api

import com.felixbrucker.jules.network.api.model.GitHubApiException
import com.felixbrucker.jules.network.api.model.PullRequestState
import com.felixbrucker.jules.network.api.model.parseGitHubError
import com.squareup.moshi.Moshi
import retrofit2.HttpException
import javax.inject.Inject
import javax.inject.Singleton

data class GitHubPullRequestUrl(
    val owner: String,
    val repo: String,
    val pullNumber: Long
) {
    companion object {
        fun parse(url: String): GitHubPullRequestUrl? {
            return try {
                val cleaned = url.removePrefix("http://").removePrefix("https://")
                val parts = cleaned.split("/")
                if (parts.size >= 5 && parts[3] == "pull") {
                    val owner = parts[1]
                    val repo = parts[2]
                    val rawNumber = parts[4].takeWhile { it.isDigit() }
                    val pullNumber = rawNumber.toLongOrNull() ?: return null
                    if (owner.isNotBlank() && repo.isNotBlank()) {
                        GitHubPullRequestUrl(owner = owner, repo = repo, pullNumber = pullNumber)
                    } else null
                } else null
            } catch (e: Exception) {
                null
            }
        }
    }
}

@Singleton
class GitHubApi @Inject constructor(
    private val apiService: GitHubApiService,
    private val moshi: Moshi
) {
    suspend fun getPullRequestState(owner: String, repo: String, pullNumber: Long): PullRequestState {
        val response = executeApiCall {
            apiService.getPullRequest(owner = owner, repo = repo, pullNumber = pullNumber)
        }
        return response.pullRequestState
    }

    suspend fun getPullRequestState(url: String): PullRequestState? {
        val parsedUrl = GitHubPullRequestUrl.parse(url) ?: return null
        return getPullRequestState(
            owner = parsedUrl.owner,
            repo = parsedUrl.repo,
            pullNumber = parsedUrl.pullNumber
        )
    }

    private inline fun <T> executeApiCall(block: () -> T): T {
        return try {
            block()
        } catch (e: HttpException) {
            val error = e.parseGitHubError(moshi = moshi)
            throw GitHubApiException(
                apiError = error,
                httpStatusCode = e.code(),
                cause = e
            )
        }
    }

    companion object {
        const val DEFAULT_BASE_URL = "https://api.github.com/"
    }
}

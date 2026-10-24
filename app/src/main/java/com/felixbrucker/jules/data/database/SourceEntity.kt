package com.felixbrucker.jules.data.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.felixbrucker.jules.network.api.model.GitHubBranch
import com.felixbrucker.jules.network.api.model.Source

data class EmbeddedGitHubRepo(
    val owner: String,
    val repo: String,
    val isPrivate: Boolean = false,
    val defaultBranchName: String? = null,
    val branches: List<GitHubBranch>? = null
)

@Entity(tableName = "sources")
data class SourceEntity(
    @PrimaryKey
    val id: String,
    val name: String,
    @Embedded(prefix = "github_repo_")
    val githubRepo: EmbeddedGitHubRepo?
)

fun Source.toEntity(): SourceEntity {
    return SourceEntity(
        id = id,
        name = name,
        githubRepo = githubRepo?.let { repo ->
            EmbeddedGitHubRepo(
                owner = repo.owner,
                repo = repo.repo,
                isPrivate = repo.isPrivate,
                defaultBranchName = repo.defaultBranch?.displayName,
                branches = repo.branches
            )
        }
    )
}

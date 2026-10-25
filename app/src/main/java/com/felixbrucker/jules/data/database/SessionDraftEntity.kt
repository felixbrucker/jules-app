package com.felixbrucker.jules.data.database

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Relation
import java.time.Instant
import java.util.UUID

@Entity(tableName = "session_drafts")
data class SessionDraftEntity(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String? = null,
    val prompt: String = "",
    val sourceId: String? = null,
    val branch: String? = null,
    val requirePlanApproval: Boolean = true,
    val updatedAt: Instant = Instant.now()
)

data class SessionDraftWithSource(
    @Embedded
    val draft: SessionDraftEntity,
    @Relation(
        parentColumn = "sourceId",
        entityColumn = "id"
    )
    val source: SourceEntity?
)

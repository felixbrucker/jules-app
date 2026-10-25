package com.felixbrucker.jules.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "activities",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["sessionId"]),
        Index(value = ["sessionId", "createTime"]),
        Index(value = ["sessionId", "unidiffPatch"])
    ]
)
data class ActivityEntity(
    @PrimaryKey
    val id: String,
    val sessionId: String,
    val originator: String,
    val createTime: Instant,
    val type: String,
    val activityJson: String,
    val unidiffPatch: String? = null,
    val changedFilesJson: String? = null
)

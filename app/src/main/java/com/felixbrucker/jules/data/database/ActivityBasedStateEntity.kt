package com.felixbrucker.jules.data.database

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.time.Instant

@Entity(
    tableName = "activity_based_states",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE,
            onUpdate = ForeignKey.CASCADE
        )
    ]
)
data class ActivityBasedStateEntity(
    @PrimaryKey val sessionId: String,
    val latestUnidiff: String? = null,
    val latestDiff: ComputedDiff? = null,
    val latestActivityCreatedAt: Instant? = null
)

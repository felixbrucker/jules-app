package com.felixbrucker.jules.data.database

import androidx.room.TypeConverter
import com.felixbrucker.jules.network.api.model.PullRequestState
import com.felixbrucker.jules.network.api.model.SessionState
import java.time.Instant

class Converters {
    @TypeConverter
    fun fromSessionState(state: SessionState): String = state.name

    @TypeConverter
    fun toSessionState(value: String): SessionState = SessionState.valueOf(value)

    @TypeConverter
    fun fromPullRequestState(state: PullRequestState): String = state.name

    @TypeConverter
    fun toPullRequestState(value: String): PullRequestState = PullRequestState.valueOf(value)

    @TypeConverter
    fun fromInstant(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun toInstant(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }
}

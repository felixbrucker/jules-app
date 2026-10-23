package com.felixbrucker.jules.network.api.filter

import java.time.Instant

sealed class ActivityFilter : Filter<ActivityFilter> {
    data class CreatedAfter(val createdAfter: Instant) : ActivityFilter() {
        override fun toAip160String(): String = "create_time>\"$createdAfter\""
    }
}

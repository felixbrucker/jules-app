package com.felixbrucker.jules.network.api.filter

sealed class SessionFilter : Filter<SessionFilter> {
    data class ByArchived(val isArchived: Boolean) : SessionFilter() {
        override fun toAip160String(): String = "archived=$isArchived"
    }
}

package com.felixbrucker.jules.network.api.filter

sealed class SourceFilter : Filter<SourceFilter> {
    data class ByName(val name: String) : SourceFilter() {
        override fun toAip160String(): String = "name=\"$name\""
    }
}

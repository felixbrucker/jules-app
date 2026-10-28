package com.felixbrucker.jules.network.api.adapter

import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson
import java.time.Instant

class InstantAdapter {

    @ToJson
    fun toJson(instant: Instant?): String? {
        return instant?.toString()
    }

    @FromJson
    fun fromJson(json: String?): Instant? {
        if (json.isNullOrBlank()) return null
        return Instant.parse(json)
    }
}

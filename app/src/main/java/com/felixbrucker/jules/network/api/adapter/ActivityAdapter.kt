package com.felixbrucker.jules.network.api.adapter

import com.felixbrucker.jules.network.api.model.Activity
import com.felixbrucker.jules.network.api.model.RestActivity
import com.squareup.moshi.FromJson
import com.squareup.moshi.JsonWriter
import com.squareup.moshi.ToJson

class ActivityAdapter {

    @Suppress("unused")
    @FromJson
    fun fromJson(restActivity: RestActivity): Activity {
        return restActivity.toActivity()
    }

    @Suppress("unused")
    @ToJson
    fun toJson(writer: JsonWriter, value: Activity) {
        throw UnsupportedOperationException("Activity serialization is not supported")
    }
}

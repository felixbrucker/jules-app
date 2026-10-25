package com.felixbrucker.jules.network.api.adapter

import com.felixbrucker.jules.network.api.model.Activity
import com.felixbrucker.jules.network.api.model.RestActivity
import com.felixbrucker.jules.network.api.model.toRestActivity
import com.squareup.moshi.FromJson
import com.squareup.moshi.ToJson

class ActivityAdapter {

    @Suppress("unused")
    @FromJson
    fun fromJson(restActivity: RestActivity): Activity {
        return restActivity.toActivity()
    }

    @Suppress("unused")
    @ToJson
    fun toJson(activity: Activity): RestActivity {
        return activity.toRestActivity()
    }
}

package com.felixbrucker.jules.network.api.model

import com.squareup.moshi.Json
import com.squareup.moshi.JsonClass

@JsonClass(generateAdapter = true)
data class WorkflowInput(
    @Json(name = "name") val name: String,
    @Json(name = "description") val description: String? = null,
    @Json(name = "required") val required: Boolean = false,
    @Json(name = "default") val default: String? = null,
    @Json(name = "type") val type: String? = null,
    @Json(name = "options") val options: List<String>? = null
)

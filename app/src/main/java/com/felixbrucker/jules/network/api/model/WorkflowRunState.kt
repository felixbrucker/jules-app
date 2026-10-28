package com.felixbrucker.jules.network.api.model

enum class WorkflowRunState {
    QUEUED,
    IN_PROGRESS,
    SUCCESS,
    FAILURE;

    companion object {
        fun from(status: String?, conclusion: String?): WorkflowRunState {
            val s = status?.lowercase()
            val c = conclusion?.lowercase()

            if (s == "completed") {
                return when (c) {
                    "success", "neutral", "skipped" -> SUCCESS
                    else -> FAILURE
                }
            }

            if (s in listOf("failure", "startup_failure")) {
                return FAILURE
            }

            if (s == "in_progress") {
                return IN_PROGRESS
            }

            if (s in listOf("queued", "pending", "requested", "waiting", "expected")) {
                return QUEUED
            }

            return when (c) {
                "success", "neutral", "skipped" -> SUCCESS
                "failure", "cancelled", "timed_out", "action_required", "stale" -> FAILURE
                else -> QUEUED
            }
        }
    }
}

package com.felixbrucker.jules.network.api.util

import com.felixbrucker.jules.network.api.model.FileChangeType
import com.felixbrucker.jules.network.api.model.ParsedFile

object UnidiffParser {

    // Single-pass streaming line traversal avoids regex block splitting and line list allocations.
    fun parse(patch: String?): List<ParsedFile> {
        if (patch.isNullOrBlank()) return emptyList()

        val parsedFiles = mutableListOf<ParsedFile>()
        var fromPath: String? = null
        var toPath: String? = null
        var additions = 0
        var deletions = 0
        var inHunk = false
        var hasBlock = false

        fun flushBlock() {
            if (!hasBlock) return
            val finalPath = toPath ?: fromPath
            if (finalPath != null) {
                val changeType = when {
                    fromPath == null -> FileChangeType.CREATED
                    toPath == null -> FileChangeType.DELETED
                    else -> FileChangeType.MODIFIED
                }
                parsedFiles.add(
                    ParsedFile(
                        path = finalPath,
                        changeType = changeType,
                        additions = additions,
                        deletions = deletions
                    )
                )
            }
            fromPath = null
            toPath = null
            additions = 0
            deletions = 0
            inHunk = false
            hasBlock = false
        }

        for (line in patch.lineSequence()) {
            if (line.startsWith("diff --git ")) {
                flushBlock()
                hasBlock = true
            } else if (hasBlock) {
                when {
                    line.startsWith("--- ") -> {
                        val path = line.substring(4).trim()
                        fromPath = cleanPath(path)
                    }
                    line.startsWith("+++ ") -> {
                        val path = line.substring(4).trim()
                        toPath = cleanPath(path)
                    }
                    line.startsWith("@@ ") -> {
                        inHunk = true
                    }
                    inHunk -> {
                        if (line.startsWith("+") && !line.startsWith("+++")) {
                            additions++
                        } else if (line.startsWith("-") && !line.startsWith("---")) {
                            deletions++
                        }
                    }
                }
            }
        }
        flushBlock()

        return parsedFiles
    }

    private fun cleanPath(path: String): String? {
        if (path == "/dev/null") return null
        var p = path
        if (p.startsWith("a/")) p = p.substring(2)
        if (p.startsWith("b/")) p = p.substring(2)
        return p
    }
}

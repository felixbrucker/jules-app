package com.felixbrucker.jules.network.api.util

import com.felixbrucker.jules.network.api.model.FileChangeType
import com.felixbrucker.jules.network.api.model.ParsedFile

object UnidiffParser {

    fun parse(patch: String?): List<ParsedFile> {
        if (patch.isNullOrBlank()) return emptyList()

        val rawBlocks = patch.split(Regex("^diff --git ", RegexOption.MULTILINE))
        val parsedFiles = mutableListOf<ParsedFile>()

        for (block in rawBlocks) {
            if (block.isBlank()) continue

            val lines = block.lines()
            var fromPath: String? = null
            var toPath: String? = null
            var additions = 0
            var deletions = 0
            var inHunk = false

            for (line in lines) {
                when {
                    line.startsWith("--- ") -> {
                        val path = line.removePrefix("--- ").trim()
                        fromPath = if (path == "/dev/null") null else path.removePrefix("a/").removePrefix("b/")
                    }
                    line.startsWith("+++ ") -> {
                        val path = line.removePrefix("+++ ").trim()
                        toPath = if (path == "/dev/null") null else path.removePrefix("a/").removePrefix("b/")
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

            val finalPath = toPath ?: fromPath ?: continue
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

        return parsedFiles
    }
}

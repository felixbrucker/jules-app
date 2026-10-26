package com.felixbrucker.jules.network.api.util

import androidx.collection.LruCache
import com.felixbrucker.jules.network.api.model.FileChangeType
import com.felixbrucker.jules.network.api.model.ParsedFile

object UnidiffParser {

    private val cache = LruCache<String, List<ParsedFile>>(20)
    private val diffGitRegex = Regex("^diff --git ", RegexOption.MULTILINE)

    fun parse(patch: String?): List<ParsedFile> {
        if (patch.isNullOrBlank()) return emptyList()

        val cached = cache.get(patch)
        if (cached != null) return cached

        val parsed = parseInternal(patch)
        cache.put(patch, parsed)
        return parsed
    }

    private fun parseInternal(patch: String): List<ParsedFile> {
        val rawBlocks = patch.split(diffGitRegex)
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

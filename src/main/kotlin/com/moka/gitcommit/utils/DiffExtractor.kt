package com.moka.gitcommit.utils

import com.intellij.openapi.vcs.changes.Change
import com.moka.gitcommit.settings.AppSettings

object DiffExtractor {

    private const val MAX_FILE_BYTES = 500_000L

    fun extract(changes: Collection<Change>): String {
        val settings = AppSettings.getInstance()
        val ignorePatterns = parseIgnorePatterns(settings.state.ignorePatterns)

        val sb = StringBuilder()

        for (change in changes) {
            val file = change.virtualFile ?: continue
            if (file.fileType.isBinary) continue
            if (file.length > MAX_FILE_BYTES) continue
            if (shouldIgnore(file.path, ignorePatterns)) continue

            val path = file.path
            val after = change.afterRevision?.content ?: continue
            val before = change.beforeRevision?.content ?: ""

            sb.append("--- a/").appendLine(path)
            sb.append("+++ b/").appendLine(path)

            if (before.isBlank()) {
                after.lines().forEach { sb.append("+").appendLine(it) }
            } else {
                after.lines().forEach { sb.appendLine(it) }
            }
            sb.appendLine()
        }

        val result = sb.toString()
        val limit = settings.state.maxDiffLength
        return if (result.length > limit) {
            result.take(limit) + "\n... (diff truncated at $limit chars)"
        } else {
            result
        }
    }

    internal fun parseIgnorePatterns(raw: String): List<String> =
        raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith('#') }

    internal fun shouldIgnore(filePath: String, patterns: List<String>): Boolean {
        if (patterns.isEmpty()) return false
        val normalized = filePath.replace('\\', '/')
        val fileName = normalized.substringAfterLast('/')
        return patterns.any { pattern -> matchesGlob(normalized, fileName, pattern) }
    }

    private fun matchesGlob(normalized: String, fileName: String, pattern: String): Boolean {
        val regex = try {
            globToRegex(pattern)
        } catch (_: Exception) {
            return false
        }
        return if ('/' !in pattern) {
            regex.matches(fileName)
        } else {
            val segments = normalized.split('/')
            segments.indices.any { start ->
                regex.matches(segments.drop(start).joinToString("/"))
            }
        }
    }

    private fun globToRegex(glob: String): Regex {
        val sb = StringBuilder()
        var i = 0
        while (i < glob.length) {
            when {
                glob[i] == '*' && i + 1 < glob.length && glob[i + 1] == '*' -> {
                    sb.append(".*")
                    i += 2
                    // Consume the optional '/' separator after '**'
                    if (i < glob.length && glob[i] == '/') i++
                }
                glob[i] == '*' -> { sb.append("[^/]*"); i++ }
                glob[i] == '?' -> { sb.append("[^/]"); i++ }
                else -> { sb.append(Regex.escape(glob[i].toString())); i++ }
            }
        }
        return Regex(sb.toString())
    }
}

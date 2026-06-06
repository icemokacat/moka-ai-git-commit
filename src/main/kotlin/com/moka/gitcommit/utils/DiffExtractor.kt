package com.moka.gitcommit.utils

import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change
import com.moka.gitcommit.settings.AppSettings

object DiffExtractor {

    private const val MAX_FILE_BYTES = 500_000L
    private const val CONTEXT_LINES = 3

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

    fun extractFromWorkflow(
        changes: Collection<Change>,
        unversionedFiles: Collection<FilePath>,
        projectBasePath: String?
    ): String {
        val settings = AppSettings.getInstance()
        val ignorePatterns = parseIgnorePatterns(settings.state.ignorePatterns)
        // Strip everything up to and including the project's parent directory,
        // so paths start from the project folder name (e.g. "my-project/src/Foo.kt").
        val baseParent = projectBasePath?.replace('\\', '/')?.substringBeforeLast('/')
        val sb = StringBuilder()

        for (change in changes) {
            appendChangeDiff(change, ignorePatterns, sb, baseParent)
        }
        for (filePath in unversionedFiles) {
            appendUnversionedDiff(filePath, ignorePatterns, sb, baseParent)
        }

        return truncate(sb.toString(), settings.state.maxDiffLength)
    }

    private fun relativize(absolutePath: String, baseParent: String?): String {
        if (baseParent == null) return absolutePath
        val normalized = absolutePath.replace('\\', '/')
        return if (normalized.startsWith("$baseParent/")) normalized.removePrefix("$baseParent/")
        else normalized
    }

    private fun appendChangeDiff(change: Change, ignorePatterns: List<String>, sb: StringBuilder, baseParent: String?) {
        when (change.type) {
            Change.Type.NEW -> {
                val file = change.virtualFile ?: return
                if (file.fileType.isBinary || file.length > MAX_FILE_BYTES) return
                if (shouldIgnore(file.path, ignorePatterns)) return
                val after = change.afterRevision?.content ?: return
                val relPath = relativize(file.path, baseParent)
                sb.append("--- /dev/null\n")
                sb.append("+++ b/$relPath\n")
                after.lines().dropLastWhile { it.isEmpty() }.forEach { sb.append("+$it\n") }
                sb.append("\n")
            }
            Change.Type.DELETED -> {
                val beforeRevision = change.beforeRevision ?: return
                val path = beforeRevision.file.path
                if (shouldIgnore(path, ignorePatterns)) return
                val before = beforeRevision.content ?: return
                if (before.length > MAX_FILE_BYTES) return
                val relPath = relativize(path, baseParent)
                sb.append("--- a/$relPath\n")
                sb.append("+++ /dev/null\n")
                before.lines().dropLastWhile { it.isEmpty() }.forEach { sb.append("-$it\n") }
                sb.append("\n")
            }
            Change.Type.MODIFICATION -> {
                val file = change.virtualFile ?: return
                if (file.fileType.isBinary || file.length > MAX_FILE_BYTES) return
                if (shouldIgnore(file.path, ignorePatterns)) return
                val before = change.beforeRevision?.content ?: return
                val after = change.afterRevision?.content ?: return
                val relPath = relativize(file.path, baseParent)
                sb.append("--- a/$relPath\n")
                sb.append("+++ b/$relPath\n")
                appendLineDiff(
                    before.lines().dropLastWhile { it.isEmpty() },
                    after.lines().dropLastWhile { it.isEmpty() },
                    sb
                )
                sb.append("\n")
            }
            Change.Type.MOVED -> {
                val beforePath = change.beforeRevision?.file?.path ?: return
                val afterPath = change.afterRevision?.file?.path ?: return
                if (shouldIgnore(afterPath, ignorePatterns)) return
                val file = change.virtualFile
                if (file != null && (file.fileType.isBinary || file.length > MAX_FILE_BYTES)) return
                val before = change.beforeRevision?.content ?: ""
                val after = change.afterRevision?.content ?: ""
                val relBeforePath = relativize(beforePath, baseParent)
                val relAfterPath = relativize(afterPath, baseParent)
                sb.append("--- a/$relBeforePath\n")
                sb.append("+++ b/$relAfterPath\n")
                if (before == after) {
                    sb.append("(renamed only, no content changes)\n")
                } else {
                    appendLineDiff(
                        before.lines().dropLastWhile { it.isEmpty() },
                        after.lines().dropLastWhile { it.isEmpty() },
                        sb
                    )
                }
                sb.append("\n")
            }
            else -> return
        }
    }

    private fun appendUnversionedDiff(filePath: FilePath, ignorePatterns: List<String>, sb: StringBuilder, baseParent: String?) {
        val virtualFile = filePath.virtualFile ?: return
        if (virtualFile.fileType.isBinary || virtualFile.length > MAX_FILE_BYTES) return
        if (shouldIgnore(virtualFile.path, ignorePatterns)) return
        val content = runCatching {
            String(virtualFile.contentsToByteArray(), Charsets.UTF_8)
        }.getOrNull() ?: return
        val relPath = relativize(virtualFile.path, baseParent)
        sb.append("--- /dev/null\n")
        sb.append("+++ b/$relPath\n")
        content.lines().dropLastWhile { it.isEmpty() }.forEach { sb.append("+$it\n") }
        sb.append("\n")
    }

    private fun appendLineDiff(beforeLines: List<String>, afterLines: List<String>, sb: StringBuilder) {
        val fragments = runCatching {
            ComparisonManager.getInstance().compareLines(
                beforeLines.joinToString("\n"),
                afterLines.joinToString("\n"),
                ComparisonPolicy.DEFAULT,
                EmptyProgressIndicator()
            )
        }.getOrNull()

        if (fragments == null) {
            beforeLines.forEach { sb.append("-$it\n") }
            afterLines.forEach { sb.append("+$it\n") }
            return
        }

        if (fragments.isEmpty()) return

        var lastEnd1 = 0
        var lastEnd2 = 0

        for (fragment in fragments) {
            val ctxStart1 = maxOf(lastEnd1, fragment.startLine1 - CONTEXT_LINES)
            val ctxStart2 = maxOf(lastEnd2, fragment.startLine2 - CONTEXT_LINES)

            beforeLines.subList(ctxStart1, fragment.startLine1).forEach { sb.append(" $it\n") }

            val hunkBefore = fragment.endLine1 - ctxStart1
            val hunkAfter = fragment.endLine2 - ctxStart2
            sb.append("@@ -${ctxStart1 + 1},$hunkBefore +${ctxStart2 + 1},$hunkAfter @@\n")

            beforeLines.subList(fragment.startLine1, fragment.endLine1).forEach { sb.append("-$it\n") }
            afterLines.subList(fragment.startLine2, fragment.endLine2).forEach { sb.append("+$it\n") }

            lastEnd1 = fragment.endLine1
            lastEnd2 = fragment.endLine2
        }

        val trailingEnd = minOf(beforeLines.size, lastEnd1 + CONTEXT_LINES)
        beforeLines.subList(lastEnd1, trailingEnd).forEach { sb.append(" $it\n") }
    }

    private fun truncate(result: String, limit: Int): String =
        if (result.length > limit) result.take(limit) + "\n... (diff truncated at $limit chars)"
        else result

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

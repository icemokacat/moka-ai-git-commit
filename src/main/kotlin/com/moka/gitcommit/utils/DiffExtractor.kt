package com.moka.gitcommit.utils

import com.intellij.diff.comparison.ComparisonManager
import com.intellij.diff.comparison.ComparisonPolicy
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.Change

object DiffExtractor {

    private const val MAX_FILE_BYTES = 500_000L
    private const val CONTEXT_LINES = 3

    internal data class CompiledPattern(val regex: Regex, val hasSlash: Boolean)

    fun extractFromWorkflow(
        changes: Collection<Change>,
        unversionedFiles: Collection<FilePath>,
        projectBasePath: String?,
        ignorePatterns: String,
        maxDiffLength: Int
    ): String {
        val compiled = parseIgnorePatterns(ignorePatterns)
        val baseParent = projectBasePath?.replace('\\', '/')?.substringBeforeLast('/')
        val sb = StringBuilder()
        for (change in changes) appendChangeDiff(change, compiled, sb, baseParent)
        for (filePath in unversionedFiles) appendUnversionedDiff(filePath, compiled, sb, baseParent)
        return truncate(sb.toString(), maxDiffLength)
    }

    internal fun relativize(absolutePath: String, baseParent: String?): String {
        if (baseParent == null) return absolutePath
        val normalized = absolutePath.replace('\\', '/')
        return if (normalized.startsWith("$baseParent/")) normalized.removePrefix("$baseParent/")
        else normalized
    }

    private fun appendChangeDiff(
        change: Change,
        patterns: List<CompiledPattern>,
        sb: StringBuilder,
        baseParent: String?
    ) {
        when (change.type) {
            Change.Type.NEW -> {
                val file = change.virtualFile ?: return
                if (file.fileType.isBinary || file.length > MAX_FILE_BYTES) return
                if (shouldIgnore(file.path, patterns)) return
                val after = change.afterRevision?.content ?: return
                val relPath = relativize(file.path, baseParent)
                sb.append("--- /dev/null\n+++ b/$relPath\n")
                after.trimEnd('\n', '\r').lines().forEach { sb.append("+$it\n") }
                sb.append("\n")
            }
            Change.Type.DELETED -> {
                val beforeRevision = change.beforeRevision ?: return
                val path = beforeRevision.file.path
                if (shouldIgnore(path, patterns)) return
                val before = beforeRevision.content ?: return
                if (before.length > MAX_FILE_BYTES) return
                val relPath = relativize(path, baseParent)
                sb.append("--- a/$relPath\n+++ /dev/null\n")
                before.trimEnd('\n', '\r').lines().forEach { sb.append("-$it\n") }
                sb.append("\n")
            }
            Change.Type.MODIFICATION -> {
                val file = change.virtualFile ?: return
                if (file.fileType.isBinary || file.length > MAX_FILE_BYTES) return
                if (shouldIgnore(file.path, patterns)) return
                val before = change.beforeRevision?.content ?: return
                val after = change.afterRevision?.content ?: return
                val relPath = relativize(file.path, baseParent)
                sb.append("--- a/$relPath\n+++ b/$relPath\n")
                appendLineDiff(before, after, sb)
                sb.append("\n")
            }
            Change.Type.MOVED -> {
                val beforePath = change.beforeRevision?.file?.path ?: return
                val afterPath = change.afterRevision?.file?.path ?: return
                if (shouldIgnore(afterPath, patterns)) return
                val file = change.virtualFile
                if (file != null && (file.fileType.isBinary || file.length > MAX_FILE_BYTES)) return
                val before = change.beforeRevision?.content ?: ""
                val after = change.afterRevision?.content ?: ""
                val relBeforePath = relativize(beforePath, baseParent)
                val relAfterPath = relativize(afterPath, baseParent)
                sb.append("--- a/$relBeforePath\n+++ b/$relAfterPath\n")
                if (before == after) {
                    sb.append("(renamed only, no content changes)\n")
                } else {
                    appendLineDiff(before, after, sb)
                }
                sb.append("\n")
            }
            else -> return
        }
    }

    private fun appendUnversionedDiff(
        filePath: FilePath,
        patterns: List<CompiledPattern>,
        sb: StringBuilder,
        baseParent: String?
    ) {
        val virtualFile = filePath.virtualFile ?: return
        if (virtualFile.fileType.isBinary || virtualFile.length > MAX_FILE_BYTES) return
        if (shouldIgnore(virtualFile.path, patterns)) return
        val content = runCatching {
            String(virtualFile.contentsToByteArray(), Charsets.UTF_8)
        }.getOrNull() ?: return
        val relPath = relativize(virtualFile.path, baseParent)
        sb.append("--- /dev/null\n+++ b/$relPath\n")
        content.trimEnd('\n', '\r').lines().forEach { sb.append("+$it\n") }
        sb.append("\n")
    }

    // Bug fix: take raw strings, normalize consistently so ComparisonManager line indices
    // match our line list. Previously, dropLastWhile on the split list could produce an
    // empty list while ComparisonManager still saw 1 line in the joined string, causing
    // EmptyList.subList(0, 1) to crash.
    private fun appendLineDiff(before: String, after: String, sb: StringBuilder) {
        val beforeNorm = before.trimEnd('\n', '\r')
        val afterNorm = after.trimEnd('\n', '\r')
        val beforeLines = if (beforeNorm.isEmpty()) emptyList() else beforeNorm.lines()
        val afterLines = if (afterNorm.isEmpty()) emptyList() else afterNorm.lines()

        if (beforeLines.isEmpty()) {
            afterLines.forEach { sb.append("+$it\n") }
            return
        }
        if (afterLines.isEmpty()) {
            beforeLines.forEach { sb.append("-$it\n") }
            return
        }

        val fragments = runCatching {
            ComparisonManager.getInstance().compareLines(
                beforeNorm, afterNorm,
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
            val hunkBefore = fragment.endLine1 - ctxStart1
            val hunkAfter = fragment.endLine2 - ctxStart2

            sb.append("@@ -${ctxStart1 + 1},$hunkBefore +${ctxStart2 + 1},$hunkAfter @@\n")
            beforeLines.subList(ctxStart1, fragment.startLine1).forEach { sb.append(" $it\n") }
            beforeLines.subList(fragment.startLine1, fragment.endLine1).forEach { sb.append("-$it\n") }
            afterLines.subList(fragment.startLine2, fragment.endLine2).forEach { sb.append("+$it\n") }

            lastEnd1 = fragment.endLine1
            lastEnd2 = fragment.endLine2
        }

        val trailingEnd = minOf(beforeLines.size, lastEnd1 + CONTEXT_LINES)
        beforeLines.subList(lastEnd1, trailingEnd).forEach { sb.append(" $it\n") }
    }

    internal fun truncate(result: String, limit: Int): String =
        if (result.length > limit) result.take(limit) + "\n... (diff truncated at $limit chars)"
        else result

    // Returns pre-compiled patterns so globToRegex is called once per pattern, not per file.
    internal fun parseIgnorePatterns(raw: String): List<CompiledPattern> =
        raw.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith('#') }
            .mapNotNull { pattern ->
                runCatching {
                    CompiledPattern(regex = globToRegex(pattern), hasSlash = '/' in pattern)
                }.getOrNull()
            }

    internal fun shouldIgnore(filePath: String, patterns: List<CompiledPattern>): Boolean {
        if (patterns.isEmpty()) return false
        val normalized = filePath.replace('\\', '/')
        val fileName = normalized.substringAfterLast('/')
        return patterns.any { cp -> matchesPattern(normalized, fileName, cp) }
    }

    private fun matchesPattern(normalized: String, fileName: String, cp: CompiledPattern): Boolean =
        if (!cp.hasSlash) {
            cp.regex.matches(fileName)
        } else {
            val segments = normalized.split('/')
            segments.indices.any { start ->
                cp.regex.matches(segments.drop(start).joinToString("/"))
            }
        }

    internal fun globToRegex(glob: String): Regex {
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

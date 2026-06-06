package com.moka.gitcommit.utils

import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiffExtractorGlobTest {

    private fun ignored(filePath: String, pattern: String): Boolean {
        val patterns = DiffExtractor.parseIgnorePatterns(pattern)
        return DiffExtractor.shouldIgnore(filePath, patterns)
    }

    // ── build/** 패턴 ─────────────────────────────────────────────────

    @Test fun `build 디렉터리 하위 파일은 무시`() =
        assertTrue(ignored("project/build/classes/Main.class", "build/**"))

    @Test fun `src 파일은 build 패턴에 무시되지 않음`() =
        assertFalse(ignored("src/main/Hello.kt", "build/**"))

    // ── 정확한 파일명 패턴 ──────────────────────────────────────────────

    @Test fun `package-lock json 은 정확한 이름으로 무시`() =
        assertTrue(ignored("project/package-lock.json", "package-lock.json"))

    @Test fun `yarn lock 무시`() =
        assertTrue(ignored("yarn.lock", "yarn.lock"))

    @Test fun `다른 이름의 json 파일은 무시되지 않음`() =
        assertFalse(ignored("src/config.json", "package-lock.json"))

    // ── 와일드카드 확장자 패턴 ────────────────────────────────────────────

    @Test fun `min js 파일 무시`() =
        assertTrue(ignored("src/app.min.js", "*.min.js"))

    @Test fun `min css 파일 무시`() =
        assertTrue(ignored("dist/style.min.css", "*.min.css"))

    @Test fun `일반 js 파일은 min 패턴에 무시되지 않음`() =
        assertFalse(ignored("src/app.js", "*.min.js"))

    // ── ** 재귀 패턴 ──────────────────────────────────────────────────

    @Test fun `__pycache__ 디렉터리 재귀 무시`() =
        assertTrue(ignored("project/src/__pycache__/module.cpython-311.pyc", "**/__pycache__/**"))

    @Test fun `node_modules 재귀 무시`() =
        assertTrue(ignored("project/node_modules/lodash/index.js", "node_modules/**"))

    @Test fun `중첩 경로의 node_modules 무시`() =
        assertTrue(ignored("apps/web/node_modules/react/index.js", "node_modules/**"))

    // ── 주석 및 빈 줄 처리 ────────────────────────────────────────────

    @Test fun `주석 줄은 패턴으로 처리되지 않음`() =
        assertFalse(ignored("build/output.jar", "# build/**"))

    @Test fun `빈 줄은 무시됨`() =
        assertFalse(ignored("build/output.jar", "\n\n   \n"))

    @Test fun `복수 패턴 중 하나가 일치하면 무시`() {
        val patterns = "src/**\nbuild/**\ndist/**"
        assertTrue(ignored("build/classes/Main.class", patterns))
        assertTrue(ignored("dist/app.js", patterns))
        assertFalse(ignored("docs/readme.md", patterns))
    }

    // ── 기본 ignorePatterns 통합 검증 ─────────────────────────────────

    @Test fun `기본 패턴으로 잠금 파일 무시`() {
        val defaultPatterns = com.moka.gitcommit.settings.AppSettings.DEFAULT_IGNORE_PATTERNS
        val patterns = DiffExtractor.parseIgnorePatterns(defaultPatterns)
        assertTrue(DiffExtractor.shouldIgnore("yarn.lock", patterns))
        assertTrue(DiffExtractor.shouldIgnore("package-lock.json", patterns))
        assertTrue(DiffExtractor.shouldIgnore("go.sum", patterns))
    }

    @Test fun `기본 패턴으로 빌드 결과물 무시`() {
        val patterns = DiffExtractor.parseIgnorePatterns(
            com.moka.gitcommit.settings.AppSettings.DEFAULT_IGNORE_PATTERNS
        )
        assertTrue(DiffExtractor.shouldIgnore("build/libs/plugin.jar", patterns))
        assertTrue(DiffExtractor.shouldIgnore("dist/main.js", patterns))
    }

    @Test fun `기본 패턴으로 소스 파일은 무시되지 않음`() {
        val patterns = DiffExtractor.parseIgnorePatterns(
            com.moka.gitcommit.settings.AppSettings.DEFAULT_IGNORE_PATTERNS
        )
        assertFalse(DiffExtractor.shouldIgnore("src/main/Hello.kt", patterns))
        assertFalse(DiffExtractor.shouldIgnore("src/test/HelloTest.kt", patterns))
    }
}

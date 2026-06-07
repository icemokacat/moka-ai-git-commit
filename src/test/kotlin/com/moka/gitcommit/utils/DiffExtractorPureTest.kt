package com.moka.gitcommit.utils

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class DiffExtractorPureTest {

    // ── relativize ─────────────────────────────────────────────────────────

    @Test fun `relativize - null baseParent는 원본 경로 반환`() {
        assertEquals(
            "/home/user/project/src/Main.kt",
            DiffExtractor.relativize("/home/user/project/src/Main.kt", null)
        )
    }

    @Test fun `relativize - basePath의 부모 기준으로 상대 경로 변환`() {
        // project.basePath = "/home/user/project" → baseParent = "/home/user"
        assertEquals(
            "project/src/Main.kt",
            DiffExtractor.relativize("/home/user/project/src/Main.kt", "/home/user")
        )
    }

    @Test fun `relativize - baseParent로 시작하지 않으면 원본 반환`() {
        assertEquals(
            "/other/path/file.kt",
            DiffExtractor.relativize("/other/path/file.kt", "/home/user")
        )
    }

    @Test fun `relativize - Windows 역슬래시를 슬래시로 정규화`() {
        assertEquals(
            "project/src/Main.kt",
            DiffExtractor.relativize("C:/Users/dev/project/src/Main.kt", "C:/Users/dev")
        )
    }

    @Test fun `relativize - Windows 역슬래시 경로도 처리`() {
        assertEquals(
            "project/src/Main.kt",
            DiffExtractor.relativize("C:\\Users\\dev\\project\\src\\Main.kt", "C:/Users/dev")
        )
    }

    @Test fun `relativize - 경계 슬래시 정확히 일치해야 분리됨`() {
        // "/home/user2" 는 "/home/user"로 시작하지만 슬래시 경계가 다름
        assertEquals(
            "/home/user2/file.kt",
            DiffExtractor.relativize("/home/user2/file.kt", "/home/user")
        )
    }

    // ── truncate ───────────────────────────────────────────────────────────

    @Test fun `truncate - 빈 문자열은 그대로 반환`() {
        assertEquals("", DiffExtractor.truncate("", 100))
    }

    @Test fun `truncate - 제한보다 짧으면 그대로 반환`() {
        val text = "hello world"
        assertEquals(text, DiffExtractor.truncate(text, 100))
    }

    @Test fun `truncate - 정확히 제한 길이이면 그대로 반환`() {
        val text = "a".repeat(50)
        assertEquals(text, DiffExtractor.truncate(text, 50))
    }

    @Test fun `truncate - 제한 초과 시 자르고 메시지 추가`() {
        val text = "a".repeat(200)
        val result = DiffExtractor.truncate(text, 100)
        assertTrue(result.startsWith("a".repeat(100)))
        assertTrue(result.contains("truncated at 100 chars"))
    }

    @Test fun `truncate - 잘린 결과는 제한 길이만큼 정확히 포함`() {
        val text = "abcde".repeat(10)   // 50 chars
        val result = DiffExtractor.truncate(text, 20)
        assertTrue(result.startsWith("abcdeabcdeabcdeabcde"))
        assertFalse(result.startsWith("abcdeabcdeabcdeabcdea"))
    }

    // ── globToRegex ────────────────────────────────────────────────────────

    @Test fun `globToRegex - 리터럴 문자는 정확히 일치`() {
        val regex = DiffExtractor.globToRegex("README.md")
        assertTrue(regex.matches("README.md"))
        assertFalse(regex.matches("README-md"))
        assertFalse(regex.matches("XREADME.md"))
    }

    @Test fun `globToRegex - 특수 정규식 문자는 이스케이프됨`() {
        val regex = DiffExtractor.globToRegex("file.min.js")
        assertTrue(regex.matches("file.min.js"))
        assertFalse(regex.matches("fileXminXjs"))
    }

    @Test fun `globToRegex - 단일 스타는 슬래시 제외 임의 문자열`() {
        val regex = DiffExtractor.globToRegex("*.min.js")
        assertTrue(regex.matches("app.min.js"))
        assertTrue(regex.matches(".min.js"))
        assertFalse(regex.matches("dir/app.min.js"))
    }

    @Test fun `globToRegex - 이중 스타는 슬래시 포함 임의 문자열`() {
        val regex = DiffExtractor.globToRegex("build/**")
        assertTrue(regex.matches("build/classes/Main.class"))
        assertTrue(regex.matches("build/libs/plugin.jar"))
        assertTrue(regex.matches("build/"))
    }

    @Test fun `globToRegex - 물음표는 슬래시 제외 단일 문자`() {
        val regex = DiffExtractor.globToRegex("file?.kt")
        assertTrue(regex.matches("fileA.kt"))
        assertTrue(regex.matches("file1.kt"))
        assertFalse(regex.matches("file.kt"))
        assertFalse(regex.matches("file/a.kt"))
    }

    @Test fun `globToRegex - 이중 스타 후 슬래시는 슬래시까지 소비`() {
        val regex = DiffExtractor.globToRegex("**/__pycache__/**")
        assertTrue(regex.matches("src/__pycache__/cache.pyc"))
        assertTrue(regex.matches("__pycache__/cache.pyc"))
    }

    // ── parseIgnorePatterns ────────────────────────────────────────────────

    @Test fun `parseIgnorePatterns - 빈 문자열은 빈 목록 반환`() {
        assertTrue(DiffExtractor.parseIgnorePatterns("").isEmpty())
    }

    @Test fun `parseIgnorePatterns - 주석 줄만 있으면 빈 목록`() {
        val raw = "# build 결과물\n# 잠금 파일"
        assertTrue(DiffExtractor.parseIgnorePatterns(raw).isEmpty())
    }

    @Test fun `parseIgnorePatterns - 공백 줄은 제거됨`() {
        val raw = "  \n\n   \n"
        assertTrue(DiffExtractor.parseIgnorePatterns(raw).isEmpty())
    }

    @Test fun `parseIgnorePatterns - 유효한 패턴이 컴파일됨`() {
        val patterns = DiffExtractor.parseIgnorePatterns("build/**\n*.log\nnode_modules/**")
        assertEquals(3, patterns.size)
    }

    @Test fun `parseIgnorePatterns - 슬래시 포함 패턴은 hasSlash=true`() {
        val patterns = DiffExtractor.parseIgnorePatterns("build/**")
        assertEquals(1, patterns.size)
        assertTrue(patterns[0].hasSlash)
    }

    @Test fun `parseIgnorePatterns - 슬래시 없는 패턴은 hasSlash=false`() {
        val patterns = DiffExtractor.parseIgnorePatterns("*.log")
        assertEquals(1, patterns.size)
        assertFalse(patterns[0].hasSlash)
    }

    @Test fun `parseIgnorePatterns - 혼합 패턴에서 주석과 공백 제거`() {
        val raw = """
            # 빌드 결과물
            build/**

            # 잠금 파일
            yarn.lock
        """.trimIndent()
        val patterns = DiffExtractor.parseIgnorePatterns(raw)
        assertEquals(2, patterns.size)
    }

    // ── shouldIgnore (CompiledPattern 기반) ───────────────────────────────

    @Test fun `shouldIgnore - 빈 패턴 목록은 무시 안 함`() {
        assertFalse(DiffExtractor.shouldIgnore("build/Main.class", emptyList()))
    }

    @Test fun `shouldIgnore - 슬래시 없는 패턴은 파일명만 비교`() {
        val patterns = DiffExtractor.parseIgnorePatterns("*.log")
        assertTrue(DiffExtractor.shouldIgnore("deep/path/app.log", patterns))
        assertFalse(DiffExtractor.shouldIgnore("deep/path/app.kt", patterns))
    }

    @Test fun `shouldIgnore - 슬래시 패턴은 경로 세그먼트와 비교`() {
        val patterns = DiffExtractor.parseIgnorePatterns("node_modules/**")
        assertTrue(DiffExtractor.shouldIgnore("apps/web/node_modules/react/index.js", patterns))
        assertFalse(DiffExtractor.shouldIgnore("apps/web/src/index.js", patterns))
    }

    @Test fun `shouldIgnore - Windows 역슬래시 경로도 처리`() {
        val patterns = DiffExtractor.parseIgnorePatterns("*.log")
        assertTrue(DiffExtractor.shouldIgnore("C:\\Logs\\app.log", patterns))
    }
}

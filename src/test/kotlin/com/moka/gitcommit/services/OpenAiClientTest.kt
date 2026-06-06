package com.moka.gitcommit.services

import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * 실제 OpenAI API에 요청을 보내는 통합 테스트.
 * OPENAI_API_KEY가 없으면 전체 SKIP.
 *
 * 권장 방법: 프로젝트 루트에 .env.test.local 파일 생성 (gitignore됨)
 *   OPENAI_API_KEY=sk-...
 *   OPENAI_API_URL=https://api.openai.com/v1/chat/completions   # 선택 사항
 *   OPENAI_MODEL=gpt-4o-mini                                    # 선택 사항
 *
 * 대안 (PowerShell 직접 설정):
 *   $env:OPENAI_API_KEY="sk-..."; .\gradlew test
 */
class OpenAiClientTest {

    private val apiKey  = (System.getProperty("OPENAI_API_KEY")  ?: System.getenv("OPENAI_API_KEY"))  ?: ""
    private val apiUrl  = (System.getProperty("OPENAI_API_URL")  ?: System.getenv("OPENAI_API_URL"))  ?: "https://api.openai.com/v1/chat/completions"
    private val model   = (System.getProperty("OPENAI_MODEL")    ?: System.getenv("OPENAI_MODEL"))    ?: "gpt-4o-mini"

    @BeforeEach
    fun requireApiKey() {
        assumeTrue(apiKey.isNotBlank(), "OPENAI_API_KEY 환경 변수가 없어 통합 테스트를 건너뜁니다.")
    }

    // ── 1. API 키 유효성 검증 ────────────────────────────────────────────

    @Test
    fun `testConnection - 유효한 키로 연결 성공`() {
        val result = OpenAiClient.testConnection(apiKey, apiUrl)
        println("[testConnection] result = $result")
        assertEquals("연결 성공", result)
    }

    @Test
    fun `testConnection - 잘못된 키는 401 예외`() {
        val ex = assertThrows<IOException> {
            OpenAiClient.testConnection("sk-invalid-key-for-test-000000000000", apiUrl)
        }
        println("[testConnection-invalid] exception = ${ex.message}")
        assertTrue(
            ex.message?.contains("401") == true || ex.message?.contains("인증") == true,
            "401 인증 오류를 기대했지만: ${ex.message}"
        )
    }

    @Test
    fun `testConnection - 빈 API 키는 즉시 예외`() {
        val ex = assertThrows<IllegalArgumentException> {
            OpenAiClient.testConnection("", apiUrl)
        }
        println("[testConnection-blank] exception = ${ex.message}")
        assertTrue(ex.message?.contains("비어있습니다") == true)
    }

    // ── 2. 커밋 메시지 자동 생성 ────────────────────────────────────────

    @Test
    fun `generate - 샘플 diff로 커밋 메시지 생성`() {
        val config = OpenAiConfig(apiKey = apiKey, apiUrl = apiUrl, model = model)

        val sampleDiff = """
            --- a/src/main/kotlin/example/Greeter.kt
            +++ b/src/main/kotlin/example/Greeter.kt
            @@ -0,0 +1,7 @@
            +package example
            +
            +class Greeter {
            +    fun greet(name: String): String {
            +        return "Hello, ${'$'}name!"
            +    }
            +}
        """.trimIndent()

        val promptTemplate = """
            다음 git diff를 보고 conventional commit 형식으로 한 줄 커밋 메시지를 작성해줘.
            - 형식: <type>(<scope>): <subject>
            - type: feat, fix, refactor, docs, test, chore 중 하나
            - 언어: 한국어
            - 마크다운, 코드블록 없이 커밋 메시지 한 줄만 출력
        """.trimIndent()

        val fullPrompt = "$promptTemplate\n\n$sampleDiff"

        println("[generate] ── REQUEST ──────────────────────────────────")
        println("[generate] model   : $model")
        println("[generate] apiUrl  : $apiUrl")
        println("[generate] prompt  :\n$fullPrompt")
        println("[generate] ─────────────────────────────────────────────")

        val result = OpenAiClient.generate(config, fullPrompt)

        println("[generate] ── RESPONSE ─────────────────────────────────")
        println("[generate] message : $result")
        println("[generate] ─────────────────────────────────────────────")

        assertTrue(result.isNotBlank(), "생성된 커밋 메시지가 비어있음")
    }
}

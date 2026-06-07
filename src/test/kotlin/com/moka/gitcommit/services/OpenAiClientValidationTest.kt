package com.moka.gitcommit.services

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertTrue

/**
 * API 키나 실제 네트워크 없이 실행 가능한 입력 유효성 검증 테스트.
 * 예외는 HTTP 요청 전 즉시 발생하므로 네트워크 접속 불필요.
 */
class OpenAiClientValidationTest {

    // ── testConnection 입력 유효성 ────────────────────────────────────────

    @Test fun `testConnection - 빈 API 키는 즉시 예외`() {
        val ex = assertThrows<IllegalArgumentException> {
            OpenAiClient.testConnection("", "https://api.openai.com/v1/chat/completions")
        }
        assertTrue(ex.message?.contains("비어있습니다") == true)
    }

    @Test fun `testConnection - 공백만 있는 API 키는 즉시 예외`() {
        val ex = assertThrows<IllegalArgumentException> {
            OpenAiClient.testConnection("   ", "https://api.openai.com/v1/chat/completions")
        }
        assertTrue(ex.message?.contains("비어있습니다") == true)
    }

    @Test fun `testConnection - http로 시작하지 않는 URL은 즉시 예외`() {
        val ex = assertThrows<IllegalArgumentException> {
            OpenAiClient.testConnection("sk-somekey", "ftp://api.openai.com/v1")
        }
        assertTrue(ex.message?.contains("http") == true)
    }

    @Test fun `testConnection - 빈 URL은 즉시 예외`() {
        val ex = assertThrows<IllegalArgumentException> {
            OpenAiClient.testConnection("sk-somekey", "")
        }
        assertTrue(ex.message?.contains("http") == true)
    }

    @Test fun `testConnection - https URL은 검증 통과`() {
        // 실제 연결은 하지 않음 — URL 검증만 통과하면 IOException이 발생해야 함
        // (네트워크 없는 환경에서는 ConnectException 발생)
        val ex = assertThrows<Exception> {
            OpenAiClient.testConnection("sk-somekey", "https://127.0.0.1:1/v1/chat/completions")
        }
        // IllegalArgumentException이 아니어야 함 (URL 형식은 유효)
        assertTrue(ex !is IllegalArgumentException, "URL 형식은 유효해야 함: ${ex::class.simpleName}")
    }

    @Test fun `testConnection - http URL도 검증 통과`() {
        val ex = assertThrows<Exception> {
            OpenAiClient.testConnection("sk-somekey", "http://127.0.0.1:1/v1/chat/completions")
        }
        assertTrue(ex !is IllegalArgumentException, "URL 형식은 유효해야 함: ${ex::class.simpleName}")
    }
}

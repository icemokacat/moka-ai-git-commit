package com.moka.gitcommit.services

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

object OpenAiClient {

    private val json = Json { ignoreUnknownKeys = true }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    @Serializable
    private data class ChatRequest(
        val model: String,
        val messages: List<Message>,
        val temperature: Double = 0.3
    )

    @Serializable
    private data class Message(val role: String, val content: String)

    @Serializable
    private data class ChatResponse(val choices: List<Choice>)

    @Serializable
    private data class Choice(val message: Message)

    fun generate(config: OpenAiConfig, prompt: String): String {
        val body = json.encodeToString(
            ChatRequest(
                model = config.model,
                messages = listOf(Message("user", prompt))
            )
        )

        val request = Request.Builder()
            .url(config.apiUrl)
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer ${config.apiKey}")
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(
                    when (response.code) {
                        401 -> "Invalid API key (401). Check Settings → Tools → Moka Git AI Commit."
                        404 -> "Endpoint not found (404). Check the API URL in settings."
                        429 -> "Rate limit exceeded (429). Try again in a moment."
                        else -> "OpenAI API error: HTTP ${response.code}"
                    }
                )
            }

            val responseText = response.body?.string()
                ?: throw IOException("Empty response from OpenAI")

            return json.decodeFromString<ChatResponse>(responseText)
                .choices
                .firstOrNull()
                ?.message
                ?.content
                ?.trim()
                ?: throw IOException("No content in OpenAI response")
        }
    }

    /** 백그라운드 스레드에서 호출. API 키와 URL 형식 검증 후 실제 연결 확인. */
    fun testConnection(apiKey: String, apiUrl: String): String {
        if (apiKey.isBlank()) throw IllegalArgumentException("API 키가 비어있습니다.")
        if (!apiUrl.startsWith("http://") && !apiUrl.startsWith("https://"))
            throw IllegalArgumentException("URL은 http:// 또는 https://로 시작해야 합니다.")

        val body = json.encodeToString(
            ChatRequest(model = "gpt-4o-mini", messages = listOf(Message("user", "hi")))
        )
        val request = Request.Builder()
            .url(apiUrl)
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $apiKey")
            .build()

        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) {
                throw IOException(
                    when (response.code) {
                        401 -> "인증 실패 — API 키를 확인해주세요 (401)"
                        403 -> "접근 거부 (403)"
                        404 -> "URL을 찾을 수 없습니다 (404)"
                        429 -> "요청 한도 초과, 잠시 후 다시 시도하세요 (429)"
                        else -> "HTTP ${response.code}"
                    }
                )
            }
        }
        return "연결 성공"
    }
}

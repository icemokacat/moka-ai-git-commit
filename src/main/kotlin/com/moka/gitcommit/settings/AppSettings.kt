package com.moka.gitcommit.settings

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.components.*

@State(
    name = "MokaGitAICommit",
    storages = [Storage("mokaGitAICommit.xml")]
)
@Service(Service.Level.APP)
class AppSettings : PersistentStateComponent<AppSettings.State> {

    data class State(
        var openAiModel: String = "gpt-4o-mini",
        var promptTemplate: String = DEFAULT_PROMPT_TEMPLATE,
        var maxDiffLength: Int = 12_000,
        var openAiBaseUrl: String = "https://api.openai.com/v1/chat/completions",
        var ignorePatterns: String = DEFAULT_IGNORE_PATTERNS,
        var lastPrompt: String = ""
    )

    private var _state = State()
    override fun getState(): State = _state
    override fun loadState(state: State) { _state = state }

    // API 키는 PasswordSafe (시스템 키체인) — plain XML에 저장 금지
    var apiKey: String
        get() = PasswordSafe.instance.getPassword(credentialAttributes()) ?: ""
        set(value) = PasswordSafe.instance.setPassword(credentialAttributes(), value)

    private fun credentialAttributes() = CredentialAttributes(
        generateServiceName("MokaGitAICommit", "openai-api-key")
    )

    companion object {
        fun getInstance(): AppSettings = service()

        val DEFAULT_PROMPT_TEMPLATE: String by lazy {
            AppSettings::class.java
                .getResourceAsStream("/prompts/default-prompt.txt")
                ?.reader(Charsets.UTF_8)
                ?.readText()
                ?.trimEnd()
                ?: ""
        }

        val DEFAULT_IGNORE_PATTERNS = """
# 빌드 결과물
build/**
dist/**
out/**
target/**
.gradle/**
.next/**
.nuxt/**

# 패키지 매니저 잠금 파일
package-lock.json
yarn.lock
pnpm-lock.yaml
Gemfile.lock
poetry.lock
go.sum
Pipfile.lock
Cargo.lock

# 축소(minified) 파일
*.min.js
*.min.css

# 생성된 코드
*.generated.*
*_pb2.py
*.pb.go
*.g.dart

# 컴파일 결과물
*.class
*.pyc
**/__pycache__/**

# 의존성
node_modules/**
vendor/**

# 로그 / 캐시 / 커버리지
*.log
.cache/**
coverage/**
.nyc_output/**
""".trimIndent()
    }
}

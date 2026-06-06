# Implementation Plan — Moka Git AI Commit

## 구현 순서

1. Gradle 프로젝트 셋업
2. `plugin.xml` 등록
3. `AppSettings` — 설정 영속성
4. `AppSettingsConfigurable` — Settings UI
5. `DiffExtractor` — git diff 추출
6. `OpenAiClient` — OpenAI API 호출
7. `GenerateCommitMessageAction` — 커밋창 연동

각 단계는 이전 단계에 의존하므로 순서대로 구현.

---

## 1. Gradle 프로젝트 셋업

### `gradle.properties`

```properties
pluginGroup=com.moka.gitcommit
pluginName=Moka Git AI Commit
pluginVersion=1.0.0

pluginSinceBuild=242
pluginUntilBuild=251.*

platformType=IC
platformVersion=2024.2

javaVersion=21

okhttpVersion=4.12.0
serializationVersion=1.7.3
```

### `build.gradle.kts`

```kotlin
plugins {
    id("java")
    kotlin("jvm") version "2.0.0"
    id("org.jetbrains.intellij.platform") version "2.3.0"
    kotlin("plugin.serialization") version "2.0.0"
}

group = providers.gradleProperty("pluginGroup").get()
version = providers.gradleProperty("pluginVersion").get()

repositories {
    mavenCentral()
    intellijPlatform { defaultRepositories() }
}

dependencies {
    intellijPlatform {
        create(providers.gradleProperty("platformType"), providers.gradleProperty("platformVersion"))
        bundledPlugin("Git4Idea")
        instrumentationTools()
        pluginVerifier()
    }
    implementation("com.squareup.okhttp3:okhttp:${providers.gradleProperty("okhttpVersion").get()}")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:${providers.gradleProperty("serializationVersion").get()}")
}

kotlin { jvmToolchain(21) }

intellijPlatform {
    pluginConfiguration {
        name = providers.gradleProperty("pluginName")
        version = providers.gradleProperty("pluginVersion")
        ideaVersion {
            sinceBuild = providers.gradleProperty("pluginSinceBuild")
            untilBuild = providers.gradleProperty("pluginUntilBuild")
        }
    }
    publishing { token = providers.environmentVariable("PUBLISH_TOKEN") }
}
```

---

## 2. `plugin.xml`

경로: `src/main/resources/META-INF/plugin.xml`

```xml
<idea-plugin>
  <id>com.moka.gitcommit</id>
  <name>Moka Git AI Commit</name>
  <version>1.0.0</version>
  <vendor email="icemokacat@gmail.com" url="https://github.com/icemokacat">icemokacat</vendor>

  <description><![CDATA[
    Generate git commit messages using OpenAI API.<br/>
    You write the prompt — the plugin connects your changes to the AI.
  ]]></description>

  <change-notes><![CDATA[
    <b>1.0.0</b> Initial release.
  ]]></change-notes>

  <depends>com.intellij.modules.platform</depends>
  <depends>Git4Idea</depends>

  <extensions defaultExtensionNs="com.intellij">
    <applicationService
        serviceImplementation="com.moka.gitcommit.settings.AppSettings"/>

    <applicationConfigurable
        parentId="tools"
        instance="com.moka.gitcommit.settings.AppSettingsConfigurable"
        id="com.moka.gitcommit.settings"
        displayName="Moka Git AI Commit"/>

    <notificationGroup
        id="com.moka.gitcommit"
        displayType="BALLOON"/>
  </extensions>

  <actions>
    <action
        id="com.moka.gitcommit.GenerateCommitMessage"
        class="com.moka.gitcommit.actions.GenerateCommitMessageAction"
        text="Generate AI Commit Message"
        description="Generate commit message using OpenAI API">
      <keyboard-shortcut keymap="$default" first-keystroke="ctrl alt C"/>
      <add-to-group group-id="Vcs.MessageActionGroup" anchor="first"/>
    </action>
  </actions>
</idea-plugin>
```

---

## 3. `AppSettings.kt`

경로: `src/main/kotlin/com/moka/gitcommit/settings/AppSettings.kt`

역할: 설정값 영속화. API 키는 시스템 키체인(`PasswordSafe`), 나머지는 XML.

```kotlin
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
        var openAiModel: String = "gpt-4o",
        var promptTemplate: String = "",
        var maxDiffLength: Int = 12_000,
        var openAiBaseUrl: String = "https://api.openai.com/v1"
    )

    private var _state = State()
    override fun getState(): State = _state
    override fun loadState(state: State) { _state = state }

    // API key는 PasswordSafe (시스템 키체인) — plain XML에 저장하면 안 됨
    var apiKey: String
        get() = PasswordSafe.instance.getPassword(credentialAttributes()) ?: ""
        set(value) = PasswordSafe.instance.setPassword(credentialAttributes(), value)

    private fun credentialAttributes() = CredentialAttributes(
        generateServiceName("MokaGitAICommit", "openai-api-key")
    )

    companion object {
        fun getInstance(): AppSettings = service()
    }
}
```

---

## 4. `AppSettingsConfigurable.kt`

경로: `src/main/kotlin/com/moka/gitcommit/settings/AppSettingsConfigurable.kt`

역할: Settings → Tools → Moka Git AI Commit UI 패널.
`isModified()` / `apply()` / `reset()` 세 메서드 모두 필수.

```kotlin
package com.moka.gitcommit.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.ui.ComboBox
import com.intellij.ui.components.*
import com.intellij.util.ui.FormBuilder
import javax.swing.JComponent
import javax.swing.JPanel

class AppSettingsConfigurable : Configurable {

    private val apiKeyField = JBPasswordField()
    private val modelField = ComboBox(arrayOf("gpt-4o", "gpt-4o-mini", "gpt-4-turbo", "gpt-3.5-turbo"))
    private val promptField = JBTextArea(10, 60).apply {
        lineWrap = true
        wrapStyleWord = true
    }
    private val baseUrlField = JBTextField()
    private val maxDiffField = JBTextField(6)

    override fun getDisplayName() = "Moka Git AI Commit"

    override fun createComponent(): JComponent = FormBuilder.createFormBuilder()
        .addLabeledComponent("OpenAI API Key:", apiKeyField)
        .addLabeledComponent("Model:", modelField)
        .addLabeledComponent("Base URL:", baseUrlField)
        .addLabeledComponent("Max diff length:", maxDiffField)
        .addLabeledComponent("Prompt template:", JBScrollPane(promptField))
        .addComponentFillVertically(JPanel(), 0)
        .panel

    override fun isModified(): Boolean {
        val s = AppSettings.getInstance()
        return String(apiKeyField.password) != s.apiKey
            || modelField.selectedItem != s.state.openAiModel
            || promptField.text != s.state.promptTemplate
            || baseUrlField.text != s.state.openAiBaseUrl
            || maxDiffField.text != s.state.maxDiffLength.toString()
    }

    override fun apply() {
        val s = AppSettings.getInstance()
        s.apiKey = String(apiKeyField.password)
        s.state.openAiModel = modelField.selectedItem as String
        s.state.promptTemplate = promptField.text
        s.state.openAiBaseUrl = baseUrlField.text
        s.state.maxDiffLength = maxDiffField.text.toIntOrNull() ?: 12_000
    }

    override fun reset() {
        val s = AppSettings.getInstance()
        apiKeyField.text = s.apiKey
        modelField.selectedItem = s.state.openAiModel
        promptField.text = s.state.promptTemplate
        baseUrlField.text = s.state.openAiBaseUrl
        maxDiffField.text = s.state.maxDiffLength.toString()
    }
}
```

---

## 5. `DiffExtractor.kt`

경로: `src/main/kotlin/com/moka/gitcommit/utils/DiffExtractor.kt`

역할: staged 변경사항 → 문자열 diff. 바이너리/대용량 파일 제외, 크기 제한 적용.

```kotlin
package com.moka.gitcommit.utils

import com.intellij.openapi.vcs.changes.Change
import com.moka.gitcommit.settings.AppSettings

object DiffExtractor {

    private const val MAX_FILE_BYTES = 500_000L

    fun extract(changes: Collection<Change>): String {
        val sb = StringBuilder()

        for (change in changes) {
            val file = change.virtualFile ?: continue
            if (file.fileType.isBinary) continue
            if (file.length > MAX_FILE_BYTES) continue

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
        val limit = AppSettings.getInstance().state.maxDiffLength
        return if (result.length > limit) {
            result.take(limit) + "\n... (diff truncated at $limit chars)"
        } else {
            result
        }
    }
}
```

---

## 6. `OpenAiClient.kt`

경로: `src/main/kotlin/com/moka/gitcommit/services/OpenAiClient.kt`

역할: OpenAI `/v1/chat/completions` 호출. **반드시 백그라운드 스레드에서만 호출.**

```kotlin
package com.moka.gitcommit.services

import com.moka.gitcommit.settings.AppSettings
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
        .readTimeout(60, TimeUnit.SECONDS)
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

    fun generate(settings: AppSettings, prompt: String): String {
        val body = json.encodeToString(
            ChatRequest(
                model = settings.state.openAiModel,
                messages = listOf(Message("user", prompt))
            )
        )

        val request = Request.Builder()
            .url("${settings.state.openAiBaseUrl}/chat/completions")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer ${settings.apiKey}")
            .build()

        val response = http.newCall(request).execute()

        if (!response.isSuccessful) {
            throw IOException(
                when (response.code) {
                    401 -> "Invalid API key (401). Check Settings -> Tools -> Moka Git AI Commit."
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
```

---

## 7. `GenerateCommitMessageAction.kt`

경로: `src/main/kotlin/com/moka/gitcommit/actions/GenerateCommitMessageAction.kt`

역할: 커밋창 버튼 → diff 추출 → OpenAI 호출 → 메시지 삽입. 모든 흐름의 진입점.

```kotlin
package com.moka.gitcommit.actions

import com.intellij.notification.NotificationGroupManager
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.VcsDataKeys
import com.moka.gitcommit.services.OpenAiClient
import com.moka.gitcommit.settings.AppSettings
import com.moka.gitcommit.utils.DiffExtractor

class GenerateCommitMessageAction : AnAction() {

    // 인스턴스 필드 금지 — 서비스/싱글톤 사용

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.getData(VcsDataKeys.CHANGES)?.isNotEmpty() == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val document = e.getData(VcsDataKeys.COMMIT_MESSAGE_DOCUMENT) ?: return
        val changes = e.getData(VcsDataKeys.CHANGES)?.toList() ?: return

        val settings = AppSettings.getInstance()

        if (settings.apiKey.isBlank()) {
            notify(project, "API key not set. Go to Settings -> Tools -> Moka Git AI Commit.", NotificationType.WARNING)
            return
        }
        if (settings.state.promptTemplate.isBlank()) {
            notify(project, "Prompt template is empty. Go to Settings -> Tools -> Moka Git AI Commit.", NotificationType.WARNING)
            return
        }

        val diff = DiffExtractor.extract(changes)
        val prompt = settings.state.promptTemplate.trimEnd() + "\n\n" + diff

        object : Task.Backgroundable(project, "Generating commit message...", true) {
            override fun run(indicator: ProgressIndicator) {
                runCatching { OpenAiClient.generate(settings, prompt) }
                    .onSuccess { message ->
                        ApplicationManager.getApplication().invokeLater {
                            document.setText(message)
                        }
                    }
                    .onFailure { err ->
                        notify(project, err.message ?: "Unknown error.", NotificationType.ERROR)
                    }
            }
        }.queue()
    }

    private fun notify(project: Project, content: String, type: NotificationType) {
        NotificationGroupManager.getInstance()
            .getNotificationGroup("com.moka.gitcommit")
            .createNotification(content, type)
            .notify(project)
    }
}
```

---

## 최종 파일 구조

```
src/
  main/
    kotlin/com/moka/gitcommit/
      actions/
        GenerateCommitMessageAction.kt
      services/
        OpenAiClient.kt
      settings/
        AppSettings.kt
        AppSettingsConfigurable.kt
      utils/
        DiffExtractor.kt
    resources/META-INF/
      plugin.xml
build.gradle.kts
gradle.properties
```

---

## 검증 방법

```
# Windows:    .\gradlew build
# macOS/Linux: ./gradlew build

# 1. 컴파일
# .\gradlew build  /  ./gradlew build

# 2. plugin.xml 유효성
# .\gradlew verifyPlugin  /  ./gradlew verifyPlugin

# 3. 실제 동작 확인
# .\gradlew runIde  /  ./gradlew runIde
# -> 테스트 IDE 열림
# -> 아무 프로젝트에서 파일 수정 -> git stage -> Ctrl+K
# -> Settings -> Tools -> Moka Git AI Commit 에서 API 키 + 프롬프트 설정
# -> Generate AI Commit Message 버튼 클릭
```

---

## 나중에 추가 가능한 것들

- [ ] Settings UI에 "Test Connection" 버튼
- [ ] 선택된 파일만 diff 추출 (현재는 전체 staged)
- [ ] 스트리밍 응답 (타이핑 효과)
- [ ] 프롬프트 템플릿에 `{branch}`, `{files}` 변수 치환
- [ ] 여러 커밋 메시지 후보 생성 후 선택

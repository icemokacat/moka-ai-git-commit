# skill: getting-started

## 언제 사용하나

프로젝트를 처음 클론하거나 새로 셋업할 때. 첫 코드 작성 전 이 순서대로 진행.

## 첫 시작 6단계

### 1. Gradle 프로젝트 셋업

`build.gradle.kts`와 `gradle.properties` 구성 (`CLAUDE.md` Tech Stack 참조).
OkHttp, kotlinx.serialization은 `implementation`에 — IDE가 제공하지 않으므로 JAR에 번들해야 함.

### 2. plugin.xml 등록

```xml
<idea-plugin>
  <id>com.moka.gitcommit</id>
  <name>Moka Git AI Commit</name>
  <vendor email="icemokacat@gmail.com" url="https://github.com/icemokacat">icemokacat</vendor>

  <depends>com.intellij.modules.platform</depends>
  <depends>Git4Idea</depends>

  <extensions defaultExtensionNs="com.intellij">
    <applicationService serviceImplementation="com.moka.gitcommit.settings.AppSettings"/>
    <applicationConfigurable parentId="tools"
        instance="com.moka.gitcommit.settings.AppSettingsConfigurable"
        id="com.moka.gitcommit.settings"
        displayName="Moka Git AI Commit"/>
    <notificationGroup id="com.moka.gitcommit" displayType="BALLOON"/>
  </extensions>

  <actions>
    <action id="com.moka.gitcommit.GenerateCommitMessage"
            class="com.moka.gitcommit.actions.GenerateCommitMessageAction"
            text="Generate AI Commit Message">
      <keyboard-shortcut keymap="$default" first-keystroke="ctrl alt C"/>
      <add-to-group group-id="Vcs.MessageActionGroup" anchor="first"/>
    </action>
  </actions>
</idea-plugin>
```

### 3. Settings 구현 (AppSettings.kt)

```kotlin
@State(name = "MokaGitAICommit", storages = [Storage("mokaGitAICommit.xml")])
@Service(Service.Level.APP)
class AppSettings : PersistentStateComponent<AppSettings.State> {

    data class State(
        var openAiModel: String = "gpt-4o",
        var promptTemplate: String = "",
        var maxDiffLength: Int = 12000,
        var openAiBaseUrl: String = "https://api.openai.com/v1"
    )

    private var state = State()
    override fun getState(): State = state
    override fun loadState(state: State) { this.state = state }

    // API key는 PasswordSafe (시스템 키체인) — plain XML에 저장하면 안 됨
    var apiKey: String
        get() = PasswordSafe.instance.getPassword(credentialAttributes()) ?: ""
        set(value) = PasswordSafe.instance.setPassword(credentialAttributes(), value)

    private fun credentialAttributes() =
        CredentialAttributes(generateServiceName("MokaGitAI", "openai-api-key"))

    companion object {
        fun getInstance(): AppSettings = service()
    }
}
```

### 4. git diff 추출 (DiffExtractor.kt)

```kotlin
object DiffExtractor {
    fun extract(changes: Collection<Change>): String {
        val builder = StringBuilder()
        for (change in changes) {
            val path = change.virtualFile?.path ?: continue
            if (change.virtualFile?.fileType?.isBinary == true) continue

            val before = change.beforeRevision?.content ?: ""
            val after = change.afterRevision?.content ?: continue

            builder.appendLine("--- a/$path")
            builder.appendLine("+++ b/$path")
            builder.appendLine(after)
        }
        val result = builder.toString()
        val limit = AppSettings.getInstance().state.maxDiffLength
        return if (result.length > limit) result.take(limit) + "\n... (truncated)" else result
    }
}
```

실제 unified diff 포맷이 필요하면 `IdeaTextPatchBuilder.buildPatch()` 사용.

### 5. Action 구현 (GenerateCommitMessageAction.kt)

```kotlin
class GenerateCommitMessageAction : AnAction() {

    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible =
            e.getData(VcsDataKeys.CHANGES)?.isNotEmpty() == true
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
        val document = e.getData(VcsDataKeys.COMMIT_MESSAGE_DOCUMENT) ?: return
        val changes = e.getData(VcsDataKeys.CHANGES) ?: return

        val settings = AppSettings.getInstance()
        if (settings.apiKey.isBlank()) {
            notify(project, "API key not set. Go to Settings -> Tools -> Moka Git AI Commit.")
            return
        }

        val diff = DiffExtractor.extract(changes.toList())
        val prompt = settings.state.promptTemplate + "\n\n" + diff

        object : Task.Backgroundable(project, "Generating commit message...") {
            override fun run(indicator: ProgressIndicator) {
                runCatching { OpenAiClient.generate(settings, prompt) }
                    .onSuccess { msg ->
                        ApplicationManager.getApplication().invokeLater {
                            document.setText(msg)
                        }
                    }
                    .onFailure { err ->
                        notify(project, err.message ?: "Failed to generate message.")
                    }
            }
        }.queue()
    }
}
```

### 6. 테스트 실행

> OS에 따라 `.\gradlew` (Windows) 또는 `./gradlew` (macOS/Linux) 사용.

```
.\gradlew runIde         # Windows
./gradlew runIde         # macOS / Linux
# 테스트 IntelliJ 인스턴스 열림
# File -> New Project -> git init
# 파일 변경 후 커밋창 (Ctrl+K) 열어서 버튼 확인
```

---

## 참고 오픈소스

| 프로젝트 | 참고할 부분 |
|---|---|
| [ai-commits-intellij-plugin](https://github.com/Blarc/ai-commits-intellij-plugin) | 전체 구조, 멀티 프로바이더 패턴 |
| [GitMuse](https://github.com/jelenadjuric01/GitMuse) | threading/에러 처리 패턴 |
| [intellij-platform-plugin-template](https://github.com/JetBrains/intellij-platform-plugin-template) | Gradle 셋업, CI/CD |

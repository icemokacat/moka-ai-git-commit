# moka-git-ai-commit — Claude Context

## Project Purpose

IntelliJ IDEA plugin that auto-generates git commit messages via OpenAI API.
The plugin is a thin connector: it extracts the git diff, feeds it into a user-written
prompt template, calls OpenAI, and inserts the response into the commit message field.
The user owns the prompt entirely — the plugin provides no built-in prompt logic.

## Development Environment

> **Rule for Claude:** Shell syntax depends on the user's OS. Before running any command (Gradle, git, installs, file ops), confirm the user's environment if it is not already known.

**Ask at the start of a session when the OS is unclear:**
> "어떤 OS/쉘 환경에서 작업하고 계신가요? (Windows PowerShell / macOS‧Linux bash)"

| OS | Gradle | Env var | Path separator |
|---|---|---|---|
| Windows (PowerShell) | `.\gradlew` | `$env:VAR="x"; .\gradlew ...` | `\` |
| macOS / Linux (bash/zsh) | `./gradlew` | `VAR=x ./gradlew ...` | `/` |
| Docker container interior | `./gradlew` | `export VAR=x` | `/` |

Once confirmed, use that syntax consistently for the rest of the session.

## Tech Stack

| Layer | Choice |
|---|---|
| Language | Kotlin (JVM 21) |
| Build | Gradle + `org.jetbrains.intellij` plugin v2.x |
| IDE target | IntelliJ IDEA 2024.2+ (build 242+) |
| HTTP | OkHttp 4 (bundled in plugin JAR) |
| JSON | kotlinx.serialization |
| Plugin deps | `git4idea` |

## Architecture

```
GenerateCommitMessageAction (AnAction)
  ↓ reads
VcsDataKeys.SELECTED_CHANGES + ChangeListManager
  ↓ builds
DiffExtractor → unified diff string (capped at 12 KB)
  ↓ combines with
AppSettings.promptTemplate (user-written)
  ↓ sends via Task.Backgroundable
OpenAiClient → POST /v1/chat/completions
  ↓ on success (invokeLater → EDT)
CommitMessageDocument.setText(result)
```

## Key Files (once scaffold is created)

```
src/main/kotlin/com/moka/gitcommit/
  actions/GenerateCommitMessageAction.kt   # AnAction entry point
  settings/AppSettings.kt                  # PersistentStateComponent
  settings/AppSettingsConfigurable.kt      # Settings UI
  services/OpenAiClient.kt                 # HTTP + threading
  utils/DiffExtractor.kt                   # git diff -> string
src/main/resources/META-INF/plugin.xml     # Extension points
build.gradle.kts
gradle.properties
```

## Build Commands

> Windows PowerShell — use `.\gradlew` (backslash). Never use `./gradlew` on the host.

```powershell
.\gradlew runIde          # Run plugin in a test IDE instance
.\gradlew buildPlugin     # Build distributable ZIP -> build/distributions/
.\gradlew test            # Run unit tests
.\gradlew verifyPlugin    # Validate plugin.xml and binary compatibility

# Publish to JetBrains Marketplace (PowerShell env var syntax)
$env:PUBLISH_TOKEN="your_token"; .\gradlew publishPlugin
```

## Hard Rules (never violate)

1. **No EDT blocking** — all HTTP calls go through `Task.Backgroundable` or coroutines with `Dispatchers.IO`. EDT calls only for UI updates via `invokeLater`.
2. **No class fields in AnAction** — state stored in services, not action instances. Violating this causes memory leaks.
3. **API key never logged** — redact `Bearer <token>` before any log output.
4. **Diff size cap** — truncate input at 12 000 chars before sending to OpenAI. Configurable up to 50 000.
5. **`exit 0` on non-critical errors** — plugin must never crash the IDE.

## Settings UI (AppSettingsConfigurable)

Settings → Tools → Moka Git AI Commit 에서 제공하는 기능. UI 명세는 변경 시 이 표를 기준으로 유지할 것.

| 설정 키 | UI 컨트롤 | 저장 위치 | 기본값 |
|---|---|---|---|
| `openAiApiKey` | `JBPasswordField` (마스킹 입력) | `PasswordSafe` (시스템 키체인) | — |
| `openAiModel` | `ComboBox` (프리셋 + 직접 입력) | `AppSettings.State` XML | `gpt-4o` |
| `openAiBaseUrl` | `JBTextField` | `AppSettings.State` XML | `https://api.openai.com/v1` |
| `maxDiffLength` | `JBTextField` (숫자) | `AppSettings.State` XML | `12000` |
| `promptTemplate` | `JBTextArea` (멀티라인) + `JBScrollPane` | `AppSettings.State` XML | — |

**UI 요구사항 (절대 위반 금지):**
- `promptTemplate` 필드는 단일 줄 입력 금지 — 최소 10행, 스크롤 가능, 자동 줄바꿈(`lineWrap=true`, `wrapStyleWord=true`) 적용
- `openAiBaseUrl`은 반드시 사용자가 편집 가능해야 함 — 하드코딩 금지 (Azure OpenAI, Ollama 등 커스텀 엔드포인트 지원)
- `openAiApiKey`는 반드시 `PasswordSafe`에 저장 — `AppSettings.State` XML에 평문 기록 절대 금지
- `openAiModel` 기본 프리셋: `gpt-4o`, `gpt-4o-mini`, `gpt-4-turbo`, `gpt-3.5-turbo`

## Extension Points Registered

- `com.intellij.applicationService` -> `AppSettings`
- `com.intellij.applicationConfigurable` -> `AppSettingsConfigurable` (under Tools)
- `com.intellij.notificationGroup` -> balloon notifications
- `actions` -> `GenerateCommitMessageAction` added to `Vcs.MessageActionGroup`

## Reference Implementations

- https://github.com/Blarc/ai-commits-intellij-plugin — multi-provider, most mature
- https://github.com/jelenadjuric01/GitMuse — clean threading/error patterns
- https://github.com/JetBrains/intellij-platform-plugin-template — official scaffold

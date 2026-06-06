# moka-git-ai-commit — Claude Context

## Project Purpose

IntelliJ IDEA plugin that auto-generates git commit messages via OpenAI API.
The plugin is a thin connector: it extracts the git diff, feeds it into a user-written
prompt template, calls OpenAI, and inserts the response into the commit message field.
The user owns the prompt entirely — the plugin provides no built-in prompt logic.

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

```bash
./gradlew runIde          # Run plugin in a test IDE instance
./gradlew buildPlugin     # Build distributable ZIP -> build/distributions/
./gradlew test            # Run unit tests
./gradlew verifyPlugin    # Validate plugin.xml and binary compatibility
./gradlew publishPlugin   # Publish to JetBrains Marketplace (needs token)
```

## Hard Rules (never violate)

1. **No EDT blocking** — all HTTP calls go through `Task.Backgroundable` or coroutines with `Dispatchers.IO`. EDT calls only for UI updates via `invokeLater`.
2. **No class fields in AnAction** — state stored in services, not action instances. Violating this causes memory leaks.
3. **API key never logged** — redact `Bearer <token>` before any log output.
4. **Diff size cap** — truncate input at 12 000 chars before sending to OpenAI. Configurable up to 50 000.
5. **`exit 0` on non-critical errors** — plugin must never crash the IDE.

## Settings Stored

- `openAiApiKey` — stored via `PasswordSafe` (system keychain), not plain XML
- `openAiModel` — string, default `gpt-4o`
- `promptTemplate` — multiline string, entirely user-controlled
- `maxDiffLength` — int, default 12000
- `openAiBaseUrl` — string, default `https://api.openai.com/v1` (allows custom endpoints)

## Extension Points Registered

- `com.intellij.applicationService` -> `AppSettings`
- `com.intellij.applicationConfigurable` -> `AppSettingsConfigurable` (under Tools)
- `com.intellij.notificationGroup` -> balloon notifications
- `actions` -> `GenerateCommitMessageAction` added to `Vcs.MessageActionGroup`

## Reference Implementations

- https://github.com/Blarc/ai-commits-intellij-plugin — multi-provider, most mature
- https://github.com/jelenadjuric01/GitMuse — clean threading/error patterns
- https://github.com/JetBrains/intellij-platform-plugin-template — official scaffold

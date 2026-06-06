# IntelliJ Plugin Development Rules

> Project-specific rules for moka-git-ai-commit. Applies to all Kotlin/plugin code.

## Threading Model (Most Critical)

IntelliJ has two threads: EDT (UI) and background. Mixing them crashes the IDE.

| Operation | Thread |
|---|---|
| HTTP calls, I/O, OpenAI API | Background (`Task.Backgroundable` or `Dispatchers.IO`) |
| Reading/writing UI components | EDT only |
| `Document.setText()` (commit message) | EDT via `invokeLater` |
| Reading `VcsDataKeys` in `actionPerformed` | EDT (already there) |

```kotlin
// Correct pattern
object : Task.Backgroundable(project, "Generating...") {
    override fun run(indicator: ProgressIndicator) {
        val result = callOpenAi(...)           // background
        ApplicationManager.getApplication().invokeLater {
            document.setText(result)           // EDT
        }
    }
}.queue()
```

## AnAction Rules

- **No instance fields** — `AnAction` instances persist for the IDE lifetime. State in fields leaks memory. Use `project.service<MyService>()` instead.
- `update()` is called on every UI repaint. Keep it under 1ms — no I/O, no service lookups.
- `getActionUpdateThread()` should return `ActionUpdateThread.BGT` for VCS actions.

```kotlin
// Wrong
class MyAction : AnAction() {
    private val client = OkHttpClient()  // LEAK
}

// Correct
class MyAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val client = e.project!!.service<OpenAiClient>()
    }
}
```

## Security

- API key stored via `PasswordSafe` (system keychain) — never in plain XML state.
- Before any `LOG.debug()` touching a request/response: replace `Bearer .*` with `Bearer [REDACTED]`.
- Never log full diff content at INFO level — source code may contain secrets.

## Diff Extraction

- Cap diff at `AppSettings.maxDiffLength` (default 12 000 chars) before sending.
- Use `IdeaTextPatchBuilder` for proper unified diff — do not manually build patch strings.
- Skip binary files and files larger than 500 KB.

## Error Handling

- Catch `IOException`, `JsonParseException`, HTTP 4xx/5xx separately with distinct user messages.
- HTTP 401 → "Invalid API key. Check Settings → Tools → Moka Git AI."
- HTTP 429 → "OpenAI rate limit hit. Try again in a moment."
- All exceptions must be caught inside `Task.Backgroundable.run()`.
- Wrap all top-level entry points in `runCatching` — plugin must never propagate to the IDE.

## plugin.xml Conventions

- `<vendor>` must include `email` and `url`.
- `<change-notes>` must be updated before each release.
- `idea-version since-build` must match `pluginSinceBuild` in `gradle.properties`.
- Use `<depends>com.intellij.modules.platform</depends>` + Gradle dependency for `git4idea`.

## Gradle Conventions

- No version literals in the `dependencies {}` block — use `gradle.properties` for all versions.
- OkHttp and kotlinx.serialization go in `implementation`, not `compileOnly` (not provided by IDE).
- `patchPluginXml` must set `sinceBuild` and `untilBuild`.

## Kotlin Style

- `data class` for settings state.
- `object` for stateless services/clients.
- Named parameters when calling functions with 3+ arguments.
- No `!!` unless null is provably impossible.
- `runCatching` over bare `try/catch` for simple success/failure paths.

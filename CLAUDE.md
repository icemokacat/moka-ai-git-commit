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

Settings → Tools → Moka Git AI Commit. `JBTabbedPane`으로 3개 탭 구성. UI 명세는 변경 시 이 섹션을 기준으로 유지할 것.

### 탭 구조

```
┌─────────────────────────────────────────────────┐
│  [API Settings]  [Prompt Settings]  [Ignore List] │
├─────────────────────────────────────────────────┤
│  탭별 콘텐츠                                       │
└─────────────────────────────────────────────────┘
```

### Tab 1 — API Settings

| 설정 키 | UI 컨트롤 | 저장 위치 | 기본값 |
|---|---|---|---|
| `openAiApiKey` | `JBPasswordField` + 눈 아이콘 토글 | `PasswordSafe` (시스템 키체인) | — |
| `openAiModel` | `ComboBox` (프리셋 + 직접 입력) | `AppSettings.State` XML | `gpt-4o` |
| `openAiBaseUrl` | `JBTextField` | `AppSettings.State` XML | `https://api.openai.com/v1` |
| — | Test Connection 버튼 + 인라인 결과 | — | — |
| — | platform.openai.com HyperlinkLabel | — | — |

프리셋 모델: `gpt-4o`, `gpt-4o-mini`, `gpt-4-turbo`, `gpt-3.5-turbo`

### Tab 2 — Prompt Settings

| 설정 키 | UI 컨트롤 | 저장 위치 | 기본값 |
|---|---|---|---|
| `maxDiffLength` | `JBTextField` (숫자) | `AppSettings.State` XML | `12000` |
| `promptTemplate` | `JBTextArea` (멀티라인) + `JBScrollPane` | `AppSettings.State` XML | — |

### Tab 3 — Ignore List

| 설정 키 | UI 컨트롤 | 저장 위치 | 기본값 |
|---|---|---|---|
| `ignorePatterns` | `JBTextArea` (멀티라인) + `JBScrollPane` | `AppSettings.State` XML | 아래 기본값 참조 |

탭 상단에 다음 설명 텍스트를 `JBLabel`로 표시 (편집 불가):

> 한 줄에 하나씩 파일 패턴을 입력하세요. .gitignore와 동일한 glob 형식을 사용합니다.  
> 설정된 패턴에 해당하는 파일은 AI에 전송되는 diff에서 제외됩니다.

`ignorePatterns` 기본값 (AppSettings.State 초기값):

```
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
```

### UI 요구사항 (절대 위반 금지)

- `promptTemplate`, `ignorePatterns` 필드는 단일 줄 입력 금지 — 최소 10행, `JBScrollPane` 감싸기, `lineWrap=true`, `wrapStyleWord=true` 적용
- `openAiBaseUrl`은 반드시 사용자가 편집 가능해야 함 — 하드코딩 금지
- `openAiApiKey`는 반드시 `PasswordSafe`에 저장 — `AppSettings.State` XML에 평문 기록 절대 금지
- 모든 탭에 `HORIZONTAL_SCROLLBAR_NEVER` — 가로 스크롤 없음

### 레이아웃 규칙 (절대 위반 금지)

- **라벨 너비 일치** — `FormBuilder.createFormBuilder()` + `addLabeledComponent()` 사용. 탭 내 모든 행의 라벨 열 너비가 자동으로 동일하게 정렬됨.
- **우측 위젯 오른쪽 정렬** — 입력 필드 오른쪽에 아이콘·버튼이 붙는 경우 `BorderLayout(CENTER=필드, EAST=아이콘)` 구조로 구현. 눈 아이콘(API Key 토글) 등 모든 인라인 위젯에 적용.
- **입력창 반응형 너비** — 창 너비 변경 시 라벨은 고정, 입력박스만 늘어남/줄어듦. 외부 래퍼에 `fill = GridBagConstraints.HORIZONTAL, weightx = 1.0` 적용. 입력 필드에 고정 픽셀 너비 설정 금지.
- **`JBPasswordField` 생성자 주의** — IntelliJ Platform `JBPasswordField`는 no-arg 생성자만 지원. `JBPasswordField(text, columns)` 형태는 컴파일 오류. 필요 시 `field.columns = N`으로 별도 설정.

## Extension Points Registered

- `com.intellij.applicationService` -> `AppSettings`
- `com.intellij.applicationConfigurable` -> `AppSettingsConfigurable` (under Tools)
- `com.intellij.notificationGroup` -> balloon notifications
- `actions` -> `GenerateCommitMessageAction` added to `Vcs.MessageActionGroup`

## 재발 방지 규칙 (실제 버그에서 도출)

### 입력 필드 너비가 내용에 따라 늘어나는 문제

**증상**: 설정창에서 API 키를 입력할 때 필드(및 창 전체)가 오른쪽으로 늘어남.

**원인**: `JBPasswordField()`, `JBTextField()` 기본 생성자는 `columns = 0` 으로 초기화됨.
`getPreferredSize()` 가 `columns == 0` 일 때 현재 **내용 너비**를 반환 → 타이핑할수록 preferred width 증가 →
`FormBuilder` / `GridBagLayout` 이 이 크기를 기준으로 form 전체를 확장.

**규칙**: 모든 단일행 입력 필드는 `columns = 1` 로 초기화.
`FormBuilder.addLabeledComponent()` 의 `fill=HORIZONTAL, weightx=1.0` 이 실제 너비를 결정하므로,
preferred width 힌트는 아주 작게 고정해야 한다.

```kotlin
// 잘못된 예 — columns = 0 (기본값), 내용 기반 너비 확장
private val apiKeyField = JBPasswordField()

// 올바른 예 — columns = 1, 레이아웃 매니저가 너비 결정
private val apiKeyField = JBPasswordField().apply { columns = 1 }
```

### OkHttp response body 미닫기 → 연결 hang

**증상**: `testConnection()` 이 "연결 중..." 상태에서 최대 60초 동안 응답 없음.

**원인**: `http.newCall(request).execute()` 로 받은 `Response` 를 닫지 않으면 OkHttp 가
연결 풀 반환을 위해 body 전체를 소비할 때까지 대기. `readTimeout(60s)` 이 경과해야 스레드가 해제됨.

**규칙**: 모든 OkHttp `Response` 는 반드시 `response.use { }` 블록으로 닫아야 함.
body 를 읽는 경우 `response.body?.string()` 이 내부적으로 body 를 소비하지만,
status code 만 확인하는 경우에도 `use` 로 감싸지 않으면 커넥션이 회수되지 않는다.

```kotlin
// 잘못된 예 — response body 미닫기, 60초 hang
val response = http.newCall(request).execute()
if (!response.isSuccessful) throw IOException("...")
return "연결 성공"   // response 가 닫히지 않음

// 올바른 예 — use 블록으로 자동 close
http.newCall(request).execute().use { response ->
    if (!response.isSuccessful) throw IOException("...")
    return "연결 성공"
}
```

### StatusText.text Kotlin property setter 컴파일 오류 → 플러그인 로드 실패

**증상**: 설정창이 Settings → Tools 목록에서 아예 사라짐 (플러그인이 로드되지 않음).

**원인**: IntelliJ의 `StatusText.setText(String)`은 메서드 체이닝을 위해 `StatusText`를 반환한다.
Kotlin은 반환값이 `void`가 아닌 setter를 mutable property로 합성하지 않으므로
`field.emptyText.text = "..."` 는 컴파일 에러 → 플러그인 전체 로드 실패.

**규칙**: `JBTextField.emptyText`에 hint 텍스트를 설정하려면 property 할당 대신
메서드 호출(`emptyText.setText("...")`)을 써야 한다.
단, `reset()`이 항상 실제 저장값으로 필드를 채우므로 emptyText hint는 불필요하다.

```kotlin
// 잘못된 예 — 컴파일 에러, 플러그인 로드 실패
private val baseUrlField = JBTextField().apply {
    emptyText.text = "https://api.openai.com/v1/chat/completions"
}

// 올바른 예 — columns = 1 만 적용 (reset()이 실제 값으로 채움)
private val baseUrlField = JBTextField().apply { columns = 1 }
```

### JBTextField를 FormBuilder에 직접 추가 시 10px 너비로 렌더링

**증상**: 설정창 API URL 입력 필드가 10px 너비로 렌더링되고 클릭/포커스/입력 불가.

**원인**: `JBTextField`는 `ExtendableTextField`를 상속하며 `getMaximumSize()`가
`getPreferredSize()`를 반환하도록 오버라이드되어 있다. `columns = 1`이면 preferred width ≈ 10px.
`FormBuilder.addLabeledComponent()`의 `fill=HORIZONTAL`이 maximum size 이상으로 늘리지 못함.
`JBPasswordField`는 `JPasswordField`를 직접 상속하므로 이 문제가 없다.

**규칙**: `JBTextField`를 `FormBuilder`에 추가할 때는 `JPanel(BorderLayout())`으로 감싸서 추가.
`BorderLayout.CENTER`가 패널 너비를 채우고, `FormBuilder`는 패널을 `fill=HORIZONTAL`로 확장한다.

```kotlin
// 잘못된 예 — 10px 너비
.addLabeledComponent("API URL:", baseUrlField)

// 올바른 예 — JPanel 래퍼로 full-width 보장
val baseUrlPanel = JPanel(BorderLayout()).apply { add(baseUrlField, BorderLayout.CENTER) }
.addLabeledComponent("API URL:", baseUrlPanel)
```

### modal 다이얼로그 안에서 invokeLater 가 실행되지 않는 문제

**증상**: Test Connection 버튼 클릭 후 "연결 중..."에서 영구 멈춤. 연결 성공/실패 메시지가 표시 안 됨.

**원인**: IntelliJ Settings 다이얼로그는 modal 창이다.
`ApplicationManager.getApplication().invokeLater { }` 의 기본값은 `defaultModalityState()`이며,
이 상태에서는 현재 modal 컨텍스트가 해제될 때(= Settings 창 닫힐 때)까지 람다가 실행되지 않는다.
백그라운드 스레드에서 연결이 완료되어도 UI 업데이트가 pending 상태로 남는다.

**규칙**: Settings UI 안에서 백그라운드 → EDT 전환 시 반드시 `ModalityState.current()`를
EDT에서 미리 캡처하여 `invokeLater` 두 번째 인자로 전달한다.

```kotlin
// 잘못된 예 — modal 창에서 invokeLater가 실행 안 됨
ApplicationManager.getApplication().executeOnPooledThread {
    val result = doWork()
    ApplicationManager.getApplication().invokeLater {  // Settings 닫힐 때까지 pending
        updateUi(result)
    }
}

// 올바른 예 — EDT에서 미리 캡처한 modality 전달
val modality = ModalityState.current()  // EDT에서 캡처
ApplicationManager.getApplication().executeOnPooledThread {
    val result = doWork()
    ApplicationManager.getApplication().invokeLater({
        updateUi(result)
    }, modality)
}
```

### Document.setText() 는 WriteCommandAction 없이 호출 시 조용히 실패

**증상**: `invokeLater` 안에서 `document.setText(message)`를 호출해도 커밋 메시지 창에 아무것도 표시 안 됨.

**원인**: IntelliJ `Document` 수정은 반드시 Write Action 컨텍스트 안에서 이루어져야 한다.
Write Action 없이 호출하면 assertion 오류로 조용히 실패하고, 커밋 다이얼로그가 modal이라
기본 `invokeLater`도 창이 닫힐 때까지 실행되지 않는다 (modal 다이얼로그 문제 참고).

**규칙**: 커밋 다이얼로그에서 `Document.setText()`를 호출할 때는 두 가지를 반드시 지킨다.
1. EDT에서 `ModalityState.current()` 미리 캡처
2. `WriteCommandAction.runWriteCommandAction(project) { ... }` 으로 감싸기

```kotlin
// 잘못된 예 — Write Action 없음 + ModalityState 없음 → 조용히 실패
ApplicationManager.getApplication().invokeLater {
    document.setText(message)
}

// 올바른 예
val modality = ModalityState.current()  // EDT에서 미리 캡처
// ... 백그라운드 작업 후 ...
ApplicationManager.getApplication().invokeLater({
    WriteCommandAction.runWriteCommandAction(project) { document.setText(message) }
}, modality)
```

### VcsDataKeys.CHANGES는 IntelliJ 2024.2 커밋 UI에서 항상 empty

**증상**: `actionPerformed()`에서 `e.getData(VcsDataKeys.CHANGES)`를 호출하면 파일이 체크되어 있어도 항상 empty 반환. "No changes selected" 메시지가 항상 표시됨.

**원인**: IntelliJ 2024.2의 새로운 커밋 UI(커밋 툴 윈도우)에서 `Vcs.MessageActionGroup`의 데이터 컨텍스트는 체크된 변경사항을 `VcsDataKeys.CHANGES`로 제공하지 않는다. `update()` (BGT)뿐 아니라 `actionPerformed()` (EDT)에서도 동일하게 empty.

**규칙**: 커밋 다이얼로그 버튼에서 pending changes를 가져올 때는 `VcsDataKeys.CHANGES` 대신 `ChangeListManager.getInstance(project).defaultChangeList.changes`를 사용한다. "변경사항 없음" 체크는 실제 uncommitted changes 유무로 판단한다.

```kotlin
// 잘못된 예 — IntelliJ 2024.2에서 항상 empty
val changes = e.getData(VcsDataKeys.CHANGES)?.toList().orEmpty()

// 올바른 예
val changes = ChangeListManager.getInstance(project).defaultChangeList.changes.toList()
if (changes.isEmpty()) {
    WriteCommandAction.runWriteCommandAction(project) { document.setText("No changes to commit.") }
    return
}
```

### AnAction에서 ActionUpdateThread import 누락 → 아이콘 회색 박스

**증상**: 커밋 다이얼로그 툴바 버튼이 아이콘 없이 회색 박스로만 표시됨.

**원인**: `getActionUpdateThread()`에서 `ActionUpdateThread.BGT`를 사용했지만
`ActionUpdateThread` import가 없어 컴파일 오류 → 플러그인 클래스 로드 실패 →
action이 비활성(disabled) 상태로 렌더링되어 아이콘이 회색 박스로 보임.

**규칙**: `AnAction`을 상속하고 `getActionUpdateThread()`를 override할 때는
반드시 `import com.intellij.openapi.actionSystem.ActionUpdateThread`를 추가한다.
`AnAction`, `AnActionEvent`와 같은 패키지이지만 자동 import가 누락되기 쉽다.

```kotlin
// 잘못된 예 — ActionUpdateThread import 없음 → 컴파일 오류
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class MyAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT  // 컴파일 오류
}

// 올바른 예
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent

class MyAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT
}
```

### PasswordSafe 비동기 로드와 사용자 입력 충돌 (reset() 경쟁 조건)

**증상**: 설정창에서 API 키를 입력했는데 잠시 후 필드가 빈 값 또는 이전 값으로 초기화됨.

**원인**: `reset()` 이 PasswordSafe 를 백그라운드 스레드에서 읽고 `invokeLater { apiKeyField.text = key }` 로
필드를 업데이트. 사용자가 그 사이에 타이핑하면 타이핑 내용이 덮어써짐.

**규칙**: `invokeLater` 안에서 필드를 업데이트할 때, 사용자가 이미 입력한 내용이 있으면 덮어쓰지 않는다.

```kotlin
// 올바른 예 — 필드가 비어 있을 때만 저장된 키로 채움
ApplicationManager.getApplication().invokeLater {
    if (String(apiKeyField.password).isEmpty()) {
        apiKeyField.text = key
    }
}
```

## 테스트 전략

### 테스트 가능 설계 원칙

IntelliJ 서비스(`@Service`, `PasswordSafe`, `VcsDataKeys`)에 의존하는 코드는 IDE 없이 테스트할 수 없다.
테스트 가능한 코드는 **순수 데이터 클래스 + 독립 함수** 형태로 분리한다.

| 레이어 | 테스트 가능 여부 | 이유 |
|---|---|---|
| `OpenAiClient.generate(config: OpenAiConfig, prompt)` | ✅ 가능 | OkHttp + kotlinx만 의존 |
| `OpenAiClient.testConnection(apiKey, baseUrl)` | ✅ 가능 | 동일 |
| `DiffExtractor.parseIgnorePatterns`, `shouldIgnore` | ✅ 가능 | 순수 문자열 로직 |
| `GenerateCommitMessageAction.actionPerformed` | ❌ 불가 | `AnActionEvent`, VCS API 필요 |
| `AppSettingsConfigurable` UI | ❌ 불가 | Swing + IntelliJ UI 필요 |

### OpenAI API 연결 검증 방식

`testConnection()`은 `POST /v1/chat/completions`에 최소 요청(`gpt-4o-mini`, `"hi"`)을 전송해
실제 연결과 인증을 검증한다. Response body는 소비하지 않고 status code만 확인하며
`response.use { }` 로 반드시 닫아야 한다 (OkHttp response body 미닫기 규칙 참고).

### 통합 테스트 실행 방법

```powershell
# Windows PowerShell — OPENAI_API_KEY 환경 변수 필요
$env:OPENAI_API_KEY="sk-..."; .\gradlew test

# 특정 모델 지정 (기본값: gpt-4o-mini)
$env:OPENAI_API_KEY="sk-..."; $env:OPENAI_MODEL="gpt-4o"; .\gradlew test

# 커스텀 API URL (예: local proxy)
$env:OPENAI_API_KEY="sk-..."; $env:OPENAI_API_URL="http://localhost:8080/v1/chat/completions"; .\gradlew test
```

`OPENAI_API_KEY` 환경 변수가 없으면 통합 테스트는 자동으로 SKIP된다 (`assumeTrue`).

### 코드 구현 시 테스트 요구사항

1. **HTTP 클라이언트 함수**: IntelliJ 서비스를 파라미터로 받지 말 것 — 순수 데이터 클래스(`OpenAiConfig`)로 받아야 함
2. **DiffExtractor 순수 함수**: `parseIgnorePatterns`, `shouldIgnore` 는 `internal` 가시성으로 유지 — 단위 테스트 접근 보장
3. **EDT 접근 금지 패턴**: `actionPerformed`에서 `PasswordSafe` 직접 접근 금지 — `Task.Backgroundable.run()` 내부로 이동
4. **새 로직 추가 시**: 테스트 없이 PR 금지. `src/test/kotlin/` 에 대응 테스트 파일 작성

## README 작성 규칙

README.md는 반드시 **사용자 가이드**와 **개발자 가이드** 두 섹션으로 구분해서 작성한다.

- **사용자 가이드**: 설치, 설정, 사용법. 실제 테스트로 확인된 내용만 작성. 검증 안 된 기능(커스텀 엔드포인트 등)은 기재 금지.
- **개발자 가이드**: 빌드, `runIde` 테스트, 단위/통합 테스트 실행 방법, 빌드 오류 안내.

## Reference Implementations

- https://github.com/Blarc/ai-commits-intellij-plugin — multi-provider, most mature
- https://github.com/jelenadjuric01/GitMuse — clean threading/error patterns
- https://github.com/JetBrains/intellij-platform-plugin-template — official scaffold

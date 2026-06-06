# IntelliJ Plugin Development Rules

> moka-git-ai-commit 전용 규칙. 모든 Kotlin/플러그인 코드에 적용.

## OS & 셸 (명령어 실행 전 반드시 확인)

기여자의 OS는 **고정되어 있지 않음** — 가정하기 전에 반드시 물어볼 것.

**언제 물어보나:** 셸 명령어 실행이 필요한 세션 시작 시, OS가 명시되지 않았다면:
> "어떤 OS/쉘 환경에서 작업하고 계신가요? (Windows PowerShell / macOS‧Linux bash)"

| 상황 | 셸 | Gradle | 환경변수 |
|---|---|---|---|
| Windows 호스트 | PowerShell | `.\gradlew` | `$env:VAR="x"` |
| macOS / Linux 호스트 | bash/zsh | `./gradlew` | `VAR=x ./gradlew` |
| Docker 컨테이너 내부 | bash/sh | `./gradlew` | `export VAR=x` |

OS가 확인되면 해당 문법을 세션 전체에 일관되게 적용. 문법을 섞지 말 것 (예: Windows에서 `./gradlew`, Linux에서 `.\gradlew` 금지).

## 스레딩 모델 (가장 중요)

IntelliJ는 EDT(UI)와 백그라운드 두 개의 스레드를 가짐. 혼용 시 IDE 크래시 발생.

| 작업 | 스레드 |
|---|---|
| HTTP 호출, I/O, OpenAI API | 백그라운드 (`Task.Backgroundable` 또는 `Dispatchers.IO`) |
| UI 컴포넌트 읽기/쓰기 | EDT 전용 |
| `Document.setText()` (커밋 메시지) | `invokeLater`를 통한 EDT |
| `actionPerformed`에서 `VcsDataKeys` 읽기 | EDT (이미 여기 있음) |

```kotlin
// 올바른 패턴
object : Task.Backgroundable(project, "Generating...") {
    override fun run(indicator: ProgressIndicator) {
        val result = callOpenAi(...)           // 백그라운드
        ApplicationManager.getApplication().invokeLater {
            document.setText(result)           // EDT
        }
    }
}.queue()
```

## AnAction 규칙

- **인스턴스 필드 금지** — `AnAction` 인스턴스는 IDE 수명 동안 유지됨. 필드에 상태를 저장하면 메모리 누수 발생. 대신 `project.service<MyService>()` 사용.
- `update()`는 모든 UI 리페인트마다 호출됨. 1ms 이하로 유지 — I/O, 서비스 조회 금지.
- `getActionUpdateThread()`는 VCS 액션에서 `ActionUpdateThread.BGT` 반환 필요.

```kotlin
// 잘못된 예
class MyAction : AnAction() {
    private val client = OkHttpClient()  // 메모리 누수
}

// 올바른 예
class MyAction : AnAction() {
    override fun actionPerformed(e: AnActionEvent) {
        val client = e.project!!.service<OpenAiClient>()
    }
}
```

## 보안

- API 키는 `PasswordSafe` (시스템 키체인)에 저장 — plain XML 상태에 저장 금지.
- 요청/응답을 다루는 `LOG.debug()` 호출 전: `Bearer .*`를 `Bearer [REDACTED]`로 교체.
- diff 전체 내용을 INFO 레벨로 로깅 금지 — 소스 코드에 비밀이 포함될 수 있음.

## Diff 추출

- 전송 전 diff를 `AppSettings.maxDiffLength` (기본값 12,000자)로 제한.
- 올바른 unified diff 형식을 위해 `IdeaTextPatchBuilder` 사용 — 패치 문자열 수동 조립 금지.
- 바이너리 파일 및 500KB 초과 파일 건너뜀.

## 에러 처리

- `IOException`, `JsonParseException`, HTTP 4xx/5xx를 각각 별도 사용자 메시지로 구분해 처리.
- HTTP 401 → "Invalid API key. Check Settings → Tools → Moka Git AI."
- HTTP 429 → "OpenAI rate limit hit. Try again in a moment."
- 모든 예외는 `Task.Backgroundable.run()` 내부에서 반드시 처리.
- 최상위 진입점 전체를 `runCatching`으로 감쌀 것 — 플러그인이 IDE로 예외를 전파하면 안 됨.

## plugin.xml 규칙

- `<vendor>`에 반드시 `email`과 `url` 포함.
- 각 릴리즈 전에 `<change-notes>` 업데이트.
- `idea-version since-build`는 `gradle.properties`의 `pluginSinceBuild`와 일치해야 함.
- `<depends>com.intellij.modules.platform</depends>` + `git4idea` Gradle 의존성 사용.

## Gradle 규칙

- `dependencies {}` 블록에 버전 리터럴 금지 — 모든 버전은 `gradle.properties`에 관리.
- OkHttp와 kotlinx.serialization은 `implementation`에 배치, `compileOnly` 아님 (IDE가 제공하지 않으므로 JAR에 번들 필요).
- `patchPluginXml`에서 반드시 `sinceBuild`와 `untilBuild` 설정.

## Kotlin 스타일

- 설정 상태에는 `data class` 사용.
- 상태 없는 서비스/클라이언트에는 `object` 사용.
- 인자가 3개 이상인 함수 호출 시 named parameter 사용.
- null이 절대 불가능한 경우가 아니면 `!!` 금지.
- 단순 성공/실패 경로에는 bare `try/catch` 대신 `runCatching` 사용.

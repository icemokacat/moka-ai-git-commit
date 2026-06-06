# skill: feature

## When to Use

새 기능을 추가하거나 기존 기능을 수정할 때 따라야 할 개발 워크플로우.

## 새 기능 추가 순서

### 1. 어떤 레이어인지 파악

| 변경 유형 | 수정할 파일 |
|---|---|
| UI (버튼, 설정창) | `AppSettingsConfigurable.kt`, `plugin.xml` |
| 설정값 추가 | `AppSettings.kt` + `AppSettingsConfigurable.kt` |
| OpenAI 호출 로직 | `OpenAiClient.kt` |
| diff 추출 로직 | `DiffExtractor.kt` |
| 커밋창 연동 | `GenerateCommitMessageAction.kt` |
| 새 액션 추가 | `actions/` 하위 새 파일 + `plugin.xml` 등록 |

### 2. 설정값 추가할 때

현재 제공되는 설정 항목과 각 UI 컨트롤:

| 설정 | UI 컨트롤 | 비고 |
|---|---|---|
| API Key | `JBPasswordField` | `PasswordSafe` 저장, XML 평문 금지 |
| Model | `ComboBox` | 프리셋: `gpt-4o`, `gpt-4o-mini`, `gpt-4-turbo`, `gpt-3.5-turbo` |
| Base URL | `JBTextField` | 커스텀 엔드포인트 지원용, 하드코딩 금지 |
| Prompt template | `JBTextArea(10, 60)` + `JBScrollPane` | 멀티라인 필수, 단일 줄 입력 금지 |
| Max diff length | `JBTextField` | 숫자 입력 |

새 설정을 추가할 때:

```kotlin
// 1. AppSettings.State에 필드 추가
data class State(
    var newOption: String = "default"
)

// 2. AppSettingsConfigurable에 UI 컴포넌트 추가
// 3. isModified(), apply(), reset() 세 메서드 모두 업데이트
```

> **Prompt 에디터 주의:** `promptTemplate` 필드는 반드시 `JBTextArea` + `JBScrollPane` 조합 사용.
> `JBTextField` (단일 줄) 또는 일반 `TextArea`로 교체 금지.

### 3. 새 AnAction 추가할 때

```kotlin
// actions/ 하위에 파일 생성
class MyNewAction : AnAction() {
    override fun getActionUpdateThread() = ActionUpdateThread.BGT

    override fun update(e: AnActionEvent) {
        e.presentation.isEnabledAndVisible = e.project != null
    }

    override fun actionPerformed(e: AnActionEvent) {
        val project = e.project ?: return
    }
}
```

```xml
<!-- plugin.xml에 등록 -->
<action id="com.moka.gitcommit.MyNewAction"
        class="com.moka.gitcommit.actions.MyNewAction"
        text="...">
    <add-to-group group-id="Vcs.MessageActionGroup" anchor="after"
                  relative-to-action="com.moka.gitcommit.GenerateCommitMessageAction"/>
</action>
```

### 4. HTTP 엔드포인트 변경할 때

- `OpenAiClient.kt`의 base URL은 `AppSettings.openAiBaseUrl`에서 읽음
- 모델 파라미터 추가 시 `AppSettings.State`에 필드 추가 후 `OpenAiClient`에서 참조
- 응답 파싱 변경 시 반드시 `runCatching` 유지

## 테스트 방법

> OS에 따라 명령어 형식이 다릅니다. Windows는 `.\gradlew`, macOS/Linux는 `./gradlew`.

```
# 1. 컴파일 확인
.\gradlew build          # Windows
./gradlew build          # macOS / Linux

# 2. 테스트 실행
.\gradlew test           # Windows
./gradlew test           # macOS / Linux

# 3. 실제 IDE에서 동작 확인
.\gradlew runIde         # Windows
./gradlew runIde         # macOS / Linux
# -> IntelliJ 테스트 인스턴스 열림
# -> 임시 프로젝트에서 git 변경사항 만들고 커밋창 열어서 확인
```

## 주의사항

- 네트워크 호출은 반드시 `Task.Backgroundable` 안에서
- 결과를 UI에 반영할 때는 `invokeLater` 필수
- `AnAction` 클래스에 `val`/`var` 필드 절대 금지
- 커밋 메시지는 `conventional` 형식: `feat:`, `fix:`, `refactor:` 등

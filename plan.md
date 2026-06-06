# Implementation Plan — Moka Git AI Commit

## 구현 현황

### 완료 ✅

1. **Gradle 프로젝트 셋업** — `build.gradle.kts`, `gradle.properties`, `settings.gradle.kts`
   - Kotlin 2.1.0 (Gradle 9.3.0 호환), IntelliJ Platform Gradle Plugin 2.3.0
2. **`plugin.xml` 등록** — 서비스, 설정, 알림 그룹, 액션 등록
   - `Vcs.MessageActionGroup` + `ChangesView.CommitToolbar` 두 그룹에 추가
3. **`AppSettings.kt`** — XML 영속화, API 키는 `PasswordSafe` (시스템 키체인)
4. **`AppSettingsConfigurable.kt`** — Settings UI
   - 가로 오버플로우 수정: `JBPasswordField(null, 25)` + `JBTextField(25)` + `GridBagLayout(fill=NONE)` 래퍼
   - Prompt template: 세로 스크롤만, 가로 스크롤 없음
   - **Test Connection** 버튼 + 인라인 결과 표시
   - platform.openai.com 링크 (API 키 안내)
   - API Key 필드 우측 눈 아이콘 버튼 (평문 표시/숨기기 토글)
5. **`DiffExtractor.kt`** — staged diff 추출, 바이너리/500KB 초과 파일 제외, 크기 제한
6. **`OpenAiClient.kt`** — OkHttp4 + kotlinx.serialization
   - Base URL trailing slash 버그 수정 (`trimEnd('/')`)
   - HTTP 404 명확한 오류 메시지 추가
   - `testConnection(apiKey, baseUrl)` 함수 추가
7. **`GenerateCommitMessageAction.kt`** — 커밋창 연동, 백그라운드 HTTP, EDT 메시지 삽입
8. **`.gitignore`** — Gradle, IDE, OS, 시크릿 파일 제외

### 알려진 제한사항

- 커밋 툴바 버튼이 `>>` 오버플로우 영역에 가려질 수 있음 — **Ctrl+Alt+C** 단축키로 동일하게 사용 가능

### 완료 (이번 세션) ✅

9. **`AppSettings.kt`** — `State`에 `ignorePatterns: String` 필드 추가, 기본값(빌드 결과물·잠금 파일·minified·생성 코드·의존성 패턴) 포함
10. **`AppSettingsConfigurable.kt`** 전면 재작업
    - `JBPasswordField()` (컴파일 오류 수정)
    - `JBTabbedPane` 3탭: API Settings / Prompt Settings / Ignore List
    - 반응형 레이아웃: `BorderLayout.NORTH` 래퍼로 입력 필드 가로 stretch
    - Ignore List 탭: glob 패턴 설명 + `ignorePatterns` JBTextArea
11. **`DiffExtractor.kt`** — glob 패턴 필터링 (`globToRegex` + 서브경로 매칭, 크로스플랫폼)

---

## 🔴 미해결 버그 (다음 세션에서 이어서)

### 버그: `ChangeListManager` vs `VcsDataKeys.CHANGES` 충돌

**현상**: 두 API 중 하나를 쓰면 반드시 한 케이스가 깨짐.

| 방식 | 파일 체크 O | 파일 체크 X (uncommitted 없음) | 파일 체크 X (uncommitted 있음) |
|---|---|---|---|
| `VcsDataKeys.CHANGES` | ❌ 항상 empty → "No changes" 표시 | ✅ empty | ✅ empty |
| `ChangeListManager.defaultChangeList.changes` | ✅ 정상 생성 | ✅ empty | ❌ 생성됨 (원하지 않는 동작) |

**현재 코드 상태**: `ChangeListManager` 사용 중 → CASE2(파일 체크 후 생성)는 동작, CASE1(아무 파일도 없을 때 "No changes") 동작 확인 필요.

**조사 방향**:
- `VcsDataKeys.CHANGES`가 IntelliJ 2024.2 새 커밋 UI에서 항상 empty인 이유 규명
- `e.getData(VcsDataKeys.CHANGE_LISTS)` 시도 → changelist 단위로 변경 여부 확인
- `ApplicationManager.getApplication().runReadAction { ChangeListManager... }` 로 Read Action 래핑 필요 여부 확인
- 참고 구현: [Blarc/ai-commits-intellij-plugin](https://github.com/Blarc/ai-commits-intellij-plugin) 에서 changes 획득 방식 확인

**확인된 사실**:
- `VcsDataKeys.CHANGES`는 `update()` (BGT)뿐 아니라 `actionPerformed()` (EDT)에서도 empty
- `ChangeListManager.defaultChangeList.changes`는 uncommitted changes 전체를 반환 (체크 여부 무관)
- `WriteCommandAction` + `ModalityState.current()` 조합은 정상 동작 확인됨

---

## 파일 구조

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
settings.gradle.kts
```

---

## 빌드 및 검증

```powershell
# Windows (PowerShell)
.\gradlew build          # 컴파일
.\gradlew verifyPlugin   # plugin.xml 유효성
.\gradlew runIde         # 테스트 IDE 실행

# macOS / Linux
./gradlew build
./gradlew verifyPlugin
./gradlew runIde
```

**동작 확인 순서:**
1. `runIde` → 테스트 IDE 열림
2. Settings → Tools → Moka Git AI Commit → API Key + Prompt 입력 → **Test Connection** 확인
3. 아무 프로젝트에서 파일 수정 → git stage → Ctrl+K (커밋 창)
4. Ctrl+Alt+C 또는 툴바 버튼 클릭 → 메시지 생성 확인

---

## verifyPlugin 설정 플랜

### 문제

`.\gradlew verifyPlugin` 실행 시 아래 오류 발생:

```
No IDE resolved for verification with the IntelliJ Plugin Verifier.
```

`build.gradle.kts`에 `intellijPlatform.pluginVerification.ides` 블록이 누락되어 있음.
`pluginVerifier()` 의존성은 이미 추가되어 있지만, 어떤 IDE 버전을 대상으로 검증할지 지정이 없는 상태.

### 선택지 비교

| 방식 | 장점 | 단점 | 추천 상황 |
|---|---|---|---|
| A. `recommended()` | JetBrains 권장 버전 자동 선택, 유지보수 최소 | 빌드마다 여러 IDE 다운로드(~수 GB), 느림 | CI 전수 검증 단계 |
| B. `gradle.properties` 버전 명시 | 버전 관리 명확, 재현 가능, 빠름 | 새 버전 출시 시 직접 업데이트 필요 | 로컬 개발 + 현 CI |
| C. `local()` (로컬 설치 IDE) | 다운로드 없음 | 기기마다 경로 다름, CI 불가 | 개인 로컬 전용 |

### 권장 방안: B (gradle.properties 기반 명시적 버전 관리)

**이유**

- 현재 `sinceBuild=242 ~ untilBuild=251.*` 범위이므로 최소·최대 양 끝(242, 최신 안정) 두 버전 검증이 실질적
- 버전은 `gradle.properties`에서 관리 → 코드 변경 없이 검증 대상 추가·변경 가능
- `recommended()` 는 IDE 3~5개를 다운로드해 로컬 첫 빌드 시간이 크게 증가; 명시적 버전은 1~2개로 제한 가능
- 확장성: `verifyIdeVersions` 프로퍼티에 쉼표로 버전을 추가하는 것만으로 검증 대상 확장

### 구체적 변경 내용

**`gradle.properties`에 추가**

```properties
# 검증 대상 IDE 버전 (쉼표 구분, 형식: TYPE-VERSION)
# IC=Community, IU=Ultimate
# sinceBuild=242(2024.2) ~ untilBuild=251.*(2024.3)
verifyIdeVersions=IC-2024.2,IC-2024.3
```

**`build.gradle.kts`의 `intellijPlatform {}` 블록에 추가**

```kotlin
pluginVerification {
    ides {
        providers.gradleProperty("verifyIdeVersions").get()
            .split(",")
            .map { it.trim() }
            .forEach { spec ->
                val dashIdx = spec.indexOf('-')
                val typeCode = spec.substring(0, dashIdx)
                val version  = spec.substring(dashIdx + 1)
                ide(IntelliJPlatformType.fromCode(typeCode), version)
            }
    }
}
```

> **주의**: `IntelliJPlatformType.fromCode()` 가 없으면 enum 직접 참조 방식으로 대체
> (`IntelliJPlatformType.IntellijIdeaCommunity` 등) — 컴파일 시 확인 필요.

**`repositories` 블록** — 현재 `defaultRepositories()` 가 이미 있으므로 변경 불필요.

### 작업 순서

1. `gradle.properties`에 `verifyIdeVersions` 프로퍼티 추가
2. `build.gradle.kts`에 `pluginVerification` 블록 추가
3. `.\gradlew verifyPlugin` 재실행 → 성공 확인
4. (선택) CLAUDE.md의 Build Commands 섹션에 첫 실행 시 IDE 다운로드 발생 안내 추가

### 미래 확장 (CI 전수 검증이 필요해질 때)

`gradle.properties` 방식 대신 `recommended()` 전환:

```kotlin
pluginVerification {
    ides { recommended() }
}
```

이 경우 CI matrix build 분리 또는 Gradle 빌드 캐시 전략 필요.

---

## 향후 개선 사항

- [ ] 선택된 파일만 diff 추출 (현재는 전체 staged)
- [ ] 스트리밍 응답 (타이핑 효과)
- [ ] 프롬프트 템플릿에 `{branch}`, `{files}` 변수 치환
- [ ] 여러 커밋 메시지 후보 생성 후 선택
- [ ] 커밋 툴바 버튼 아이콘 추가 (가시성 개선)
- [ ] JetBrains Marketplace 출시

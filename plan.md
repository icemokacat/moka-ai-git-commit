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

## 향후 개선 사항

- [ ] 선택된 파일만 diff 추출 (현재는 전체 staged)
- [ ] 스트리밍 응답 (타이핑 효과)
- [ ] 프롬프트 템플릿에 `{branch}`, `{files}` 변수 치환
- [ ] 여러 커밋 메시지 후보 생성 후 선택
- [ ] 커밋 툴바 버튼 아이콘 추가 (가시성 개선)
- [ ] JetBrains Marketplace 출시

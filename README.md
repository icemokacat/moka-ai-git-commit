# Moka Git AI Commit

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](LICENSE)

[⚡Intelij Marketplace Link](https://plugins.jetbrains.com/plugin/32149-moka-git-ai-commit)

OpenAI API를 사용해 git 커밋 메시지를 자동으로 생성하는 IntelliJ IDEA 플러그인.

스테이징된 변경사항을 읽어 직접 작성한 프롬프트와 결합한 뒤 커밋 메시지 필드를 채워줌

내장 프롬프트나 기본값 없음, 프롬프트는 온전히 사용자 몫.

플러그인은 단순히 연결 역할만 수행 합니다.

---

## 목차

- [사용자 가이드](#사용자-가이드)
  - [요구사항](#요구사항)
  - [설치](#설치)
  - [설정](#설정)
  - [사용법](#사용법)
- [개발자 가이드](#개발자-가이드)
  - [개발 환경](#개발-환경)
  - [빌드](#빌드)
  - [runIde 샌드박스 IDE에서 직접 테스트](#runide--샌드박스-ide에서-직접-테스트)
  - [테스트](#테스트)
  - [빌드 오류 안내](#빌드-오류-안내)

---

## 사용자 가이드

### 요구사항

- IntelliJ IDEA 2024.2 이상 (Community 또는 Ultimate)
- OpenAI API 키 (또는 호환 엔드포인트)

### 설치

**JetBrains Marketplace에서**

1. Settings → Plugins → Marketplace 이동
2. "Moka Git AI Commit" 검색
3. Install → IDE 재시작

**ZIP 직접 설치**

1. [Releases](https://github.com/icemokacat/moka-ai-git-commit/releases)에서 최신 ZIP 다운로드
2. Settings → Plugins → ⚙ → Install Plugin from Disk
3. ZIP 선택 → IDE 재시작

### 설정

Settings → Tools → **Moka Git AI Commit** 에서 3개 탭을 설정합니다.

#### API Settings 탭

| 항목 | 설명                                                                     |
|---|------------------------------------------------------------------------|
| OpenAI API Key | `sk-...` 형식의 OpenAI API 키. 시스템 키체인에 저장됨 (plain text 아님).               |
| Model | 사용할 모델. 직접 입력도 가능. 기본값: `gpt-4o-mini`                                  |
| API URL | 요청을 보낼 전체 엔드포인트 URL. 기본값: `https://api.openai.com/v1/chat/completions` |
| Test Connection | API 키와 URL이 유효한지 즉시 검증.                                                |

API 키가 없으면 설정창 하단 링크에서 [platform.openai.com](https://platform.openai.com/api-keys) 에서 발급.

#### Prompt Settings 탭

| 항목 | 설명 |
|---|---|
| Max diff length | OpenAI에 전송할 diff 최대 글자 수. 기본값: 12,000자 |
| Prompt template | 직접 작성하는 프롬프트. 플러그인은 이 뒤에 git diff를 붙여 전송. |

프롬프트 예시:
```
다음 git diff를 보고 conventional commit 형식으로 한 줄 커밋 메시지를 작성해줘.
- 형식: <type>(<scope>): <subject>
- type: feat, fix, refactor, docs, test, chore 중 하나
- 언어: 한국어
- 마크다운, 코드블록 없이 커밋 메시지 한 줄만 출력
```

#### Ignore List 탭

diff에서 제외할 파일 패턴을 glob 형식으로 지정합니다 (`.gitignore`와 동일한 형식).
기본값으로 빌드 결과물, 잠금 파일, 축소 파일 등이 제외되어 있습니다.

### 사용법

1. git에서 변경사항 스테이징
2. 커밋 다이얼로그 열기 — `Ctrl+K` (Windows/Linux) / `Cmd+K` (macOS)
3. **Generate AI Commit Message** 클릭 또는 **Ctrl+Alt+C** 입력
4. 생성된 메시지 검토·수정 후 커밋

> 버튼이 보이지 않으면 툴바의 `>>` 오버플로우 영역을 확인하거나 단축키 `Ctrl+Alt+C`를 사용하세요.

---

## 개발자 가이드

### 개발 환경

| 항목 | 버전 |
|---|---|
| JDK | 21 이상 |
| Kotlin | 2.1.0 |
| Gradle | 9.5.0 (래퍼 사용, 별도 설치 불필요) |
| IntelliJ Platform | 2024.2 (IC-242) |
| 대상 IDE | IntelliJ IDEA 2024.2+ |

```powershell
# 저장소 클론
git clone https://github.com/icemokacat/moka-git-ai-commit
cd moka-git-ai-commit
```

### 빌드

```powershell
# Windows PowerShell
.\gradlew buildPlugin       # build/distributions/*.zip 생성
.\gradlew verifyPlugin      # plugin.xml 및 바이너리 호환성 검증
.\gradlew compileKotlin     # 컴파일만 (빠른 문법 확인)
```

```bash
# macOS / Linux
./gradlew buildPlugin
./gradlew verifyPlugin
./gradlew compileKotlin
```

**`verifyPlugin` 검증 대상 버전**

`gradle.properties`의 `verifyIdeVersions` 프로퍼티로 검증할 IDE 버전을 지정합니다.

```properties
# 형식: TYPE-VERSION (쉼표 구분) / IC=Community, IU=Ultimate
verifyIdeVersions=IC-2024.2,IC-2024.3
```

첫 실행 시 지정된 IDE 버전을 다운로드합니다 (버전당 약 300~600 MB). 이후 실행은 캐시되어 빠릅니다.

### runIde — 샌드박스 IDE에서 직접 테스트

플러그인이 설치된 별도의 IntelliJ IDEA 인스턴스를 실행합니다.
실제 커밋 다이얼로그와 설정 창을 UI로 직접 확인할 때 사용합니다.

```powershell
# Windows PowerShell
.\gradlew runIde

# macOS / Linux
./gradlew runIde
```

**처음 실행 시 주의사항**

- IntelliJ Platform 샌드박스 이미지를 다운로드하기 때문에 **첫 실행은 수 분이 걸립니다**. 이후 실행은 빠릅니다.
- 샌드박스 IDE는 실제 설치된 IDE와 독립된 설정/키체인을 사용합니다.
- 샌드박스에서 API 키를 입력해도 실제 IDE에는 영향 없습니다.

**수동 테스트 체크리스트**

```
[ ] Settings → Tools → Moka Git AI Commit 열리는지 확인
[ ] API Key 입력 → 눈 아이콘으로 표시/숨기기
[ ] Test Connection → "연결 성공" 표시 확인
[ ] 창 크기 조절 시 입력 필드가 늘어나는지 확인 (라벨은 고정)
[ ] 커밋 다이얼로그에서 Generate AI Commit Message 버튼 동작 확인
[ ] Ctrl+Alt+C 단축키 동작 확인
```

### 테스트

#### 단위 테스트 (API 키 불필요)

`DiffExtractor`의 glob 패턴 매칭 로직 등 순수 함수를 검증합니다.

```powershell
.\gradlew test
```

API 키 없이도 15개 단위 테스트가 모두 실행됩니다. 통합 테스트는 자동으로 SKIP됩니다.

#### 통합 테스트 (실제 OpenAI API 호출)

실제 API 요청/응답을 검증합니다. 키가 없으면 SKIP되므로 CI에서도 안전하게 실행 가능합니다.

**설정 방법 (권장): `.env.test.local` 파일**

프로젝트 루트에 `.env.test.local` 파일을 생성합니다 (`.gitignore`에 포함되어 커밋되지 않음):

```bash
# .env.test.local.example 을 복사해서 시작
cp .env.test.local.example .env.test.local
```

`.env.test.local` 파일 내용:
```
OPENAI_API_KEY=sk-...
# OPENAI_API_URL=https://api.openai.com/v1/chat/completions   # 기본값
# OPENAI_MODEL=gpt-4o-mini                                    # 기본값
```

이후 `.\gradlew test` 실행. 별도 환경변수 설정 불필요.

**대안: PowerShell 직접 설정**

```powershell
$env:OPENAI_API_KEY="sk-..."; .\gradlew test
# 특정 모델 지정
$env:OPENAI_API_KEY="sk-..."; $env:OPENAI_MODEL="gpt-4o"; .\gradlew test
```

**통합 테스트 항목**

| 테스트 | 내용 |
|---|---|
| `testConnection - 유효한 키로 연결 성공` | 정상 키로 연결 확인 |
| `testConnection - 잘못된 키는 401 예외` | 잘못된 키에 대한 에러 처리 |
| `testConnection - 빈 API 키는 즉시 예외` | 입력 검증 |
| `generate - 샘플 diff로 커밋 메시지 생성` | 실제 커밋 메시지 생성 (요청/응답 전문 로그 출력) |

### 빌드 오류 안내

#### 우리 코드 문제가 아닌 오류

**첫 빌드가 매우 느린 경우**

```
> Task :downloadProductFile
```

IntelliJ Platform SDK를 처음 다운로드하는 중입니다. 수백 MB로 네트워크 상태에 따라
5~15분 걸릴 수 있습니다. 정상이며 이후 빌드는 캐시되어 빠릅니다.

**`Could not resolve` / 의존성 다운로드 실패**

```
Could not resolve com.squareup.okhttp3:okhttp:4.x.x
```

네트워크 또는 Maven Central 연결 문제입니다. 재시도하거나 VPN/프록시 설정을 확인하세요.

```powershell
.\gradlew buildPlugin --refresh-dependencies
```

**`SSL handshake failed` / 인증서 오류**

회사 내부망 또는 프록시 환경에서 발생합니다. JDK 신뢰 저장소 또는 프록시 설정을 확인하세요.
`gradle.properties`에 프록시를 설정할 수 있습니다:

```properties
systemProp.https.proxyHost=proxy.example.com
systemProp.https.proxyPort=8080
```

**`runIde` 실행 후 샌드박스 IDE가 흰 화면에서 멈추는 경우**

샌드박스 IDE 초기 인덱싱 중입니다. 1~2분 대기하면 정상 로드됩니다.

**`Kotlin daemon process` 관련 경고**

```
w: Kotlin daemon process is not responsive
```

Gradle 빌드를 재실행하면 대부분 해결됩니다. Daemon 캐시를 초기화하려면:

```powershell
.\gradlew --stop
.\gradlew buildPlugin
```

#### 우리 코드 문제인 오류

컴파일 오류, `plugin.xml` 관련 오류, 테스트 실패는 코드 변경 후 발생한 경우 이 저장소의
이슈 트래커에 보고해 주세요.

---

## 라이선스

Released under [MIT](LICENSE) by [@icemokacat](https://github.com/icemokacat).

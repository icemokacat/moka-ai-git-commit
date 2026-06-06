# skill: build

## 언제 사용하나

Plugin 빌드, 로컬 테스트 IDE 실행, 배포 ZIP 생성, JetBrains Marketplace 배포가 필요할 때.

## 환경

OS에 따라 Gradle 명령어 형식이 다릅니다. 처음 빌드를 실행하기 전에 사용자 환경을 확인하세요.

| OS | 명령어 형식 |
|---|---|
| Windows (PowerShell) | `.\gradlew <task>` |
| macOS / Linux (bash/zsh) | `./gradlew <task>` |

## 명령어

### 로컬 개발 (가장 자주 사용)

```
# Windows PowerShell
.\gradlew runIde        # 테스트 IDE 인스턴스 실행
.\gradlew build         # 빌드만 (컴파일 확인)
.\gradlew test          # 테스트 실행
.\gradlew verifyPlugin  # plugin.xml 유효성 + 바이너리 호환성 검사

# macOS / Linux
./gradlew runIde
./gradlew build
./gradlew test
./gradlew verifyPlugin
```

### 배포 준비

```
# 배포용 ZIP 생성 -> build/distributions/*.zip
# Windows:  .\gradlew buildPlugin
# macOS/Linux: ./gradlew buildPlugin

# Marketplace 배포 — 환경변수 설정 방법이 OS마다 다름
# Windows PowerShell:
$env:PUBLISH_TOKEN = "your_token"; .\gradlew publishPlugin

# macOS / Linux:
PUBLISH_TOKEN=your_token ./gradlew publishPlugin
```

### 문제 해결

```
# Gradle 캐시 초기화 후 클린 빌드
# Windows:   .\gradlew clean build
# macOS/Linux: ./gradlew clean build

# IDE 플랫폼 인덱스 재다운로드
# Windows:   .\gradlew --refresh-dependencies build
# macOS/Linux: ./gradlew --refresh-dependencies build
```

## 환경 요구사항

- JDK 21 이상 (IntelliJ 2024.2+ 요구사항)
- Gradle Wrapper 사용 — 시스템 Gradle 사용 금지

## 로그 위치

- 테스트 IDE 로그: `build/idea-sandbox/system/log/idea.log`
- 플러그인 로그: IntelliJ -> Help -> Show Log in Explorer

## 배포 전 체크리스트

- [ ] `gradle.properties`의 `pluginVersion` 업데이트
- [ ] `plugin.xml`의 `<change-notes>` 업데이트
- [ ] `.\gradlew verifyPlugin` (Windows) / `./gradlew verifyPlugin` (macOS/Linux) 통과
- [ ] `.\gradlew test` (Windows) / `./gradlew test` (macOS/Linux) 통과
- [ ] `sinceBuild` / `untilBuild` 범위 확인

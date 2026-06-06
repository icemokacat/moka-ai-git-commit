# skill: build

## When to Use

Plugin 빌드, 로컬 테스트 IDE 실행, 배포 ZIP 생성, JetBrains Marketplace 배포가 필요할 때.

## Commands

### 로컬 개발 (가장 자주 사용)

```bash
# 테스트 IDE 인스턴스 실행 (플러그인 자동 로드)
./gradlew runIde

# 빌드만 (IDE 실행 없이 컴파일 확인)
./gradlew build

# 테스트 실행
./gradlew test

# plugin.xml 유효성 + 바이너리 호환성 검사
./gradlew verifyPlugin
```

### 배포 준비

```bash
# 배포용 ZIP 생성 -> build/distributions/*.zip
./gradlew buildPlugin

# Marketplace 배포 (PUBLISH_TOKEN 환경변수 필요)
PUBLISH_TOKEN=your_token ./gradlew publishPlugin
```

### 문제 해결

```bash
# Gradle 캐시 초기화 후 클린 빌드
./gradlew clean build

# IDE 플랫폼 인덱스 재다운로드
./gradlew --refresh-dependencies build
```

## 환경 요구사항

- JDK 21 이상 (IntelliJ 2024.2+ 요구사항)
- Gradle Wrapper 사용 (`./gradlew`) — 시스템 Gradle 사용 금지
- Windows: PowerShell에서 `.\gradlew build`

## 로그 위치

- 테스트 IDE 로그: `build/idea-sandbox/system/log/idea.log`
- 플러그인 로그: IntelliJ -> Help -> Show Log in Explorer

## 배포 전 체크리스트

- [ ] `gradle.properties`의 `pluginVersion` 업데이트
- [ ] `plugin.xml`의 `<change-notes>` 업데이트
- [ ] `./gradlew verifyPlugin` 통과
- [ ] `./gradlew test` 통과
- [ ] `sinceBuild` / `untilBuild` 범위 확인

# Moka Git AI Commit

OpenAI API를 사용해 git 커밋 메시지를 자동으로 생성하는 IntelliJ IDEA 플러그인.

스테이징된 변경사항을 읽어 직접 작성한 프롬프트와 결합한 뒤 커밋 메시지 필드를 채워줌 — 내장 프롬프트나 기본값 없음, 프롬프트는 온전히 사용자 몫.

## 기능

- 커밋 다이얼로그에서 클릭 한 번으로 커밋 메시지 생성
- 완전한 사용자 정의 프롬프트 템플릿 (형식, 언어, 스타일을 직접 결정)
- 모든 OpenAI 호환 엔드포인트 지원 (OpenAI, Azure OpenAI, 로컬 Ollama 등)
- API 키는 시스템 키체인에 저장 (plain text 저장 아님)
- 토큰 사용량 조절을 위한 diff 크기 제한 설정 가능

## 요구사항

- IntelliJ IDEA 2024.2 이상 (Community 또는 Ultimate)
- OpenAI API 키 (또는 호환 엔드포인트)
- 소스에서 빌드 시 JDK 21 이상

## 설치

### JetBrains Marketplace에서

1. IntelliJ IDEA 실행
2. Settings → Plugins → Marketplace 이동
3. "Moka Git AI Commit" 검색
4. 설치 후 재시작

### ZIP 직접 설치

1. Releases에서 최신 ZIP 다운로드
2. Settings → Plugins → 톱니바퀴 아이콘 → Install Plugin from Disk
3. ZIP 파일 선택 후 재시작

## 설정

1. Settings → Tools → Moka Git AI Commit 열기
2. OpenAI API 키 입력
3. 텍스트 영역에 프롬프트 템플릿 작성

### 프롬프트 템플릿

프롬프트 템플릿은 완전히 사용자 것. 플러그인은 프롬프트 뒤에 git diff를 추가해서 전송함.

예시:
```
Write a concise git commit message in Korean.
Use conventional commit format: type(scope): description
Keep it under 72 characters.

Changes:
```

모델에게 원하는 언어, 형식, 스타일을 자유롭게 지시할 수 있음.

### 커스텀 엔드포인트

Azure OpenAI 또는 로컬 모델(예: Ollama) 사용 시:
1. Base URL을 해당 엔드포인트로 설정
2. Model을 엔드포인트가 요구하는 모델명으로 설정

## 사용법

1. git에서 변경사항 스테이징
2. 커밋 다이얼로그 열기 (Ctrl+K / Cmd+K)
3. **Generate AI Commit Message** 클릭 또는 Ctrl+Alt+C 입력
4. 생성된 메시지 검토 및 수정
5. 커밋

## 소스에서 빌드

```bash
git clone https://github.com/icemokacat/moka-git-ai-commit
cd moka-git-ai-commit

# macOS / Linux
./gradlew runIde          # 테스트 IDE 인스턴스 실행
./gradlew buildPlugin     # 배포용 ZIP 빌드

# Windows (PowerShell)
.\gradlew runIde
.\gradlew buildPlugin
```

아키텍처 설명은 `CLAUDE.md`, 빌드 상세는 `.claude/skills/build.md` 참조.

## 라이선스

MIT

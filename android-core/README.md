# chrono-core

크로노 퀘스트 네이티브 코어 Gradle 프로젝트. 지금은 `rules` 모듈(순수 Kotlin 판정 엔진)만 있다.

## 준비

- JDK 21: Eclipse Temurin 21을 `~/Library/Java/JavaVirtualMachines/temurin-21.jdk`에 설치한다 (Intel macOS 26에서는 Homebrew openjdk 병 파일이 없어 소스 빌드가 필요하다).

## 테스트

```bash
cd android-core
./gradlew :rules:test
```

설계: `../docs/superpowers/specs/2026-11-25-chrono-quest-design.md`

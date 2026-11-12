# STORY ENGLISH 3.7.1 — 오프라인 Android 프로토타입

**이 프로젝트는 원본 STORY ENGLISH 3.7.0의 소스나 그 빌드가 아닙니다.** 원본 프로젝트 ZIP이 현 세션에 공급되지 않아, 검증 가능한 최소 기능을 **독립 앱**으로 구현한 것입니다.

- 앱 패키지: `com.storyenglish.prototype` (원본 `com.storyenglish.app`을 덮어쓰지 않음)
- UI: Kotlin + Jetpack Compose + Material3, 네이티브 Android
- 오프라인: 서버·API 키·인터넷 권한 없음
- 비노골적 성인 영어 자체 작성 학습 예시 `The Boundary`, 7개 대화/영한 자막, TTS 청취, 표현 3개, 영어 이해 퀴즈, 문장 연습, 복습 카드
- 시각 콘텐츠는 **정적 스토리보드**임. 원본 비디오/Media3 플레이어와 동일하지 않음
- `MATURE`: 데모용 19+ 자기확인, 법적 연령 확인 수단 아님
- `EXPLICIT`: 차단, 학습 콘텐츠 없음
- 광고·분석·로그인·원격 AI 없음. 자동 생성 음성은 Android 시스템 TTS 사용.
- 진행상태 일부는 앱 로컬 기본 저장소에만 저장됨

## GitHub Actions

`.github/workflows/story-english-prototype-apk.yml`이 `story-english-build` 브랜치의 프로토타입 디렉터리 변경에 반응해 Debug APK를 빌드합니다.

```text
story-english/prototype/android/
  settings.gradle.kts
  build.gradle.kts
  gradle.properties
  app/build.gradle.kts
  app/src/main/AndroidManifest.xml
  app/src/main/java/com/storyenglish/prototype/...
  app/src/test/java/com/storyenglish/prototype/...
```

AGP 8.9.2 / Gradle 8.11.1 / Kotlin Compose 2.1.20 / JDK 17 / Android API 35를 적용했습니다. 이는 **프로토타입 독립 툴체인**이며, 기존 3.7.0의 SDK 37 설정을 재현한 것이 아닙니다.

## 배포 금지 오해

본 APK의 빌드가 성공해도 원본 STORY ENGLISH의 3.7.1 통합, 운영 백엔드 연동, 실제 AI, 성인물 권리 관리, 동영상 플레이어, 모든 24편과 5/6개 성인 모듈 구현, 사람의 콘텐츠 승인 또는 운영 배포 적합성이 증명되지 않습니다.

이 저장소는 공개 저장소입니다. 원본 앱 소스 및 제3자 미디어, 자격증명, 개인정보 등은 검토 없이 업로드하지 마십시오.

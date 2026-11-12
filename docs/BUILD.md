# Windows 11, JDK21, Gradle 9.8.0: Android 빌드 및 설치

### 전제 조건

- Windows 11, JDK 21; `JAVA_HOME`으로 JDK 21 지정
- Gradle 9.8.0 설치 후 PATH에 `gradle` 등록 (Wrapper 파일은 포함되지 않음)
- Android SDK Platform 36, SDK Build Tools 36.0.0, Android SDK Platform Tools(ADB), SDK 경로 `ANDROID_HOME` 또는 `local.properties`의 `sdk.dir`
- 인터넷 연결이 있는 초기 빌드는 AGP 9.5.0을 Google Maven/Gradle repositories에서 다운로드해야 할 수 있음
- Android compileSdk 36 / minSdk 26 / targetSdk 35. Java 소스 target 17

AGP 9.5.0 공식 최소 요구는 Gradle 9.6.0 및 JDK 17입니다. 따라서 기존 JDK 21과 Gradle 9.8.0은 최소 버전 요구를 충족합니다. 이 관계는 **빌드 성공 검증**과 다릅니다.

### 빌드

1. ZIP 압축을 풉니다. 프로젝트 루트(`GalaxyUniversalIO_M0`)로 이동합니다.
2. `java -version`, `javac -version`, `gradle --version`, `echo %ANDROID_HOME%`으로 환경을 확인합니다.
3. `build-debug-windows.bat`를 실행합니다. 이 배치 파일은 순수 Java 및 모의 USB 테스트 3개를 거쳐 `gradle :app:assembleDebug`를 호출합니다.
4. 실제 빌드가 성공했다면 `app\build\outputs\apk\debug\app-debug.apk`를 확인하고 SHA-256을 기록합니다.
5. Galaxy S20 Ultra의 USB 디버깅을 사용하여 `verify-galaxy-windows.bat`를 실행합니다. 연결된 ADB 장치가 한 대인 상태여야 합니다. 사용자의 승인 없이 다른 기기 설치를 시도하지 마세요.
6. 실제 OTG 장치 테스트는 `docs/M0_3_SCOPE_AND_HARDWARE_TEST.md`를 보고 진행합니다.

### 수동 명령

```bat
call tests\run-host-tests.bat
call tests\run-printer-transport-tests.bat
call tests\run-imaging-pdf-tests.bat
gradle --no-daemon :app:assembleDebug --stacktrace
adb devices -l
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

### 자주 발생하는 오류

- SDK path not found → Android SDK 경로 확인
- AGP plugin download fails → 외부 저장소 접속 및 방화벽 설정 확인
- USB device not found → USB Host/OTG, 케이블, 전원 및 USB 권한 확인
- PDF 인쇄 실패 → 인쇄 서비스 제공자 설치, PDF 데이터 유효성 및 페이지 범위 확인
- PTP JPEG import 실패 → PTP Still Image Class 6/1/1, 사진 포맷/16 MiB 제한/인터페이스 점유 확인. USB MTP 단독 장치가 지원된다는 뜻은 아님

이 배포본에는 APK가 없습니다. 위 명령은 Windows에서 실행할 절차이며 이 실행 환경에서 성공한 것처럼 해석하면 안 됩니다.

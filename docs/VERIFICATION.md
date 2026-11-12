> **Historical M0.3 reference** — For the latest M0.5 status, read `docs/M0_5_VERIFICATION.md` and `docs/M0_5_CHANGELOG.md`.

# M0.3 개발 검증 기록 — 2026-11-12

| 항목 | 직접 확인한 결과 | 범위/제약 |
|---|---|---|
| M0.2 소스 ZIP 압축 오류 검사 | PASS | CRC 검사만; 실행 아님 |
| Java 순수 호스트 테스트 | PASS, 55개 | JDK 21 실행, Java 17 컴파일 타깃 |
| USB Printer Class 모의 전송 | PASS, 19개 | 가상 장치 |
| USB PTP + PDF 전처리 | PASS, 29개 | 독립적인 순수 Java 함수 검증 |
| PTP USB 가상 전송 | PASS, 18개 | 가상 연결 응답, 실물 PTP 아님 |
| 전체 앱 Java API-시그니처 모의 컴파일 | PASS, Java 10개 | 손으로 작성한 Android stub 42개, Android SDK 아님 |
| Gradle 버전 최소 요구 조건 | 호환 관계 확인 | 공식 AGP 9.5 최소 Gradle 9.6.0, JDK 17, Build Tools 36.0.0 |
| 실제 Android SDK Gradle assembleDebug | **실행 불가** | Android SDK와 Gradle 설치본 없음, 외부 DNS 접속 실패 |
| APK 설치 및 실행 | **미실행** | APK 생성 안 됨, Galaxy S20 Ultra 없음 |
| USB 프린터 실제 인쇄 성공 | **미실행** | 실물 장치 및 인쇄 언어 검증 자료 없음 |
| 일반 USB 문서 스캐너 이미지 획득 | **미구현** | PTP JPEG input과 구분 필요 |
| USB PTP JPEG 실물 입력 | **미실행** | 실물 PTP 장치 없음 |
| Android Print Service를 통한 PDF 실제 출력 | **미실행** | 설치된 서비스/프린터 없음 |
| 호환 장치 실증 DB | **0개 모델 검증** | 데이터 구조만 존재 |

총 121개 JDK 기반 검사 통과는 **코드의 일부 로직·모의 인터페이스 테스트**일 뿐, Android OS에서 빌드하거나 USB 장치가 동작함을 입증하지 않습니다. 모든 실물 성공 증거는 기종, 펌웨어, 빌드 버전, USB VID/PID, 결과와 로그를 기록해야 합니다.

## 재현 명령

```bat
call tests\run-host-tests.bat
call tests\run-printer-transport-tests.bat
call tests\run-imaging-pdf-tests.bat
call build-debug-windows.bat
call verify-galaxy-windows.bat
```

`tests/run-android-signature-smoke.py`는 Python 3이 있다면 추가 확인할 수 있으나, 실제 Android SDK 컴파일을 대신하지 않습니다.

## 근거 자료

- https://developer.android.com/build/releases/agp-9-5-0-release-notes
- https://gradle.org/releases/
- https://developer.android.com/reference/android/hardware/usb/UsbDeviceConnection
- https://developer.android.com/training/printing/custom-docs
- https://learn.microsoft.com/en-us/windows-hardware/drivers/image/ptp-required-commands

> **Historical M0.3 reference** — For the latest M0.5 status, read `docs/M0_5_VERIFICATION.md` and `docs/M0_5_CHANGELOG.md`.

# M0.3 검증 범위 및 실물 테스트 계획

## 기능 계층

| 기능 | 소스 | 실제 SDK APK 빌드 | 실물 검증 |
|---|---|---|---|
| USB 장치 목록·권한·인터페이스 진단 | 구현 | 미실행 | 미실행 |
| USB 프린터 1/2 프로토콜 상태 조회·RAW 전송 | 구현 | 미실행 | 미실행 |
| USB IPP-over-USB 프로토콜 4 식별만 | 구현 | 미실행 | 미실행 |
| Android PDF 시스템 인쇄(설치된 Print Service 이용) | 구현 | 미실행 | 미실행 |
| USB PTP 6/1/1 Still Image JPEG 첫 번째 파일 가져오기 | 구현 | 미실행 | 미실행 |
| 모든 평판 스캐너의 문서 스캔 | 미구현 | 해당 없음 | 해당 없음 |
| HWP/HWPX/PDF 원본을 USB RAW 프린터 언어로 변환 | 미구현 | 해당 없음 | 해당 없음 |
| USB IPP-over-USB 통신, eSCL/AirScan 프로토콜 | 미구현 | 해당 없음 | 해당 없음 |
| VID/PID 실증 호환 DB | 구조만 있음(검증 0대) | 해당 없음 | 해당 없음 |

## 기술적 구분

- PTP Still Image Class (06/01/01)는 주로 디지털 카메라·카메라 유사 장치용이며, 모든 문서 스캐너가 사용하는 프로토콜이 **아닙니다**. 구현 기능은 첫 16개 객체 중 최대 16MiB JPEG 1개를 읽는 것에 한정합니다. 원본 장치 데이터는 변경하지 않습니다.
- Android Print Framework는 별도의 프린트 서비스에 출력 작업을 넘깁니다. OS 인쇄 UI 호출이나 스풀 데이터 작성 성공을 실제 종이 출력으로 판정하지 않습니다. 일부 Print Services는 USB를 지원하지 않고 Wi-Fi만 지원할 수 있습니다.
- Protocol 4 IPP-over-USB 장치는 인터페이스로 분류하지만 직접 IPP 요청/응답·HTTP 연결은 **구현하지 않았습니다**.
- USB Printer RAW는 프린터 PDL과 이미 호환되는 PRN/PCL/PostScript 파일만 전송합니다. 모든 .ps 확장자가 모든 프린터에 적합한 것은 아닙니다.
- 이 실행 환경에는 Gradle, Android SDK, 연결된 Galaxy S20 Ultra 및 실제 장치가 없었습니다. 다른 환경에서 각 테스트를 반드시 수행해야 합니다.

## Galaxy S20 Ultra 실물 체크리스트

1. Windows: `build-debug-windows.bat` 로 **실제** assembleDebug 성공을 확인하고 `certutil -hashfile app\build\outputs\apk\debug\app-debug.apk SHA256` 기록.
2. Galaxy Android 13, OTG 어댑터 및 외부 전원(필요시) 준비. USB 디버깅/ADB로 APK 설치할 때와 실제 OTG 테스트 연결을 혼동하지 말 것.
3. 앱 시작 → USB 장치 연결·분리 반복 → 목록 변경이 표시되는지 확인. 화면 회전 및 백그라운드 전환 후 수동 새로고침 확인.
4. USB 권한 요청 → 승인/거부 및 재연결 테스트. VID/PID/interface/endpoint 보고서 저장 → 장치 시리얼 기본 미포함 확인.
5. USB Printer 7/1/1 또는 7/1/2: 상태 조회 후, 해당 프린터의 **지원 인쇄 언어 확인**을 전제로 적합한 1페이지 RAW 작업을 수동 전송. 실제 출력 여부를 기록.
6. 프로토콜 7/1/4 장치: 인식은 되지만 RAW 버튼이 비활성화되는지 확인.
7. 표준 PTP Still Image(6/1/1) 장치: 저장 위치 선택, JPEG를 가져오고 원본과 크기/이미지 표시를 확인. 16 MiB 초과, 연결 해제, 이미지 없는 장치 테스트.
8. 시스템 PDF 인쇄: Android 인쇄 대화상자 → 설치된 프린터 서비스·장치 선택 → 물리 출력 후 성공 여부 기록. 부분 페이지만 선택하면 지원하지 않는다는 오류를 보여야 함.
9. USB 프린터, 문서 스캐너 **각 실제 모델명/VID/PID**와 관찰 결과를 `docs/DEVICE_TEST_RECORD.csv`에 기입.
10. 과도한 전력 소모, USB 케이블 분리, 접근 거부, 알 수 없는 장치, RAW 데이터 오류 테스트 후 앱 재시작으로 정상 동작 확인.

## 실물 장치 데이터 보안

USB 장치명·VID/PID·경로는 내부 환경 정보를 드러낼 수 있습니다. 기본적으로 시리얼은 출력하지 않습니다. 보고서를 외부에 공유하기 전에 민감 정보가 없는지 확인하세요.

## 참고 근거

- Android USB Host: https://developer.android.com/develop/connectivity/usb/host
- Android Print framework: https://developer.android.com/training/printing/custom-docs
- PTP required commands: https://learn.microsoft.com/en-us/windows-hardware/drivers/image/ptp-required-commands
- AGP 9.5 compatibility: https://developer.android.com/build/releases/agp-9-5-0-release-notes

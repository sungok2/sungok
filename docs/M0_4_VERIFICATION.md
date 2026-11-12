# Galaxy Universal I/O M0.4 — 검증 요약 (2026-11-12)

## 검증됨 (실제 실행 결과)

- 원본 M0.3 ZIP 정상 추출 및 보존.
- JDK 21 `javac --release 17` 기반 호스트 로직 55개 검사 통과.
- USB 프린터 모의 전송 19개 검사 통과.
- PTP/PDF 로직 29개 검사 통과.
- PTP 모의 전송 18개 검사 통과.
- 신규 eSCL 프로토콜, 입력 경계값, LAN 주소 제한, 가상 USB HTTP 프레이밍, 스캔 작업 시퀀스 58개 검사 통과.
- 신규 IPP/1.1 바이너리 요청·응답, PDF MIME 지원 사전조회, USB PDF Job 수락/거부·전송 억제 26개 검사 통과.
- 신규 PC 사설 IP를 사용하는 모의 eSCL HTTP 서버에서 실제 TCP 요청(평판 1장/ADF 2장, JPEG 수신) 연동 통과.
- Java 앱 소스 17개 파일이 손으로 구현한 52개 Android API 시그니처와 함께 `javac --release 17` 테스트 컴파일 통과. **정식 Android SDK 빌드와는 다릅니다.**
- XML / JSON 구문 검사, ZIP 압축 CRC와 추출 내용 일치 확인 (배포 절차에서 재확인).

## 검증되지 않음

- Android Gradle Plugin 9.5.0과 Android SDK를 통한 `:app:assembleDebug`, APK 설치 및 런타임 동작.
- Galaxy S20 Ultra(Android 13) USB OTG·프린터·문서 스캐너의 실제 연결 및 출력.
- 임의의 eSCL/IPP-over-USB 기종의 실제 호환성. 모의 HTTP 서버는 실물이 아닙니다.
- 인쇄 결과, 스캔 색/해상도/용지/ADF 연속 페이지 정확도.
- 제조사 독점 스캐너 지원, IPP 인쇄, 모든 문서 형식 호환성.

**판정:** 소스·테스트·문서 배포 후보 완료. 전체 범용 하드웨어 제품/실물 검증 완료로 표시하지 않음.

## 확인하지 못한 빌드 이유

실행 컨테이너에는 JDK 21만 있고 Gradle 실행 파일과 Android SDK가 없으며 외부 다운로드가 원활하지 않음. 실제 Gradle 빌드 명령은 실행할 수 없음. Windows 빌드 스크립트는 작성했으나 사용자 PC 실행 결과는 아직 받지 못함.

## 재현/입증 순서

1. Windows에서 `build-debug-windows.bat`, 전체 로그 저장, `app-debug.apk` 생성 확인.
2. `adb shell getprop ro.build.version.release`와 `adb install -r` 성공 확인.
3. PC eSCL 모의 서버와 같은 Wi-Fi에서 앱 LAN eSCL → PDF 1장/2장 저장 및 파일 열기.
4. 실제 eSCL 지원 LAN 스캐너에서 기종·펌웨어·오류 기록.
5. IPP-over-USB 장치를 연결하고 USB 권한 승인, USB eSCL PDF 확인.
6. RAW/시스템 PDF/USB PTP 기존 기능별 시험.
7. 각 테스트를 `docs/DEVICE_TEST_RECORD.csv`에 증거와 함께 기록. **미검증을 성공으로 기록하지 않음.**

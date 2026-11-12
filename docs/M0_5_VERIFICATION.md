> **이전 버전 M0.5 기록** — 현재 상태는 `docs/M0_6_VERIFICATION.md` 참고.

# Galaxy Universal I/O M0.5 기술 검증 보고서

작성일: 2026-11-12

## 1. 검증한 사실

- 입력 소스: 이전 사용자 제공 `GalaxyUniversalIO_M0_4_Final_Source_Candidate.zip` (73 entries, ZIP CRC 검사 통과)
- JDK: `openjdk version "21.0.12.1"` 확인
- Java 앱 파일: 17개
- 테스트 결과: 호스트 55, 가상 Printer 전송 19, PTP/PDF 29 + 가상 PTP 18, eSCL·USB/HTTPS URI 79, USB IPP 26 = **총 226/226 PASS**
- **Android API 가짜 클래스 52개 + 앱 Java 17개**의 `javac --release 17` 컴파일 통과.
- 사설 TCP 모의 스캐너 연동: JPEG/PNG의 평판 각 1페이지, ADF 각 2페이지 수신 성공. 4개 시나리오 통과. 실제 TCP 전송을 사용하지만 서버 자체는 테스트용 가상 장치.
- XML 및 설정의 입력원 독립 프로파일링, MIME 협상, PNG CRC 검사를 JDK 테스트로 검증.
- ADF 정확히 12장 수용, 13장일 때 결과 PDF 미저장 로직을 가상 응답 테스트로 검증.

## 2. 검증하지 못한 것

- Gradle 9.8.0 실행과 AGP 9.5.0을 통한 실제 Android 소스 빌드: 실행 환경에 `gradle` 없음.
- Android SDK Platform 36 및 Build Tools, `sdkmanager`, `adb`: 실행 환경에 없음.
- HTTPS eSCL 서버와 실제 TLS 핸드셰이크/인증서 검증의 장치별 성공: 미검증. HTTPS는 `HttpURLConnection`의 HTTPS 구현을 사용해 OS 기본 인증서·호스트 검증 정책을 그대로 적용하는 코드.
- APK 컴파일, 서명, Galaxy S20 Ultra 설치/실행, USB OTG/전원/권한 실측: 미실시.
- 실제 스캐너의 flatbed/ADF 인식, 실제 스캔 PDF 품질, 프린터 종이 출력, 제조사 고유 프로토콜 호환성: **0종 검증**.
- OCR, mDNS 자동 발견, 자동 이미지 보정, TLS 자체서명 서버 접속, 제조사 전용 스캐너 드라이버: 미구현.

## 3. 위험 요소와 권장 시험 순서

1. 사용자 Windows 11에서 JDK 21, Gradle 9.8.0, Android SDK 36 설치 후 `build-debug-windows.bat`을 실행하고 **실제 최초 컴파일 오류**를 기록.
2. 디버그 APK 생성 성공 시 SHA-256 기록, Galaxy S20 Ultra Android 13에 `adb install -r`로 설치한 다음 USB 권한/연결·분리를 검증.
3. 같은 Wi-Fi의 사용자 관리 테스트 PC에서 `mock-escl-scanner.py`로 JPEG/PNG 스캔 기능을 검증.
4. 테스트용 실물 eSCL 복합기에 대해 평판 1장, ADF 2·12장, 비지원 형식, 작업 취소 및 권한 분리 케이스를 기록.
5. USB IPP-over-USB/Printer RAW 전송 및 PTP는 **실물 기종별로** 시험. 전송 성공만으로 종이 출력·이미지 품질은 확정 불가.

## 4. 최종 판정

**M0.5 소스와 모의 시험은 통과. APK 빌드·단말 설치·실물 호환성 인증은 미실시이므로 완제품으로 판정할 수 없음.**

참고 프로토콜 구현 및 문서:
- https://github.com/alexpevzner/sane-airscan/blob/master/airscan-escl.c
- https://chromium.googlesource.com/chromiumos/third_party/virtual-usb-printer/+/220c226dfa084717b7fbe1caec13494fa0729681/escl_manager_test.cc
- https://developer.android.com/develop/connectivity/usb/host
- https://developer.android.com/reference/android/net/http/HttpResponseCache

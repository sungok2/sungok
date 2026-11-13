# Galaxy Universal I/O M0.10 — NTFS SAF MASTER Hardening (2026-11-13)

- 루팅 없이 NTFS USB 쓰기 지원: **외부 NTFS 읽기/쓰기 DocumentsProvider가 노출한 폴더에서만 가능**. OS의 NTFS 읽기 전용을 직접 해제하지 않음.
- Windows 파일명 검증, SHA-256 전량 재읽기 검증, 기존 이름 덮어쓰기 차단과 외부 변경 경합 차단 시도.
- 파일별 SHA-256 검증 보고서 TXT 저장. 실패 시 사용자 확인식 잔여 임시 파일 복구; 작업 재개와 자체 USB NTFS 드라이버는 미구현.
- 상세: docs/M0_10_HARDENING.md

---

# Galaxy Universal I/O — M0.9 NTFS USB 파일 전송 (SAF 호환)

**작동 범위:** 본 앱에서 파일 선택 → 외부 DocumentsProvider의 대상 폴더 선택 → USB에 임시 파일 생성 → SHA-256 읽기 재검증 → 원래 이름으로 변경. 루팅은 필요하지 않습니다.

**필수 조건:** OTG Master처럼 **NTFS를 읽기·쓰기로 연결하고 Android DocumentsProvider로 노출하는 별도 앱**이 필요합니다. 삼성 기본 저장소가 NTFS를 읽기 전용으로만 노출하는 경우 이 기능만으로 권한을 우회하거나 쓰기가 활성화되지는 않습니다. 이는 직접 NTFS 드라이버를 통합한 버전이 아닙니다.

- 앱 상단의 **USB 저장장치 복사 (NTFS / 외부 제공자)** 선택
- 복사할 파일(최대 128개) 선택
- Android 폴더 선택기에서 NTFS 공급자의 쓰기 가능한 대상 폴더 선택
- 같은 이름의 기존 파일이 있으면 중단; 원본을 삭제하거나 덮어쓰지 않음
- 취소·분리·쓰기 오류 시 임시 파일 삭제 시도; 실패하면 흔적이 남을 수 있음
- 파일별 복사 후 대상 읽기 재검증 및 SHA-256/크기 확인
- **디버그 APK 빌드 및 실기기 검증 여부는 GitHub Actions 결과와 별도로 확인해야 함**

개발 참고와 제한: [docs/M0_9_NTFS_SAF.md](docs/M0_9_NTFS_SAF.md).

---

# Galaxy Universal I/O — M0.8 Android runtime QA build

- Target: Galaxy S20 Ultra / Android 13; developer: Windows 11 / JDK 21 / Gradle 9.8.0.
- M0.8 adds a visible scan cancellation request, guards against concurrent scan requests, makes scan and printer controls full-width on portrait screens, corrects stale report versions, and adds an Android 13 emulator launch/screenshot CI smoke test.
- Cancellation is **not guaranteed immediate** during an Android USB `bulkTransfer()` or HTTP read timeout. The app requests interruption and prevents partial PDF export when interrupted before the final copy. Android's document picker may create a blank destination even when a scan fails or is cancelled.
- APK build success and emulator activity launch are separate from real hardware interoperability.
- **Not supported universally:** vendor-specific USB scanner protocols, TWAIN/WIA-only scanners, all printer drivers, OCR, duplex ADF, mDNS, and every printer document language.
- Existing eSCL scan limits remain 12 ADF pages / 32 MiB per job and 8 MiB per image. PDFs may downsample to approximately 3 megapixels per page. A large document should be split into multiple scans.
- The CI workflow is implemented in `.github/workflows/build-android-apk.yml`; `tests/ci-emulator-smoke.sh` checks installation, Activity startup, process survival and captures UI screenshots. Only actual workflow results demonstrate runtime success.

---

# Galaxy Universal I/O — M0.7 USB 통신 및 CI 보강 소스판

- 작업일: 2026-11-12
- 개발대상: Galaxy S20 Ultra / Android 13, Windows 11 / JDK 21 / Gradle 9.8.0
- 산출물: **Android Java 전체 소스 ZIP. APK가 아닙니다.**
- M0.6에서 이어받아 USB HTTP 요청 사전 검사, 실패 연결 폐기, 스캔 중단 신호 확인 및 GitHub Actions 수동 APK 빌드 워크플로를 추가했습니다.

## M0.7 추가사항

1. `UsbHttpRequestPolicy`: USB 요청은 **전송 전에** 메서드/경로/Content-Type/요청·응답 크기를 검증하며 CRLF, 경로 이탈, 잘못된 HTTP 요청을 거부합니다.
2. USB 통신 오류 후 **연결 폐기**: 부분 전송·응답 파싱 오류가 발생한 연결은 재사용하지 않습니다. `Connection: close`도 반영합니다.
3. HTTP 메시지 검사: 압축/중복 `Content-Encoding`, 금지된 Chunked Trailer, 비표준 연결 모드를 차단합니다. JPEG뿐 아니라 PNG 수신을 Accept 헤더에 명시합니다.
4. eSCL 작업 취소: 스캔 스레드의 interruption 상태를 작업 시작과 페이지 사이에서 검사합니다. Android UI 취소 버튼은 아직 없으며, 물리 USB I/O 대기 중 즉시 종료를 보장하지 않습니다.
5. `.github/workflows/build-android-apk.yml`: **GitHub Actions 수동 실행**으로 JDK 21·Gradle 9.8.0·Android SDK 36 설치와 자동검사, 디버그 APK 빌드·아티팩트 게시를 시도합니다. **실제 GitHub Actions 빌드는 미실행**입니다.
6. JDK 기반 자동 검사 **316개 통과**, Android API 모의 컴파일 **20개 소스 통과**. 실물 스캐너/프린터 검증 0대, SDK/Gradle 실제 빌드 미실시.

호스트 검사는 `verify-host-linux.sh` 또는 `verify-host-windows.bat`으로 한 번에 실행할 수 있습니다.

GitHub 절차 및 제한사항: `docs/M0_7_CI.md`. 상세 보고서: `docs/M0_7_VERIFICATION.md`. 변경내역: `docs/M0_7_CHANGELOG.md`.

---

# 과거 M0.6 기능 요약 (참고용)

- 작성 및 검증: 2026-11-12
- 대상 단말: Samsung Galaxy S20 Ultra (Android 13)
- 개발 환경: Windows 11 / **JDK 21** / **설치형 Gradle 9.8.0** / Android Gradle Plugin 9.5.0 / compileSdk 36 / targetSdk 35
- **제공 내용은 Android Java 전체 소스이며 APK가 아닙니다.** 이번 환경에는 Android SDK, Gradle 실행 파일, 실제 USB 장치가 없으므로 **APK 빌드·갤럭시 설치·실물 스캔·실물 인쇄는 검증되지 않았습니다.**


## M0.6 신규 보강 (M0.5 대비)

1. **USB HTTP/1.x 응답 프레이밍 보강**: `Content-Length`와 `Transfer-Encoding` 중복·충돌을 거부하고, 헤더 제어문자/잘못된 길이/204 응답의 불법 본문을 검사합니다. USB 스트림 동기화 오류의 위험을 낮춥니다.
2. **ADF HTTP 204 종료 응답 처리**: ADF에서 최소 한 장을 수신한 경우 204(No Content)를 종료로 처리합니다. 빈 ADF는 실패로 판정하고, `DELETE` 정리를 유지합니다.
3. **PDF 사전 생성·임시 파일 정리**: 스캔 PDF를 앱 전용 캐시에 완성한 후 문서 제공자의 출력 스트림을 엽니다. PDF 렌더링 실패 시 대상 파일 열기를 방지합니다. 저장 제공자에 최종 복사 중 실패하면 대상에 일부 데이터가 남을 수 있으므로 **원자적 저장은 보장하지 않습니다**.
4. **비암호화 LAN 스캔 확인**: HTTP LAN 전송 전에 문서 내용이 노출될 수 있음을 안내하고 명시적 동의를 받습니다. `https://` 입력은 Android의 정상 인증서 검증을 유지합니다.
5. **신규 회귀 테스트 43개**: HTTP 응답 프레이밍·PDF 임시 저장 검사 37개, ADF 종료 처리 및 실제 USB 전송 추가 검사 6개. 총 269개 호스트 검사 통과.

M0.6 권위 문서: `docs/M0_6_VERIFICATION.md`. **실제 Gradle·Android SDK 빌드 및 물리 스캐너/프린터 시험은 여전히 미완료**입니다.

## 구현한 기능 및 실제 검증 범위

| 기능 | 소스 상태 | 증거 |
|---|---|---|
| USB Host 자동 탐색, VID/PID/Interface/Endpoint 진단, 권한 요청, TXT 내보내기 | 구현 | 모의 API 컴파일·호스트 테스트 |
| USB Printer Class 상태 조회 및 사용자 승인 RAW(.prn/.pcl/.ps) 전송 | 구현 | 가상 USB 전송 테스트 |
| Android 시스템 PDF 인쇄 | 구현 | Android API 모의 컴파일 |
| USB IPP PDF 직접 인쇄 (`application/pdf` 확인한 장치만) | 구현 | 가상 USB/IPP 검사 |
| USB PTP JPEG 이미지 가져오기 | 구현 | 가상 USB 테스트 |
| LAN eSCL/AirScan 스캔 (수동 사설 IPv4 주소) | JPEG/PNG 구현 | 사설 TCP 모의 스캐너 연동 성공 |
| **HTTPS eSCL** (`https://사설IPv4[:port]`) | OS 인증서 검증을 사용하는 HTTPS 연결 구현 | URL 제한·URI 정책 검사 (TLS 실증은 미실시) |
| USB IPP-over-USB eSCL (USB 7/1/4) | JPEG/PNG 구현 | USB HTTP 가상 장치 검사 |
| 평판 1장, ADF 최대 12장, PDF 저장 | 구현 | ADF 12/13장 경계 모의 검사 및 TCP 모의 연동 |
| **입력원별 해상도·색상·형식 선택** | 구현 | ADF/평판 상이한 설정 테스트 |
| **PNG 수신 정합성·CRC 검사** | 구현 | PNG 정상·변조 검사 |

**중요:** '구현'은 특정 프로토콜을 처리하는 소스가 있다는 뜻이며, 모든 기종에서 실증되었다는 의미가 아닙니다. 제조사 독자 프로토콜, TWAIN/WIA 전용 스캐너를 '범용'으로 지원하지 않습니다.

## 보안 및 지원 제한

- 네트워크 스캐너는 사용자가 명시한 **사설 IPv4·링크로컬**에만 접속합니다. DNS/공인 IP/경로 및 교차 출처 리다이렉트는 차단합니다.
- 기존 장비 호환용 HTTP 주소를 계속 지원하므로 **신뢰된 LAN에서만** 사용하세요. HTTPS는 기본 TrustManager/HostnameVerifier를 사용하며 자체서명 인증서나 IP 주소 불일치 인증서를 우회하지 않습니다. 실제 TLS 장치 연결 시험은 하지 않았습니다.
- JPEG/PNG 외의 PDF/TIFF/기타 eSCL 이미지 형식은 스캔 수신 미지원. OCR, 자동 기울기 보정, 자동 자르기, mDNS 발견, 양면 ADF, 제조사 전용 드라이버, HWP 인쇄 변환은 미구현.
- JPEG/PNG 이미지 최대 8 MiB/페이지, 전체 32 MiB, ADF 12장. 13장이 확인되면 **PDF를 저장하지 않고 실패**합니다. JPEG/PNG의 전체 손상 여부는 Android 디코더의 실단말 시험도 필요합니다.
- USB 접근 권한은 Android 사용자 승인에 의존하며, 시리얼번호는 보고서에 기본 포함하지 않습니다. 실제 지원 장치 수는 **0개 검증** 상태입니다.

## JDK 기반 재현 테스트

```sh
bash tests/run-host-tests.sh
bash tests/run-printer-transport-tests.sh
bash tests/run-imaging-pdf-tests.sh
bash tests/run-escl-tests.sh
bash tests/run-ipp-tests.sh
bash tests/run-reliability-tests.sh
python3 tests/run-android-signature-smoke.py
```

검증 기록: **269/269 Java·가상장치 테스트 통과**, **앱 Java 19개 파일 Android API 모의 컴파일 통과**. 이는 Android SDK 빌드가 아닙니다.

사설 네트워크 모의 eSCL 서버 (PC의 실제 사설 IPv4 주소로 바꾸세요):

```sh
python3 tests/mock-escl-scanner.py --bind 192.168.1.20 --port 8765 --format jpeg
bash tests/run-escl-network-integration.sh 192.168.1.20:8765 jpeg
# 모의 서버를 중지한 후:
python3 tests/mock-escl-scanner.py --bind 192.168.1.20 --port 8765 --format png
bash tests/run-escl-network-integration.sh 192.168.1.20:8765 png
```

모의 서버는 인증이 없으므로 공용 네트워크에 열지 마세요. 이번 세션에서 JPEG/PNG × 평판/ADF **총 4개 네트워크 통합 시나리오가 통과**했습니다.

## Windows 11 Android APK 빌드 및 설치

JDK 21, Gradle 9.8.0, Android SDK Platform 36 / Build Tools 36.0.0을 **사용자 Windows 환경에 설치**한 뒤 프로젝트 루트에서:

```bat
call build-debug-windows.bat
call verify-galaxy-windows.bat
```

빌드 성공 시 `app\build\outputs\apk\debug\app-debug.apk` 생성 여부를 확인합니다. 빌드가 실제 성공하기 전에는 APK가 생성되었다고 판단하면 안 됩니다. Galaxy S20 Ultra에서의 USB OTG 전원·권한·스캐너/프린터 실제 결과를 `docs/DEVICE_TEST_RECORD.csv`에 기록하세요.

최신 기술 검증과 후속 하드웨어 시험 체크리스트: `docs/M0_6_VERIFICATION.md`, `docs/M0_5_HARDWARE_TEST.md`.

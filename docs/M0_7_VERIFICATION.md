# Galaxy Universal I/O M0.7 — 실제 검증 보고서

검증일: 2026-11-12 (한국시간). 환경: Linux 컨테이너, OpenJDK 21.0.12.1.
원본: `GalaxyUniversalIO_M0_6_Source_Reviewed.zip` (기존 소스 ZIP 압축 확인 후 수정).

## 실제 실행한 검증 명령 및 결과

```text
$ ./verify-host-linux.sh
PASS: 55 host checks
PASS: 19 printer transport fake-device checks
PASS: 29 PTP codec + PDF spool checks
PASS: 18 PTP transport fake-device checks
PASS: 99 eSCL protocol and USB/LAN mock checks
PASS: 26 IPP protocol + fake USB print checks
PASS: 45 USB HTTP framing and PDF staging tests
PASS: 25 USB HTTP preflight policy tests
Java Android-signature stub smoke: PASS
App Java files: 20 Stub classes: 52
PASS: all M0.7 host checks and Android-signature mock compile
```

- 통합: 총 **316/316 호스트·가상 장치 검증 항목 통과**.
- 새로 작성한 정책 검사 25개 포함, 기존 M0.6 총계 269개 대비 **47개 증가**.
- Java 애플리케이션 소스 20개가 가짜 Android 클래스 52개를 사용하는 Java 컴파일 검사 통과. **Android SDK로 컴파일하지 않음**.
- `PROJECT-MANIFEST.json`, AndroidManifest.xml, GitHub Actions YAML 구조 확인.

## 네트워크 통합 시험

컨테이너의 사설 IPv4에 제공된 모의 eSCL HTTP 서버를 실제로 기동하고 TCP 통신을 수행했습니다.

```text
PASS: mock platen eSCL 1 image/jpeg pages
PASS: mock ADF eSCL 2 image/jpeg pages
PASS: mock platen eSCL 1 image/png pages
PASS: mock ADF eSCL 2 image/png pages
```

이는 모의 서버와 프로그램 사이의 실제 소켓 통신이지 실물 스캐너 시험이 아닙니다.

## 핵심 위험 보완 및 판단

| 테스트/위험 | 실제 확인 결과 |
|---|---|
| 잘못된 USB 요청 크기 | 전송 전에 거부, 가짜 USB에 기록된 바이트 0 확인 |
| HTTP 요청 경로 CRLF 및 위험문자 | 송신 전 검사에서 차단 |
| 부분 USB HTTP 수신 | 연결 폐기 및 두 번째 요청 거부 |
| `Connection: close` | 응답 수신 후 USB 연결 해제 |
| HTTP 응답 압축/중복 인코딩 | 지원하지 않는 경우 거부 |
| 청크 응답의 금지된 프레이밍 Trailer | 거부 후 연결 폐기 |
| 인터럽트된 eSCL 페이지 수신 | 다음 요청 차단, 가능한 경우 DELETE 시도 |

## 실행 불가/미검증

- 실제 **Gradle 9.8.0 + Android SDK 36 빌드**: 미실시 (이 컨테이너에 Android SDK, 설치형 Gradle 없음).
- **GitHub Actions CI**: YAML 작성/정적 검사만 완료. 실제 GitHub 실행·APK 산출물 없음.
- Galaxy S20 Ultra (Android 13)에서의 실제 앱 실행/ADB 설치: 미검증.
- USB 스캐너/프린터 실물 시험: 미검증 (호환성 확인 모델 0개).
- 제조사 독점 스캐너, TWAIN/WIA 기반 드라이버 전반, OCR, 자동 보정, TIFF, 양면 ADF: 미구현.
- 인증서가 설치된 실물 HTTPS eSCL 기기: 미검증.

## 배포 판정

**M0.7은 소스 패키지와 호스트 검증을 완료한 개발 후보이며 APK 및 실물 하드웨어 검증을 통과한 정식 완성판이 아닙니다.**

GitHub에서 수동 APK를 빌드하는 경로는 `docs/M0_7_CI.md`에 있습니다. 성공 또는 실패는 GitHub Actions 로그에서 확인해야 합니다.

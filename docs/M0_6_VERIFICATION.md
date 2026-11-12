# Galaxy Universal I/O M0.6 기술 검증 및 인수인계

검증일: 2026-11-12 · 실행환경: Linux 컨테이너 JDK 21 (Windows 11/Android 13 실기 확인 아님)

## 범위 및 실제 수행

- M0.5 ZIP 압축 무결성 확인, M0.6 변경사항 코드 작성 및 수정 파일 재검사.
- USB HTTP/1.x 응답 헤더 파싱 추출 및 엄격한 프레이밍 검사. 중복 Content-Length, Transfer-Encoding 혼용, 비정상 상태코드, 부적절한 본문 길이, 헤더 제어문자 차단. 기존 USB IPP·eSCL 전송 흐름에 적용.
- ADF 종료 처리에 204(No Content) 추가. 다른 에러의 종료 의미는 추정하지 않음.
- 앱 캐시의 임시 PDF를 완성한 뒤 사용자가 고른 문서 URI에 복사. 생성 단계 오류 시 URI 스트림을 열지 않음. 임시 파일은 성공·예외 시 삭제 시도.
- HTTP LAN 스캔 시 인증되지 않은 네트워크에서 문서 데이터 노출 위험을 고지하고 사용자 확인을 받아 진행.
- JDK 호스트 테스트 **269개 통과**, Android 프레임워크 모의 Java 컴파일 **19개 애플리케이션 Java 소스 통과**.
- 실제 사설 인터페이스 기반 HTTP 모의 서버와 TCP 연동: JPEG와 PNG 각각 평판 1페이지 및 ADF 2페이지 → **4개 시나리오 통과**.

## 검증 수준

| 평가 항목 | 상태 |
|---|---|
| ZIP 압축 무결성·파일 목록 검사 | 통과 (검사 스크립트 재현 가능) |
| JDK 21 호스트 로직 테스트 | 269개 통과 |
| Android API 모의 시그니처 Java 컴파일 | 19개 소스 통과 |
| HTTPS 인증서·TLS 실제 스캐너 접속 | 미실시 |
| 실제 Android SDK + Gradle 9.8.0 + AGP 9.5.0 빌드 | **미실시** (SDK/Gradle 부재) |
| 실제 Samsung Galaxy S20 Ultra(Android 13) APK 설치 | **미실시** (ADB/단말 부재) |
| 실물 스캐너/프린터 인식 및 출력 | **미실시** (하드웨어 부재) |
| 제조사 독점 프로토콜 USB 스캐너 전체 지원 | 미구현 |
| OCR/TIFF/PDF-eSCL 직접 수신, 양면 ADF, 자동 자르기/보정 | 미구현 |

## 남은 알려진 제약

1. Android `usesCleartextTraffic=true`는 비암호화 구형 LAN eSCL 호환용입니다. 코드는 사설 IPv4로 제한하고 UI에서 사용자가 위험을 승인하지만, 네트워크 공격자가 있는 환경의 기밀성을 보장하지 않습니다. 중요한 문서는 검증된 HTTPS 스캐너에서 처리해야 합니다.
2. 스캔 페이지 메모리 사용 한도는 8MiB/페이지, 32MiB/작업, ADF 최대 12페이지. PDF 임시 스풀 상한은 96MiB. 그보다 큰 문서는 거부될 수 있습니다.
3. 임시 PDF를 먼저 완성하더라도, Android Storage Access Framework 제공자에 **최종 복사 중 실패할 경우 불완전한 대상 파일이 남을 수 있습니다**. 완료 메시지가 없는 파일은 사용자가 확인해야 합니다.
4. USB IPP HTTP는 의도적으로 보수적 파서를 사용합니다. 신뢰 가능한 프레이밍이 없는 응답, 지원하지 않는 전송 인코딩은 거부합니다.
5. 인증되지 않은 제조사 스캐너의 프로토콜을 추측하거나, 인쇄 성공을 USB 전송의 성공과 동일시하지 않습니다.

## 재현 명령

Linux/JDK 21:

```sh
bash tests/run-host-tests.sh
bash tests/run-printer-transport-tests.sh
bash tests/run-imaging-pdf-tests.sh
bash tests/run-escl-tests.sh
bash tests/run-ipp-tests.sh
bash tests/run-reliability-tests.sh
python3 tests/run-android-signature-smoke.py
```

Windows 11(JDK 21/설치형 Gradle 9.8.0/Android SDK 36):

```bat
call build-debug-windows.bat
call verify-galaxy-windows.bat
```

위 Windows 명령은 사용자의 실제 Windows PC에서 실행해야 합니다. 현재 ZIP은 **소스 배포 후보**이며, 실단말 검증/제품 완성판이라는 표현은 사용할 수 없습니다.

기술 근거: Android Developers USB Host, Network Security Configuration, PdfDocument API. 공식 웹 문서 참조 링크는 `docs/M0_6_REFERENCES.md`에 있습니다.

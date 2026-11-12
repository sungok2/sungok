# M0.4 아키텍처 · 보안 · 프로토콜 근거

## 문서 스캔 파이프라인

```
사용자 수동 스캔 요청
   ├── LAN: 명시적으로 지정한 사설 IPv4:port -> HttpURLConnection
   └── USB: 사용자 승인 + 7/1/4 Bulk IN/OUT -> USB HTTP/1.1 프레이밍
       ↓
GET /eSCL/ScannerCapabilities -> 보안 XML 파싱 / JPEG·DPI·급지 검증
       ↓
POST /eSCL/ScanJobs (scan:ScanSettings, JPEG, 300dpi 선호)
       ↓
HTTP 201 + 제한된 동일-출처 Location 경로
       ↓
GET <job>/NextDocument (JPEG, 서명·크기 확인, ADF 반복)
       ↓
DELETE <job> (실패 시에도 시도)
       ↓
Android BitmapFactory -> PdfDocument A4 -> SAF에 사용자가 선택한 PDF 저장
```

## USB IPP 직접 PDF 전송

USB Class 7/1/4 장치를 선택해 Android SAF에서 PDF를 지정하고, 인쇄 확인 대화상자를 수락해야 전송을 시도합니다. JPEG eSCL과 같은 USB HTTP/1.1 계층에서 `POST /ipp/print`에 RFC 8010 IPP 바이너리를 전송합니다. 우선 `Get-Printer-Attributes` (0x000B)로 `document-format-supported=application/pdf`를 확인합니다. 실패/미광고면 PDF 데이터 전송을 **하지 않습니다**. `Print-Job` (0x0002) IPP 응답 성공은 **작업 접수**이지 용지 출력 확인이 아닙니다. PDF 8 MiB 상한, 명시적 확인, USB 권한 재검증을 적용했습니다. 모든 프린터에서 PDF를 자체 렌더링할 수 있는 것은 아닙니다.

네트워크 eSCL은 **HTTP만** 지원하며 출발점은 사용자 입력 사설 IPv4·link-local만 허용하고 URL redirect는 따르지 않습니다. AndroidManifest에 INTERNET 및 `usesCleartextTraffic=true`가 추가되었으므로 **신뢰된 LAN**에서만 사용하세요. TLS와 인증을 적용한 스캐너는 이 배포 범위 밖입니다. 호스트명이 아닌 숫자 사설 IPv4를 요구해 DNS 재바인딩을 줄입니다.

IPP-over-USB 경로는 Printer Class 7 / SubClass 1 / Protocol 4, Bulk IN/OUT를 가진 장치만 시도합니다. Android 권한 확인과 `claimInterface(...,false)`를 사용하고 루트·강제 커널 드라이버 분리는 요청하지 않습니다. 요청은 `Host: localhost`를 전송합니다. HTTP 응답은 Content-Length 또는 chunked로 경계가 확인된 것만 소비하며, 비정상 응답·큰 본문·미지원 상태는 오류 처리합니다. 이는 OpenPrinting `ipp-usb` 전체 호환성/quirks를 재현하지 않으므로 제조사 구현 차이에 따른 실패 가능성이 높습니다.

실제 장치는 JPEG 이외의 문서 형식을 반환하거나 색상/용지/해상도가 다를 수 있습니다. 이 경우 억지로 해석하지 않고 오류 처리하며, 데이터 유실 및 불완전 출력 방지를 우선했습니다. 문서 1장 8 MiB, 전체 32 MiB, ADF 12페이지 제한. PDF 디코더는 큰 JPEG를 약 3백만 픽셀까지 축소해 메모리 사용을 제한합니다. PDF는 저장 시점까지 페이지를 메모리에서 보유하므로 대량 배치 스캔에는 적합하지 않습니다.

Android Storage Access Framework를 사용하며 저장 권한 요청·자동 업로드는 없습니다. 장치 시리얼번호는 사용자가 보고서에 포함시키는 경우에만 읽습니다. 출력 실패로 빈 PDF 자리표시자 문서가 생성될 수 있으므로 결과 메시지를 확인하세요.

**군용/업무용 단말 또는 승인되지 않은 조직망에서는 본 테스트 서버와 USB 주변장치를 사용하지 마세요.** 방화벽·보안정책 및 사용 권한에 따라야 합니다.

## 근거 링크 (접속일 2026-11-12)

- Google ChromeOS Scanner Compatibility 1.0, USB IPP-over-USB/eSCL 요구: https://developers.google.com/chromeos/peripherals/cc-scanner-v1
- Android USB Host: https://developer.android.com/develop/connectivity/usb/host
- Android `UsbDeviceConnection`: https://developer.android.com/reference/android/hardware/usb/UsbDeviceConnection
- OpenPrinting `ipp-usb` (HTTP over USB 및 구현상 주의사항): https://github.com/OpenPrinting/ipp-usb
- eSCL 실무 구현 예시(`sane-airscan`): https://github.com/alexpevzner/sane-airscan
- RFC 8010, IPP/1.1 Encoding and Transport: https://www.rfc-editor.org/rfc/rfc8010
- Android AGP 9.5.0 Release Notes: https://developer.android.com/build/releases/agp-9-5-0-release-notes

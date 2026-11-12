# M0.3 → M0.4 개발 변경기록

1. `EsclProtocol.java`: XXE를 제한한 eSCL capabilities 파서, JPEG·급지·해상도 확인, scan settings XML, 동일 출처 job URI, JPEG 서명/크기 검증.
2. `EsclScanClient.java`: GET capabilities / POST job / GET NextDocument / DELETE 정리, 평판/ADF 지원, 재시도·페이지/용량 한도.
3. `LanEsclTransport.java`: 사용자 지정 사설 IPv4의 HTTP 통신, 리디렉션 차단, 길이/타임아웃 제한.
4. `UsbEsclHttpTransport.java`: IPP-over-USB 7/1/4 탐색, Bulk HTTP/1.1 본문 프레이밍 Content-Length/chunked, 인터페이스 소유·해제.
5. `ScannedPdfWriter.java`: JPEG를 플랫폼 `PdfDocument`를 사용해 A4 PDF로 배치/저장; 큰 JPEG 축소.
6. `MainActivity`: LAN/IP 입력, USB eSCL 선택, ADF 체크, PDF 저장, USB 권한·장치 일치 재검사, 전체 화면 세로 스크롤.
7. `AndroidManifest.xml`: 인터넷 권한 및 LAN HTTP 클리어텍스트 통신 허용.
8. 테스트: `EsclHostTests` 58개, `EsclNetworkIntegrationTests`와 PC 가짜 eSCL 서버 및 2장 JPEG fixture 추가.
9. `IppProtocol.java` / `UsbIppPdfPrinter.java`: USB PDF 출력 시 지원 MIME 사전조회, IPP/1.1 Print-Job 구현. 사용자 확인 및 8 MiB 제한.
10. Windows 빌드: `run-escl-tests.bat` 및 `run-ipp-tests.bat`를 추가하고 빌드 배치에서 실행.
11. **실물 USB/스캐너·프린터·Android SDK 빌드 없음**: 검증 상태 명시, 지원 보증 없음.

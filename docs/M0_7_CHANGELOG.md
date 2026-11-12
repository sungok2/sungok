# M0.7 변경내역 (M0.6 대비)

1. `UsbHttpRequestPolicy.java` 신규 구현: USB 요청의 메서드·대상 경로·Content-Type·요청 본문 크기·응답 크기 상한을 실제 전송 전 검증합니다. CRLF 및 경로 주입을 차단합니다.
2. `UsbEsclHttpTransport.java` 통신 중단 정책: 전송을 시작한 이후 오류가 발생하면 USB 인터페이스/연결을 정리하며 재사용하지 않습니다. HTTP `Connection: close` 반영, 청크 응답의 금지 Trailer 차단, PNG/IPP Accept 헤더 정리.
3. `UsbHttpResponseHeaders.java` 보강: 압축·중복 Content-Encoding 거부, 미지원 Connection 값 거부, 안전한 Chunked Trailer 검사 및 명시적 연결 종료 표시.
4. `EsclScanClient.java`: 스레드 interruption을 작업 시작 및 ADF 페이지 사이에 검사하고, 작업 중단 시 가능한 경우 스캔 Job 삭제 요청을 수행합니다.
5. `tests/UsbHttpPolicyTests.java` 및 각종 USB/ADF/HTTP 보안 회귀 검사를 추가해 316개 JDK 기반 검사 전부 통과.
6. `.github/workflows/build-android-apk.yml` 신규: 공식 CI 환경에서 JDK21/Gradle9.8.0/Android SDK36을 설치하고 호스트 테스트 후 APK 빌드 및 아티팩트 업로드 시도. **워크플로 실제 실행·APK 생성은 확인되지 않음**.
7. `verify-host-linux.sh`, `verify-host-windows.bat`로 일괄 검증 명령 추가.
8. 앱 버전 `0.7.0`, versionCode `8` 업데이트.

## 호환성 및 제한

- IPP-over-USB 표준을 사용해도 실제 제조사 장치의 구현 차이로 작동하지 않을 수 있습니다.
- 스캐너·프린터 실물 테스트, Android SDK 빌드, Galaxy S20 Ultra 실행은 수행되지 않았습니다.
- Connection: close를 보내는 장치의 다중 작업은 새 연결이 필요합니다. 현 설계는 임의로 오래된 연결을 재사용하지 않습니다.
- 취소 신호는 현재 스레드 중단 확인 수준이며, UI 취소 버튼이나 블로킹 USB I/O의 즉시 중단은 구현하지 않았습니다.

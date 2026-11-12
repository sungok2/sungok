> **이전 버전 M0.5 기록** — 현재 상태는 `docs/M0_6_VERIFICATION.md` 참고.

# M0.4 → M0.5 변경사항

2026-11-12

1. `EsclProtocol`: 평판·ADF를 독립적인 기능 프로파일로 해석해, 한쪽의 색상·JPEG·해상도 정보를 다른 쪽에 잘못 적용하던 문제 수정. 각 `SettingProfile`도 독립 해석.
2. JPEG 우선, JPEG 미지원시 PNG를 협상함. PNG signature, IHDR/IDAT/IEND, CRC, 이미지 크기와 길이를 확인함.
3. `EsclScanClient`: 요청된 MIME 형식과 반환 MIME 형식을 대조. ADF 12장까지 허용하고 13장을 무조건 저장하지 못하도록 경계 조건 수정.
4. `ScannedPdfWriter`: Android BitmapFactory/PdfDocument로 JPEG 및 PNG 입력을 PDF에 배치. 실단말 PDF 출력은 미시험.
5. `LanEsclTransport`: 사용자가 지정한 `http://`, `https://`, 또는 이전의 숫자 IPv4 입력 지원. HTTPS 기본 포트 443. 기본 JVM/Android 인증서 검증을 그대로 사용하고 위험한 인증서 우회 구현은 금지.
6. `MainActivity`: 안내문에 PNG/HTTPS 및 M0.5 버전 표기.
7. 가상 eSCL 서버에 PNG 응답 모드 추가, 2개의 PNG 테스트 이미지를 포함해 TCP 통합 테스트 수행.
8. 기존 기능(USB Printer RAW/IPP PDF, Android PDF Print, PTP, USB 권한 및 진단)을 변경하지 않고 전체 회귀검사를 통과함.

**새로운 기능의 실제 Android SDK APK, 실제 USB/LAN 프린터/스캐너 호환성은 별도 검증이 필요함.**

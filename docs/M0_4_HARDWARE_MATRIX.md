# M0.4 장치 호환성 및 하드웨어 시험 매트릭스

| 인터페이스 / 유형 | 소스 구현 | 자동/모의 검사 | 실물 시험 | 기종별 지원 보증 |
|---|---|---|---|---|
| USB Host VID/PID·Endpoint | 예 | 정적/모의 | 없음 | 아니요 |
| USB Printer class 7/1/1,2 RAW | 예 | 가상 USB | 없음 | 아니요 |
| USB IPP PDF 7/1/4 (`application/pdf` 지원 시) | 예 | 가상 USB IPP | 없음 | 아니요 |
| Android PDF Print Framework | 예 | 모의 | 없음 | 인쇄 서비스 필요 |
| USB PTP Still Image 6/1/1 JPEG | 예 | 가상 USB | 없음 | 아니요 |
| LAN eSCL HTTP + JPEG | 예 | 사설 LAN 가짜 서버로 연동 | 없음 | 아니요 |
| USB IPP-over-USB class 7/1/4 + eSCL | 예 | 가상 USB HTTP | 없음 | 아니요 |
| eSCL ADF 2페이지 | 예 | 가짜 서버 | 없음 | 아니요 |
| 제조사 전용 USB 스캔 프로토콜 | 아니요 | 없음 | 없음 | 미지원 |
| eSCL HTTPS·TLS·mDNS 발견 | 아니요 | 없음 | 없음 | 미지원 |
| eSCL PDF/PNG/TIFF·OCR | 아니요 | 없음 | 없음 | 미지원 |
| 직접 USB IPP·PCL 변환 인쇄 | 아니요 | 없음 | 없음 | 미지원 |

## Galaxy 검증 체크리스트

- [ ] 스마트폰 모델과 Android 버전 기록 (Galaxy S20 Ultra / Android 13 목표, 다른 경우 실제 값).
- [ ] USB OTG 호스트 역할 및 전원 공급 능력 확인(복합기 자체 전원 사용 권장).
- [ ] 앱 설치/실행 후 연결·분리 감지, USB VID/PID 및 프로토콜 표시 확인.
- [ ] USB 사용자 권한 승인/거절 및 연결 해제 처리 확인.
- [ ] 같은 LAN의 가짜 eSCL 서버에서 평판 1장 PDF 저장/열기 확인.
- [ ] 같은 LAN의 가짜 eSCL 서버에서 ADF 2장 PDF 저장/열기 확인.
- [ ] 실물 eSCL 스캐너 주소, FW, 노출된 ScannerCapabilities, LAN PDF 결과 확인.
- [ ] 실물 class 7/1/4 복합기로 USB eSCL 스캔·ADF/PDF 결과 확인.
- [ ] 기존 RAW 프린터 상태·전송 후 실제 출력 검증(출력 언어 일치 모델에 한함).
- [ ] 모든 성공/실패와 검증 증거를 CSV에 반영.

*모델/펌웨어별 지원 여부는 시험 전에는 알 수 없습니다. 현재 실물 확인 0건.*

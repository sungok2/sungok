> **Historical M0.3 reference** — For the latest M0.5 status, read `docs/M0_5_VERIFICATION.md` and `docs/M0_5_CHANGELOG.md`.

# M0.3 테스트 계획 및 완료 게이트

## 수행한 자동 검사

- [x] JDK 단독 프로토콜·프로파일 테스트 55개
- [x] 모의 프린터 전송 테스트 19개
- [x] PTP 파서 및 PDF 스트림 검증 테스트 29개
- [x] 모의 PTP 전송 테스트 18개
- [x] Java Android 모의 시그니처 컴파일: 앱 Java 10개

## 아직 수행되지 않은 필수 완료 게이트

- [ ] Windows Gradle 9.8.0 / AGP 9.5.0 / Android SDK 36 실제 빌드 및 설치
- [ ] Galaxy S20 Ultra(Android 13) APK 실행 및 USB OTG 인식·권한 시험
- [ ] 실제 USB Printer 7/1/1 또는 7/1/2 상태 조회 및 검증된 PDL로 용지 출력
- [ ] Protocol 7/1/4(IPP-over-USB)에서 RAW 전송 불가·장치 식별 확인
- [ ] USB Still Image PTP 6/1/1에서 원본 JPEG 1개 획득·저장·이미지 확인
- [ ] Android Print Service를 통한 PDF 1페이지 이상 실제 출력
- [ ] 일반 평판 스캐너 모델 선정 후 제조사 프로토콜 개발 및 문서 이미지 획득
- [ ] 오류/재연결/USB 분리·화면 회전/권한 거절 시나리오 실물 검증
- [ ] 호환성 DB에 모델·펌웨어·VID/PID·증빙 데이터 적재 및 검증

**완성 기준**: 실제 기기에서 성공한 항목만 통과로 표시. 소스·모의 테스트·SDK 빌드·실물 출력·실물 스캔은 서로 대체할 수 없음.

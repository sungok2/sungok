> **Historical M0.3 reference** — For the latest M0.5 status, read `docs/M0_5_VERIFICATION.md` and `docs/M0_5_CHANGELOG.md`.

# M0.3 외부 빌드 및 실물 검증 인계

**M0.3 소스 릴리스**입니다. APK를 임의로 생성하거나 실제 장치 검증을 수행했다고 주장하지 마세요.

1. Windows 11: JDK 21 / Gradle 9.8.0 / Android SDK Platform 36 / Build Tools 36.0.0 확인.
2. `build-debug-windows.bat` 실행, 테스트/Gradle 전체 로그 보관.
3. APK가 실제로 생성됐다면 SHA-256 기록, Galaxy S20 Ultra Android 13에 설치 및 실행.
4. 인쇄 출력, USB JPEG 입력, 권한·연결 복구, PDF 프린트 서비스 테스트를 실제 장치에서 분리 수행.
5. `docs/DEVICE_TEST_RECORD.csv`에 실제 시험 근거 기록. 성공 여부를 미확인 상태에서 임의 입력하지 말 것.
6. 일반 문서 스캐너/IPP-over-USB 등 미구현 항목은 제조사·모델별 프로토콜을 확보한 뒤 별도 모듈로 개발.

참고: `README.md`, `docs/BUILD.md`, `docs/VERIFICATION.md`, `docs/M0_3_SCOPE_AND_HARDWARE_TEST.md`.

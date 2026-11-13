# Galaxy Universal I/O M0.11 — NTFS MASTER
작성: 2026-11-13 · 테스트용 앱, 실기기 NTFS 쓰기 검증 전

## 관찰된 문제
삼성 파일 앱에서 휴대전화→NTFS USB 복사가 차단됨. USB 인식 또는 읽기는 쓰기 가능을 의미하지 않는다. 삼성 기본 저장소 제공자가 읽기 전용이면 일반 Android 파일 선택 권한만으로 쓰기 전환 불가.

이 앱에 NTFS-3G/libaums/USB SCSI 블록 드라이버는 없다. OTG Master 등의 쓰기 가능 DocumentsProvider에서 선택한 USB 폴더에만 파일을 생성한다. 외부 드라이버 필요 조건을 숨기지 않는다.

## M0.11 구체 구현
1. 원인 안내: Samsung 기본 NTFS 읽기 전용/Android URI 권한/볼륨 잠금/용량 부족/파일명 충돌/데이터 검증 실패/USB 끊김을 구분하되 실제 장치 원인을 확정했다고 주장하지 않는다.
2. USB 대상 폴더를 사용자 명시 동의로 선택. 내부 저장소 primary: 폴더는 USB 목적지/진단으로 오인하지 않게 거부한다.
3. 디렉터리 기능 플래그 FLAG_DIR_SUPPORTS_CREATE로 먼저 차단 여부 확인. 플래그는 공급자 광고일 뿐 실제 NTFS 드라이버 보증이 아님.
4. 실제 8,192바이트 무작위 임시 파일 생성→쓰기→스트림 닫기→SHA-256 재읽기 검증→삭제 시험. 성공 의미는 선택된 폴더에서 실제 파일 기록/검증/삭제 완료뿐이다.
5. 임시 파일 생성 전에 SharedPreferences.commit() 복구 기록 저장. 실패 시 새 파일 생성하지 않음. 실패/장치 분리 시 복구 기록 유지, 사용자 동의 후 임시 파일만 삭제.
6. rename 응답이 불명확한 경우 이미 게시된 최종 파일이 존재할 수 있으므로 자동 삭제 금지. 복구 중 최종 파일이 보이면 사용자 검토.
7. USB 재연결로 대상 제공자의 URI가 바뀌면 사용자 2단계 확인 후 앱의 복구 기록만 삭제 가능. USB 파일에 어떤 변경도 하지 않으며 잔여 파일의 수동 확인 필요.
8. source/output/readback/final 파일 스트림 null이면 명시적 오류와 정리 절차.
9. 기존 데이터 덮어쓰기 차단, 원본 변경/삭제 금지, Windows 파일명 검사 및 파일별 해시 보고서 유지.

## 위험·불확실성
- 확인: OS의 NTFS 읽기 전용 문제. 쓰기 가능 여부는 제공자 실제 시험으로만 판정.
- 추정 가능: Windows 빠른 시작/hibernation/Dirty NTFS, USB 케이블 전력 부족, Mass Storage 장치 호환성, 저장공간 부족, 파일권한 만료, 연결 앱 중단.
- 미확인: 해당 USB 모델·포맷 안전성, 디스크 잔여 용량, 실제 공급자/NTFS 볼륨 상태. 원인을 확인 없이 단정하지 않음.
- 불가한 보증: SHA-256 재읽기만으로 USB 캐시 flush, NTFS 로그/저널, 전원 단절 시 데이터 영구성을 증명하지 못함.
- 미구현: 루팅 없는 자체 USB MSC BOT/UASP + NTFS-3G 네이티브 엔진, ForegroundService 장시간 실행 보장, 파일 이어받기, 동시 외부 작성자를 막는 원자적 no-replace.

## 실기기 시험 절차
1. PC에서 USB 전체 백업을 먼저 진행하고 파일시스템이 정상인지 확인. 포맷 금지. Windows 안전 제거.
2. Galaxy에서 OTG Master 등의 NTFS 읽기·쓰기 제공자 연결 후 읽기·쓰기 상태를 확인. 읽기 전용이면 중단.
3. Galaxy Universal I/O M0.11의 8KB 쓰기 진단 → 외부 제공자에 노출된 USB 실제 폴더 선택 → 테스트 생성·읽기·삭제 확인.
4. 같은 폴더로 작은 전화 파일 복사 → SHA-256 보고서 → USB 안전 제거 후 PC에서 해시 독립 검증.
5. 이름 충돌, 0B, 1B, 1MiB, 100MiB, 4GiB 초과 파일(용량 충분할 때만), 취소, 권한 만료, USB 연결 해제 등을 백업된 시험용 USB에서 점검.
6. 재연결 이후 남은 임시 파일이 있으면 복구 메뉴에서 사용자 확인 후 정리. 이름 변경 응답이 불분명한 경우 최종 파일을 먼저 검사.

## 빌드·회귀 시험
- bash tests/run-ntfs-saf-tests.sh
- bash tests/run-ntfs-name-tests.sh
- bash tests/run-ntfs-transaction-tests.sh
- bash tests/run-ntfs-audit-tests.sh
- bash tests/run-ntfs-write-probe-tests.sh
- bash tests/run-ntfs-destination-advisor-tests.sh
- bash tests/run-ntfs-failure-advice-tests.sh
- python3 tests/run-android-signature-smoke.py
- gradle :app:assembleDebug
- Android API 33 에뮬레이터 UI 시작 및 기능 버튼 확인(USB 실증 아님).

## 출처/라이선스
Android Docs: https://developer.android.com/reference/android/provider/DocumentsContract.Document
Android USB Host: https://developer.android.com/develop/connectivity/usb/host
OTG Master GPL-3.0-or-later: https://github.com/moylali/OTGMaster
NTFS-3G GPL-2.0-or-later: https://github.com/tuxera/ntfs-3g

M0.11은 OTG Master 소스나 바이너리를 내장하지 않는다. 추후 독립형 NTFS 엔진을 결합하면 OSS 고지/소스 제공/라이선스 준수와 실물 장치 검증을 새로 수행해야 한다.

# Galaxy Universal I/O M0.9 — NTFS USB 쓰기 연동 검증 사양
2026-11-13 / experimental SAF bridge / 대상: Galaxy S20 Ultra, Android 13

## 정확한 지원 범위

M0.9는 NTFS 드라이버 자체가 아니다. 사용자가 Android DocumentsProvider로 노출한 NTFS 볼륨에 저장하는 파일 복사 화면이다. 삼성 파일 관리자에서 읽기 전용인 NTFS 볼륨을 임의로 쓰기 가능하게 바꾸지 못한다. 루팅 없이 가능한 경로는 OTG Master 등 별도 앱이 USB Mass Storage를 사용자 공간에서 직접 읽고 쓰며 DocumentsProvider를 제공할 때 그 URI를 선택하여 저장하는 것이다.

- 구현됨: ACTION_OPEN_DOCUMENT의 단일/복수 파일 선택, ACTION_OPEN_DOCUMENT_TREE 대상 선택, Android의 Provider 권한으로 대상 폴더 쓰기 가능 플래그 확인, 같은 이름의 파일(대소문자 무시) 선검사.
- 구현됨: 원본을 수정/삭제하지 않는 파일별 임시 생성, 스트리밍 복사, 목적지 파일 재오픈 및 SHA-256 + 크기 비교, 검증 후 renameDocument, 복사 중 취소 요청과 실패 시 임시 파일 삭제 시도.
- 구현됨: 순수 자바 스트림 로직의 JVM 회귀 테스트. 기존 프린터/스캔 코드는 유지.
- **미구현**: USB Mass Storage 자체 점유, SCSI BOT/UASP 드라이버, MBR/GPT 분할, libntfs-3g JNI, NTFS-3G 직접 마운트/쓰기, 저장소 전체에 시스템 파일시스템 마운트, 임시 파일 교체의 범용 원자성, 실기기 USB 상호운용성 보장.

## 사용 절차 (실기기)

1. PC에서 NTFS USB 데이터 **백업**을 마련하고, Windows에서 전체 종료(Fast Startup 우회) 및 안전 제거. 문제 있는 드라이브를 포맷하거나 복구하여 실험하지 말 것.
2. Galaxy S20 Ultra에서 OTG Master 또는 읽기/쓰기 DocumentsProvider를 지원하는 검증된 앱으로 NTFS USB를 연결. NTFS가 안전하지 않다고 읽기 전용으로 표시되면 반드시 쓰기 중단.
3. Galaxy Universal I/O 실행 → 'USB 저장장치 복사 (NTFS / 외부 제공자)' → 작은 임시 테스트 파일 선택 → Android 폴더 선택기에서 해당 USB의 폴더 선택.
4. 복사 완료 메시지 확인 후 OTG Master에서 파일 내용 확인. PC에서도 해시를 대조.
5. 이름 충돌, 쓰기 권한 거부, USB 강제 분리, 작업 취소, 공간 부족, 장시간 대용량 파일을 차례로 테스트. 중요한 실제 데이터가 있는 USB에는 쓰기 테스트하지 말 것.

## 핵심 설계 설명

- source/target은 Android ContentResolver URI. 저장소 이름/경로 추측이나 MANAGE_EXTERNAL_STORAGE 권한 우회 없음.
- Provider가 DIRECTORY의 FLAG_DIR_SUPPORTS_CREATE를 광고하지 않으면 쓰지 않음.
- 대상에 같은 이름이 있으면 덮어쓰지 않음. 중복 검사와 최종 이름 변경 사이의 외부 동시 수정은 원자적으로 차단되지 않는다.
- 임시 파일 `.<name>.partial-<suffix>`를 만든 뒤, 해당 파일을 재오픈해 비교한다. SHA-256 같음은 파일 전체가 독립적으로 다시 읽혔다는 증거지만 물리적 플래시의 내구성까지 증명하지는 않는다.
- 실패한 파일의 임시 데이터는 삭제를 *시도*한다. 전원 분리나 Provider 오류에서 실패할 수 있다. 완료된 이전 파일은 계속 남는다.
- 취소는 스트림 단위 체크를 따른다. USB 프리징/드라이버 대기 중 즉시 종료는 보장하지 않는다.
- 지원 파일 선택 128개 제한. 단일 실행에서 자동 폴더 계층 복제, 파일 이어받기, 드라이브 포맷 기능은 없음.
- 다른 DocumentsProvider로도 동작할 수 있으나, 해당 공급자의 URI 및 플래그 구현에 좌우된다.

## 검증 게이트

- JDK 17 이상의 JVM에서 `bash tests/run-ntfs-saf-tests.sh`: 크기/해시, 비어 있는 파일, 멀티버퍼, 쓰기 실패, 중단, 0바이트 스트림, 읽기 실패를 점검.
- GitHub Actions에서 Android SDK 36 + Java 21/Gradle 9.8.0으로 `app:assembleDebug` 컴파일 및 서명 검사.
- Android 13 에뮬레이터 Activity 시작 시험은 USB NTFS 실증이 **아님**.
- 단말 USB 실험은 미실시인 경우 미실시로 명확히 보고. OTA 권한, Android 저장소 공급자, 실제 NTFS 안전성 판단은 별도로 확인.

## 향후 완전 독립형 NTFS 엔진 (M1.x; 아직 구현되지 않음)

[USB Host permission] → [USB Mass Storage BOT + SCSI READ/WRITE] → [MBR/GPT partition + GPT CRC] → [RawBlockDevice block I/O + cache + sync] → [libntfs-3g JNI] → [Files + DocumentsProvider].

보안 요구사항: 외부 커널 마운트와 동시 쓰기 금지, BitLocker/hibernation/dirty $LogFile 감지시 읽기 전용 전환, LUN 경계/섹터 크기 확인, write barrier/flush 구현, 4 KiB와 512 B 섹터 지원, 안전 제거, 충돌 및 분리 복구.

기술 참고:
- https://developer.android.com/develop/connectivity/usb/host
- https://developer.android.com/reference/android/provider/DocumentsContract
- https://github.com/moylali/OTGMaster
- https://github.com/tuxera/ntfs-3g
- https://github.com/magnusja/libaums
- OTG Master GPL-3.0-or-later, NTFS-3G GPL-2.0-or-later; M0.9는 OTG Master 소스 코드를 복사하지 않고 SAF API를 통해 상호운용한다.

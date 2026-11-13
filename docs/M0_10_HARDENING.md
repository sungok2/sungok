# Galaxy Universal I/O M0.10 — NTFS SAF MASTER Hardening
작성일: 2026-11-13
기준 버전: M0.9
브랜치: galaxy-universal-io-ntfs-m1-0-hardening

## 실제 지원 범위

M0.10은 자체 NTFS 드라이버가 아니다. USB를 읽기/쓰기로 직접 처리하고 Android DocumentsProvider로 노출하는 외부 앱(예: OTG Master)이 반드시 필요하다. 삼성 기본 NTFS 읽기 전용 마운트를 쓰기 가능으로 해제하거나 Android 시스템 전체에 커널 파일시스템 드라이버를 설치하지 않는다. 외부 제공자 없는 갤럭시 NTFS 직접 쓰기는 미구현이다.

## 신규 구현

1. NtfsNamePolicy: Windows 호환 파일명(255 UTF-16 단위), DOS 장치명, 금지 문자, 뒤쪽 공백·마침표, UTF-16 고아 surrogate 차단. 원본 파일명 자동 변환 없음. 복수 선택의 대소문자 충돌을 전송 전 검사.
2. SafVerifiedTransaction: 순수 Java의 거래 처리 엔진. 저장 전 이름 중복 확인 → 임시 이름 난수 생성 → 복구 기록 저장 → 임시 파일 생성 → 쓰기/rename 지원 확인 → 스트리밍 복사 → 출력 닫기 → 대상 재읽기 SHA-256+크기 검증 → 중복 재검사 → 최종 rename → 변경된 실제 파일명 확인.
3. 기록이 실패하면 대상 변경 전 중단. 중간 실패 시 임시 파일 삭제 시도, 삭제 불가/생성 결과 불확실 시 복구 기록 유지.
4. 앱 재실행 시 중단된 작업 기록을 표시. 사용자가 동일 대상 폴더를 선택한 후 실제 임시 파일을 확인하고 2차 확인을 눌러야 삭제. 임의의 다른 파일은 자동 삭제하지 않음.
5. 검증 완료된 파일명·바이트 크기·SHA-256을 TSV 형태로 TXT에 내보내는 감사 보고서. 완전/부분 완료 상태를 구분.
6. ContentResolver openOutputStream("wt") 요청, 작업 진행률과 작업 취소 유지.
7. 기존 프린터·PTP·eSCL 스캐너 관련 소스 보존.

## 인식해야 할 제한

- DocumentsProvider rename은 범용 원자적 no-replace를 보장하지 않는다. 외부 앱이 동일한 이름을 동시에 만들면 경합 가능성이 있으며, OS/제공자 수준에서 완전 예방할 수 없다.
- 다른 제공자가 임시·최종 이름을 변경할 수 있다. 최종 이름 변경은 검증하되 이미 게시된 사용자 파일은 임의로 삭제하지 않는다.
- 디스크 flush, NTFS 로그/저널, 안전 제거, USB 커넥터 분리, 디스크 자체 오류의 보장은 외부 공급자·장치 책임이다. SHA-256 재읽기 성공은 USB 플래시 영구 기록을 증명하지 않는다.
- 일부 파일 시스템에서는 Android 11 이상 ACTION_OPEN_DOCUMENT_TREE로 루트/Download/Android/data 선택이 제한된다.
- 현재 Activity 기반 복사는 장시간 백그라운드 실행 보장 없음. ForegroundService, 파일 이어받기, 디렉터리 재귀, 직접 SCSI BOT/UASP, libntfs-3g JNI 등은 아직 미구현.
- 외부 NTFS 볼륨의 Windows Fast Startup/hibernation/dirty 상태는 이 앱이 직접 검사할 수 없다. 외부 제공자의 읽기 전용 방침을 존중한다.

## 재현 검증

- bash tests/run-ntfs-saf-tests.sh
- bash tests/run-ntfs-name-tests.sh
- bash tests/run-ntfs-transaction-tests.sh
- bash tests/run-ntfs-audit-tests.sh
- python3 tests/run-android-signature-smoke.py
- gradle --no-daemon :app:assembleDebug

위 결과는 실제 Actions 실행 결과로 판정하며 문서 자체는 성공 증거가 아니다. Android 13 에뮬레이터 Activity 시작은 NTFS 쓰기 검증이 아니다. 실물 Galaxy S20 Ultra의 512/4096B 섹터, NTFS 오류, USB 전원 공급, USB 분리 후 복구를 반드시 별도 검증해야 한다.

## 실기기 시험 절차

1. PC에서 테스트용 NTFS USB 자료를 완전히 백업 후 정상 안전 제거한다.
2. Galaxy에 OTG Master 등 검증된 NTFS 읽기·쓰기 제공자를 설치하고 쓰기 가능한 NTFS 볼륨인지 먼저 확인한다.
3. Galaxy Universal I/O → USB 저장장치 복사 → 작은 임시 파일 선택 → USB의 하위 폴더 선택 → SHA-256 검증 결과 확인.
4. 같은 이름의 파일, 다중 파일명 충돌, 파일 크기 변동, 취소, 분리, 공간 부족, USB 재연결 상태를 순서대로 시험한다.
5. 미완료 임시 파일의 복구 기록은 사용자 확인 없이 삭제하지 않는다. 중요한 실제 파일이 들어 있는 저장장치에서는 실험하지 않는다.

## 근거

Android SAF: https://developer.android.com/training/data-storage/shared/documents-files
DocumentsProvider: https://developer.android.com/reference/android/provider/DocumentsProvider
ContentResolver openOutputStream: https://developer.android.com/reference/android/content/ContentResolver
Microsoft 파일명 규칙: https://learn.microsoft.com/en-us/windows/win32/fileio/naming-a-file
OTG Master: https://github.com/moylali/OTGMaster
NTFS-3G: https://github.com/tuxera/ntfs-3g

오픈소스 직접 내장 시 배포 라이선스 검토 별도 필요. 본 SAF 버전은 OTG Master 코드를 복사하지 않는다.

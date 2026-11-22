# Android 배포 안내 — v0.12.5

## 제품 목적

이 앱의 주 사용 흐름은 Galaxy가 USB Host/OTG 모드로 유선 프린터·스캐너에 연결해 인쇄하거나 스캔하는 것입니다. 네트워크 eSCL 탐색은 부가 기능입니다.

PDF 인쇄는 Android `PrintManager`와 USB 직접 인쇄 두 경로로 구분합니다. WF-C5390은 앱 내장 PDF→PostScript 3 렌더러와 USB Printer Class RAW 전송을 추가했습니다. 모델 프로파일은 GitHub에서 data-only 형식으로 받아 제한된 값만 적용하고, 네트워크가 없으면 APK 내장 프로파일을 사용합니다. 이 프로파일은 Epson의 공개 PDL 사양을 바탕으로 한 protocol-reviewed 상태이며 실물 Galaxy+WF-C5390 시험 전까지 hardware-tested로 표시하지 않습니다. 제조사 PrintService는 그 서비스가 지원하는 연결방식에만 이용됩니다.

## 0.12.5 연결 안정성 개선

- Android가 전달하는 USB 연결 인텐트의 장치 객체를 사용해 신규 장치를 목록에서 우선 선택합니다. 이미 실행 중인 앱의 `onNewIntent` 경로도 처리합니다.
- 분리 이벤트에서는 대기 중인 선택을 폐기하고 장치 목록을 갱신합니다.
- USB 장치가 0개이면 Android Host 열거 단계부터 문제를 구별합니다. 갤럭시 OTG/전원/데이터 케이블/프린터 후면 PC용 USB 포트를 안내합니다.
- `targetSdk 36` 적용. 2026년 8월 31일 이후 Google Play의 Android 16(API 36) 제출 조건을 충족하는 빌드 구성입니다.
- Android 13 에뮬레이터에서는 USB 연결 인텐트의 장치 객체가 빠진 상황을 오류 없이 처리하는지 확인합니다.
- 실제 장치가 감지되었다는 것과 USB Printer Class/IPP/eSCL이 호환된다는 것은 **서로 다른 검증 단계**입니다.

## USB 기능과 실제 범위

- Android USB Host에서 장치 열거와 사용자 권한을 처리합니다.
- USB 프린터 상태 조회와 사전 작성된 PRN/PCL/PS RAW 데이터 전송은 지원 프로토콜 인터페이스에서만 동작합니다. RAW 전송 성공은 실제 인쇄 성공을 뜻하지 않습니다.
- Epson WF-C5390 프로파일로 PDF를 150 dpi 컬러 PostScript 3으로 변환해 USB Printer Class protocol 1/2 Bulk OUT으로 스트리밍 전송할 수 있습니다. 모델 문자열이 USB 장치에 없으면 사용자가 모델을 확인해야 합니다. 최대 20페이지 및 64 MiB 작업입니다.
- USB IPP-over-USB의 제한된 프로파일에서 PDF 직접 인쇄를 시도할 수 있습니다. 프린터가 `application/pdf`를 광고해야 합니다.
- USB 스캔은 IPP-over-USB eSCL 장치에서 동작하도록 구현되어 있습니다. PTP는 호환 이미지 장치에서 JPEG를 가져오는 기능이며 일반 평판/ADF 스캐너 드라이버가 아닙니다.
- 제조사 전용 스캐너 프로토콜은 현재 지원한다고 표시하지 않습니다. 제조사 앱/SDK가 공개적으로 허용하는 USB 연동 경로가 모델별로 확인되어야 합니다.

## 버전

- 앱 ID: `com.galaxyuniversalio`
- 버전: `0.12.5`, versionCode `18`
- 최소 Android: API 26
- 배포 형식: 서명된 직접 설치 APK. Google Play 업로드용 AAB와 Play App Signing은 별도입니다.

## 검증 범위

GitHub Actions에서 Android release 빌드, 패키지 메타데이터, 비디버그 설정, APK 정렬과 Android 13 에뮬레이터 스모크 검사를 수행합니다. 에뮬레이터와 모의 USB/프로토콜 테스트는 실제 Galaxy, 실제 프린터·스캐너의 모델 호환성 증거가 아닙니다. 모델명·펌웨어·VID/PID를 기록한 물리 연결 시험이 끝난 모델만 hardware-tested로 올립니다.

## 서명

서명 APK의 개인키는 저장소에 커밋하지 않습니다. 이 프로젝트 전용 PKCS#12 키스토어와 별도 암호 백업을 보관하며, 모든 후속 업데이트에서 같은 키를 사용합니다. 서명키를 분실하면 기존 설치본에 정상 업데이트할 수 없습니다. GitHub Actions 자동 서명을 사용하려면 릴리스 담당자가 `docs/VENDOR_DRIVER_INTEGRATION.md`와 키 인계서의 비밀정보를 저장소 Actions secrets에 직접 등록해야 합니다.

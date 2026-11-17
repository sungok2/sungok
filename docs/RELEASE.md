# Android 배포 안내 — v0.11.0

## 제품 목적

이 앱의 주 사용 흐름은 Galaxy가 USB Host/OTG 모드로 유선 프린터·스캐너에 연결해 인쇄하거나 스캔하는 것입니다. 네트워크 eSCL 탐색은 부가 기능입니다.

PDF 인쇄는 Android `PrintManager`에 전달합니다. Android에 설치·활성화된 제조사 `PrintService`가 해당 프린터 모델의 USB OTG를 지원하면 Android 인쇄 화면에서 그 서비스를 선택할 수 있습니다. 앱은 PC용 드라이버를 APK에 넣거나, 제조사의 비공개 프로토콜을 자동으로 대체하지 않습니다. 공식 제조사 지원 범위와 제한은 `docs/VENDOR_DRIVER_INTEGRATION.md`를 참조하십시오.

## USB 기능과 실제 범위

- Android USB Host에서 장치 열거와 사용자 권한을 처리합니다.
- USB 프린터 상태 조회와 사전 작성된 PRN/PCL/PS RAW 데이터 전송은 지원 프로토콜 인터페이스에서만 동작합니다. RAW 전송 성공은 실제 인쇄 성공을 뜻하지 않습니다.
- USB IPP-over-USB의 제한된 프로파일에서 PDF 직접 인쇄를 시도할 수 있습니다. 프린터가 `application/pdf`를 광고해야 합니다.
- USB 스캔은 IPP-over-USB eSCL 장치에서 동작하도록 구현되어 있습니다. PTP는 호환 이미지 장치에서 JPEG를 가져오는 기능이며 일반 평판/ADF 스캐너 드라이버가 아닙니다.
- 제조사 전용 스캐너 프로토콜은 현재 지원한다고 표시하지 않습니다. 제조사 앱/SDK가 공개적으로 허용하는 USB 연동 경로가 모델별로 확인되어야 합니다.

## 버전

- 앱 ID: `com.galaxyuniversalio`
- 버전: `0.11.0`, versionCode `12`
- 최소 Android: API 26
- 배포 형식: 서명된 직접 설치 APK. Google Play 업로드용 AAB와 Play App Signing은 별도입니다.

## 검증 범위

GitHub Actions에서 Android release 빌드, 패키지 메타데이터, 비디버그 설정, APK 정렬과 Android 13 에뮬레이터 스모크 검사를 수행합니다. 에뮬레이터와 모의 USB/프로토콜 테스트는 실제 Galaxy, 실제 프린터·스캐너의 모델 호환성 증거가 아닙니다. 모델명·펌웨어·VID/PID를 기록한 물리 연결 시험이 끝난 모델만 호환 목록에 올립니다.

## 서명

서명 APK의 개인키는 저장소에 커밋하지 않습니다. 이 프로젝트 전용 PKCS#12 키스토어와 별도 암호 백업을 보관하며, 모든 후속 업데이트에서 같은 키를 사용합니다. 서명키를 분실하면 기존 설치본에 정상 업데이트할 수 없습니다. GitHub Actions 자동 서명을 사용하려면 릴리스 담당자가 `docs/VENDOR_DRIVER_INTEGRATION.md`와 키 인계서의 비밀정보를 저장소 Actions secrets에 직접 등록해야 합니다.

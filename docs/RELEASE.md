# Android 배포 후보 준비

현재 프로젝트는 M1 기능 검증 단계입니다. `assembleRelease` 산출물은 출시용 앱 ID(`com.galaxyuniversalio`)와 비디버그 빌드 설정을 사용하지만, **공식 서명키가 연결되기 전에는 unsigned release 후보이며 정식 배포본이 아닙니다.**

## 현재 CI 산출물

- `app-release-unsigned.apk`: 서명 전 보관·검토용
- 릴리스 동작 확인은 CI 실행 때만 생성하는 임시 키로 별도 복사본을 서명하여 Android 13 에뮬레이터에 설치합니다. 이 임시 서명본은 배포하지 않습니다.
- 실사용 업데이트에 사용할 키스토어 또는 비밀번호를 저장소에 커밋하거나 CI 로그에 출력하지 않습니다.

## 정식 APK 서명

릴리스 담당자가 소유·백업하는 영구 키스토어와 별칭을 준비합니다. GitHub 저장소의 Actions secrets에 다음 값을 등록하면 릴리스 workflow가 서명 APK를 만들도록 설계합니다.

- `ANDROID_RELEASE_KEYSTORE_B64`: PKCS#12/JKS 키스토어의 base64
- `ANDROID_RELEASE_KEY_ALIAS`: 키 별칭
- `ANDROID_RELEASE_STORE_PASSWORD`: 키스토어 암호
- `ANDROID_RELEASE_KEY_PASSWORD`: 개인키 암호

서명키는 모든 후속 업데이트에서 동일하게 사용해야 합니다. 분실하면 기존 설치본을 같은 앱 ID로 업데이트할 수 없습니다. 이 프로젝트는 현재 해당 secrets가 등록되었음을 확인하지 못했으므로, 서명된 정식 APK라고 표시하지 않습니다.

## 배포 전 확인

1. 실제 배포 대상과 배포 방식(직접 APK 배포 또는 Google Play)을 확정합니다. Play 배포는 별도 AAB, Play App Signing, 스토어 등록 자료와 심사가 필요합니다.
2. 릴리스 버전/코드를 올리고 changelog와 알려진 제한사항을 갱신합니다.
3. 서명 APK에서 `apksigner verify --verbose`를 실행하고 SHA-256을 기록합니다.
4. 실제 Galaxy 기기에서 설치·업데이트·권한·네트워크 탐색·취소·파일 저장을 확인합니다. 물리 스캐너/프린터 모델별 호환성은 별도 증거가 있어야 표시합니다.
5. HTTP LAN 스캔은 장치와 같은 신뢰 가능한 네트워크에서만 사용하도록 배포 안내에 명시합니다.

## 이 후보의 범위

CI 컴파일, 패키지 ID, 디버그 불가 설정 및 에뮬레이터 화면 흐름은 확인할 수 있습니다. 이를 실제 Galaxy, 실제 프린터/스캐너 호환성이나 정식 서명·스토어 배포 증거로 간주하지 않습니다.

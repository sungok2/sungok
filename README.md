# OpenScan 3D — Android 3D 스캐너 (소스 코드)

**버전:** 1.0.0 (2026-11-12)  
**상태:** 스캐닝 파이프라인 및 PC에서 가능한 단위시험 완료 / Android SDK 없는 작업환경에서 APK 컴파일·실기기 검증 미실시.

> **중요:** 이 저장소는 *설치 완료된 APK가 아닙니다.* `app-debug.apk`를 Android Studio 또는 GitHub Actions로 빌드해야 합니다. 실제 기기에서 스캔 품질 및 런타임은 검증되지 않았으므로 정밀 측량에 사용하지 마세요.

## 목표 및 기술적 경계

Scaniverse, Polycam, RealityScan의 아이디어를 참고하되 해당 앱의 소스 코드·상표·기술을 복제하지 않은 **독립 Android 앱**입니다.

- **ARCore Depth API:** 16비트 깊이 영상을 스마트폰 카메라 좌표 및 AR 추적 좌표계로 변환.
- **실시간 점군 및 간이 메시:** 깊이 프레임별 3D 좌표, RGB 색상, 셀별 삼각형 생성. 지오메트리는 ARCore 로컬 세계 좌표계 **미터** 단위.
- **중복 통합:** 모드별 복셀 해상도(1.5cm, 3.5cm, 7.5cm)로 중복점을 결합. 삼각형도 중복 제거.
- **메시 품질 필터:** 급격히 떨어진 점을 연결하지 않도록 에지 길이 제한 및 퇴화 삼각형 제거.
- **오프라인 파일 보관:** PLY(binary little-endian, 정점 RGB+삼각면), OBJ(정점과 면, 정점 RGB 확장 문법), JSON(GPS 참조 위치).
- **사진 데이터셋 선택 수집:** YUV 카메라 영상을 3초 간격 JPEG로 저장하고 카메라 포즈·내부 파라미터를 `photos/frames.jsonl`에 기록. 최대 150장. **사진을 3D로 재구성하는 SfM/MVS/NeRF 엔진은 앱에 포함되지 않았습니다.**
- **3D 뷰어:** 스캔한 PLY를 3D 점군으로 표시. 손가락으로 회전, 두 손가락으로 확대/축소. 미리보기에서 면 렌더링은 미지원.
- **외부 내보내기:** PLY / OBJ / JSON 개별 공유 또는 사진까지 포함하는 프로젝트 ZIP 공유.
- **인터넷 없음:** 카메라·로컬 파일만 사용. Google Play AR 서비스 설치·갱신은 별도 외부 의존성.

### 지원 기기 및 한계

- Android 8.0(API 26)+, ARCore 지원 기기, Google Play Services for AR 설치 필요.
- **Galaxy S20 Ultra 5G**는 공식 ARCore 목록에서 Depth API 지원으로 명시되어 있습니다.
- Depth API가 없는 ARCore 호환 기기에서는 **희소 특징점만 수집**하므로 메시 생성은 지원되지 않습니다.
- ARCore 깊이 이미지는 실측 LiDAR 점군과 다르고, 추정 깊이·SLAM 오차를 포함합니다.
- 장거리 야외 대상, 하늘·유리·반사면·움직이는 물체, 특징이 적은 장면, 추적 끊김에서 실패하거나 왜곡될 수 있습니다.
- 포즈를 전역 GPS 좌표로 정합하지 않습니다. GPS는 **스캔 장소의 참고용 점 하나**입니다. 절대 위치·축척의 측량 정확도 보장 없음.
- 저장 결과는 폐합된 watertight 메시, UV 텍스처 맵, 고정밀 CAD/BIM 모델이 아닙니다.
- 앱에 볼륨 제한이 있습니다: 최대 350,000 정점, 600,000 삼각면. 제한 도달 시 수집은 자동 중단하지만 화면에 별도 상태로 표시됩니다.

## 1. Android Studio로 APK 만들기

1. [Android Studio](https://developer.android.com/studio) 설치 후 `OpenScan3D/` 폴더를 **Open**.
2. SDK Manager에서 `Android SDK Platform 35`와 `Android SDK Build-Tools 35.0.0` 설치.
3. Java 17 및 Gradle **8.11.1** 환경에서 프로젝트 동기화. 루트 `settings.gradle` 및 `build.gradle`에서 필요한 Android Gradle Plugin 8.9.2, ARCore 1.56.0, AndroidX Core 1.15.0을 다운로드.
4. IDE가 Gradle wrapper JAR 누락을 보고하면 설치된 Gradle로 `gradle wrapper --gradle-version 8.11.1`을 한 번 실행하거나 **Gradle 설정에서 Local Gradle 8.11.1을 지정**. 이 소스 패키지에는 Gradle wrapper JAR이 포함되어 있지 않습니다.
5. **Build > Build Bundle(s) / APK(s) > Build APK(s)** 선택.
6. 출력 예: `app/build/outputs/apk/debug/app-debug.apk`.
7. USB로 Galaxy 스마트폰 연결 후 APK 설치. 카메라 권한 필수, 위치 권한은 선택. Play AR 서비스 업데이트 요구가 나타날 수 있습니다.

CLI 환경에서는 Android SDK/Gradle을 설치한 뒤 `./build_android.sh` 실행.

## 2. GitHub Actions로 APK 만들기 (PC에 Android Studio가 없을 때)

1. GitHub **비공개 저장소**에 이 디렉터리의 파일을 그대로 업로드.
2. GitHub 저장소의 **Actions > Build OpenScan 3D APK > Run workflow** 실행.
3. 빌드가 성공하면 **Artifacts > OpenScan3D-debug-apk** ZIP을 내려받고 내부 `app-debug.apk`를 추출.
4. 이 방법도 실제 GitHub Actions 작업 성공 및 APK 기기 설치 결과는 현재 검증하지 못했습니다.

워크플로: `.github/workflows/build-apk.yml`.

## 3. 사용 방법

1. 스마트폰에서 OpenScan 3D를 실행해 카메라 권한 허용.
2. 스캔 영역에 따라 **사물 / 실내 / 야외** 선택.
3. `사진 데이터셋도 수집`을 켜면 JPEG + 카메라 포즈가 함께 저장됩니다.
4. **스캔 시작**을 누르고 한 방향으로만 빠르게 걷지 말고 천천히 여러 각도에서 비춥니다. 빛이 충분하고 고정된 대상에 효과적입니다.
5. **일시정지** 또는 **저장**. 저장한 데이터는 내 스캔 목록에 유지됩니다.
6. **내 스캔 > 3D 점군 보기**에서 확인한 뒤 PLY, OBJ, 전체 ZIP을 PC에 공유.
7. PLY는 MeshLab, CloudCompare 등에서 확인하고 OBJ는 Blender 등에서 메시 확인.

**주의:** Android 11 이상에서는 앱 전용 외부 저장소(`Android/data/kr.openscan.scan/files/scans`)를 파일 관리자로 바로 여는 데 제한이 있을 수 있습니다. 반드시 앱의 공유 메뉴를 사용하세요. 앱 삭제 시 앱 전용 폴더도 삭제될 수 있으므로 중요한 자료는 ZIP으로 백업해야 합니다.

## 4. 저장 결과 구조

```
Scan_20261112_203000_123/
  model.ply           # binary PLY color point cloud + faces
  model.obj           # OBJ geometry + vertex RGB extension
  metadata.json       # frame/vertex/face count, approximate GPS
  photos/             # optional photo dataset
    frame_0001.jpg
    frame_0002.jpg
    frames.jsonl      # poses and intrinsics
  project_export.zip  # when exported
```

### 카메라 좌표

- ARCore 카메라 좌표: `-Z`가 전방, `+Y`가 상방.
- 픽셀 `(u, v)`, 깊이 `d`[m]와 카메라 내부 파라미터 `(fx,fy,cx,cy)`:
  `X=(u-cx)*d/fx`, `Y=-(v-cy)*d/fy`, `Z=-d`.
- 깊이 이미지와 CPU 카메라 이미지 사이 좌표는 `Frame.transformCoordinates2d(TEXTURE_NORMALIZED, IMAGE_PIXELS)`로 정합.
- 카메라 Pose를 이용해 로컬 세계 좌표로 변환. GPS는 정합에 **사용하지 않음**.

## 5. 검증 내용 (팩트 확인)

- Google ARCore 공식 지원 기기 목록: https://developers.google.com/ar/devices
- Google Depth API 개발자 문서: https://developers.google.com/ar/develop/java/depth/developer-guide
- 공식 Frame API: https://developers.google.com/ar/reference/java/com/google/ar/core/Frame
- Android Gradle Plugin 8.9 호환: https://developer.android.com/build/releases/agp-8-9-0-release-notes

### 실제 실행한 시험

- `ScanModel` 복셀 중복 통합, 2×2 점군의 두 삼각면 생성, 중복 면 차단, 프레임 누적 검증.
- `ScanFileFormats`의 실제 바이너리 PLY·ASCII OBJ 파일 생성, PLY의 vertex/face 헤더와 little-endian 정점 및 면 데이터 파싱 검증.
- **실행하지 못한 시험:** Android 프로젝트 컴파일, APK 패키징, 단말 설치, ARCore Depth 동작, 이미지/깊이 색상 정합 정밀도, 야외 장거리 품질, 사진 데이터셋의 COLMAP 호환성.

## 6. 다음 단계 — 고품질 재구성 (별도 작업)

진정한 Scaniverse/RealityScan 수준의 고품질 표면 복원에는 여러 장의 원본 사진을 이용한 SfM/MVS/3DGS 파이프라인, 대규모 장면 분할, 중첩 루프 폐합, UV 텍스처 생성, 리메싱이 필요합니다. 본 APK 소스에는 그러한 고밀도 사진 재구성 엔진이 구현되어 있지 않습니다. `photos/` 데이터를 향후 PC의 COLMAP·OpenMVS 워크플로 입력으로 가공할 수 있으나 데이터 품질이나 성공을 보장하지 않습니다.

## 개발 노트

- 소스: Java 17 + Android GL ES 2.0 + ARCore 1.56.0, UI는 Android 기본 위젯 사용.
- 외부 서버·광고·로그인·분석 SDK 없음.
- 파일 공유는 사용자가 명시적으로 선택해야 실행됨.
- 임시 JPEG는 저장 직후 프로젝트 폴더로 이동; 스캔 폐기 시 임시 데이터 삭제.
- `tests/`의 자바 단위 테스트를 PC에서 `javac` + `java`로 실행 가능.

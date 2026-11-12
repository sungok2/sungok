# OpenScan3D v1.3: 정밀도 개선 근거 및 제한사항

- 기존: smoothed depth에서 CPU 이미지로 좌표변환 후 3D 투영, confidence 없는 픽셀 누적.
- 개선: ARCore 공식 Raw Depth 예제에 맞춰 Texture Intrinsics를 깊이영상 해상도로 보정해 3D 투영.
- confidence 0~255 중 사물 95+, 실내 85+, 야외 75+만 채택. 기존 보간된 데이터 대신 Raw Depth 측정치 사용.
- 깊이 영상 타임스탬프가 ARCore 프레임 시간과 일치할 때만 누적; 과거 깊이 재투영 반복 억제.
- 사물: 화면 중앙 76% 영역, 중심 깊이 전후 제한 및 2.8m 최대 깊이 제한.
- 메시는 점군에 덮이지 않도록 기본 메시 전용 렌더링; 점군/메시 전환 제공.
- 스캔 기본 모드는 사물; 품질 부족 시 저장 전 경고 강화.

## 참고 근거
- https://codelabs.developers.google.com/codelabs/arcore-rawdepthapi
- https://developers.google.com/ar/develop/java/depth/raw-depth
- https://developers.google.com/ar/reference/java/com/google/ar/core/Camera

## 한계 및 검증 계획
- ARCore Raw Depth는 희소하게 측정되므로 표면 구멍이 발생할 수 있음. 정교한 모델링을 위해서는 사진기반 SfM/MVS 또는 TSDF/체적융합 등 추가 복원 엔진 필요.
- 사진만 보고 촬영 대상/AR 정합 오차의 원인을 단정할 수 없음. 본 수정은 타당성 있는 구조 개선이며 실기기 형태 정확도 미검증.
- Java 순수 기하 단위시험 + Android CI 빌드 완료 후 사용자 실기기 재검증 필요.
- 기존 앱 디버그 서명이 CI 빌드마다 달라 덮어쓰기 설치가 거부될 수 있으므로 별도 applicationId `kr.openscan.scan.precision`으로 설치합니다. 기존 스캔을 보존하려면 기존 앱에서 PLY를 공유/내보내기한 다음 새 앱에서 가져오기를 이용하세요.
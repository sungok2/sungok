# GalaxyUSB NTFS Direct M1 — 별도 APK 직접쓰기 엔진 설계 및 검증

작성일: 2026-11-13

## 문제를 바로잡는 개발

Galaxy Universal I/O M0.11 SAF 화면은 파일복사 검증을 수행하지만 Android가 NTFS를 읽기 전용으로 노출하는 경우 실제 NTFS 쓰기를 제공하지 않는다. 삼성 기본 파일 관리자에서 NTFS USB에 휴대전화 파일을 복사하지 못하는 문제가 생겼다.

이번 M1은 GPL-3.0-or-later 오픈소스 OTG Master v0.5.2의 사용자공간 USB Mass Storage + libntfs-3g JNI 소스를 고정해 **별도 설치 가능한 파생 파일 관리자**로 빌드한다. 완전히 새로운 NTFS 코드를 자체 개발했다는 뜻은 아니다. 상용·개인 데이터가 있는 USB에 실증 없이 사용을 권장하지 않는다.

원본 upstream 커밋: 2ddc5d8a4de728044322661439d68b2f1540bed3
소스: https://github.com/moylali/OTGMaster
빌드 스크립트: .github/workflows/build-ntfs-direct-engine.yml

## APK 변경 사항

- applicationId: com.galaxyuniversalio.ntfsdirect
- 앱 이름: GalaxyUSB NTFS Direct (GPL)
- upstream 파일시스템 및 암호화 엔진 코드 일체 변경 없음.
- Android USB Host 권한과 libaums를 통해 물리적 USB MBR/GPT 파티션에 접근.
- JNI native .so로 NTFS-3G 읽기·쓰기 지원.
- 원본 Kotlin 앱의 문서 제공자/파일 탐색/복사 UI 사용.
- GitHub Actions에서 upstream 단위시험, arm64+x86_64 native 라이브러리, APK 서명과 applicationId를 검사.
- GPL-3.0-or-later LICENSE 포함 수정 후 전체 소스 archive, SHA256 파일과 APK를 함께 공개 예정. 성공하지 않으면 배포했다고 표시하지 않는다.

## 사용자 주의사항

이 APK는 Galaxy Universal I/O M0.11의 in-place 업데이트가 아니며 그 앱의 프린터/스캐너 기능이 동일 APK로 통합된 것도 아니다. 이 APK만으로 자체 파일 관리자에서 USB NTFS를 여는 경로가 존재하지만, Samsung My Files가 인식한 기본 read-only 경로를 커널 단계에서 writable로 전환하지 않는다.

1. Windows에서 USB 중요 자료를 백업하고 NTFS 정상 종료 및 안전 제거 여부 확인.
2. 갤럭시에 APK를 설치한 뒤 USB-C OTG에 USB 드라이브 연결.
3. GalaxyUSB NTFS Direct 앱 실행, Android USB Host 권한을 허용.
4. NTFS 파티션을 열고 볼륨 상태가 read-write인지 확인. Dirty/hibernated/문제 있는 NTFS라면 쓰기 중단.
5. 앱의 파일 복사 UI 또는 앱이 제공하는 DocumentsProvider에서 휴대전화의 작은 테스트 파일을 USB 하위 폴더로 복사.
6. 앱에서 안전하게 언마운트 후 PC에서 파일을 재확인.
7. 100MB 및 4GB 이상 전송은 기본 작은 파일 성공 및 데이터 백업 후 시험.

**포맷/암호화 버튼을 누르면 USB 데이터를 잃을 수 있다.** 상위 원본 앱에는 이러한 기능도 있으나 이 변경에서 제거하지 않았다. 파일 쓰기 검증에 포맷은 필요하지 않다.

## 검증 경계

- 소스 확보, 코드빌드: GitHub Actions의 성공 여부 확인이 필수.
- Android Native ABI: ARM64와 x86_64 .so 포함 검사.
- 실물 갤럭시 + 구체적인 USB 장치: 미검증. Android USB 권한 경쟁, USB 허브 전원 부족, NTFS Dirty/hibernation 보호, 디스크 cache FLUSH/전원장애 시 영속성은 CI로 입증 불가.
- JNI 소스의 존재만으로 특정 삼성 휴대전화에서 반드시 쓰기가 된다고 단정하지 않는다.
- GPL-3.0-or-later / NTFS-3G GPL-2.0-or-later 등 원본 저작권 및 소스 고지 유지.

## 출처

OTG Master: https://github.com/moylali/OTGMaster/releases/tag/v0.5.2
NTFS-3G: https://github.com/tuxera/ntfs-3g
Android USB Host: https://developer.android.com/develop/connectivity/usb/host

# M1 LAN eSCL 자동 탐색 — 구현 범위와 검증 기준

**범위:** M1 LAN eSCL discovery + selected-device capability validation (2026-11-16)  
**기준선:** M0.9 commit `686776f9959ac792be804d990c340a32e0f6054f`

## 구현 범위

- Android NSD로 DNS-SD `_uscan._tcp.` 서비스를 탐색하고, 발견한 서비스를 resolve하여 서비스 인스턴스명, TCP 포트, IPv4 주소를 읽습니다.
- Wi-Fi multicast lock은 8초의 탐색 구간에 한해 사용하고 종료·취소 시 해제합니다.
- resolve된 서비스의 TXT `rs` 값을 eSCL 리소스 루트로 사용합니다. 값이 없으면 일반적인 `eSCL` 루트를 기본값으로 사용합니다. 경로는 경로 구분자 외의 URL 구성요소, 인코딩된 경로, 점 세그먼트 등을 거부합니다.
- 선택된 주소는 `IPv4:port/rs` 형태로 스캔 주소 입력란에 채워집니다. 전송 계층은 내부 eSCL 경로를 광고된 리소스 루트로 매핑하고, 서비스 루트가 포함된 ScanJobs Location을 기존 경로 검증을 통과하도록 정규화합니다.
- 검색 결과가 없거나 NSD가 실패하면 사용자가 입력한 주소로 계속 진행할 수 있습니다. 이전 탐색을 취소하고 새 탐색을 시작할 때 취소된 탐색 콜백이 최신 화면을 덮지 않도록 세대 참조를 검사합니다.

## 보안과 한계

- 현재 자동 탐색은 명시적으로 평문 `_uscan._tcp` 서비스만 대상으로 합니다. TLS `_uscans._tcp`는 서비스 호스트 이름과 인증서 신원을 전송 계층까지 보존할 수 있을 때까지 탐색하거나 HTTP로 강등하지 않습니다.
- 탐색은 IPv4 전용입니다. private LAN 또는 link-local 주소와 서비스 포트만 허용하는 기존 `LanEsclTransport` 검사를 그대로 적용합니다.
- 후보 발견만으로 호환 가능하다고 표시하지 않습니다. 사용자가 후보를 고른 뒤 읽기 전용 `GET /eSCL/ScannerCapabilities`를 보냅니다. HTTP 200과 유효한 platen 또는 simplex ADF 프로파일이 확인되고 나서 주소를 입력란에 적용합니다. 이 요청은 6초 읽기 제한을 두며 ScanJobs를 만들지 않습니다.
- 멀티캐스트 차단 VLAN, 게스트 Wi-Fi 격리, VPN/핫스팟 조합, 제조사별 장치, Galaxy S20 Ultra 실물 연결은 이 CI로 입증되지 않습니다.
- 서비스가 `rs`를 누락한 경우의 기본 루트는 편의용 추정값이며, 해당 장치에서 동작한다는 보증이 아닙니다. 실패 시 수동 경로 입력이 필요합니다.

## 검증 기준

1. 호스트 회귀 검사에서 resource root 검증, endpoint 매핑, 상대·절대 Location 정규화, traversal 및 cross-origin 입력 거부 확인.
2. 실제 Android SDK로 APK 컴파일, APK 서명/패키지 확인.
3. Android 13 에뮬레이터에서 앱 실행, 자동 탐색 버튼 실행, 결과 또는 가시적 NSD 오류 상태, 탭 이동, fatal exception 부재 확인.
4. 실기기 검증은 Samsung Galaxy S20 Ultra와 실제 eSCL 장치가 제공되어야 별도 완료 처리.

GitHub Actions에서는 host regression suite, mock eSCL capability/scan integration, APK build/signature/package checks, Android 13 emulator launch/navigation smoke를 실행합니다. 에뮬레이터와 mock server 결과는 물리 Galaxy/스캐너 호환성 증거가 아닙니다.

## 근거 자료

- Android `NsdServiceInfo.getAttributes()` API: https://developer.android.com/reference/android/net/nsd/NsdServiceInfo#getAttributes()
- eSCL DNS-SD 서비스 구현 예시(`_uscan._tcp`, TXT `rs=eSCL`): https://github.com/OpenPrinting/ipp-usb/blob/master/escl.go
- eSCL discovery output and resource path behavior: https://github.com/alexpevzner/sane-airscan

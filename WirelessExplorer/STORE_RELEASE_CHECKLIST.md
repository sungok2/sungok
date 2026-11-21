# Wireless Explorer v0.9.1 — Store Release Acceptance Gate

**Status: NOT YET APPROVED FOR COMMERCIAL SALES.** Code/CI improvements are verifiable, but owner signing, actual Galaxy radio reception testing and store approval cannot be inferred from emulators.

## Critical blockers
1. **Play Console ownership**: developer account, payments profile, paid-app configuration, store verification, market availability and any account-specific testing must be completed by the owner.
2. **Store API policy**: target Android 16 (API36), checked at binary manifest level by CI. Do not bump to API37 without testing new ACCESS_LOCAL_NETWORK permission rules.
3. **Stable confidential signing key**: configure four Actions secrets in RELEASE_SIGNING.md. Never commit/export private keys. Verify APK certificate, signed AAB and upgrade continuity.
4. **Actual radio/hardware quality**: repeat on Samsung Galaxy Android 13+ and Android 16 with known BLE beacon, discoverable BT, 2.4/5 GHz APs, local authorized printer/TV/PC, positive and negative permissions. Record expected vs observed results, false merges, Wi-Fi scanning restrictions and timing.
5. **Permissions and privacy**: review first-run disclosures, Android location permission scope, published privacy policy URL, Play Data Safety declarations, deletion and local export. Confirm no SDK/network telemetry. Review all usage of MAC addresses in promotional copy.
6. **Commercial QA**: install and upgrade with *same stable key*, backup/import, rotation/background, system dark mode, large font, TalkBack, silent mode/haptic controls, performance on 3000 records, low battery/connection loss, storage full and invalid inputs. Record defects; do not claim 100% nearby coverage.
7. **Google Play closed testing**: some new personal developer accounts (created after 2023-11-13) require 12 opted-in testers continuously for at least 14 days, followed by approval for production access. Confirm applicability in Play Console.
8. **Store release artifacts**: owner-signed AAB, screenshots of genuine UI, app icon, accurate feature description, support contact, accessible privacy-policy web URL, release notes and pricing.

## Test / verification matrix

| Item | Automated | Physical Galaxy | Store owner |
|---|---|---|---|
| API36 target and AAB structure | CI | Compatibility check | Play submission |
| JVM tests, Lint, UI instrumentation Android 13/15/16 | CI | Feature reproduction | — |
| Stable signing & AAB signature | owner-key workflow after secrets | upgrade test | key backup |
| BLE/Classic/Wi-Fi radio recall and precision | simulated metadata only | **REQUIRED** | — |
| LAN TCP/UPnP consent and private-subnet limits | tests | authorized LAN | privacy policy |
| Accessibility, battery, ANR/crashes | partial emulator | **REQUIRED** | quality monitoring |
| Paid listing / payment | not in source code | — | Play Console |

## Positioning (truthful)
Korean title: **Wireless Explorer — 주변 기기 탐색**
Tagline: **BLE·Wi-Fi·Bluetooth 발견 정보를 한눈에 기록하고 분석**
Description: `Android가 제공하는 근거리 무선 탐색 정보를 기기에 저장하고 검색·필터·RSSI 변화·CSV/JSON으로 정리하는 도구입니다. 현재 Wi-Fi 접속점과 (명시적으로 허용한 사설 LAN의 경우) 응답하는 주소/서비스를 별도로 확인합니다. 일부 무선 방식과 비응답 기기는 탐지할 수 없습니다.`
Never claim: 모든 무선기기 100% 탐지 / 실시간 위치 추적 / 특정 인물·기기의 신원 확인 / Wi-Fi 장치를 모두 찾음.

## Commercial model
A paid upfront app via Google Play requires **store owner account/payment and price configuration**, not custom payment collection code. No ads, personal tracking, third-party backend or IAP SDK should be introduced solely to charge for app access. If free-trial or subscriptions become requirements, a separate properly implemented billing/entitlement design is necessary; do not silently invent one.

## Source links
- Android Google Play target API requirements: https://developer.android.com/google/play/requirements/target-sdk
- Personal developer account testing: https://support.google.com/googleplay/android-developer/answer/14151465
- Local-network permission forward compatibility: https://developer.android.com/privacy-and-security/local-network-permission
- Signing: https://developer.android.com/studio/publish/app-signing
- Google Play policies: https://play.google.com/about/developer-content-policy/

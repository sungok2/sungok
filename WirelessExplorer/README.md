# Wireless Explorer for Galaxy

**Version:** 0.9.0 (Google Play API36-compatible unsigned/debug QA candidate; owner signing and Galaxy field acceptance pending)  
**Package ID:** kr.sungok.wirelessexplorer  
**Requirements:** Android 8.0+ (API 26), Bluetooth/Wi-Fi hardware for the corresponding scans.  
**Status:** Source implementation and GitHub Actions APK build workflow. An actual Galaxy hardware test is required before any production claim.

## Purpose

Catalog as many **detectable nearby wireless endpoints** as Android public APIs allow without root, external receivers, pairing, connections or network scanning attacks. Runs on the phone, has no server, no telemetry, no analytics, and writes observations only to app-private storage until the user explicitly exports them.

**Important:** It is not technically possible for a Galaxy phone to enumerate every physical wireless device. One device can appear under several protocols/addresses and devices with randomized BLE MAC addresses can appear multiple times. Some stations do not advertise at all. Zigbee, proprietary sub-GHz RF, LoRa, UWB, mobile network radios, and other unexposed bands require compatible external radio hardware and often cooperation from the target device. Signal strength is **not** a location or distance measurement.

## Implemented

- BLE unfiltered advertisements, including name when disclosed, MAC/address (may be randomized), RSSI, manufacturer IDs and number of advertised service UUIDs.
- Bluetooth Classic inquiry, only for responding discoverable devices.
- Wi-Fi access point results: SSID/BSSID, frequency, advertised capabilities, RSSI. Cached results are explicitly tagged; new scan results are tagged as such.
- Wi-Fi Direct peer discovery with device name/address, no connections.
- Rotating mDNS/DNS-SD service discovery on the *already connected local network*. Twelve common service types.
- Three-minute foreground scanning sessions with **separate BLE discovery / Bluetooth Classic inquiry phases** to reduce radio competition and lower power after initial high-intensity BLE scan.
- Optional UPnP/SSDP discovery: sends a single standard M-SEARCH on connected private LAN when the user explicitly enables the checkbox; reads responses only, never follows returned URLs. Do not enable without network-owner authorization.
- User-controlled stop, scan callback invalidation and cleanup on backgrounding.
- Per-radio-endpoint local history, current session flags, basic heuristic category labels and last-seen timestamps.
- Wi-Fi scan result timestamps are checked against the session start time so cached AP records never masquerade as freshly observed endpoints.
- Multicast lock held while foreground mDNS discovery runs (important for older Android versions).
- Twelve DNS-SD service types rotated during the three-minute scan.
- Per-protocol scanner state, displayed alongside fresh endpoint counts and failure reasons.
- Separate labeling of unconfirmed cached entries versus previously confirmed live detections.
- Atomic local-history writes and explicit save-error warnings; verified last-confirmed timestamp never overwritten by stale Wi-Fi cache.
- v0.4.0: protect all verified signal details, RSSI, and last-seen time against subsequent cached Wi-Fi reads; cached-only records do not appear to be newly observed after repeated cache reads.
- v0.4.0: restore an interrupted atomic history write from the previous good backup and validate the recovered file size.
- v0.4.0: UPnP M-SEARCH is blocked when the active network is not Wi-Fi or is a VPN, and its UDP socket is bound explicitly to the active Wi-Fi network. Overlapping UPnP searches are rejected.
- v0.4.0: accept standards-conforming legacy HTTP/1.0 SSDP responses as well as HTTP/1.1.
- v0.4.0: show saved history on initial launch; countdown tasks deduplicated on scan restarts.
- v0.4.0: Android 13 and 15 emulator CI jobs with cache rollback instrumentation; no physical radio performance claim.
- v0.5.0: mDNS/DNS-SD service-type rotation waits for Android's asynchronous onDiscoveryStopped callback before starting the next discovery; avoids resource overlap.
- v0.5.0: opt-in diagnostics show Bluetooth, Wi-Fi, location service and precise/nearby permission readiness, useful when a known device is missing.
- v0.5.0: bounded per-endpoint RSSI history (24 recent confirmed samples, at most one per second) and a mini signal bar display. These are uncalibrated radio measurements; they cannot establish distance or direction.
- v0.5.0: CSV/JSON also include those bounded RSSI observations; cached scan entries never change the verified history.
- v0.6.0: a bounded BLE advertisement gate limits duplicate callback pressure while preserving first sightings and prompt metadata changes. Stale session emissions are dropped, including callbacks arriving after a rapid stop/restart.
- v0.6.0: mDNS rotation recovers if Android confirms an old listener stopped *after* the user started a new scan; bounded stop retries and no concurrent service-discovery listeners.
- v0.6.0: BLE advertising TX power metadata when present; user-facing BLE/Classic same-address candidate notice without merging physical identities.
- v0.6.0: user can register **known test targets** by tapping a discovered endpoint or entering exact PROTOCOL,ID[,label] rows, up to 60; shows this session's exact-match observed/missed counts. Names are never used for exact identity matching and missed targets are not assumed physically absent.
- v0.6.0: JSON history **import** through the system file picker for upgrades requiring uninstall due to changed CI debug signatures. Imported records are validated, deduplicated, restored to atomic local storage and marked external; never counted as present in the current scan. Malformed or oversized imports are rejected.
- v0.6.0: API 33/35 emulator integration test verifies JSON recovery, correct imported provenance and scan-history preservation.
- v0.7.0: separate **three distinct inventories**: nearby RF endpoints (BLE/Classic/Wi-Fi AP), optional responders to limited TCP probes on the *currently connected private Wi-Fi LAN* (which can be wired devices), and NSD/UPnP services. Never equate those counts with distinct physical devices.
- v0.7.0: if Android's nearby AP scan list is empty/throttled, independently try the currently associated Wi-Fi AP's SSID/BSSID/frequency through public connection APIs. Only a valid actual connection BSSID is counted as present. Scanner reports new APs, raw AP list size, request success, OS scan broadcasts and active-association state.
- v0.7.0: user opt-in front-page checkbox performs bounded authorized LAN discovery: ≤253 private IPv4 addresses on the Wi-Fi interface subnet, ≤7 common service TCP handshake attempts/address, maximum 12 concurrent checks, 42-second overall deadline. No login, HTTP payload, authentication, public addresses, VPN connections or unrestricted port scans. Device IPs with **no open probed port will not appear**, so 0 responders is not a proof of absence. Network permission from its owner is necessary.
- v0.7.0: Bluetooth SIG advertising company-ID labels for a handful of verified IDs (including Samsung 0x0075), identified only as company ID, not as validated physical-device manufacturer/model.
- v0.7.0: dashboard distinguishes active Wi-Fi from merely Wi-Fi radio on; radio status after 180 seconds shows the final Wi-Fi scan diagnostics rather than the misleading '검색 중' state.
- v0.7.0: CSV includes restored-history provenance and API 33/35 CI tests count LAN entries distinctly from RF endpoints.
- v0.6.1: internationalized count labels resolve two Android Lint warnings; CI tests AOSP Android 13 and Google APIs Android 15 to separate app failures from unrelated Pixel Launcher ANR popups. The remaining warning to review is the intentional targetSdk 35 versus latest API level.
- Sanitized and bounded device names/addresses/details; MAC normalization, protocol-specific identity without incorrect cross-protocol merging.
- Separate confirmed RF endpoints and LAN service counts; only *candidate* same-address Classic/BLE links, not automatic physical-device counts.
- Compact dark results-first UI with collapsed advanced tools/diagnostics (prevents initial controls from pushing results offscreen), responsive landscape typography and ForceDark disabled.
- Search by device name/address/details, unknown-only view, pagination, live scan countdown and evidence-oriented status labels.
- User-exportable text diagnostic report with per-protocol status, counts, limits and a field verification checklist.
- Wi-Fi 2.4/5/6 GHz band and channel classification, BLE advertised service identifiers.
- Android unit tests for timestamps, normalization, CSV safety, SSDP header parsing, scan schedule and report correctness.
- User-accessible full deletion of local radio observations; periodic local persistence.
- CSV formula-injection hardening for untrusted network/device names.
- Filtering by protocol; CSV and JSON exports through Android's system file picker.
- Fully local processing with no backend. The INTERNET permission is requested for local network service discovery; the app contains **no HTTP client or remote server URL**.
- Unit tests for signal-label boundaries, unknown-device classification, CSV escape handling.
- GitHub Actions builds debug APK and its SHA-256 hash.

## Source layout

- app/src/main/java/.../MainActivity.java: offline user interface, permission prompts, filtering/export.
- app/src/main/java/.../WirelessScanner.java: asynchronous Bluetooth, BLE, Wi-Fi, Wi-Fi Direct and NSD discovery.
- app/src/main/java/.../DeviceStore.java: local JSON ledger and CSV/JSON export.
- app/src/main/java/.../DeviceRecord.java: one endpoint observation.
- app/src/main/java/.../SignalRules.java: label-only classification; does not pretend to identify unknown hardware.

## Build

GitHub > Actions > **Wireless Explorer Android APK** > select a successful run > Artifacts > **WirelessExplorer-v0.9.0-debug-apk**.
Unzip and install app-debug.apk on your Galaxy (allow installation from that source if prompted). Each CI run uses GitHub's temporary debug key. Uninstalling a previous build may be required before installing a build signed with a different key; uninstalling removes the app's local history, so export it first.

To build with Android Studio, open **WirelessExplorer/** as its own project, use JDK 17, Android SDK platform 35, Android Gradle Plugin 8.9.2 and Gradle 8.11.1. A Gradle wrapper is not committed; GitHub Actions provisions Gradle.

## Runtime usage

1. Turn on Bluetooth, Wi-Fi and **Location services** on the Galaxy.
2. Press **탐색 시작** and grant Nearby devices, precise location, and (Android 13+) Nearby Wi-Fi devices.
3. Keep the app visible for up to three minutes. Walk slowly around the target area if needed. A single snapshot is incomplete; some advertisement cycles are long.
4. Use protocol filters. **이번 탐색만 표시** excludes historical entries and initial cached Wi-Fi results that have not been reconfirmed.
5. Tap a record to register its exact protocol/address as a test target; long-press to copy details. The expandable **추가 옵션** panel also lets you paste target rows as `PROTOCOL,ID,optional-name`. Targets are tested against exact endpoint IDs during *later* sessions; a missing target does not prove the radio device is absent.
6. Export a JSON backup before uninstalling an older APK. After installing the new APK, open **추가 옵션** > **JSON 탐지 기록 복원** and select that backup. Imported history is labeled external and is never counted as present in the current scan.
7. Export CSV/JSON/TXT diagnostic reports after completing an inventory session; use the exact-target report to record observed and missed known endpoints.
8. A scan stopped by the screen changing or the app leaving the foreground is intentional; start a new session after returning.

## Permissions and limits

- BLUETOOTH_SCAN and BLUETOOTH_CONNECT for Android 12+ BLE/Classical scans.
- ACCESS_FINE_LOCATION for Wi-Fi scan visibility and BLE scanning without neverForLocation (which could filter BLE beacons). The app does not request GPS coordinates.
- NEARBY_WIFI_DEVICES for Android 13+ Wi-Fi Direct peer discovery.
- ACCESS_WIFI_STATE/CHANGE_WIFI_STATE for Wi-Fi scans.
- INTERNET and CHANGE_WIFI_MULTICAST_STATE reserved for local service discovery. No cloud transmission.
- Bluetooth and Wi-Fi can be disabled independently. Scan throttling, background limits and vendor differences can suppress results.
- Android 9+ Wi-Fi scan throttling commonly limits foreground scans to four per two minutes. App requests new scans every 35 seconds, and reads cached scans in between. Scan result timestamps determine whether each access point was actually observed during the current session. Search results can be stale or missing.
- BT Classic inquiry may compete with BLE and is scheduled once after the session starts.
- mDNS service discoveries are service endpoints, not guaranteed distinct physical devices.
- No authenticated LAN connections, port scans, brute-force, decryption or RF decoding are implemented.
- The UPnP checkbox initiates *only* UDP discovery on the connected LAN; it is disabled by default and cannot examine devices on unrelated networks.
- Android 17 (API 37) local network permission policies will require another compatibility review if a future app update targets API 37+.
- On recent Android versions, local-network permission policy may evolve; test on the actual device/OS.

## v0.9 store-readiness changes (2026-11)
- Google Play submission requires Android 16 API 36 for regular phone apps; now compileSdk/targetSdk 36 using AGP 8.11.2 + Gradle 8.13.
- GitHub CI verifies targetSdk 36 inside the compiled debug APK and creates an *unsigned structural* release App Bundle (AAB), in addition to unit, lint and Android 13/15/16 emulator instrumentation.
- Owner-secret-protected release workflow creates and verifies BOTH a persistently signed APK and AAB; it cannot execute successfully without the owner's four GitHub Actions signing secrets.
- First-run prominent disclosure explains nearby identifiers and location permission before any Android permission prompt. LAN TCP/UPnP scanning requires a separate explicit authorized-network consent.
- Leaving the app during discovery now resets the stale "stop scanning" UI and records the stopped session; the app remains foreground-only and offline-first.
- Google Play privacy, store submission checklist and honest feature limitations documented; no claim that approval, monetization or field RF acceptance has occurred.

## v0.8 product experience and visual design

Inspired by familiar interaction patterns (not copied artwork or source) from Fing's device-centric network list, WiFiman's connection/signal dashboards and nRF Connect's BLE evidence, with a native, offline-first Android implementation:

- Redesigned dark, contrast-oriented dashboard with an animated decorative scan ring **clearly not a radar map**; compact RF/LAN/services indicators and timed progress bar.
- Result-first hierarchy: one large scan action, one export action, exact LAN opt-in, search, per-protocol count chips, session/unknown filters, and recent/signal/name sorting.
- Compact device cards expand only when needed to show precise metadata, evidence source, verified timestamps, same-address warnings and a real bounded RSSI trend rendered as a native Canvas line graph.
- Opt-in low-volume Android system tones for start, finish and at most eight new devices per session, with an 8.5-second cooldown. Sound is **OFF by default** and respects the notification audio stream. Haptic button feedback is optional by device settings.
- Accessibility: minimum 44dp tap targets, high-contrast tags and readable type, content descriptions for the animated ring and chart, visual cues independent of audio, and Android reduced-motion preference.
- Native source assets and no external graphic, font or audio licensing dependencies.
- Android 13/15 instrumentation UI tests verify the controls, disclosure, sound switch and drawing without claiming hardware RF capture.
- Intentional limits: Android 8+/API26 baseline, target SDK35, **debug signing from ephemeral CI**; production signing, Galaxy real-device RF field acceptance, and Play Store publication are separate release gates.

## Verification checklist (physical Galaxy)

- [ ] Install the APK on Galaxy and launch without crashes
- [ ] Approve runtime permissions; verify Bluetooth and Location are enabled
- [ ] Find an actively advertising BLE peripheral and inspect RSSI
- [ ] Find a discoverable Classic Bluetooth peripheral
- [ ] Find known Wi-Fi AP BSSID and confirm fresh versus cached label
- [ ] Optionally detect a Wi-Fi Direct peer and local mDNS service
- [ ] Stop and restart scans; ensure UI responds and no duplicate receiver crashes
- [ ] Export CSV/JSON, reopen files, compare unique endpoint counts
- [ ] Confirm stale Wi-Fi results are labeled as cached and not counted as freshly seen
- [ ] Confirm deletion clears saved data and cannot be undone
- [ ] Confirm missing scanner permissions are reported per protocol rather than aborting all scanning
- [ ] Deny permissions and verify readable error feedback
- [ ] Verify BLE active -> Classic inquiry -> balanced BLE transition
- [ ] Verify UPnP default off, opt-in requests remain LAN-only, and service entries are not counted as RF endpoints
- [ ] Confirm recent record remains dated by true fresh discovery after cached Wi-Fi reads
- [ ] Export and verify diagnostic TXT, CSV, JSON files
- [ ] Check search, unknown-only filter, pagination, accessibility text sizes and screen rotations
- [ ] Turn screen off/leave app and confirm scanner stops
- [ ] Check battery use during the 180-second scan

The Android build/unit tests alone cannot prove that radio scanning works on real Galaxy hardware.

## Official Android references

- Bluetooth permissions: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
- Wi-Fi scans and restrictions: https://developer.android.com/develop/connectivity/wifi/wifi-scan
- Wi-Fi Direct: https://developer.android.com/develop/connectivity/wifi/wifip2p
- NSD: https://developer.android.com/develop/connectivity/wifi/use-nsd

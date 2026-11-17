# Wireless Explorer for Galaxy

**Version:** 0.2.0 (engineering prototype)  
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
- Rotating passive mDNS/DNS-SD service discovery on the *already connected local network*. Eight common service types.
- Three-minute foreground scanning sessions, user-controlled stop and guaranteed scan cleanup on backgrounding.
- Per-radio-endpoint local history, current session flags, basic heuristic category labels and last-seen timestamps.
- Wi-Fi scan result timestamps are checked against the session start time so cached AP records never masquerade as freshly observed endpoints.
- Multicast lock held while foreground mDNS discovery runs (important for older Android versions).
- Twelve DNS-SD service types rotated during the three-minute scan.
- Per-protocol scanner state, displayed alongside fresh endpoint counts and failure reasons.
- Separate labeling of unconfirmed cached entries versus previously confirmed live detections.
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

GitHub > Actions > **Wireless Explorer Android APK** > select a successful run > Artifacts > **WirelessExplorer-v0.2.0-debug-apk**.
Unzip and install app-debug.apk on your Galaxy (allow installation from that source if prompted). Each CI run uses GitHub's temporary debug key. Uninstalling a previous build may be required before installing a build signed with a different key; uninstalling removes the app's local history, so export it first.

To build with Android Studio, open **WirelessExplorer/** as its own project, use JDK 17, Android SDK platform 35, Android Gradle Plugin 8.9.2 and Gradle 8.11.1. A Gradle wrapper is not committed; GitHub Actions provisions Gradle.

## Runtime usage

1. Turn on Bluetooth, Wi-Fi and **Location services** on the Galaxy.
2. Press **탐색 시작** and grant Nearby devices, precise location, and (Android 13+) Nearby Wi-Fi devices.
3. Keep the app visible for up to three minutes. Walk slowly around the target area if needed. A single snapshot is incomplete; some advertisement cycles are long.
4. Use protocol filters. **이번 탐색만 표시** excludes historical entries and initial cached Wi-Fi results that have not been reconfirmed.
5. Long-press a record to copy details. Export to CSV/JSON if needed.
6. A scan stopped by the screen changing or the app leaving the foreground is intentional; start a new session after returning.

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
- No authenticated LAN connections, service probing, port scans, brute-force, decryption or RF decoding are implemented.
- On recent Android versions, local-network permission policy may evolve; test on the actual device/OS.

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
- [ ] Turn screen off/leave app and confirm scanner stops
- [ ] Check battery use during the 120-second scan

The Android build/unit tests alone cannot prove that radio scanning works on real Galaxy hardware.

## Official Android references

- Bluetooth permissions: https://developer.android.com/develop/connectivity/bluetooth/bt-permissions
- Wi-Fi scans and restrictions: https://developer.android.com/develop/connectivity/wifi/wifi-scan
- Wi-Fi Direct: https://developer.android.com/develop/connectivity/wifi/wifip2p
- NSD: https://developer.android.com/develop/connectivity/wifi/use-nsd

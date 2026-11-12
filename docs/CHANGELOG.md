# Changelog

## M0.3 / 0.3.0-source — 2026-11-12

- Added PDF handoff through Android Print Framework, including bounded 32MiB PDF copy and cancellation (not a direct USB PDF driver).
- Added PTP Still Image Class 6/1/1 USB JPEG import, limited to first 16 object handles and 16MiB per image (not universal document scanning).
- Detected USB Printer Class 7/1/4 IPP-over-USB while explicitly prohibiting unsupported RAW transfers.
- Implemented alternate interface setting handling and failure cleanup for PTP and Printer Class transports.
- Expanded independent Java/fake USB tests from 71 to 121 checks; app Java mock signature smoke passes for 10 files.
- Added Windows ADB install helper, hardware test checklist, and CSV template; no actual APK built or device tests performed.

## M0.2 / 0.2.0-source — 2026-11-12

- Added read-only USB Printer Class GET_DEVICE_ID/GET_PORT_STATUS probe.
- Added user-confirmed pre-encoded PRN/PCL/PS RAW send over Printer Class 1/2, transfer bounds and error handling.
- Hardened USB permission callback and added diagnostics.
- 54 pure Java checks and 17 fake USB checks at that milestone.

## M0.1 / 0.1.1-source

- Added USB Host permissions, descriptor enumeration, hardware evidence profiles and opt-in serial reporting.
- Added report export and baseline host tests.

This changelog reports source changes; neither Android APK nor physical scan/print success has been verified.

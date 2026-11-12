# Follow-on roadmap: M1+ milestones and acceptance gates

**Goal:** a model-evidence-backed USB scan/print platform, not an unsupported claim of universal hardware compatibility.

| Stage | Deliverable | Acceptance gate |
|---|---|---|
| M0.3 (current) | USB diagnostics, guarded RAW send, Android PDF Print Framework handoff, PTP still-image JPEG input | JDK and fake-transport tests pass; **Android build/real hardware still open** |
| M0.3a | Samsung Galaxy S20 Ultra / Android 13 end-to-end APK | `:app:assembleDebug` passes, APK installed, USB device/permission/detach/reconnect/report tested |
| M1 | Device capability profiles, structured test reports | VID:PID+interface+firmware, PDL evidence and hardware test date captured; no inferred compatibility |
| M2 | USB printer model adapters and PDL rendering | Confirmed models print a calibrated single-page file; error and status reporting measured |
| M3 | USB scanner protocols and image acquisition | One specifically identified supported scanner returns full image, dpi, color and cancel handling |
| M4 | Print/scan pipeline and document handling | PDF/image conversion, user print options, scanner save formats; device/format combinations validated |
| M5 | Multi-device hardening and automated regression | Device matrix, Android versions, reconnect/power handling, no-leak stress tests |

### Scanner transport research conditions

USB Still Image class 06 is **not** synonymous with a document scanner; digital cameras and other devices also use it. Some scanners present a vendor-specific USB interface. The team must capture the exact VID:PID, interface descriptors, official protocol documentation or permitted reverse-engineering evidence **before** writing a model adapter. Support claims require at least one physical scan with a documented device + firmware revision.

### M0.3a mandatory test matrix

1. On Windows 11, JDK 21 and installed Gradle 9.8.0, install Android SDK 36/Build Tools 36.0.0. Build `:app:assembleDebug` and record SHA-256 and logs.
2. On Galaxy S20 Ultra Android 13, confirm USB Host enumeration and permission grant/deny for a known printer model; verify no crash when USB removed during probe.
3. Issue `GET_DEVICE_ID` and `GET_PORT_STATUS` and compare bytes with documented output from same printer.
4. With a printer-language file **known supported by that printer**, record number of bytes sent, physical paper result, errors, and repeatability.
5. Confirm documents larger than 8 MiB are rejected **before** any USB send, unsupported device classes/protocols have RAW transfer disabled, and user cancellation never sends.
6. Confirm TXT report exports without serial number unless explicitly enabled.
7. Collect device VID/PID, model, interface/subclass/protocol, PDL, tester and date into `profiles.tsv` only with justified evidence level.

The above tests cannot be marked passed without actual APK and hardware evidence.

## M0.3 확장 상태
- PTP Still Image Class JPEG 단일 입력의 소스 구현을 추가했으나, 일반 문서 스캐너 실물 이미징 구현을 의미하지 않음.
- Android Print Framework PDF 전달 소스 구현은 USB 직접 인쇄용 드라이버와 구분.
- IPP-over-USB Printer Class 프로토콜 4는 분류만 구현, HTTP/IPP는 미구현.
- 장치별 확증 없는 호환성 선언은 금지.

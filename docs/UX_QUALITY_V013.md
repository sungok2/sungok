# Galaxy Universal I/O — UX / Audio / Speed Review v0.13.0

Date: 2026-11-22. Android target: API 36, minimum API 26. Phone-first layout, reference target: 320×640 emulator and Samsung Galaxy Android 13. This is a source/build validation note, **not** evidence of USB device compatibility.

## Grounded competitor study (design patterns, not protocol promises)

| App | Official capability / interface signal | Adapted, original product decision |
| --- | --- | --- |
| Epson Smart Panel | Printer/scanner setup, monitoring, print/scan from a unified device-control interface (https://epson.com/Mobile-Printing-and-Scanning-Solutions/Printing-and-Scanning-Solutions/Epson-Smart-Panel%E2%84%A2-App/p/SMARTPANELAPP) | Three-step device status and short task labels, without copying graphics |
| HP app (formerly HP Smart) | A device home screen links to print, scan, settings and troubleshooting (https://support.hp.com/us-en/document/ish_7449949-7450003-16) | Device-first status and one-tap navigation to print / scan |
| Mopria | Android system printing and dedicated scanning, with device- and capability-dependent features (https://mopria.org/print-from-android; https://mopria.org/faq) | Keep PrintManager and USB direct workflows visibly distinct |
| Samsung Mobile Print | Printer connection, mobile print/scan focused mostly on network methods (https://play.google.com/store/apps/details?id=com.sec.print.mobileprint) | Do not misrepresent another app's Wi-Fi printing as direct wired USB support |

No competitor art, icons, audio or source code is redistributed.

## Changes

- Fixed **bottom** navigation for Scan / Print / Devices; long technical content can scroll without hiding actions.
- 2026 Android 16 edge-to-edge system bar safe area, legacy Android 8–10 fallback.
- Unified gradient product header, neutral card background, larger 54dp action targets, improved text rhythm, high-contrast primary color.
- Step-by-step connected-device evidence: 01 no USB enumeration, 02 permission required, 03 permission granted. State 03 **never** claims that paper printed or a scanner works.
- Two explicit actions from Devices to Print and Scan.
- Optional user-controlled outcome sound (default off), honoring silent/vibrate mode, and haptic feedback (default on, uses system View feedback setting). Values persisted without networking.
- Quick visual 160ms crossfade for tab content, guarded by Android's global animation-enabled flag.
- USB inventory signature cache: rebuild Spinner and verbose diagnostic report only if the device ID/name/permission state, serial choice or most recent printer probe changes.
- State-sensitive audio feedback on deliberate user operations: completed printer data transfer, scanner PDF saved, printer status probe, and errors. A successful USB transfer is explicitly not a successful paper output.
- More predictable report filename and version.

## Acceptance criteria / evidence gates

1. **Android SDK compile**: Gradle assembleDebug and assembleRelease; no warnings treated as success if exit nonzero.
2. **Host regression**: all existing USB, IPP, eSCL, TIFF/PDF and safety checks must pass.
3. **Android 13 UI smoke**: app opens; USB no-device state; USB-attach missing payload safe; fixed tabs usable after scroll; Scan/Print navigation; optional audio checkbox persists after process restart; no app fatal log.
4. **Android 16 UI verification**: physical/emulated build recommended; edge-to-edge avoids status/navigation bar overlap.
5. **Physical compatibility**: real Galaxy + printer/scanner via OTG; cable unplug, power, permissions, first print/scan, replug. Not established by emulator.
6. **Perceptual QA**: screen reader TalkBack, large font scale, landscape, touch target contrast, animation reduction and silent mode on physical devices.
7. **Performance**: compare cold-start activity launch milliseconds and repeated inventory refresh with prior build **on the same phone**. No FPS or latency claims without measurements.
8. **Commercial readiness**: stable signing, policy/data declarations and model-specific physical certification still mandatory.

## Known limitations

- Some printers expose proprietary USB interfaces or do not accept PCL, PS3 or IPP-over-USB. A USB device appearing does not indicate interoperability.
- The control tones are original platform-generated system sounds, not proprietary brand sounds. Audio is opt-in and never makes unsupported hardware assertions.
- App uses a deliberately light native Android surface; dark-mode dedicated palettes and tablet two-pane layouts have not been verified.
- Dynamic font scaling, audio volume policy on custom firmware, animation preferences and edge-to-edge require device QA; emulator results are a subset.

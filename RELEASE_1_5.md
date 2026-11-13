# OpenScan 3D Guide v1.5 — UX and sound iteration

## What changed

- Camera-first screen: mode-specific concise guidance, minimal overlay while scanning, 48 dp top controls, and separate options / library / help affordances.
- Observable state coach: no data, AR tracking, low coverage, intermediate collection, ready to inspect. Four-part stage indicators **do not represent reconstruction accuracy**.
- Contextual hints: object, room, and outdoor guides in Korean; surface/lighting/distance and the limits of raw depth are disclosed.
- User feedback: 5 original procedurally synthesized low-volume WAV sonifications (start, pause, first valid capture, saved, error), optional haptic taps, toggles persisted locally; sounds honor the phone's silent/vibrate mode.
- Recoverable flows: saving with 0 points opens diagnostic rather than pretending files exist, and settings can be opened mid-scan (pauses capture). Non-audio options stay locked during an unfinished scan.
- Previous v1.4 package is left intact; v1.5 uses a distinct package ID to avoid ephemeral debug signing-key update conflicts.

## Scope and honest limitations

- **Do not interpret coverage bars as a real-world quality, precision, or completeness metric.** They only evaluate crude frame/vertex/triangle count thresholds.
- No dense TSDF fusion, loop closure, photogrammetry, textured mesh, or automatic objective segmentation.
- Android build/test results and device-scanning accuracy must be reported separately. Without real Galaxy tests, cannot claim the model is recognizable or robust.
- No network permission is requested. Camera files are stored on device and exported only on user action.

## QA checklist (device-dependent)

1. Camera permission, ARCore present/absent, denied permissions; 3 modes; 360 dp width and large text.
2. Start, pause, resume, help while recording, settings while recording, no-point diagnostics.
3. Turn SFX and haptics on/off; verify silent and vibrate modes remain quiet; verify accessibility touch targets.
4. Start an object scan and check counters: 0 -> >0, try material types and angles. Compare PLY/OBJ result visually (not only point count).
5. Save, reopen from library, export using Android Storage Access Framework. Compare exported file sizes and validity.
6. On device rotate and zoom 3D viewer; verify no UI overlap or crash.

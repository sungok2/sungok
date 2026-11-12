> **Historical M0.3 reference** — For the latest M0.5 status, read `docs/M0_5_VERIFICATION.md` and `docs/M0_5_CHANGELOG.md`.

# Security and privacy — M0.3 additions

- No USB reads/transfers automatically when app opens. Device enumeration is read-only; printer class control requests require a button press and Android USB permission.
- RAW output is destructive in the sense that it may cause pages to print. Require explicit document selection and final confirmation, with a size cap before transfer.
- No network permissions, no automatic driver download, no background upload of VID/PID or reports.
- USB serial numbers are disabled by default and only included in exports after a checkbox is checked. Device paths/VID/PID can still be identifying data; review before sharing reports.
- USB class/VID/PID metadata is not sufficient proof of device compatibility.
- This project currently has no capability to install/execute native vendor drivers or bypass Android USB permission controls.

# Security, permissions, and privacy

- Does not ask for file-storage permissions: SAF lets the user choose the output file.
- Does not ask for internet permission, USB root access, debugging privileges, or Shizuku.
- User explicitly selects a connected device before Android `UsbManager.requestPermission` is invoked.
- USB permission response uses an *explicit* PendingIntent targeting an app-private receiver (`exported=false`). Android 12+ requires a mutable flag so Android can populate result extras; Android 14+ requires an explicit target for safe mutable callback routing.
- The receiver only broadcasts a package-scoped refresh notification. USB permission checks must still be performed via `UsbManager.hasPermission`, which is authoritative.
- Android broadcast registration uses `RECEIVER_NOT_EXPORTED` on API 33+.
- Serial number is excluded from reports by default, and accessed only when the opt-in is checked and USB permission was granted. This protects against inadvertent disclosure in shared diagnostics.
- Device path, VID/PID and strings can identify hardware. Review exported TXT before sharing externally.
- Diagnostic app never opens USB device connections or transmits USB endpoint payloads.
- `android:allowBackup=false`; no external runtime dependencies; no internet access.
- Do not deploy on managed/military devices or connect to mission systems unless officially authorized. The app does not bypass device, information-security or operational restrictions.

Known limitations: a malicious USB device can expose malformed strings/descriptors. Android USB service is trusted for enumeration; the app catches common runtime descriptor exceptions but has not been fuzzed against malformed USB peripherals. Production drivers will require bounded transfer sizes, timeouts and protocol validation.

## M0.3 입력·출력 안전장치
- PTP는 사용자가 연결·허가한 장치의 JPEG 읽기만 수행하며 16MiB 이상은 거부합니다. USB 권한과 device path/VID/PID를 저장 시 재검사합니다.
- 선택한 PDF는 최대 32MiB로 제한하고 `%PDF-` 마커를 확인합니다. 이는 전체 파일의 PDF 문법 검증 또는 무해성 증명이 아닙니다.
- 인쇄/이미지 입력은 UI 명령으로만 수행. 외부 파일을 지정 없이 자동으로 출력/수집하지 않습니다.
- 파일 저장 선택 이후 실제 장치 오류가 발생하면 비어 있는 대상 파일이 남을 수 있으므로 결과 메시지를 확인하세요.

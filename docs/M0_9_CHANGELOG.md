# Galaxy Universal I/O M0.9 — 2026-11-13

## Resolved UI problems

- The M0.8 emulator screenshot displayed a very long vertical stack of disabled USB buttons before scanner entry. M0.9 presents a scan-first page with separate Print and Device/Diagnostics navigation.
- Main network scan controls are directly visible. USB-specific capabilities are grouped on relevant pages, with clear device-permission instructions.
- 48dp+ touch controls, labelled inputs, calm card hierarchy, persistent work status, and a collapsible technical report improve usability on a narrow phone screen.
- Active tab, scanner host and ADF choice are restored after Activity recreation.

## Cancellation and protocol hardening

- Previous `LanEsclTransport.close()` was a no-op. M0.9 disconnects active `HttpURLConnection` on cancellation, and rejects new requests after close.
- Previous USB eSCL `close()` synchronized on the same object as the potentially blocked `exchange()`; it could wait for the 90-second USB transfer timeout before closing. M0.9 makes close idempotent without acquiring the exchange monitor. Closing USB endpoints during a bulk transfer is best-effort and device/OS dependent.
- The active transport is registered with the scan worker and cancelled when the user requests stop or the Activity is destroyed.

## Verification criteria

- Pure Java and fake-device host tests now include explicit LAN and USB closed-transport checks (319 total checks).
- Android API *mock-signature* Java compile is diagnostic only; full SDK compile happens in GitHub Actions.
- GitHub Actions installs the debug APK on Android 13 emulator, checks Scan / Print / Device navigation, records screenshots and checks process stability.
- Physical Samsung Galaxy, printer, scanner, all devices and real scan-to-PDF are **not verified**.

## Known limitations

- The app cannot claim universal USB scanner support. Manufacturer-specific, non-eSCL interfaces remain unsupported.
- 12-page / 32MiB aggregate input size and a separate PDF spool limit remain; changes to these require memory profiling.
- OCR, auto-straightening, auto-cropping, duplex ADF, automatic service discovery and all-file-format print conversion are not implemented.
- A failed SAF copy can still produce an incomplete file in the destination provider.
- S20 Ultra physical device and scanner/printer model-specific compatibility remain to be tested.

- Final UI review correction: disabled USB actions use disabled-state color and do not masquerade as active blue buttons when no USB device is attached.

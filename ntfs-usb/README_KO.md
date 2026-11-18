# Galaxy NTFS USB 1.0.0

Galaxy NTFS USB is a separately installable Android build based on OTG Master, with Korean NTFS read-only diagnostics. It uses the upstream userspace USB mass-storage and libntfs-3g implementation rather than relying on Samsung's native NTFS provider.

## What this build changes

- Uses application ID `com.sungok.galaxy.ntfsusb`, so it installs alongside the upstream OTG Master app.
- Labels the app Galaxy NTFS USB.
- Shows the NTFS read-only fallback reason in Korean.
- Keeps the upstream safety behavior: it does not force a write mount when NTFS reports hibernated or unclean state.

## Meaning of read-only

The NTFS driver first attempts a read-write mount. If it detects a hibernated/Fast Startup volume or an unclean volume whose journal needs Windows to finish recovery, it deliberately retries as read-only. Turning off the app's read-only preference does not override those filesystem safety checks.

If the diagnostic says Windows hibernated/Fast Startup, fully shut down Windows with Fast Startup disabled, then safely eject and reconnect the USB. If it says unclean/journal, back up important files and inspect/repair the volume on Windows before reconnecting. Do not use force-mount or format as a diagnostic step.

## Build provenance and license

- Upstream: https://github.com/moylali/OTGMaster
- Pinned upstream commit: `765d4fee3379d29773207dcc456117ba86f27039`
- The upstream project and this derived build are GPL-3.0-or-later. The source archive contains the complete corresponding source tree, upstream license, and this patch.

## Verification boundary

GitHub Actions builds and checks the APK package ID and Android APK signature. This does not establish write success with a particular USB drive or on a physical Galaxy handset. Actual USB write acceptance still depends on the NTFS volume state and the phone/OTG hardware.

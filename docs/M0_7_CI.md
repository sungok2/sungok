# M0.7 Build and validation handoff

## GitHub-hosted build (manual)

The repository root MUST contain `app/`, `build.gradle`, and `.github/workflows/build-android-apk.yml`.
Push the full extracted project to a GitHub repository, select **Actions → Galaxy Universal I-O Android debug APK → Run workflow**. After the job succeeds, download its APK artifact. This workflow is a build recipe only: **it has not been executed or verified in GitHub Actions**.

Dependencies: JDK 21, installed Gradle 9.8.0, Android SDK platform API 36 + Build Tools 36.0.0, AGP 9.5.0. Uses supported Node 24+ GitHub Actions. The debug APK is development-signed and has application ID suffix `.debug`; do not treat it as a production release.

## Host-only verification

On Linux/Windows, run the `tests/run-*-tests.sh` or `.bat` scripts. The fake Android signature compile check is not a substitute for the Android SDK build. No physical scanner, printer, or Samsung Galaxy device has been tested here.

## USB HTTP reliability policy

USB request limits and request-targets are validated **before** submitting any bytes. Once a USB request has started, malformed/framing-failed replies or short transfers cause the connection to close to prevent accidental reuse with out-of-sync bytes. `Connection: close` also closes the connection after receiving the response. This intentionally prioritizes safety over device compatibility. Some printers may require additional firmware-specific transport handling.

## Known limitations

- No OCR, proprietary scanner protocols, duplex ADF, TIFF, automatic deskew or advanced image adjustments.
- Scan to PDF remains limited to up to 12 pages and 32MiB of buffered images.
- Interruption checked between eSCL operations. Blocking USB transfers may still wait for their timeout.
- PDF export to a SAF provider is not transactional and may leave an incomplete output on copy failure.
- No actual SDK build, emulator run, or on-device scan/print has been performed as part of this deliverable.

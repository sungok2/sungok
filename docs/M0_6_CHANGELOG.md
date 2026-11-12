# M0.6 변경 기록

- Add `UsbHttpResponseHeaders.java`: strict HTTP/1.x status/header/framing validation for USB eSCL/IPP.
- Add `StagedPdfExport.java`: private-cache PDF generation with size cap and cleanup before SAF copy.
- Change `UsbEsclHttpTransport`: parse via validated headers; reject ambiguous messages.
- Change `EsclScanClient`: accept HTTP 204 end-of-ADF after one or more pages; retain empty-scan error.
- Change `MainActivity`: warn/confirm plaintext LAN scan; stage PDF before destination copy; version bump.
- Change `ScannedPdfWriter`: correct JPEG/PNG documentation and error text.
- Add `ReliabilityTests.java` and cross-platform test runners; update all dependent compilations.
- Add additional eSCL 204 unit tests.
- Update README/Gradle version/Windows build script/manifest and validation docs.

No changes to the user's Windows 11 / JDK 21 / Gradle 9.8.0 requirements.

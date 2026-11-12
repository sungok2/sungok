#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
javac --release 17 -encoding UTF-8 -d "$OUT" \
  tests/stubs/android/hardware/usb/*.java \
  app/src/main/java/com/galaxyuniversalio/PtpProtocol.java \
  app/src/main/java/com/galaxyuniversalio/PtpUsbTransport.java \
  app/src/main/java/com/galaxyuniversalio/PdfSpoolPreflight.java \
  tests/ImagingAndPdfTests.java tests/PtpTransportTests.java
java -cp "$OUT" com.galaxyuniversalio.ImagingAndPdfTests
java -cp "$OUT" com.galaxyuniversalio.PtpTransportTests

#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
TEMP="$(mktemp -d)"
trap 'rm -rf "$TEMP"' EXIT HUP INT TERM
javac --release 17 -encoding UTF-8 -d "$TEMP" \
  tests/stubs/android/hardware/usb/*.java \
  app/src/main/java/com/galaxyuniversalio/EsclProtocol.java \
  app/src/main/java/com/galaxyuniversalio/EsclScanClient.java \
  app/src/main/java/com/galaxyuniversalio/UsbEsclHttpTransport.java \
  app/src/main/java/com/galaxyuniversalio/UsbHttpResponseHeaders.java \
  app/src/main/java/com/galaxyuniversalio/UsbHttpRequestPolicy.java \
  app/src/main/java/com/galaxyuniversalio/IppProtocol.java \
  app/src/main/java/com/galaxyuniversalio/UsbIppPdfPrinter.java \
  tests/IppHostTests.java
java -cp "$TEMP" com.galaxyuniversalio.IppHostTests

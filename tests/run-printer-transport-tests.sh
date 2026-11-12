#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
javac --release 17 -encoding UTF-8 -d "$OUT" \
  tests/stubs/android/hardware/usb/*.java \
  app/src/main/java/com/galaxyuniversalio/UsbPrinterProtocol.java \
  app/src/main/java/com/galaxyuniversalio/UsbPrinterTransport.java \
  tests/PrinterTransportTests.java
java -cp "$OUT" com.galaxyuniversalio.PrinterTransportTests

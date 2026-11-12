#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
OUTPUT_DIR="$(mktemp -d)"
trap 'rm -rf "$OUTPUT_DIR"' EXIT HUP INT TERM
javac --release 17 -encoding UTF-8 -d "$OUTPUT_DIR" \
  app/src/main/java/com/galaxyuniversalio/UsbDeviceClassifier.java \
  app/src/main/java/com/galaxyuniversalio/UsbProfileCatalog.java \
  app/src/main/java/com/galaxyuniversalio/UsbPrinterProtocol.java \
  tests/HostTests.java
java -cp "$OUTPUT_DIR" com.galaxyuniversalio.HostTests

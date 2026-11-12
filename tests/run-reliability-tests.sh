#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
OUT="$(mktemp -d)"
trap 'rm -rf "$OUT"' EXIT HUP INT TERM
javac --release 17 -encoding UTF-8 -d "$OUT" \
  app/src/main/java/com/galaxyuniversalio/UsbHttpResponseHeaders.java \
  app/src/main/java/com/galaxyuniversalio/StagedPdfExport.java \
  tests/ReliabilityTests.java
java -cp "$OUT" com.galaxyuniversalio.ReliabilityTests

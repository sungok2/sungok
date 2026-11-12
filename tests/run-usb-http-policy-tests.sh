#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")/.."
TEMP="$(mktemp -d)"
trap 'rm -rf "$TEMP"' EXIT HUP INT TERM
javac --release 17 -encoding UTF-8 -d "$TEMP" \
  app/src/main/java/com/galaxyuniversalio/UsbHttpRequestPolicy.java \
  tests/UsbHttpPolicyTests.java
java -cp "$TEMP" com.galaxyuniversalio.UsbHttpPolicyTests

#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp="$(mktemp -d)";trap 'rm -rf "$tmp"' EXIT
javac --release 17 -encoding UTF-8 -d "$tmp" \
 app/src/main/java/com/galaxyuniversalio/NtfsFailureAdvice.java \
 tests/NtfsFailureAdviceTests.java
java -cp "$tmp" com.galaxyuniversalio.NtfsFailureAdviceTests

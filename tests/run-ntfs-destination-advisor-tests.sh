#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
javac -encoding UTF-8 --release 17 -d "$tmp" \
 app/src/main/java/com/galaxyuniversalio/NtfsProviderAdvisor.java \
 tests/NtfsProviderAdvisorTests.java
java -cp "$tmp" com.galaxyuniversalio.NtfsProviderAdvisorTests

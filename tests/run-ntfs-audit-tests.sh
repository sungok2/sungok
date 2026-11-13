#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
tmp="$(mktemp -d)"; trap 'rm -rf "$tmp"' EXIT
javac -encoding UTF-8 --release 17 -d "$tmp" \
 app/src/main/java/com/galaxyuniversalio/NtfsNamePolicy.java \
 app/src/main/java/com/galaxyuniversalio/VerifiedStreamCopy.java \
 app/src/main/java/com/galaxyuniversalio/SafVerifiedTransaction.java \
 app/src/main/java/com/galaxyuniversalio/NtfsTransferAudit.java \
 tests/NtfsAuditHostTests.java
java -cp "$tmp" com.galaxyuniversalio.NtfsAuditHostTests

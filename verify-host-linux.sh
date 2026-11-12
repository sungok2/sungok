#!/usr/bin/env sh
set -eu
cd "$(dirname "$0")"
for t in \
  tests/run-host-tests.sh \
  tests/run-printer-transport-tests.sh \
  tests/run-imaging-pdf-tests.sh \
  tests/run-escl-tests.sh \
  tests/run-ipp-tests.sh \
  tests/run-reliability-tests.sh \
  tests/run-usb-http-policy-tests.sh; do
  sh "$t"
done
python3 tests/run-android-signature-smoke.py
echo 'PASS: all M0.7 host checks and Android-signature mock compile'

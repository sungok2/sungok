# Device profile registry

File: `app/src/main/assets/profiles.tsv` (UTF-8 TSV, exact 8 columns).

`VID  PID  VENDOR  MODEL  CATEGORY  PROTOCOL  EVIDENCE  NOTES`

Use 4-digit hex VID/PID, without `0x`. `EVIDENCE` is one of `discovered`, `protocol-reviewed`, `hardware-tested`.

The shipping registry has no device models. A synthetic entry for tests exists **only** in `tests/HostTests.java`, not in shipped assets. An exact VID/PID match is informational, not a driver implementation. Records must not be marked hardware-tested without real test evidence.

Proposed production registry fields for M1: evidence URL or lab reference, manufacturer, product name, hardware revision, firmware, scanner/printer functions, protocol adapter ID, test OS/device, permissions, successful/failed operations, capture date, maintainer, review state. This detailed schema/editor are not implemented in M0.3; only the simple 8-column TSV registry exists.

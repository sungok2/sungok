#!/usr/bin/env bash
set -euo pipefail
APK="app/build/outputs/apk/debug/app-debug.apk"
PKG="com.galaxyuniversalio.debug"
mkdir -p ci-evidence
adb wait-for-device
adb install -r "$APK"
adb logcat -c
adb shell am start -W -n "$PKG/com.galaxyuniversalio.MainActivity" | tee ci-evidence/launch.txt
sleep 5
# The process must survive onCreate/onResume without a physical USB device.
adb shell pidof "$PKG" | tee ci-evidence/pid.txt
# Capture evidence for a manual UI/UX review.
adb shell uiautomator dump /sdcard/window.xml || true
adb pull /sdcard/window.xml ci-evidence/window.xml || true
adb exec-out screencap -p > ci-evidence/initial-screen.png
if grep -q 'FATAL EXCEPTION' <(adb logcat -d -t 4000 | grep -A20 'FATAL EXCEPTION' || true); then
  echo 'Fatal Android exception detected after launch' >&2
  exit 1
fi
# Verify that at least the main activity title is present in the accessible hierarchy.
grep -q 'Galaxy Universal I/O' ci-evidence/window.xml

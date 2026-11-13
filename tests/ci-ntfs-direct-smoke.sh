#!/usr/bin/env bash
set -euo pipefail
mkdir -p direct-ui-evidence
adb wait-for-device
adb install -r direct-ntfs.apk
adb logcat -c
adb shell am start -W -n com.galaxyuniversalio.ntfsdirect/app.fayaz.otgmaster.MainActivity | tee direct-ui-evidence/launch.txt
sleep 6
adb shell pidof com.galaxyuniversalio.ntfsdirect | tee direct-ui-evidence/pid.txt
adb shell uiautomator dump /sdcard/direct-ntfs-window.xml || true
adb pull /sdcard/direct-ntfs-window.xml direct-ui-evidence/window.xml || true
adb exec-out screencap -p > direct-ui-evidence/main-screen.png
if adb logcat -d -t 6000 | grep -A15 'FATAL EXCEPTION' | grep -F 'com.galaxyuniversalio.ntfsdirect'; then
  echo 'Fatal app exception found during launch' >&2
  exit 1
fi
test -s direct-ui-evidence/main-screen.png
echo 'PASS: Android 13 APK installs, launches, and remains live without USB'

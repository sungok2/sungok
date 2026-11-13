#!/usr/bin/env bash
set -euo pipefail
APK="app/build/outputs/apk/debug/app-debug.apk"
PKG="com.galaxyuniversalio.debug"
mkdir -p ci-evidence
adb wait-for-device
adb install -r "$APK"
adb logcat -c
adb shell am start -W -n "$PKG/com.galaxyuniversalio.MainActivity" | tee ci-evidence/launch.txt
sleep 4
adb shell pidof "$PKG" | tee ci-evidence/pid.txt

capture() {
  local file="$1"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml "ci-evidence/${file}-window.xml" >/dev/null
  adb exec-out screencap -p > "ci-evidence/${file}-screen.png"
  test -s "ci-evidence/${file}-screen.png"
}

tap_text() {
  local text="$1" xml="ci-evidence/${2}-window.xml" point
  point="$(python3 tests/ui_nav_assert.py tap "$xml" "$text")"
  adb shell input tap $point
  sleep 2
}

capture "scan"
python3 tests/ui_nav_assert.py assert ci-evidence/scan-window.xml "문서 스캔"
python3 tests/ui_nav_assert.py assert ci-evidence/scan-window.xml "네트워크 스캔 → PDF 저장"
tap_text "인쇄" "scan"
capture "print"
python3 tests/ui_nav_assert.py assert ci-evidence/print-window.xml "PDF 인쇄"
python3 tests/ui_nav_assert.py assert ci-evidence/print-window.xml "PDF 파일 선택 후 인쇄"
tap_text "장치 · 진단" "print"
capture "devices"
python3 tests/ui_nav_assert.py assert ci-evidence/devices-window.xml "USB 장치"
adb shell input swipe 170 550 170 200 420
sleep 1
capture "devices-details"
python3 tests/ui_nav_assert.py assert ci-evidence/devices-details-window.xml "장치 기술 진단"

if adb logcat -d -t 5000 | grep -A15 'FATAL EXCEPTION' | grep -q "$PKG"; then
  echo 'Fatal exception detected in app process' >&2
  exit 1
fi
echo 'PASS: Android 13 app launch + scan/print/devices tab navigation and screenshots'

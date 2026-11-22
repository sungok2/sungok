#!/usr/bin/env bash
set -euo pipefail
APK="${APK_PATH:-app/build/outputs/apk/debug/app-debug.apk}"
PKG="${APP_PACKAGE:-com.galaxyuniversalio.debug}"
EVIDENCE_DIR="${EVIDENCE_DIR:-ci-evidence}"
mkdir -p "$EVIDENCE_DIR"
adb wait-for-device
adb install -r "$APK"
adb logcat -c
adb shell am start -W -n "$PKG/com.galaxyuniversalio.MainActivity" | tee "$EVIDENCE_DIR"/launch.txt
sleep 4
adb shell pidof "$PKG" | tee "$EVIDENCE_DIR"/pid.txt

capture() {
  local file="$1"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml ""$EVIDENCE_DIR"/${file}-window.xml" >/dev/null
  adb exec-out screencap -p > ""$EVIDENCE_DIR"/${file}-screen.png"
  test -s ""$EVIDENCE_DIR"/${file}-screen.png"
}

tap_text() {
  local text="$1" xml=""$EVIDENCE_DIR"/${2}-window.xml" point
  point="$(python3 tests/ui_nav_assert.py tap "$xml" "$text")"
  adb shell input tap $point
  sleep 2
}

capture "devices-initial"
python3 tests/ui_nav_assert.py assert "$EVIDENCE_DIR"/devices-initial-window.xml "USB 인쇄 · 스캔"
python3 tests/ui_nav_assert.py assert "$EVIDENCE_DIR"/devices-initial-window.xml "USB 장치"
python3 tests/ui_nav_assert.py assert "$EVIDENCE_DIR"/devices-initial-window.xml "USB 장치 미감지"
# A USB-attach intent without a device Parcelable must fail gracefully, not crash.
adb shell am start -W -n "$PKG/com.galaxyuniversalio.MainActivity" -a android.hardware.usb.action.USB_DEVICE_ATTACHED \
  | tee "$EVIDENCE_DIR"/usb-attach-no-payload.txt
sleep 2
capture "usb-attach-no-payload"
python3 tests/ui_nav_assert.py assert "$EVIDENCE_DIR"/usb-attach-no-payload-window.xml "USB 연결 알림을 받았지만 Android에서 장치 정보를 전달하지 않았습니다."
tap_text "스캔" "usb-attach-no-payload"
capture "scan"
python3 tests/ui_nav_assert.py assert "$EVIDENCE_DIR"/scan-window.xml "문서 스캔"
python3 tests/ui_nav_assert.py assert "$EVIDENCE_DIR"/scan-window.xml "스캐너에서 문서를 가져와 PDF로 저장합니다."
python3 tests/ui_nav_assert.py assert "$EVIDENCE_DIR"/scan-window.xml "같은 Wi-Fi에서 스캐너 찾기"
tap_text "같은 Wi-Fi에서 스캐너 찾기" "scan"
sleep 9
capture "scan-discovery"
if grep -Fq "eSCL 탐색 후보가 없습니다." "$EVIDENCE_DIR"/scan-discovery-window.xml; then
  echo 'PASS: LAN scanner discovery completed with no scanner present'
elif grep -Fq "발견한 eSCL 후보 선택" "$EVIDENCE_DIR"/scan-discovery-window.xml; then
  echo 'PASS: LAN scanner discovery returned selectable scanner results'
elif grep -Fq "mDNS 탐색 실패" "$EVIDENCE_DIR"/scan-discovery-window.xml; then
  echo 'PASS: LAN scanner discovery reported a visible platform error'
else
  echo 'LAN scanner discovery did not reach a visible terminal state' >&2
  exit 1
fi

tap_text "인쇄" "scan-discovery"
capture "print"
python3 tests/ui_nav_assert.py assert "$EVIDENCE_DIR"/print-window.xml "PDF 또는 직접 USB 인쇄 방식을 선택하세요."
python3 tests/ui_nav_assert.py assert "$EVIDENCE_DIR"/print-window.xml "PDF 인쇄"
python3 tests/ui_nav_assert.py assert "$EVIDENCE_DIR"/print-window.xml "제조사 드라이버로 PDF 인쇄"
if ! python3 tests/ui_nav_assert.py assert "$EVIDENCE_DIR"/print-window.xml "USB 연결 확인"; then
  adb shell input swipe 160 565 160 350 400
  capture "print"
fi
tap_text "USB 연결 확인" "print"
capture "usb-empty"
python3 tests/ui_nav_assert.py assert "$EVIDENCE_DIR"/usb-empty-window.xml "USB 장치 미감지"
python3 tests/ui_nav_assert.py assert "$EVIDENCE_DIR"/usb-empty-window.xml "다시 확인"
tap_text "닫기" "usb-empty"
adb shell input swipe 160 230 160 555 400
capture "print-return"
tap_text "장치 · 진단" "print-return"
capture "devices"
python3 tests/ui_nav_assert.py assert "$EVIDENCE_DIR"/devices-window.xml "USB 장치"
# The bottom tab bar must remain visible while long device content scrolls.
for attempt in $(seq 1 7); do
  adb shell input swipe 170 535 170 180 350
  sleep 1
  capture "preferences"
  if python3 tests/ui_nav_assert.py assert "$EVIDENCE_DIR"/preferences-window.xml "작업 완료 · 오류 효과음" > /dev/null 2>&1; then
    break
  fi
done
python3 tests/ui_nav_assert.py assert "$EVIDENCE_DIR"/preferences-window.xml "작업 완료 · 오류 효과음"
python3 tests/ui_nav_assert.py assert "$EVIDENCE_DIR"/preferences-window.xml "장치 · 진단"
python3 tests/ui_nav_assert.py assert "$EVIDENCE_DIR"/preferences-window.xml "인쇄"
python3 tests/ui_nav_assert.py assert "$EVIDENCE_DIR"/preferences-window.xml "스캔"
tap_text "작업 완료 · 오류 효과음" "preferences"
capture "sound-enabled"
python3 tests/ui_nav_assert.py checked "$EVIDENCE_DIR"/sound-enabled-window.xml "작업 완료 · 오류 효과음"

# The technical report must still be reachable beneath optional preferences.
for attempt in $(seq 1 5); do
  adb shell input swipe 170 535 170 180 350
  sleep 1
  capture "devices-details"
  if python3 tests/ui_nav_assert.py assert "$EVIDENCE_DIR"/devices-details-window.xml "장치 기술 진단" > /dev/null 2>&1; then
    break
  fi
done
python3 tests/ui_nav_assert.py assert "$EVIDENCE_DIR"/devices-details-window.xml "장치 기술 진단"

# Preference must survive force stop and cold relaunch.
adb shell am force-stop "$PKG"
adb shell am start -W -n "$PKG/com.galaxyuniversalio.MainActivity" > "$EVIDENCE_DIR"/relaunch.txt
sleep 2
for attempt in $(seq 1 7); do
  adb shell input swipe 170 535 170 180 350
  sleep 1
  capture "settings-restored"
  if python3 tests/ui_nav_assert.py assert "$EVIDENCE_DIR"/settings-restored-window.xml "작업 완료 · 오류 효과음" > /dev/null 2>&1; then
    break
  fi
done
python3 tests/ui_nav_assert.py checked "$EVIDENCE_DIR"/settings-restored-window.xml "작업 완료 · 오류 효과음"

if adb logcat -d -t 5000 | grep -A15 'FATAL EXCEPTION' | grep -q "$PKG"; then
  echo 'Fatal exception detected in app process' >&2
  exit 1
fi
echo 'PASS: Android 13 app launch + LAN discovery + scan/print/devices navigation'

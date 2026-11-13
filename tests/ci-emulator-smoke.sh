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
grep -q 'Galaxy Universal I/O · M0.9' ci-evidence/window.xml


# Locate the newly added NTFS/SAF button by accessibility bounds, not hardcoded pixels.
python3 - <<'PY' > ci-evidence/ntfs-tap.txt
from xml.etree import ElementTree as ET
import re
root=ET.parse("ci-evidence/window.xml").getroot()
matches=[node for node in root.iter("node") if "USB 저장장치 복사" in node.attrib.get("text","")]
if len(matches) != 1:
    raise SystemExit("Expected one NTFS transfer button, found %s" % len(matches))
bounds=matches[0].attrib.get("bounds","")
m=re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",bounds)
if not m:
    raise SystemExit("Invalid bounds: "+bounds)
left,top,right,bottom=map(int,m.groups())
print((left+right)//2,(top+bottom)//2)
PY
read -r NTFS_X NTFS_Y < ci-evidence/ntfs-tap.txt
adb shell input tap "$NTFS_X" "$NTFS_Y"
sleep 2
adb shell uiautomator dump /sdcard/ntfs-window.xml
adb pull /sdcard/ntfs-window.xml ci-evidence/ntfs-window.xml
adb exec-out screencap -p > ci-evidence/ntfs-copy-screen.png
grep -q 'USB 파일 전송' ci-evidence/ntfs-window.xml
grep -q '파일 선택하고 복사' ci-evidence/ntfs-window.xml
adb shell pidof "$PKG" > ci-evidence/ntfs-pid.txt
if grep -q 'FATAL EXCEPTION' <(adb logcat -d -t 4000 | grep -A20 'FATAL EXCEPTION' || true); then
  echo 'Fatal Android exception detected while opening NTFS transfer' >&2
  exit 1
fi

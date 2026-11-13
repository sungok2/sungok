#!/usr/bin/env bash
set -euo pipefail
APK="app/build/outputs/apk/debug/app-debug.apk"
PKG="com.galaxyuniversalio.m011"
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
grep -q 'Galaxy Universal I/O · M0.11' ci-evidence/window.xml


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
grep -q 'USB 쓰기 가능 여부 진단' ci-evidence/ntfs-window.xml

# Verify that the new destructive-operation warning is shown before any probe.
python3 - <<'PY' > ci-evidence/probe-tap.txt
from xml.etree import ElementTree as ET
import re
root = ET.parse("ci-evidence/ntfs-window.xml").getroot()
found = [n for n in root.iter("node") if "USB 쓰기 가능 여부 진단" in n.attrib.get("text","")]
if len(found) != 1:
    raise SystemExit("Expected exactly one user-consent NTFS probe button")
bounds = found[0].attrib.get("bounds","")
match = re.fullmatch(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]",bounds)
if not match:
    raise SystemExit("Bad probe button bounds "+bounds)
l,t,r,b=map(int,match.groups())
print((l+r)//2,(t+b)//2)
PY
read -r PROBE_X PROBE_Y < ci-evidence/probe-tap.txt
adb shell input tap "$PROBE_X" "$PROBE_Y"
sleep 1
adb shell uiautomator dump /sdcard/probe-warning.xml
adb pull /sdcard/probe-warning.xml ci-evidence/probe-warning.xml
grep -q 'USB 쓰기 실험 안내' ci-evidence/probe-warning.xml
grep -q '폴더 선택' ci-evidence/probe-warning.xml
adb exec-out screencap -p > ci-evidence/probe-warning.png
adb shell input keyevent KEYCODE_BACK
sleep 1

adb shell pidof "$PKG" > ci-evidence/ntfs-pid.txt
if grep -q 'FATAL EXCEPTION' <(adb logcat -d -t 4000 | grep -A20 'FATAL EXCEPTION' || true); then
  echo 'Fatal Android exception detected while opening NTFS transfer' >&2
  exit 1
fi

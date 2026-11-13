#!/usr/bin/env bash
set -euo pipefail
PKG="com.storyenglish.qualitylab"
ACTIVITY="com.storyenglish.preview.MainActivity"
OUT="$GITHUB_WORKSPACE/story-english-emulator-evidence"
mkdir -p "$OUT"
adb install -r story-english-preview/app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
adb shell am start -W -n "$PKG/$ACTIVITY" | tee "$OUT/launch.txt"
sleep 5
adb shell pidof "$PKG" | tee "$OUT/pid.txt"
adb shell screencap -p /sdcard/story_age.png
adb pull /sdcard/story_age.png "$OUT/01_age_gate.png" >/dev/null

dump_ui() {
  local target="$1"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml "$OUT/$target" >/dev/null
}

tap_named() {
  local needle="$1"
  dump_ui "ui_before_tap.xml"
  local coords
  coords="$(python3 - "$OUT/ui_before_tap.xml" "$needle" <<'PY'
import sys,xml.etree.ElementTree as ET,re
tree=ET.parse(sys.argv[1]); q=sys.argv[2]
for node in tree.iter('node'):
    a=node.attrib
    if q in a.get("text","") or q in a.get("content-desc",""):
        coords=list(map(int,re.findall(r"\d+",a.get("bounds",""))))
        if len(coords)==4 and coords[2]>coords[0] and coords[3]>coords[1]:
            print((coords[0]+coords[2])//2,(coords[1]+coords[3])//2)
            break
PY
)"
  if [[ -z "$coords" ]]; then
    echo "UI control not found: $needle" >&2
    return 1
  fi
  read -r x y <<< "$coords"
  adb shell input tap "$x" "$y"
}

dump_ui "01_age_gate.xml"
grep -q "19세 이상" "$OUT/01_age_gate.xml"
tap_named "저는 19세 이상"
sleep 4
adb shell pidof "$PKG"
adb shell screencap -p /sdcard/story_home.png
adb pull /sdcard/story_home.png "$OUT/02_home.png" >/dev/null
dump_ui "02_home.xml"
grep -q "STORY ENGLISH" "$OUT/02_home.xml"

# Scroll only when content is below the viewport.
adb shell input swipe 500 1600 500 530 380
sleep 1
tap_named "영상 학습 열기"
sleep 5
adb shell pidof "$PKG"
adb shell screencap -p /sdcard/story_video.png
adb pull /sdcard/story_video.png "$OUT/03_cinema_player.png" >/dev/null
dump_ui "03_cinema_player.xml"
grep -q "재생 / 일시정지" "$OUT/03_cinema_player.xml"

adb logcat -d -s AndroidRuntime:E > "$OUT/android_runtime_errors.log"
if grep -q "Process: $PKG" "$OUT/android_runtime_errors.log";then
    echo "App crash found in AndroidRuntime log" >&2
    exit 1
fi
echo "PASS: app launched, age confirmation, cinematic player navigation, no app crash" | tee "$OUT/RESULT.txt"

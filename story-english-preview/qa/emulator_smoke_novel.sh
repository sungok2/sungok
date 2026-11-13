#!/usr/bin/env bash
set -euo pipefail
PKG="com.storyenglish.novellab"
ACTIVITY="com.storyenglish.preview.MainActivity"
OUT="$GITHUB_WORKSPACE/story-english-novel-qa-evidence"
mkdir -p "$OUT"
adb install -r story-english-preview/app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop "$PKG" >/dev/null 2>&1 || true
adb shell am start -W -n "$PKG/$ACTIVITY" | tee "$OUT/launch.txt"
sleep 5
adb shell pidof "$PKG" | tee "$OUT/pid.txt"

capture(){
  local key="$1"
  adb shell screencap -p "/sdcard/$key.png"
  adb pull "/sdcard/$key.png" "$OUT/$key.png" >/dev/null
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml "$OUT/$key.xml" >/dev/null
}
tap_once(){
  local needle="$1"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml "$OUT/temp.xml" >/dev/null
  local point
  point="$(python3 - "$OUT/temp.xml" "$needle" <<'PY'
import sys,xml.etree.ElementTree as ET,re
root=ET.parse(sys.argv[1]);needle=sys.argv[2]
for node in root.iter('node'):
  attr=node.attrib
  if needle not in attr.get('text','') and needle not in attr.get('content-desc',''):continue
  coords=list(map(int,re.findall(r'\d+',attr.get('bounds',''))))
  if len(coords)==4 and coords[2]>coords[0] and coords[3]>coords[1]:
    print((coords[0]+coords[2])//2,(coords[1]+coords[3])//2)
    break
PY
)"
  if [[ -z "$point" ]];then return 1;fi
  read -r x y <<< "$point"
  adb shell input tap "$x" "$y"
}
tap_with_scroll(){
  local needle="$1"
  for n in 0 1 2 3 4 5;do
    if tap_once "$needle";then return 0;fi
    adb shell input swipe 500 1740 500 560 370
    sleep 1
  done
  echo "Missing control: $needle" >&2
  return 1
}
capture "01_age_confirmation"
grep -q '19세 이상' "$OUT/01_age_confirmation.xml"
tap_once '저는 19세 이상'
sleep 3
capture "02_home"
grep -q 'STORY ENGLISH' "$OUT/02_home.xml"
tap_once '소설 탭'
sleep 2
capture "03_novel_empty"
grep -q '성인소설 원문 학습' "$OUT/03_novel_empty.xml"
tap_with_scroll '성인 로맨스 오리지널 예문 체험'
sleep 2
capture "04_novel_loaded"
grep -q 'After Midnight' "$OUT/04_novel_loaded.xml"
tap_with_scroll '원문 듣기'
sleep 2
capture "05_read_aloud"
adb shell pidof "$PKG"
adb logcat -d -s AndroidRuntime:E > "$OUT/android_runtime_errors.log"
if grep -q "Process: $PKG" "$OUT/android_runtime_errors.log";then
  echo "CRASH FOUND" >&2
  exit 1
fi
echo "PASS: 19+ entry, chapter import, sentence-based listening control and no app crash" | tee "$OUT/RESULT.txt"

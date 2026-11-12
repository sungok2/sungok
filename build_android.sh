#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
if ! command -v gradle >/dev/null 2>&1; then
  echo 'Android SDK + Gradle 8.11.1이 필요합니다. Android Studio 또는 GitHub Actions를 이용하세요.' >&2
  exit 1
fi
if [[ -z "${ANDROID_HOME:-}" && -z "${ANDROID_SDK_ROOT:-}" && ! -f local.properties ]]; then
  echo 'ANDROID_HOME 또는 ANDROID_SDK_ROOT를 설정하거나 local.properties에 sdk.dir을 지정하세요.' >&2
  exit 1
fi
gradle --no-daemon :app:assembleDebug
printf '\nAPK: app/build/outputs/apk/debug/app-debug.apk\n'

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-$HOME/Library/Android/sdk}}"
ADB_BIN="${ADB:-$SDK_ROOT/platform-tools/adb}"
EMULATOR_BIN="${EMULATOR:-$SDK_ROOT/emulator/emulator}"
GRADLEW="$ROOT_DIR/gradlew"

AVD_NAME="${1:-${ANDROID_AVD_NAME:-Pixel_8_API_35}}"
APP_ID="${APP_ID:-app.tessera}"
ACTIVITY_NAME="${ACTIVITY_NAME:-.MainActivity}"
APK_PATH="${APK_PATH:-$ROOT_DIR/app/build/outputs/apk/debug/app-debug.apk}"

require_command() {
  local path="$1"
  local label="$2"

  if [[ ! -x "$path" ]]; then
    echo "Missing $label at: $path" >&2
    exit 1
  fi
}

first_running_emulator() {
  "$ADB_BIN" devices | awk '/^emulator-[0-9]+[[:space:]]+device$/{ print $1; exit }'
}

wait_for_android_boot() {
  local serial="$1"

  "$ADB_BIN" -s "$serial" wait-for-device >/dev/null
  until [[ "$("$ADB_BIN" -s "$serial" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]]; do
    sleep 2
  done
}

start_emulator_if_needed() {
  local serial
  serial="$(first_running_emulator)"

  if [[ -n "$serial" ]]; then
    echo "Using running emulator: $serial" >&2
    printf '%s\n' "$serial"
    return 0
  fi

  echo "Starting emulator: $AVD_NAME" >&2
  "$EMULATOR_BIN" -avd "$AVD_NAME" >/tmp/tessera-emulator.log 2>&1 &

  for _ in {1..90}; do
    serial="$(first_running_emulator)"
    if [[ -n "$serial" ]]; then
      echo "Emulator connected: $serial" >&2
      wait_for_android_boot "$serial"
      printf '%s\n' "$serial"
      return 0
    fi
    sleep 2
  done

  echo "Timed out waiting for emulator '$AVD_NAME' to appear." >&2
  echo "See /tmp/tessera-emulator.log for emulator output." >&2
  exit 1
}

component_name() {
  if [[ "$ACTIVITY_NAME" == .* ]]; then
    printf '%s/%s\n' "$APP_ID" "$ACTIVITY_NAME"
  else
    printf '%s/%s\n' "$APP_ID" "$ACTIVITY_NAME"
  fi
}

require_command "$ADB_BIN" "adb"
require_command "$EMULATOR_BIN" "emulator"
require_command "$GRADLEW" "Gradle wrapper"

EMULATOR_SERIAL="$(start_emulator_if_needed)"

echo "Building debug APK"
"$GRADLEW" :app:assembleDebug

if [[ ! -f "$APK_PATH" ]]; then
  echo "APK not found at: $APK_PATH" >&2
  exit 1
fi

echo "Installing APK on $EMULATOR_SERIAL"
"$ADB_BIN" -s "$EMULATOR_SERIAL" install -r "$APK_PATH"

echo "Launching app"
"$ADB_BIN" -s "$EMULATOR_SERIAL" shell am start -n "$(component_name)"

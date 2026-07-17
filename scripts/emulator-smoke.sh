#!/usr/bin/env bash
set -euo pipefail

OUT="build/emulator-smoke"
mkdir -p "$OUT"

APK="$(find app/build/outputs/apk -name '*.apk' | head -1)"
if [ -z "$APK" ]; then
  echo "APK not found. Build the app before running emulator smoke."
  exit 1
fi

adb wait-for-device
adb shell settings put global window_animation_scale 0 || true
adb shell settings put global transition_animation_scale 0 || true
adb shell settings put global animator_duration_scale 0 || true
adb install -r "$APK"
adb shell pm grant app.rubjai.mobile android.permission.READ_MEDIA_IMAGES || true
adb shell pm grant app.rubjai.mobile android.permission.READ_EXTERNAL_STORAGE || true

shot() {
  local name="$1"
  adb exec-out screencap -p > "$OUT/$name.png"
}

dump() {
  local name="$1"
  adb shell uiautomator dump /sdcard/window.xml >/dev/null
  adb pull /sdcard/window.xml "$OUT/$name.xml" >/dev/null
}

require_text() {
  local file="$1"
  local text="$2"
  if ! grep -q "$text" "$file"; then
    echo "Expected UI text '$text' not found in $file"
    exit 1
  fi
}

adb shell am force-stop app.rubjai.mobile || true
adb shell am start -W -n app.rubjai.mobile/.MainActivity >/dev/null
sleep 3
shot "01-start"

# Walk through onboarding by tapping the primary bottom button.
for _ in 1 2 3 4 5 6; do
  adb shell input tap 540 1780
  sleep 0.8
done
shot "02-auth"
dump "02-auth"

# Start anonymous trial mode. This is the non-admin path for emulator UI testing.
adb shell input tap 540 1510
sleep 8
shot "03-home"
dump "03-home"
require_text "$OUT/03-home.xml" "หน้าหลัก"
require_text "$OUT/03-home.xml" "ยอดใช้จ่าย"
require_text "$OUT/03-home.xml" "จดเพิ่ม"
require_text "$OUT/03-home.xml" "วันนี้"

# Open add transaction flow and category sheet.
adb shell input tap 820 1690
sleep 2
shot "04-editor"
dump "04-editor"
require_text "$OUT/04-editor.xml" "บันทึกรายจ่าย"
require_text "$OUT/04-editor.xml" "เลือกหมวด"

adb shell input tap 540 930
sleep 1
shot "05-category"
dump "05-category"
require_text "$OUT/05-category.xml" "เลือกหมวด"
require_text "$OUT/05-category.xml" "อาหาร"

# Close category/editor and verify account hub.
adb shell input keyevent 4
sleep 0.6
adb shell input keyevent 4
sleep 0.8
adb shell input tap 720 1840
sleep 1
shot "06-account"
dump "06-account"
require_text "$OUT/06-account.xml" "บัญชีของฉัน"
require_text "$OUT/06-account.xml" "ข้อมูลส่วนตัว"
require_text "$OUT/06-account.xml" "วางแผนหนี้"

# Profile page.
adb shell input tap 540 620
sleep 1
shot "07-profile"
dump "07-profile"
require_text "$OUT/07-profile.xml" "โปรไฟล์"
require_text "$OUT/07-profile.xml" "จัดการหมวด"
adb shell input keyevent 4
sleep 0.8

# Debt planner page.
adb shell input tap 540 760
sleep 1
shot "08-debts"
dump "08-debts"
require_text "$OUT/08-debts.xml" "รายการหนี้"
require_text "$OUT/08-debts.xml" "เพิ่มหนี้"
adb shell input keyevent 4
sleep 0.8

echo "Android emulator smoke UI passed. Screenshots are in $OUT"

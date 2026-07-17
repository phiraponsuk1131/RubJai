param(
  [string]$PackageName = "app.rubjai.mobile",
  [string]$OutDir = "build\local-device-smoke"
)

$ErrorActionPreference = "Stop"
[Console]::OutputEncoding = [System.Text.UTF8Encoding]::new()

function Find-Adb {
  $candidates = @()
  $pathAdb = Get-Command adb.exe -ErrorAction SilentlyContinue
  if ($pathAdb) { $candidates += $pathAdb.Source }
  if ($env:ANDROID_HOME) { $candidates += (Join-Path $env:ANDROID_HOME "platform-tools\adb.exe") }
  if ($env:ANDROID_SDK_ROOT) { $candidates += (Join-Path $env:ANDROID_SDK_ROOT "platform-tools\adb.exe") }
  $candidates += (Join-Path (Get-Location) ".tools\platform-tools\adb.exe")
  $candidates += (Join-Path $env:LOCALAPPDATA "Android\Sdk\platform-tools\adb.exe")
  $candidates += "C:\Android\Sdk\platform-tools\adb.exe"

  foreach ($candidate in $candidates | Select-Object -Unique) {
    if ($candidate -and (Test-Path -LiteralPath $candidate)) { return $candidate }
  }
  throw "ADB not found. Install Android platform-tools or use a machine that already has Android Studio/SDK. This script does not need admin rights once adb.exe exists."
}

function Invoke-Adb {
  param([Parameter(ValueFromRemainingArguments = $true)][string[]]$Args)
  & $script:Adb @Args
  if ($LASTEXITCODE -ne 0) { throw "adb failed: $($Args -join ' ')" }
}

function Require-Text {
  param([string]$File, [string]$Text)
  $content = Get-Content -LiteralPath $File -Raw -Encoding UTF8
  if (-not $content.Contains($Text)) { throw "Expected UI text '$Text' not found in $File" }
}

function Save-Shot {
  param([string]$Name)
  $remote = "/sdcard/$Name.png"
  Invoke-Adb shell screencap -p $remote
  Invoke-Adb pull $remote (Join-Path $OutDir "$Name.png") | Out-Null
  Invoke-Adb shell rm $remote | Out-Null
}

function Save-Dump {
  param([string]$Name)
  $remote = "/sdcard/window.xml"
  Invoke-Adb shell uiautomator dump $remote | Out-Null
  Invoke-Adb pull $remote (Join-Path $OutDir "$Name.xml") | Out-Null
}

$script:Adb = Find-Adb
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$devices = & $script:Adb devices
if (($devices | Select-String -Pattern "`tdevice").Count -eq 0) {
  throw "No Android device/emulator is connected. Connect a phone with USB debugging enabled, then run this script again."
}

$apk = Get-ChildItem -Path "app\build\outputs\apk" -Recurse -Filter "*.apk" -ErrorAction SilentlyContinue |
  Sort-Object LastWriteTime -Descending |
  Select-Object -First 1
if (-not $apk) { throw "APK not found. Build first with scripts\build-apk-portable.ps1 or Gradle assembleDebug." }

Invoke-Adb wait-for-device
Invoke-Adb install -r $apk.FullName
& $script:Adb shell pm grant $PackageName android.permission.READ_MEDIA_IMAGES | Out-Null
& $script:Adb shell pm grant $PackageName android.permission.READ_EXTERNAL_STORAGE | Out-Null
& $script:Adb shell settings put global window_animation_scale 0 | Out-Null
& $script:Adb shell settings put global transition_animation_scale 0 | Out-Null
& $script:Adb shell settings put global animator_duration_scale 0 | Out-Null

Invoke-Adb shell am force-stop $PackageName
Invoke-Adb shell am start -W -n "$PackageName/.MainActivity" | Out-Null
Start-Sleep -Seconds 3
Save-Shot "01-start"

foreach ($i in 1..6) {
  Invoke-Adb shell input tap 540 1780
  Start-Sleep -Milliseconds 800
}
Save-Shot "02-auth"
Save-Dump "02-auth"

Invoke-Adb shell input tap 540 1510
Start-Sleep -Seconds 8
Save-Shot "03-home"
Save-Dump "03-home"
Require-Text (Join-Path $OutDir "03-home.xml") "หน้าหลัก"
Require-Text (Join-Path $OutDir "03-home.xml") "ยอดใช้จ่าย"
Require-Text (Join-Path $OutDir "03-home.xml") "จดเพิ่ม"
Require-Text (Join-Path $OutDir "03-home.xml") "วันนี้"

Invoke-Adb shell input tap 820 1690
Start-Sleep -Seconds 2
Save-Shot "04-editor"
Save-Dump "04-editor"
Require-Text (Join-Path $OutDir "04-editor.xml") "บันทึกรายจ่าย"
Require-Text (Join-Path $OutDir "04-editor.xml") "เลือกหมวด"

Invoke-Adb shell input tap 540 930
Start-Sleep -Seconds 1
Save-Shot "05-category"
Save-Dump "05-category"
Require-Text (Join-Path $OutDir "05-category.xml") "เลือกหมวด"
Require-Text (Join-Path $OutDir "05-category.xml") "อาหาร"

Invoke-Adb shell input keyevent 4
Start-Sleep -Milliseconds 600
Invoke-Adb shell input keyevent 4
Start-Sleep -Milliseconds 800
Invoke-Adb shell input tap 720 1840
Start-Sleep -Seconds 1
Save-Shot "06-account"
Save-Dump "06-account"
Require-Text (Join-Path $OutDir "06-account.xml") "บัญชีของฉัน"
Require-Text (Join-Path $OutDir "06-account.xml") "ข้อมูลส่วนตัว"
Require-Text (Join-Path $OutDir "06-account.xml") "วางแผนหนี้"

Invoke-Adb shell input tap 540 620
Start-Sleep -Seconds 1
Save-Shot "07-profile"
Save-Dump "07-profile"
Require-Text (Join-Path $OutDir "07-profile.xml") "โปรไฟล์"
Require-Text (Join-Path $OutDir "07-profile.xml") "จัดการหมวด"
Invoke-Adb shell input keyevent 4
Start-Sleep -Milliseconds 800

Invoke-Adb shell input tap 540 760
Start-Sleep -Seconds 1
Save-Shot "08-debts"
Save-Dump "08-debts"
Require-Text (Join-Path $OutDir "08-debts.xml") "รายการหนี้"
Require-Text (Join-Path $OutDir "08-debts.xml") "เพิ่มหนี้"

Write-Host "Local device smoke UI passed. Screenshots are in $OutDir"

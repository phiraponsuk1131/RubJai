const fs = require("fs");

const scanner = fs.readFileSync("app/src/main/java/app/rubjai/mobile/AutoSlipScanner.kt", "utf8");
const main = fs.readFileSync("app/src/main/java/app/rubjai/mobile/MainActivity.kt", "utf8");

function fail(message) {
  console.error(message);
  process.exitCode = 1;
}

if (!scanner.includes("const val MAX_LOOKBACK_DAYS = 31")) {
  fail("Slip sync must define a 31-day maximum lookback window.");
}
if (!scanner.includes("add(Calendar.DAY_OF_YEAR, -(KPlusSyncManager.MAX_LOOKBACK_DAYS - 1))")) {
  fail("Slip sync worker must start from the configured month lookback window.");
}
if (!scanner.includes("registerContentObserver(MediaStore.Images.Media.EXTERNAL_CONTENT_URI")) {
  fail("Real-time slip sync must watch MediaStore while the app is open.");
}
if (!scanner.includes("MIN_REALTIME_GAP_MS = 3_000L")) {
  fail("Real-time slip sync must be throttled.");
}
if (!scanner.includes("scanned < 3000")) {
  fail("One-month slip sync needs a larger scan cap than the old today-only limit.");
}
if (!main.includes("กำลังค้นหาสลิปย้อนหลัง 1 เดือน")) {
  fail("Home sync status must tell the user that sync scans back one month.");
}

if (process.exitCode) process.exit(process.exitCode);
console.log("Slip sync window check passed.");

const fs = require("fs");

const main = fs.readFileSync("app/src/main/java/app/rubjai/mobile/MainActivity.kt", "utf8");
const build = fs.readFileSync("app/build.gradle.kts", "utf8");

function fail(message) {
  console.error(message);
  process.exitCode = 1;
}

if (!build.includes('versionName = "3.0.0"')) fail("Expected app versionName 3.0.0 for the full UI redesign release.");
if (!main.includes("HomeReferenceScreen(")) fail("Home screen must render HomeReferenceScreen.");
if (!main.includes("HomeSlipSyncBand(")) fail("Slip sync must be displayed inline on the home timeline.");
if (!main.includes("HomeTimelineRow(")) fail("Home timeline rows with category icons are required.");
if (!main.includes("ExtendedFloatingActionButton(") || !main.includes('Text("จดเพิ่ม"')) fail("Home add action must use the blue extended จดเพิ่ม button.");
if (!main.includes("SlipSourceCard(initial.slipUri")) fail("Transaction editor must show the source slip card when a slip image is available.");
if (!main.includes("FullScreenSlipDialog(initial.slipUri")) fail("Transaction editor must allow opening the source slip full screen.");

for (const oldSymbol of [
  "fun RubJaiTopBar",
  "fun HomeActions",
  "fun KPlusSyncStatus",
  "fun TransactionDetailDialog",
  "fun SummaryCard",
  "fun EntryFilters",
  "fun SpendingOverview",
  "fun EntryRow",
  "fun QuickOverview",
]) {
  if (main.includes(oldSymbol)) fail(`Old UI implementation must not remain in MainActivity: ${oldSymbol}`);
}

const scaffoldBlock = main.match(/Scaffold\([\s\S]*?\n\s*\) \{ padding ->/);
if (!scaffoldBlock) fail("Could not locate main Scaffold block.");
else if (scaffoldBlock[0].includes("RubJaiTopBar(")) fail("Old coral top bar must not appear on the redesigned home screen.");

const mainTabBlock = main.match(/if \(mainTab == 0\) \{[\s\S]*?\} else item/);
if (!mainTabBlock) fail("Could not locate mainTab home block.");
else {
  const block = mainTabBlock[0];
  for (const oldCall of ["SummaryCard(", "HomeActions(", "KPlusSyncStatus(", "SpendingOverview(", "EntryRow("]) {
    if (block.includes(oldCall)) fail(`Old home UI call still appears in active home block: ${oldCall}`);
  }
}

if (/message\s*=\s*if \(found > 0\).*สแกนเสร็จ/.test(main)) fail("Slip sync completion must not show a popup message.");
if (!main.includes("syncStatusText")) fail("Slip sync status should be tracked inline, not via popup.");

if (process.exitCode) process.exit(process.exitCode);
console.log("UI flow check passed.");

const fs = require("fs");

const main = fs.readFileSync("app/src/main/java/app/rubjai/mobile/MainActivity.kt", "utf8");
const build = fs.readFileSync("app/build.gradle.kts", "utf8");
const launcherForeground = fs.readFileSync("app/src/main/res/drawable/ic_launcher_foreground.xml", "utf8");

function fail(message) {
  console.error(message);
  process.exitCode = 1;
}

if (!build.includes('versionName = "3.0.5"')) fail("Expected app versionName 3.0.5 for the QR tuning release.");
if (!build.includes("versionCode = 24")) fail("Expected app versionCode 24 for v3.0.5.");
if (!main.includes("HomeReferenceScreen(")) fail("Home screen must render HomeReferenceScreen.");
if (!main.includes("HomeSlipSyncBand(")) fail("Slip sync must be displayed inline on the home timeline.");
if (!main.includes("HomeTimelineRow(")) fail("Home timeline rows with category icons are required.");
if (!main.includes("ExtendedFloatingActionButton(") || !main.includes('Text("จดเพิ่ม"')) fail("Home add action must use the blue extended จดเพิ่ม button.");
if (!main.includes("SlipSourceCard(initial.slipUri")) fail("Transaction editor must show the source slip card when a slip image is available.");
if (!main.includes("FullScreenSlipDialog(initial.slipUri")) fail("Transaction editor must allow opening the source slip full screen.");
if (!main.includes("R.drawable.rubjai_logo")) fail("The redesigned app must use the new RubJai mark.");
if (main.includes("R.drawable.rubjai_mascot")) fail("Old square mascot must not be used in active app UI.");
if (!launcherForeground.includes("@drawable/rubjai_logo")) fail("Launcher foreground must use the new RubJai mark.");

if (main.includes("padding(start = 138.dp)") || main.includes("Modifier.width(138.dp)")) {
  fail("Home rail must be responsive; fixed 138.dp rail caused narrow vertical text.");
}
if (!main.includes("val railWidth = if (maxWidth < 420.dp) 76.dp else 92.dp")) {
  fail("Home rail must keep the responsive reference-video layout.");
}
if (!main.includes("MascotBadge(")) fail("Home mascot/logo must be cropped as a badge, not a large square image.");
if (!main.includes("Text(moneyPlain(monthExpense)") || !main.includes("modifier = Modifier.weight(1f)")) {
  fail("Home monthly amount must keep horizontal width and avoid vertical text.");
}
if (!main.includes("LocalSlipLinkStore.put(context, item.id, draftValue.slipUri)")) {
  fail("Editing an entry must preserve the local source-slip link.");
}
if (!main.includes("SlipSourceCard(pending.draft.slipUri")) {
  fail("Pending slip review must preview the source image from the device.");
}

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

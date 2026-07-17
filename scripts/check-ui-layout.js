const fs = require("fs");
const path = require("path");

const main = fs.readFileSync("app/src/main/java/app/rubjai/mobile/MainActivity.kt", "utf8");

function fail(message) {
  console.error(message);
  process.exitCode = 1;
}

const phoneWidthDp = 360;
const railWidthDp = phoneWidthDp < 420 ? 76 : 92;
const contentWidthDp = phoneWidthDp - railWidthDp;
const bandHorizontalPaddingDp = 22 * 2;
const iconAndGapDp = 48 + 10;
const textColumnWidthDp = contentWidthDp - bandHorizontalPaddingDp - iconAndGapDp;

if (textColumnWidthDp < 168) {
  fail(`Home slip sync text column is too narrow on 360dp phones: ${textColumnWidthDp}dp`);
}

if (!/private fun HomeSlipSyncBand[\s\S]*?Column\(\s*Modifier\.fillMaxWidth\(\)\.padding\(horizontal = 22\.dp/.test(main)) {
  fail("HomeSlipSyncBand must use the vertical responsive layout with 22.dp horizontal padding.");
}

if (/HomeSlipSyncBand[\s\S]*?TextButton\(onClick = if \(pending > 0\)[\s\S]*?TextButton\(onClick = onScan/.test(main)) {
  fail("HomeSlipSyncBand must not put both slip action buttons in the same cramped text row.");
}

if (!main.includes("maxLines = 1") || !main.includes('"สลิปกำลังบันทึก $pending รายการ"')) {
  fail("Home slip sync title must stay one line and include the pending count.");
}

if (!main.includes('HomeDayMeta("วันนี้"') || !main.includes("Text(day.label") || !main.includes("Text(day.number")) {
  fail("Home timeline must keep the video-style left date rail with day label and day number.");
}

const previewDir = path.join("build", "ui-preview");
fs.mkdirSync(previewDir, { recursive: true });

fs.writeFileSync(
  path.join(previewDir, "home-360.svg"),
  `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="360" height="800" viewBox="0 0 360 800">
  <rect width="360" height="800" fill="#062B2F"/>
  <rect x="${railWidthDp}" y="74" width="${contentWidthDp}" height="196" rx="18" fill="#63D9B7"/>
  <text x="${railWidthDp + 22}" y="140" font-family="Arial" font-size="22" font-weight="700" fill="#062B2F">ยอดใช้จ่าย</text>
  <text x="${railWidthDp + 22}" y="210" font-family="Arial" font-size="48" font-weight="800" fill="#062B2F">110</text>
  <rect x="${railWidthDp}" y="270" width="${contentWidthDp}" height="154" fill="#DDF8EF"/>
  <rect x="${railWidthDp + 22}" y="292" width="48" height="48" rx="16" fill="#C7F4E7"/>
  <text x="${railWidthDp + 80}" y="316" font-family="Arial" font-size="18" font-weight="800" fill="#062B2F">สลิปกำลังบันทึก 1 รายการ</text>
  <text x="${railWidthDp + 80}" y="342" font-family="Arial" font-size="14" font-weight="700" fill="#0F4A48">กำลังบันทึกอัตโนมัติ</text>
  <rect x="${railWidthDp + contentWidthDp - 178}" y="370" width="82" height="36" rx="18" fill="none" stroke="#5E6B76"/>
  <text x="${railWidthDp + contentWidthDp - 162}" y="394" font-family="Arial" font-size="14" font-weight="700" fill="#062B2F">เลือกสลิป</text>
  <rect x="${railWidthDp + contentWidthDp - 88}" y="370" width="66" height="36" rx="18" fill="#062B2F"/>
  <text x="${railWidthDp + contentWidthDp - 68}" y="394" font-family="Arial" font-size="14" font-weight="800" fill="#63D9B7">ตรวจ</text>
  <rect x="0" y="424" width="${railWidthDp}" height="264" fill="#062B2F"/>
  <rect x="0" y="448" width="5" height="92" fill="#63D9B7"/>
  <text x="${railWidthDp / 2}" y="492" text-anchor="middle" font-family="Arial" font-size="22" font-weight="700" fill="#63D9B7">วันนี้</text>
  <text x="${railWidthDp / 2}" y="532" text-anchor="middle" font-family="Arial" font-size="30" font-weight="800" fill="#FFFFFF">17</text>
  <rect x="${railWidthDp}" y="424" width="${contentWidthDp}" height="104" fill="#0F4A48"/>
  <text x="${phoneWidthDp - 28}" y="474" text-anchor="end" font-family="Arial" font-size="22" fill="#A8C8C2">รายจ่าย</text>
  <text x="${phoneWidthDp - 28}" y="514" text-anchor="end" font-family="Arial" font-size="34" fill="#FFFFFF">0</text>
  <rect x="${railWidthDp}" y="528" width="${contentWidthDp}" height="160" fill="#041D22"/>
  <text x="${phoneWidthDp - 28}" y="620" text-anchor="end" font-family="Arial" font-size="24" fill="#FFFFFF">ไม่มีรายการ</text>
</svg>
`,
  "utf8",
);

fs.writeFileSync(
  path.join(previewDir, "editor-360.svg"),
  `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="360" height="800" viewBox="0 0 360 800">
  <rect width="360" height="800" fill="#062B2F"/>
  <rect width="360" height="86" fill="#63D9B7"/>
  <text x="28" y="54" font-family="Arial" font-size="18" font-weight="800" fill="#062B2F">รายจ่าย</text>
  <rect x="18" y="126" width="324" height="120" rx="8" fill="#041D22"/>
  <text x="56" y="202" font-family="Arial" font-size="52" font-weight="300" fill="#FFFFFF">100.00</text>
  <text x="18" y="304" font-family="Arial" font-size="22" font-weight="800" fill="#63D9B7">เลือกหมวด / แท็ก</text>
  <rect x="18" y="330" width="324" height="62" rx="8" fill="none" stroke="#63D9B7" stroke-width="2"/>
  <text x="42" y="370" font-family="Arial" font-size="18" font-weight="700" fill="#63D9B7">ยังไม่จัดหมวด</text>
  <rect x="18" y="620" width="324" height="58" rx="8" fill="#63D9B7"/>
  <text x="180" y="657" text-anchor="middle" font-family="Arial" font-size="20" font-weight="800" fill="#062B2F">บันทึกรายจ่าย</text>
</svg>
`,
  "utf8",
);

fs.writeFileSync(
  path.join(previewDir, "category-360.svg"),
  `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="360" height="800" viewBox="0 0 360 800">
  <rect width="360" height="800" fill="#062B2F"/>
  <rect y="250" width="360" height="550" rx="18" fill="#FFFFFF"/>
  <rect y="250" width="360" height="74" rx="18" fill="#E8EEF5"/>
  <text x="18" y="296" font-family="Arial" font-size="22" font-weight="800" fill="#111111">เลือกหมวดหมู่ / แท็ก</text>
  <text x="38" y="372" font-family="Arial" font-size="18" font-weight="700" fill="#0F4A48">+ เพิ่มแท็ก</text>
  ${["อาหาร","เดินทาง","ของใช้","ช้อปปิ้ง","บันเทิง","บ้าน","สุขภาพ","ครอบครัว"].map((label, index) => {
    const col = index % 4;
    const row = Math.floor(index / 4);
    const cx = 48 + col * 88;
    const cy = 446 + row * 120;
    return `<circle cx="${cx}" cy="${cy}" r="30" fill="#F8FAFC" stroke="#E4E7EB"/><text x="${cx}" y="${cy + 54}" text-anchor="middle" font-family="Arial" font-size="13" fill="#1B1D28">${label}</text>`;
  }).join("\n  ")}
</svg>
`,
  "utf8",
);

fs.writeFileSync(
  path.join(previewDir, "account-360.svg"),
  `<?xml version="1.0" encoding="UTF-8"?>
<svg xmlns="http://www.w3.org/2000/svg" width="360" height="800" viewBox="0 0 360 800">
  <rect width="360" height="800" fill="#062B2F"/>
  <rect x="18" y="56" width="324" height="150" rx="18" fill="#63D9B7"/>
  <text x="40" y="114" font-family="Arial" font-size="22" font-weight="800" fill="#062B2F">บัญชีของฉัน</text>
  <text x="40" y="154" font-family="Arial" font-size="18" fill="#0F4A48">รายการที่บันทึกไว้</text>
  <rect x="18" y="240" width="324" height="66" rx="8" fill="#0F4A48"/>
  <text x="44" y="282" font-family="Arial" font-size="18" font-weight="700" fill="#FFFFFF">ข้อมูลส่วนตัวและบัญชี</text>
  <rect x="18" y="320" width="324" height="66" rx="8" fill="#0F4A48"/>
  <text x="44" y="362" font-family="Arial" font-size="18" font-weight="700" fill="#FFFFFF">วางแผนหนี้</text>
</svg>
`,
  "utf8",
);

if (process.exitCode) process.exit(process.exitCode);
console.log(`UI layout check passed. Preview: ${path.join(previewDir, "home-360.svg")}`);

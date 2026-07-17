const fs = require("fs");
const path = require("path");

const root = process.cwd();
const ignoredDirectories = new Set([".git", ".gradle", ".kotlin", ".tools", ".toolchain", "build"]);
const riskyExtensions = new Set([".jpg", ".jpeg", ".png", ".webp", ".heic", ".pdf"]);
const allowedBinaryAssets = new Set([
  path.normalize("app/src/main/res/drawable-nodpi/rubjai_mascot.png"),
  path.normalize("app/src/main/res/drawable-nodpi/rubjai_icon.png"),
]);
const forbiddenTextPatterns = [
  /C:[\\/]+Users[\\/]+[^"'\s]+[\\/]+Downloads/i,
  /Record_2026-07-17/i,
  /Screenshot_2026-07-17/i,
  /01619[0-9A-Z]+/i,
  /DM260716/i,
];

const findings = [];

function walk(directory) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const fullPath = path.join(directory, entry.name);
    const relative = path.relative(root, fullPath);
    const normalized = path.normalize(relative);
    if (entry.isDirectory()) {
      if (!ignoredDirectories.has(entry.name)) walk(fullPath);
      continue;
    }

    const ext = path.extname(entry.name).toLowerCase();
    if (normalized === path.normalize("scripts/check-privacy.js")) continue;
    if (riskyExtensions.has(ext) && !allowedBinaryAssets.has(normalized)) {
      findings.push(`${relative}: image/pdf files are not allowed in repo unless explicitly whitelisted`);
      continue;
    }

    if (![".kt", ".js", ".md", ".yml", ".yaml", ".xml", ".kts", ".gradle", ".txt", ".sh"].includes(ext)) continue;
    const text = fs.readFileSync(fullPath, "utf8");
    forbiddenTextPatterns.forEach((pattern) => {
      if (pattern.test(text)) findings.push(`${relative}: contains private local slip/screenshot/video reference`);
    });
  }
}

walk(root);

if (findings.length) {
  console.error("Privacy check failed. Do not commit private slip images, screenshots, videos, local Downloads paths, or real slip references.");
  findings.forEach((finding) => console.error(finding));
  process.exit(1);
}

console.log("Privacy check passed.");

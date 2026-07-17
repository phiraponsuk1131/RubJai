const fs = require("fs");
const path = require("path");

const root = process.cwd();
const allowedExtensions = new Set([".kt", ".md"]);
const ignoredDirectories = new Set([".git", ".gradle", ".tools", ".toolchain", "build"]);

const badPatterns = [
  { name: "replacement character", pattern: /\uFFFD/ },
  { name: "C1 control character", pattern: /[\u0080-\u009F]/ },
  { name: "UTF-8/Windows-874 mojibake", pattern: /(?:เธ|เน|โ€)[\u0080-\u009F]/ },
  { name: "repeated Thai mojibake fragments", pattern: /(?:เธ.*เธ|เน.*เน|โ€)/ },
  { name: "mojibake bullet/ellipsis/arrow", pattern: /โ€[ขฆ]|โ\u0086[\u0092\u0093]/ },
];

const findings = [];

function walk(directory) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    if (entry.isDirectory()) {
      if (!ignoredDirectories.has(entry.name)) walk(path.join(directory, entry.name));
      continue;
    }

    const file = path.join(directory, entry.name);
    if (!allowedExtensions.has(path.extname(file))) continue;
    const lines = fs.readFileSync(file, "utf8").split(/\r?\n/);

    lines.forEach((line, index) => {
      badPatterns.forEach(({ name, pattern }) => {
        if (pattern.test(line)) {
          findings.push({
            file: path.relative(root, file),
            line: index + 1,
            name,
            text: line.trim().slice(0, 180),
          });
        }
      });
    });
  }
}

walk(root);

if (findings.length > 0) {
  console.error("Text integrity check failed. Fix mojibake or invalid Unicode before building APK.");
  findings.slice(0, 80).forEach((finding) => {
    console.error(`${finding.file}:${finding.line} ${finding.name}: ${finding.text}`);
  });
  if (findings.length > 80) console.error(`...and ${findings.length - 80} more finding(s).`);
  process.exit(1);
}

console.log("Text integrity check passed.");

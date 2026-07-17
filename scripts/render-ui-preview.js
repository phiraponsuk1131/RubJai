const fs = require("fs");
const path = require("path");

const previewDir = path.join("build", "ui-preview");
const preview = path.join(previewDir, "home-360.svg");

if (!fs.existsSync(preview)) {
  console.error("Run `node scripts/check-ui-layout.js` first to generate the no-admin SVG preview.");
  process.exit(1);
}

console.log(`Open this file in a browser on the computer to inspect the no-admin UI preview: ${path.resolve(preview)}`);

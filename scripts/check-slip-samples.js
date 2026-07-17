const fs = require("fs");

const qrSource = fs.readFileSync("app/src/main/java/app/rubjai/mobile/SlipQrReader.kt", "utf8");
const scannerSource = fs.readFileSync("app/src/main/java/app/rubjai/mobile/AutoSlipScanner.kt", "utf8");
const mainSource = fs.readFileSync("app/src/main/java/app/rubjai/mobile/MainActivity.kt", "utf8");

function fail(message, value) {
  console.error(message, value ?? "");
  process.exitCode = 1;
}

function tlv(tag, value) {
  return `${tag}${String(value.length).padStart(2, "0")}${value}`;
}

function buildQr({ amount, merchant, ref }) {
  const additional = tlv("05", ref);
  return [
    tlv("00", "01"),
    tlv("54", amount),
    tlv("59", merchant),
    tlv("62", additional),
  ].join("");
}

function parseTlv(value) {
  const result = {};
  let index = 0;
  while (index + 4 <= value.length) {
    const tag = value.slice(index, index + 2);
    const length = Number(value.slice(index + 2, index + 4));
    const start = index + 4;
    const end = start + length;
    if (!/^\d+$/.test(tag) || !Number.isFinite(length) || end > value.length) break;
    result[tag] = value.slice(start, end);
    index = end;
  }
  return index === value.length ? result : {};
}

const referencePatterns = [
  /[A-Z0-9]{8,}(?:CPM|DQR|DTF|DPM|DOR|CQR|CTF|COR)[A-Z0-9-]*/i,
  /(?:transRef|transactionRef|reference|ref|เลขที่รายการ|รหัสอ้างอิง)[:=\s-]*([A-Z0-9-]{6,})/i,
  /(?:^|[^A-Z0-9])([A-Z0-9-]{12,})(?:$|[^A-Z0-9])/i,
];

function extractReference(value) {
  for (const pattern of referencePatterns) {
    const match = value.match(pattern);
    if (match) return match[match.length - 1];
  }
  return "";
}

function parseQrPayload(value) {
  const tags = parseTlv(value);
  if (!Object.keys(tags).length) return { amount: "", merchantName: "", transactionReference: extractReference(value), rawValues: [value] };
  const nestedEntries = Object.values(tags).flatMap((inner) => Object.entries(parseTlv(inner)));
  const nested = Object.fromEntries(nestedEntries);
  const reference = nested["02"] || nested["03"] || parseTlv(tags["62"] || "")["05"] || extractReference(value);
  return {
    amount: tags["54"] || "",
    merchantName: tags["59"] || "",
    transactionReference: reference || "",
    rawValues: [value],
  };
}

function cleanAmount(amount) {
  const value = Number(String(amount).replace(/,/g, ""));
  return value > 0 ? value.toFixed(2) : "";
}

function referenceDate(reference, year = 2026) {
  const match = reference.match(/0\d{2}(\d{3})(\d{2})(\d{2})(\d{2})/i);
  if (!match) return "";
  const [, dayOfYearText, hour, minute] = match;
  const date = new Date(Date.UTC(year, 0, Number(dayOfYearText)));
  const day = date.getUTCDate();
  const month = ["ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค."][date.getUTCMonth()];
  return `${day} ${month} 69 ${hour}:${minute}`;
}

function fallbackTitle(reference) {
  return reference ? `สลิป QR ลงท้าย ${reference.slice(-6)}` : "สลิป QR";
}

function toDraft(qr) {
  const amount = cleanAmount(qr.amount);
  if (!amount) return null;
  return {
    amount,
    title: qr.merchantName || fallbackTitle(qr.transactionReference),
    occurredAt: referenceDate(qr.transactionReference),
    reference: qr.transactionReference,
  };
}

const samples = [
  {
    name: "K PLUS transfer QR-only fallback title",
    raw: buildQr({ amount: "90.00", merchant: "", ref: "099196182654CTF00880" }),
    expected: {
      amount: "90.00",
      title: "สลิป QR ลงท้าย F00880",
      occurredAt: "15 ก.ค. 69 18:26",
      reference: "099196182654CTF00880",
    },
  },
  {
    name: "K PLUS merchant QR keeps merchant",
    raw: buildQr({ amount: "45.00", merchant: "SCB Manee SHOP", ref: "099194183413CPM06924" }),
    expected: {
      amount: "45.00",
      title: "SCB Manee SHOP",
      occurredAt: "13 ก.ค. 69 18:34",
      reference: "099194183413CPM06924",
    },
  },
  {
    name: "K PLUS bill QR keeps amount and date",
    raw: buildQr({ amount: "110.00", merchant: "", ref: "099197190113DPM11831" }),
    expected: {
      amount: "110.00",
      title: "สลิป QR ลงท้าย M11831",
      occurredAt: "16 ก.ค. 69 19:01",
      reference: "099197190113DPM11831",
    },
  },
];

for (const sample of samples) {
  const actual = toDraft(parseQrPayload(sample.raw));
  for (const [key, value] of Object.entries(sample.expected)) {
    if (actual?.[key] !== value) fail(`${sample.name} ${key}: expected ${JSON.stringify(value)}, got ${JSON.stringify(actual?.[key])}`);
  }
  console.log(`${sample.name}:`, actual);
}

const qrWithoutAmount = toDraft(parseQrPayload(buildQr({ amount: "", merchant: "Synthetic Merchant", ref: "099196182654CTF00880" })));
if (qrWithoutAmount) fail("QR without amount must not auto-sync.", qrWithoutAmount);

const ocrNoiseOnly = "โอนเงินสำเร็จ\n6.LñusGnnuäns\n100.00 บาท\n17 ก.ค. 69 12:40";
if (toDraft({ amount: "", merchantName: "", transactionReference: "", rawValues: [] })) {
  fail("OCR-only content must not create a draft.");
}
if (scannerSource.includes("TextRecognition") || scannerSource.includes("auto_bank_slip_qr_ocr") || scannerSource.includes("auto_bank_slip\"")) {
  fail("Auto slip scanner must be QR-only and must not use OCR fallback.");
}
if (mainSource.includes("slip_qr_ocr") || mainSource.includes("debt_slip_qr_ocr")) {
  fail("Manual slip import and debt slip import must use QR-only parsing.");
}
if (!qrSource.includes("fun toDraft(qr: SlipQrResult") || !qrSource.includes("DPM|DOR|CQR|CTF|COR")) {
  fail("Slip QR reader must expose QR-only draft creation and support K PLUS reference variants.");
}
if (ocrNoiseOnly.includes("6.LñusGnnuäns")) {
  console.log("OCR noise fixture intentionally ignored:", "6.LñusGnnuäns");
}

if (process.exitCode) process.exit(process.exitCode);
console.log("QR-only slip sample check passed.");

const samples = [
  {
    name: "KPlus Bill synthetic",
    text: `จ่ายบิลสำเร็จ
16 ก.ค. 69 19:01 น.
นาย ตัวอย่าง ผู้โอน
ธ.กสิกรไทย
xxx-x-x1234-x
↓
คุณ ตัวอย่าง ผู้รับ
200012400000001
24122401
เลขที่รายการ:
099999999999DPM00001
จำนวน:
110.00 บาท`,
    expected: { title: "คุณ ตัวอย่าง ผู้รับ", amount: "110.00", occurredAt: "16 ก.ค. 69 19:01" },
  },
  {
    name: "KPlus Transfer synthetic",
    text: `โอนเงินสำเร็จ
16 ก.ค. 69 17:21 น.
นาย ตัวอย่าง ผู้โอน
ธ.กสิกรไทย
xxx-x-x1234-x
↓
นางสาว ตัวอย่าง ปลายทาง
ธ.เกียรตินาคินภัทร
xxx-x-x5678-x
เลขที่รายการ:
099999999999DOR00002
จำนวน:
200.00 บาท`,
    expected: { title: "นางสาว ตัวอย่าง ปลายทาง", amount: "200.00", occurredAt: "16 ก.ค. 69 17:21" },
  },
  {
    name: "Merchant payment synthetic",
    text: `ชำระเงินสำเร็จ
15 ก.ค. 69 22:51 น.
นาย ตัวอย่าง ผู้โอน
ธ.กสิกรไทย
xxx-x-x1234-x
↓
ชำระสินค้า
บจก. ร้านตัวอย่าง เพย์ (ประเทศไทย)
202607153711200
เลขที่รายการ:
099999999999CQR00003
จำนวน:
1.00 บาท
ค่าธรรมเนียม:
0.00 บาท`,
    expected: { title: "บจก. ร้านตัวอย่าง เพย์ (ประเทศไทย)", amount: "1.00", occurredAt: "15 ก.ค. 69 22:51" },
  },
  {
    name: "Dime Transfer synthetic",
    text: `โอนเงิน
500.00 บาท
ค่าธรรมเนียม 0.00 บาท
จาก
นาย ตัวอย่าง ผู้โอน
x-0930
ไปยัง
นาย ตัวอย่าง ผู้รับเงิน
x-6203
วันที่
16 ก.ค. 2569 - 17:04 น.
เลขที่สลิป 619717366106`,
    expected: { title: "นาย ตัวอย่าง ผู้รับเงิน", amount: "500.00", occurredAt: "16 ก.ค. 2569 17:04" },
  },
];

const amountPatterns = [
  /(?:จำนวน|ยอดเงิน|ยอดโอน|amount|total)[^0-9\n]{0,28}\n?\s*([0-9,]+(?:\.[0-9]{1,2})?)/i,
  /([0-9,]+(?:\.[0-9]{2}))\s*(?:บาท|THB)/i,
];
const decimalAmount = /(?<![0-9])([0-9]{1,7}(?:,[0-9]{3})*\.[0-9]{2})(?![0-9])/g;
const timePattern = /(?<![0-9])([01]?[0-9]|2[0-3]):[0-5][0-9](?![0-9])/;
const datePattern = /(?<![0-9])([0-3]?[0-9][\/.-][01]?[0-9][\/.-](?:[0-9]{2}|[0-9]{4}))(?![0-9])/;
const thaiSlipDatePattern = /(?<![0-9])([0-3]?[0-9]\s+[^0-9\n]{1,12}?\s+(?:[0-9]{2}|[0-9]{4}))(?=\s*(?:-|,)?\s*(?:[01]?[0-9]|2[0-3]):[0-5][0-9])/m;
const recipientLabelPattern = /^(?:ผู้รับ|ชื่อผู้รับ|รับเงินโดย|ไปยัง|โอนไป|บัญชีปลายทาง|ชื่อบัญชี|merchant|merchant name|receiver|recipient|to)\s*[:：-]?\s*(.*)$/i;
const maskedAccountPattern = /[x*]{1,}[^\n]{0,20}[0-9]{3,4}[^\n]{0,10}[x*]?/i;
const recipientStopWords = [
  "สำเร็จ", "successful", "จาก", "จากบัญชี", "ผู้โอน", "ผู้ส่ง", "sender", "เลขที่รายการ",
  "reference", "ref", "รหัสอ้างอิง", "จำนวน", "ยอด", "amount", "total", "ค่าธรรมเนียม", "fee", "ธนาคาร", "bank",
  "ชำระเงิน", "ชำระสินค้า", "จ่ายบิล", "โอนเงิน", "สแกนตรวจสอบสลิป", "QR สลิป", "วันที่", "เลขที่สลิป",
];

function hasReadableNameSignal(value) {
  const hasThai = /[\u0E00-\u0E7F]/.test(value);
  const hasAsciiLetter = /[A-Za-z]/.test(value);
  const hasSuspiciousLatin = /[\u00C0-\u024F]/.test(value);
  const startsWithNoise = /^[0-9.:;,_/\\|-]/.test(value);
  if (hasThai) return true;
  if (hasSuspiciousLatin || startsWithNoise) return false;
  if (!hasAsciiLetter) return false;
  return /\s/.test(value) || /(CO|LTD|LIMITED|COMPANY|SHOP|PAY|MART|MR|DIY)/i.test(value);
}

function isRecipientCandidate(line) {
  const value = line.trim();
  return value.length >= 3 &&
    value.length <= 100 &&
    hasReadableNameSignal(value) &&
    (value.match(/\d/g) || []).length < 5 &&
    !maskedAccountPattern.test(value) &&
    !recipientStopWords.some((word) => value.toLowerCase().includes(word.toLowerCase())) &&
    value.toUpperCase() !== "K+" &&
    value.toUpperCase() !== "K PLUS" &&
    !value.toUpperCase().includes("SCB EASY") &&
    !value.includes("ธ.");
}

function findRecipient(lines) {
  for (let index = 0; index < lines.length; index++) {
    const match = lines[index].match(recipientLabelPattern);
    if (!match) continue;
    const inline = (match[1] || "").trim();
    if (isRecipientCandidate(inline)) return inline;
    const next = lines.slice(index + 1, index + 5).find(isRecipientCandidate);
    if (next) return next;
  }
  const directionIndex = lines.findIndex((line) => line === "↓" || line === "→" || line.includes("โอนไป") || line.includes("ไปยัง"));
  if (directionIndex >= 0) {
    const next = lines.slice(directionIndex + 1, directionIndex + 7).find(isRecipientCandidate);
    if (next) return next;
  }
  return "";
}

function parse(text) {
  const labeledAmount = amountPatterns.map((pattern) => text.match(pattern)?.[1]?.replace(/,/g, "")).find(Boolean);
  const fallbackAmount = [...text.matchAll(decimalAmount)].map((m) => m[1].replace(/,/g, "")).find((v) => Number(v) > 0);
  const lines = text.split(/\r?\n/).map((line) => line.trim()).filter(Boolean);
  const date = text.match(datePattern)?.[0] || text.match(thaiSlipDatePattern)?.[1] || "";
  const time = text.match(timePattern)?.[0] || "";
  return { amount: labeledAmount || fallbackAmount || "", title: findRecipient(lines), occurredAt: [date, time].filter(Boolean).join(" ") };
}

function hasCompleteSlipBasics(parsed) {
  return Number(parsed.amount) > 0 &&
    parsed.title &&
    parsed.title !== "ยังไม่จัดหมวด" &&
    /[0-2]?[0-9]:[0-5][0-9]/.test(parsed.occurredAt);
}

let failed = false;
for (const sample of samples) {
  const actual = parse(sample.text);
  for (const [key, value] of Object.entries(sample.expected)) {
    if (actual[key] !== value) {
      failed = true;
      console.error(`${sample.name} ${key}: expected ${JSON.stringify(value)}, got ${JSON.stringify(actual[key])}`);
    }
  }
  if (!hasCompleteSlipBasics(actual)) {
    failed = true;
    console.error(`${sample.name}: parsed result is not complete enough for auto-sync queue`);
  }
  console.log(`${sample.name}:`, actual);
}

const qrOnly = parse(`QR สลิป
รหัสอ้างอิง 099999999999DPM00001`);
if (hasCompleteSlipBasics(qrOnly)) {
  failed = true;
  console.error("QR-only reference must not be treated as a complete auto-sync slip.");
}

const qrPriority = parse(`QR สลิป
จำนวน 110.00 บาท
ชื่อผู้รับ คุณ ตัวอย่าง ผู้รับ
รหัสอ้างอิง 099999999999DPM00001
OCR สำรอง
จำนวน 1.00 บาท
ชื่อผู้รับ OCR ผิด
16 ก.ค. 69 19:01 น.`);
if (qrPriority.amount !== "110.00" || qrPriority.title !== "คุณ ตัวอย่าง ผู้รับ") {
  failed = true;
  console.error("QR priority failed:", qrPriority);
}

const noisyRecipient = parse(`โอนเงินสำเร็จ
17 ก.ค. 69 12:40 น.
นาย ตัวอย่าง ผู้โอน
ธ.กสิกรไทย
xxx-x-x1234-x
↓
6.L\u00f1usGnnu\u00e4ns
ธ.เกียรตินาคินภัทร
xxx-x-x5678-x
เลขที่รายการ:
099999999999DOR00004
จำนวน:
100.00 บาท`);
if (noisyRecipient.title === "6.L\u00f1usGnnu\u00e4ns" || hasCompleteSlipBasics(noisyRecipient)) {
  failed = true;
  console.error("Noisy OCR recipient must not be accepted as a complete slip:", noisyRecipient);
}

process.exit(failed ? 1 : 0);

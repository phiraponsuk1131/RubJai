const samples = [
  {
    name: "KPlus ShopeePay",
    text: `ชำระเงินสำเร็จ
15 ก.ค. 69 22:51 น.
นาย พีรพล ส
ธ.กสิกรไทย
xxx-x-x7620-x
↓
ชำระสินค้าช้อปปี้
บจก. ช้อปปี้เพย์ (ประเทศไทย)
202607153711200
เลขที่รายการ:
016196225145CQR02202
จำนวน:
1.00 บาท
ค่าธรรมเนียม:
0.00 บาท`,
    expected: { title: "บจก. ช้อปปี้เพย์ (ประเทศไทย)", amount: "1.00", occurredAt: "15 ก.ค. 69 22:51" },
  },
  {
    name: "KPlus Bill",
    text: `จ่ายบิลสำเร็จ
16 ก.ค. 69 19:01 น.
นาย พีรพล ส
ธ.กสิกรไทย
xxx-x-x7620-x
↓
อรรสา รอดแสวง
200012412249267
24122401
เลขที่รายการ:
016197190113DPM11831
จำนวน:
110.00 บาท`,
    expected: { title: "อรรสา รอดแสวง", amount: "110.00", occurredAt: "16 ก.ค. 69 19:01" },
  },
  {
    name: "KPlus Transfer",
    text: `โอนเงินสำเร็จ
16 ก.ค. 69 17:21 น.
นาย พีรพล ส
ธ.กสิกรไทย
xxx-x-x7620-x
↓
นาย พีรพล สุขพลาย
ธ.เกียรตินาคินภัทร
xxx-x-x1028-x
เลขที่รายการ:
016197172106DOR09793
จำนวน:
200.00 บาท`,
    expected: { title: "นาย พีรพล สุขพลาย", amount: "200.00", occurredAt: "16 ก.ค. 69 17:21" },
  },
  {
    name: "Dime Transfer",
    text: `โอนเงิน
500.00 บาท
ค่าธรรมเนียม 0.00 บาท
จาก
นาย พีรพล สุขพลาย
x-0930
ไปยัง
นาย พีรพล สุขพลาย
x-6203
วันที่
16 ก.ค. 2569 - 17:04 น.
เลขที่สลิป 619717366106`,
    expected: { title: "นาย พีรพล สุขพลาย", amount: "500.00", occurredAt: "16 ก.ค. 2569 17:04" },
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
const maskedAccountPattern = /[x*]{1,}[^\\n]{0,20}[0-9]{3,4}[^\\n]{0,10}[x*]?/i;
const recipientStopWords = [
  "สำเร็จ", "successful", "จาก", "จากบัญชี", "ผู้โอน", "ผู้ส่ง", "sender", "เลขที่รายการ",
  "reference", "ref", "รหัสอ้างอิง", "จำนวน", "ยอด", "amount", "total", "ค่าธรรมเนียม", "fee", "ธนาคาร", "bank",
  "ชำระเงิน", "ชำระสินค้า", "จ่ายบิล", "โอนเงิน", "สแกนตรวจสอบสลิป", "QR สลิป", "วันที่", "เลขที่สลิป",
];

function isRecipientCandidate(line) {
  const value = line.trim();
  return value.length >= 3 &&
    value.length <= 100 &&
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

let failed = false;
for (const sample of samples) {
  const actual = parse(sample.text);
  for (const [key, value] of Object.entries(sample.expected)) {
    if (actual[key] !== value) {
      failed = true;
      console.error(`${sample.name} ${key}: expected ${JSON.stringify(value)}, got ${JSON.stringify(actual[key])}`);
    }
  }
  console.log(`${sample.name}:`, actual);
}
process.exit(failed ? 1 : 0);

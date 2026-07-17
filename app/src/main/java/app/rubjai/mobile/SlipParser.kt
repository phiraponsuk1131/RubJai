package app.rubjai.mobile

import java.util.Locale
import java.util.Date
import java.util.Calendar

object SlipParser {
    private val thaiMonths = arrayOf("ม.ค.", "ก.พ.", "มี.ค.", "เม.ย.", "พ.ค.", "มิ.ย.", "ก.ค.", "ส.ค.", "ก.ย.", "ต.ค.", "พ.ย.", "ธ.ค.")
    private val amountPatterns = listOf(
        Regex("(?:จำนวน|ยอดเงิน|ยอดโอน|amount|total)[^0-9\\n]{0,28}\\n?\\s*([0-9,]+(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE),
        Regex("([0-9,]+(?:\\.[0-9]{2}))\\s*(?:บาท|THB)", RegexOption.IGNORE_CASE),
    )
    private val decimalAmount = Regex("(?<![0-9])([0-9]{1,7}(?:,[0-9]{3})*\\.[0-9]{2})(?![0-9])")
    private val timePattern = Regex("(?<![0-9])([01]?[0-9]|2[0-3]):[0-5][0-9](?![0-9])")
    private val datePattern = Regex("(?<![0-9])([0-3]?[0-9][/.-][01]?[0-9][/.-](?:[0-9]{2}|[0-9]{4}))(?![0-9])")
    private val thaiSlipDatePattern = Regex("(?m)(?<![0-9])([0-3]?[0-9]\\s+[^0-9\\n]{1,12}?\\s+(?:[0-9]{2}|[0-9]{4}))(?=\\s*(?:-|,)?\\s*(?:[01]?[0-9]|2[0-3]):[0-5][0-9])")
    private val remarkPattern = Regex("(?:remark|note|memo|หมายเหตุ|บันทึกช่วยจำ)\\s*[:：-]?\\s*([^\\n]{2,120})", RegexOption.IGNORE_CASE)
    private val mrDiyPattern = Regex("(?i)MR\\s*[.\\-]?\\s*D\\s*[.\\-]?\\s*I\\s*[.\\-]?\\s*Y\\s*[.\\-]?[A-Z0-9.\\-]*")
    private val maskedAccountPattern = Regex("(?i)[x*]{2,}[^\\n]{0,20}[0-9]{3,4}[^\\n]{0,10}[x*]")
    private val recipientLabelPattern = Regex("(?i)^(?:ผู้รับ|ชื่อผู้รับ|รับเงินโดย|ไปยัง|โอนไป|บัญชีปลายทาง|ชื่อบัญชี|merchant|merchant name|receiver|recipient|to)\\s*[:：-]?\\s*(.*)$")
    private val recipientStopWords = listOf(
        "สำเร็จ", "successful", "จาก", "จากบัญชี", "ผู้โอน", "ผู้ส่ง", "sender", "เลขที่รายการ",
        "reference", "ref", "รหัสอ้างอิง", "จำนวน", "ยอด", "amount", "total", "ค่าธรรมเนียม", "fee", "ธนาคาร", "bank",
        "ชำระเงิน", "ชำระสินค้า", "จ่ายบิล", "โอนเงิน", "สแกนตรวจสอบสลิป", "QR สลิป",
    )
    fun parse(text: String, source: String, imageDate: Date? = null): DraftTransaction {
        val labeledAmount = amountPatterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1) }?.replace(",", "")
        val fallbackAmounts = decimalAmount.findAll(text).mapNotNull { it.groupValues[1].replace(",", "").toDoubleOrNull() }.filter { it > 0.0 }.toList()
        val amount = labeledAmount ?: fallbackAmounts.firstOrNull()?.let { "%.2f".format(Locale.US, it) }.orEmpty()
        val lines = text.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        val recognizedMrDiy = mrDiyPattern.find(text)?.value?.replace(Regex("\\s+"), "")
        val recipient = findRecipient(lines)
        val merchant = recognizedMrDiy ?: recipient ?: lines.firstOrNull { line ->
            line.length in 3..100 && listOf("SHOP", "MR.D.I.Y", "MINOR", "LIMITED", "CO.,LTD", "COMPANY").any { line.contains(it, true) }
        }.orEmpty()
        val remark = remarkPattern.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val category = "ยังไม่จัดหมวด"
        val rawDate = datePattern.find(text)?.value ?: thaiSlipDatePattern.find(text)?.groupValues?.getOrNull(1)?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        val date = normalizeDate(rawDate, imageDate)
        val time = timePattern.find(text)?.value.orEmpty()
        return DraftTransaction(amount, merchant.ifBlank { category }, TransactionType.EXPENSE, source, text, category, remark, listOf(date, time).filter(String::isNotBlank).joinToString(" "))
    }

    private fun findRecipient(lines: List<String>): String? {
        lines.forEachIndexed { index, line ->
            val match = recipientLabelPattern.find(line) ?: return@forEachIndexed
            val inline = match.groupValues.getOrNull(1).orEmpty().trim()
            if (isRecipientCandidate(inline)) return inline
            lines.drop(index + 1).take(4).firstOrNull(::isRecipientCandidate)?.let { return it }
        }

        val directionIndex = lines.indexOfFirst { line ->
            line == "↓" || line == "→" || line.contains("โอนไป", true) || line.contains("ไปยัง", true)
        }
        if (directionIndex >= 0) {
            lines.drop(directionIndex + 1).take(6).firstOrNull(::isRecipientCandidate)?.let { return it }
        }

        val accountIndexes = lines.indices.filter { maskedAccountPattern.containsMatchIn(lines[it]) }
        val recipientAccountIndex = accountIndexes.getOrNull(1) ?: return null
        val candidate = (recipientAccountIndex - 1 downTo (recipientAccountIndex - 4).coerceAtLeast(0)).map { lines[it] }.firstOrNull { line ->
            isRecipientCandidate(line)
        }
        if (!candidate.isNullOrBlank()) return candidate
        val ending = Regex("[0-9]{3,4}").find(lines[recipientAccountIndex])?.value
        return ending?.let { "โอนไปบัญชีลงท้าย $it" }
    }

    private fun isRecipientCandidate(line: String): Boolean {
        val value = line.trim()
        return value.length in 3..100 &&
            value.count(Char::isDigit) < 5 &&
            !maskedAccountPattern.containsMatchIn(value) &&
            recipientStopWords.none { value.contains(it, true) } &&
            !value.contains("ธ.", true) &&
            !value.contains("ธนาคาร", true) &&
            !value.contains("บัญชี", true) &&
            !value.equals("K+", true) &&
            !value.equals("K PLUS", true) &&
            !value.contains("SCB EASY", true) &&
            !value.contains("Krungthai NEXT", true)
    }

    private fun normalizeDate(raw: String, imageDate: Date?): String {
        if (raw.isBlank()) return imageDate?.let(::thaiImageDate).orEmpty()
        if (datePattern.matches(raw) || raw.any { it in 'ก'..'๙' }) return raw
        val fallback = imageDate ?: return raw
        val day = Regex("^[0-3]?[0-9]").find(raw)?.value?.toIntOrNull() ?: return raw
        val year = Regex("(?:[0-9]{2}|[0-9]{4})$").find(raw)?.value ?: return raw
        val month = thaiMonths[Calendar.getInstance().apply { time = fallback }.get(Calendar.MONTH)]
        return "$day $month $year"
    }

    private fun thaiImageDate(date: Date): String {
        val calendar = Calendar.getInstance().apply { time = date }
        val buddhistYear = (calendar.get(Calendar.YEAR) + 543) % 100
        val time = "%02d:%02d".format(Locale.US, calendar.get(Calendar.HOUR_OF_DAY), calendar.get(Calendar.MINUTE))
        return "${calendar.get(Calendar.DAY_OF_MONTH)} ${thaiMonths[calendar.get(Calendar.MONTH)]} ${buddhistYear.toString().padStart(2, '0')} $time"
    }

}

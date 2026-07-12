package app.rubjai.mobile

import java.util.Locale
import java.util.Date
import java.util.Calendar
import java.text.SimpleDateFormat

object SlipParser {
    private val amountPatterns = listOf(
        Regex("(?:จำนวน|ยอด|amount|เงินเข้า|รับเงิน|โอนเงิน)[^0-9]{0,20}([0-9,]+(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE),
        Regex("([0-9,]+(?:\\.[0-9]{2}))\\s*(?:บาท|THB)", RegexOption.IGNORE_CASE),
    )
    private val decimalAmount = Regex("(?<![0-9])([0-9]{1,7}(?:,[0-9]{3})*\\.[0-9]{2})(?![0-9])")
    private val timePattern = Regex("(?<![0-9])([01]?[0-9]|2[0-3]):[0-5][0-9](?![0-9])")
    private val datePattern = Regex("(?<![0-9])([0-3]?[0-9][/.-][01]?[0-9][/.-](?:[0-9]{2}|[0-9]{4}))(?![0-9])")
    private val thaiSlipDatePattern = Regex("(?m)(?<![0-9])([0-3]?[0-9]\\s+[^0-9\\n]{1,12}?\\s+(?:[0-9]{2}|[0-9]{4}))(?=\\s+(?:[01]?[0-9]|2[0-3]):[0-5][0-9])")
    private val remarkPattern = Regex("(?:remark|note|memo|หมายเหตุ|บันทึกช่วยจำ)\\s*[:：-]?\\s*([^\\n]{2,120})", RegexOption.IGNORE_CASE)
    private val mrDiyPattern = Regex("(?i)MR\\s*[.\\-]?\\s*D\\s*[.\\-]?\\s*I\\s*[.\\-]?\\s*Y\\s*[.\\-]?[A-Z0-9.\\-]*")
    private val maskedAccountPattern = Regex("(?i)[x*]{2,}[^\\n]{0,20}[0-9]{3,4}[^\\n]{0,10}[x*]")
    fun parse(text: String, source: String, imageDate: Date? = null): DraftTransaction {
        val labeledAmount = amountPatterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1) }?.replace(",", "")
        val fallbackAmounts = decimalAmount.findAll(text).mapNotNull { it.groupValues[1].replace(",", "").toDoubleOrNull() }.filter { it > 0.0 }.toList()
        val amount = labeledAmount ?: fallbackAmounts.firstOrNull()?.let { "%.2f".format(Locale.US, it) }.orEmpty()
        val lines = text.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        val recognizedMrDiy = mrDiyPattern.find(text)?.value?.replace(Regex("\\s+"), "")
        val recipient = findKPlusRecipient(lines)
        val merchant = recognizedMrDiy ?: recipient ?: lines.firstOrNull { line ->
            line.length in 3..100 && listOf("SHOP", "MR.D.I.Y", "MINOR", "LIMITED", "CO.,LTD", "COMPANY").any { line.contains(it, true) }
        } ?: lines.firstOrNull { it.length in 3..80 && !it.any(Char::isDigit) && !it.contains(":") && !it.contains("สำเร็จ") }.orEmpty()
        val remark = remarkPattern.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val category = categorize("$merchant $remark $text")
        val rawDate = datePattern.find(text)?.value ?: thaiSlipDatePattern.find(text)?.groupValues?.getOrNull(1)?.replace(Regex("\\s+"), " ")?.trim().orEmpty()
        val date = normalizeDate(rawDate, imageDate)
        val time = timePattern.find(text)?.value.orEmpty()
        return DraftTransaction(amount, merchant, TransactionType.EXPENSE, source, text, category, remark, listOf(date, time).filter(String::isNotBlank).joinToString(" "))
    }

    private fun findKPlusRecipient(lines: List<String>): String? {
        val accountIndexes = lines.indices.filter { maskedAccountPattern.containsMatchIn(lines[it]) }
        val recipientAccountIndex = accountIndexes.getOrNull(1) ?: accountIndexes.lastOrNull() ?: return null
        val candidate = (recipientAccountIndex - 1 downTo (recipientAccountIndex - 4).coerceAtLeast(0)).map { lines[it] }.firstOrNull { line ->
            line.length in 3..100 && !line.contains("กสิกร", true) && !line.contains("KBank", true) && !line.contains("สำเร็จ") && !maskedAccountPattern.containsMatchIn(line) && line.count(Char::isDigit) < 5
        }
        if (!candidate.isNullOrBlank()) return candidate
        val ending = Regex("[0-9]{3,4}").find(lines[recipientAccountIndex])?.value
        return ending?.let { "โอนไปบัญชีลงท้าย $it" }
    }

    private fun normalizeDate(raw: String, imageDate: Date?): String {
        if (raw.isBlank()) return imageDate?.let { SimpleDateFormat("d MMM yy", Locale("th", "TH")).format(it) }.orEmpty()
        if (datePattern.matches(raw) || raw.any { it in 'ก'..'๙' }) return raw
        val fallback = imageDate ?: return raw
        val day = Regex("^[0-3]?[0-9]").find(raw)?.value?.toIntOrNull() ?: return raw
        val calendar = Calendar.getInstance().apply { time = fallback; set(Calendar.DAY_OF_MONTH, day.coerceIn(1, getActualMaximum(Calendar.DAY_OF_MONTH))) }
        return SimpleDateFormat("d MMM yy", Locale("th", "TH")).format(calendar.time)
    }

    private fun categorize(value: String): String {
        return when {
            listOf("restaurant", "food", "cafe", "coffee", "minor dq", "dairy queen", "อาหาร", "ข้าว", "กาแฟ").any { value.contains(it, true) } -> "อาหารและเครื่องดื่ม"
            listOf("grab", "bolt", "taxi", "bts", "mrt", "fuel", "gas", "เดินทาง", "น้ำมัน").any { value.contains(it, true) } -> "เดินทาง"
            listOf("mr.d.i.y", "shop", "store", "mall", "ของใช้", "ซื้อของ").any { value.contains(it, true) } -> "ของใช้/ช้อปปิ้ง"
            listOf("bill", "muni", "electric", "water", "internet", "ค่าไฟ", "ค่าน้ำ", "บิล").any { value.contains(it, true) } -> "บิล/สาธารณูปโภค"
            listOf("hospital", "clinic", "pharmacy", "health", "ยา", "โรงพยาบาล").any { value.contains(it, true) } -> "สุขภาพ"
            listOf("transfer", "โอนเงิน").any { value.contains(it, true) } -> "โอนเงิน"
            else -> "อื่น ๆ"
        }
    }
}

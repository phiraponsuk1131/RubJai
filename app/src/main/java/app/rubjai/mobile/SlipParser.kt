package app.rubjai.mobile

import java.util.Locale

object SlipParser {
    private val amountPatterns = listOf(
        Regex("(?:จำนวน|ยอด|amount|เงินเข้า|รับเงิน|โอนเงิน)[^0-9]{0,20}([0-9,]+(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE),
        Regex("([0-9,]+(?:\\.[0-9]{2}))\\s*(?:บาท|THB)", RegexOption.IGNORE_CASE),
    )
    private val decimalAmount = Regex("(?<![0-9])([0-9]{1,7}(?:,[0-9]{3})*\\.[0-9]{2})(?![0-9])")
    private val timePattern = Regex("(?<![0-9])([01]?[0-9]|2[0-3]):[0-5][0-9](?![0-9])")
    private val datePattern = Regex("(?<![0-9])([0-3]?[0-9][/.-][01]?[0-9][/.-](?:[0-9]{2}|[0-9]{4}))(?![0-9])")
    private val incomeWords = listOf("เงินเข้า", "รับเงิน", "ได้รับ", "salary", "เงินเดือน", "credit")
    private val remarkPattern = Regex("(?:remark|note|memo|หมายเหตุ|บันทึกช่วยจำ)\\s*[:：-]?\\s*([^\\n]{2,120})", RegexOption.IGNORE_CASE)
    fun parse(text: String, source: String): DraftTransaction {
        val labeledAmount = amountPatterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1) }?.replace(",", "")
        val fallbackAmounts = decimalAmount.findAll(text).mapNotNull { it.groupValues[1].replace(",", "").toDoubleOrNull() }.filter { it > 0.0 }.toList()
        val amount = labeledAmount ?: fallbackAmounts.firstOrNull()?.let { "%.2f".format(Locale.US, it) }.orEmpty()
        val type = if (incomeWords.any { text.contains(it, ignoreCase = true) }) TransactionType.INCOME else TransactionType.EXPENSE
        val lines = text.lineSequence().map(String::trim).filter(String::isNotBlank).toList()
        val merchant = lines.firstOrNull { line ->
            line.length in 3..100 && listOf("SHOP", "MR.D.I.Y", "MINOR", "LIMITED", "CO.,LTD", "COMPANY").any { line.contains(it, true) }
        } ?: lines.firstOrNull { it.length in 3..80 && !it.any(Char::isDigit) && !it.contains("สำเร็จ") }.orEmpty()
        val remark = remarkPattern.find(text)?.groupValues?.getOrNull(1)?.trim().orEmpty()
        val category = categorize("$merchant $remark $text", type)
        val date = datePattern.find(text)?.value.orEmpty()
        val time = timePattern.find(text)?.value.orEmpty()
        return DraftTransaction(amount, merchant, TransactionType.EXPENSE, source, text, category, remark, listOf(date, time).filter(String::isNotBlank).joinToString(" "))
    }

    private fun categorize(value: String, type: TransactionType): String {
        if (type == TransactionType.INCOME && listOf("salary", "เงินเดือน", "payroll").any { value.contains(it, true) }) return "เงินเดือน"
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

package app.rubjai.mobile

object SlipParser {
    private val amountPatterns = listOf(
        Regex("(?:จำนวน|ยอด|amount|เงินเข้า|รับเงิน|โอนเงิน)[^0-9]{0,20}([0-9,]+(?:\\.[0-9]{1,2})?)", RegexOption.IGNORE_CASE),
        Regex("([0-9,]+(?:\\.[0-9]{2}))\\s*(?:บาท|THB)", RegexOption.IGNORE_CASE),
    )
    private val incomeWords = listOf("เงินเข้า", "รับเงิน", "ได้รับ", "salary", "เงินเดือน", "credit")
    fun parse(text: String, source: String): DraftTransaction {
        val amount = amountPatterns.firstNotNullOfOrNull { it.find(text)?.groupValues?.getOrNull(1) }?.replace(",", "").orEmpty()
        val type = if (incomeWords.any { text.contains(it, ignoreCase = true) }) TransactionType.INCOME else TransactionType.EXPENSE
        val meaningfulLine = text.lineSequence().map(String::trim).firstOrNull { it.length in 3..80 && !it.any(Char::isDigit) }.orEmpty()
        return DraftTransaction(amount, meaningfulLine, type, source, text)
    }
}

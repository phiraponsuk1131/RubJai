package app.rubjai.mobile

import android.content.Context

object CategoryStore {
    private const val PREFS = "rubjai_categories"
    val expenseDefaults = listOf(
        "ยังไม่จัดหมวด", "อาหารและเครื่องดื่ม", "เดินทาง", "ท่องเที่ยว", "ของใช้และจิปาถะ",
        "ช้อปปิ้ง", "บิล/สาธารณูปโภค", "ที่พัก", "สุขภาพ", "การศึกษา",
        "ครอบครัว", "สัตว์เลี้ยง", "ชำระหนี้", "โอนเงิน", "ใช้จ่ายทั่วไป",
    )
    val incomeDefaults = listOf("เงินเดือน", "รายรับ", "โบนัส", "คืนเงิน", "ดอกเบี้ย", "ขายของ", "อื่น ๆ")

    fun all(context: Context, income: Boolean): List<String> {
        val defaults = if (income) incomeDefaults else expenseDefaults
        val key = if (income) "income_custom" else "expense_custom"
        return (defaults + context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getStringSet(key, emptySet()).orEmpty().sorted()).distinct()
    }

    fun add(context: Context, income: Boolean, value: String) {
        val name = value.trim().take(50)
        if (name.isBlank()) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = if (income) "income_custom" else "expense_custom"
        prefs.edit().putStringSet(key, prefs.getStringSet(key, emptySet()).orEmpty() + name).apply()
    }

    fun remove(context: Context, income: Boolean, value: String) {
        val defaults = if (income) incomeDefaults else expenseDefaults
        if (value in defaults) return
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val key = if (income) "income_custom" else "expense_custom"
        prefs.edit().putStringSet(key, prefs.getStringSet(key, emptySet()).orEmpty() - value).apply()
    }

    fun clear(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
}

object LocalSlipLinkStore {
    private const val PREFS = "rubjai_local_slip_links"
    fun put(context: Context, transactionId: String, uri: String) {
        if (transactionId.isNotBlank() && uri.isNotBlank()) context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(transactionId, uri).apply()
    }
    fun get(context: Context, transactionId: String): String = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(transactionId, "").orEmpty()
    fun remove(context: Context, transactionId: String) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(transactionId).apply()
    fun clear(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().clear().apply()
}

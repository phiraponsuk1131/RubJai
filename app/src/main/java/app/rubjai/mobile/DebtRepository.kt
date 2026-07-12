package app.rubjai.mobile

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import java.util.Date
import java.util.UUID
import kotlin.math.ceil
import kotlin.math.ln

data class Debt(
    val id: String = "",
    val name: String = "",
    val originalBalance: Double = 0.0,
    val remainingBalance: Double = 0.0,
    val annualInterestPercent: Double = 0.0,
    val latestPayment: Double = 0.0,
    val paymentsMade: Int = 0,
    val createdAt: Date = Date(),
) {
    fun estimatedMonths(): Int? {
        if (remainingBalance <= 0) return 0
        if (latestPayment <= 0) return null
        val rate = annualInterestPercent / 1200.0
        if (rate <= 0) return ceil(remainingBalance / latestPayment).toInt()
        if (latestPayment <= remainingBalance * rate) return null
        return ceil(-ln(1 - rate * remainingBalance / latestPayment) / ln(1 + rate)).toInt()
    }
    fun encouragement(): String = when {
        remainingBalance <= 0 -> "ยอดเยี่ยมมาก คุณปลดหนี้ก้อนนี้สำเร็จแล้ว 🎉"
        originalBalance > 0 && remainingBalance / originalBalance <= .25 -> "ใกล้ถึงเส้นชัยแล้ว อีกนิดเดียว!"
        originalBalance > 0 && remainingBalance / originalBalance <= .5 -> "ผ่านมาเกินครึ่งทางแล้ว คุณทำได้ดีมาก"
        paymentsMade > 0 -> "ทุกยอดที่จ่ายกำลังพาคุณเข้าใกล้อิสรภาพทางการเงิน"
        else -> "เริ่มวันนี้ก็ดีกว่ารอวันพร้อม ก้าวแรกสำคัญเสมอ"
    }
}

data class DebtPayment(
    val amount: Double = 0.0,
    val merchant: String = "",
    val occurredAt: String = "",
    val remark: String = "",
    val paidAt: Date? = null,
)

class DebtRepository {
    private val db = FirebaseFirestore.getInstance()
    private val auth = FirebaseAuth.getInstance()
    private fun debts() = db.collection("users").document(auth.currentUser!!.uid).collection("debts")
    fun observe(change: (List<Debt>) -> Unit) = debts().orderBy("createdAt", Query.Direction.DESCENDING).addSnapshotListener { snap, _ -> change(snap?.toObjects(Debt::class.java).orEmpty()) }
    fun observePayments(debtId: String, change: (List<DebtPayment>) -> Unit) = debts().document(debtId).collection("payments")
        .orderBy("paidAt", Query.Direction.DESCENDING)
        .addSnapshotListener { snap, _ -> change(snap?.toObjects(DebtPayment::class.java).orEmpty()) }
    fun add(name: String, balance: Double, interest: Double, done: (String?) -> Unit) {
        val id = UUID.randomUUID().toString(); val debt = Debt(id, name.trim(), balance, balance, interest)
        debts().document(id).set(debt).addOnSuccessListener { done(null) }.addOnFailureListener { done(it.localizedMessage) }
    }
    fun applySlip(debt: Debt, draft: DraftTransaction, done: (String?) -> Unit) {
        val amount = draft.amount.toDoubleOrNull() ?: return done("อ่านยอดจากสลิปไม่ได้")
        val fingerprint = draft.rawText.trim().lowercase().hashCode().toUInt().toString(16)
        val debtRef = debts().document(debt.id); val paymentRef = debtRef.collection("payments").document(fingerprint)
        db.runTransaction { tx ->
            if (tx.get(paymentRef).exists()) error("DUPLICATE")
            val latest = tx.get(debtRef).toObject(Debt::class.java) ?: error("NOT_FOUND")
            tx.set(paymentRef, mapOf("amount" to amount, "merchant" to draft.title, "occurredAt" to draft.occurredAt, "remark" to draft.remark, "reference" to fingerprint, "paidAt" to Date(), "rawText" to draft.rawText.take(3000)))
            tx.update(debtRef, mapOf("remainingBalance" to (latest.remainingBalance - amount).coerceAtLeast(0.0), "latestPayment" to amount, "paymentsMade" to latest.paymentsMade + 1))
        }.addOnSuccessListener { done(null) }.addOnFailureListener { done(if (it.message == "DUPLICATE") "สลิปนี้ถูกใช้ตัดยอดแล้ว" else it.localizedMessage) }
    }
}

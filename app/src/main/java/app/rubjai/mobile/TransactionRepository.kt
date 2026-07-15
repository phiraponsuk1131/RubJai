package app.rubjai.mobile

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp
import java.security.MessageDigest
import java.util.Date
import java.util.UUID

data class MoneyTransaction(
    val id: String = "",
    val amount: Double = 0.0,
    val title: String = "",
    val type: String = "EXPENSE",
    val source: String = "manual",
    val rawText: String = "",
    val category: String = "ใช้จ่ายทั่วไป",
    val remark: String = "",
    val occurredAt: String = "",
    val slipFingerprint: String = "",
    @ServerTimestamp val createdAt: Date? = null,
    val updatedAt: Date? = null,
)

class TransactionRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private fun transactions(uid: String) = db.collection("users").document(uid).collection("transactions")

    private fun withUser(missing: (() -> Unit)? = null, block: (String) -> Unit) {
        auth.currentUser?.uid?.let(block) ?: missing?.invoke()
    }

    fun observe(onChange: (List<MoneyTransaction>) -> Unit) = withUser(block = { uid ->
        transactions(uid).orderBy("createdAt", Query.Direction.DESCENDING).limit(500)
            .addSnapshotListener { snapshot, _ -> onChange(snapshot?.toObjects(MoneyTransaction::class.java).orEmpty()) }
    })

    fun add(draft: DraftTransaction, onDone: (String?) -> Unit) = withUser(
        missing = { onDone("กรุณาเข้าสู่ระบบใหม่") },
        block = { uid ->
            val fingerprint = draft.slipFingerprint.ifBlank { fingerprintFor(draft) }
            val slipBased = draft.type == TransactionType.EXPENSE && (draft.rawText.isNotBlank() || draft.slipFingerprint.isNotBlank())
            val id = if (slipBased) documentIdFor(draft) else UUID.randomUUID().toString()
            val ref = transactions(uid).document(id)
            val item = MoneyTransaction(
                id = id,
                amount = draft.amount.toDouble(),
                title = draft.title.trim().take(200),
                type = draft.type.name,
                source = draft.source,
                rawText = "", // OCR is used on-device only; never upload full slip text.
                category = draft.category.ifBlank { "ใช้จ่ายทั่วไป" }.take(50),
                remark = draft.remark.trim().take(500),
                occurredAt = draft.occurredAt.trim().take(50),
                slipFingerprint = if (slipBased) fingerprint else "",
                createdAt = Date(),
                updatedAt = Date(),
            )
            if (!slipBased) {
                ref.set(item).addOnSuccessListener { onDone(null) }
                    .addOnFailureListener { onDone(it.localizedMessage ?: "บันทึกไม่สำเร็จ") }
            } else {
                db.runTransaction { transaction ->
                    if (transaction.get(ref).exists()) error("DUPLICATE_SLIP")
                    transaction.set(ref, item)
                }.addOnSuccessListener { onDone(null) }
                    .addOnFailureListener {
                        onDone(if (it.message == "DUPLICATE_SLIP") "สลิปนี้ถูกบันทึกแล้ว" else it.localizedMessage ?: "บันทึกไม่สำเร็จ")
                    }
            }
        },
    )

    fun update(item: MoneyTransaction, draft: DraftTransaction, onDone: (String?) -> Unit) = withUser(
        missing = { onDone("กรุณาเข้าสู่ระบบใหม่") },
        block = { uid ->
            val values = mapOf(
                "amount" to (draft.amount.toDoubleOrNull() ?: item.amount),
                "title" to draft.title.trim().take(200),
                "type" to draft.type.name,
                "category" to draft.category.ifBlank { "ใช้จ่ายทั่วไป" }.take(50),
                "remark" to draft.remark.trim().take(500),
                "occurredAt" to draft.occurredAt.trim().take(50),
                "updatedAt" to Date(),
            )
            transactions(uid).document(item.id).update(values)
                .addOnSuccessListener { onDone(null) }
                .addOnFailureListener { onDone(it.localizedMessage ?: "แก้ไขไม่สำเร็จ") }
        },
    )

    fun delete(item: MoneyTransaction, onDone: (String?) -> Unit) = withUser(
        missing = { onDone("กรุณาเข้าสู่ระบบใหม่") },
        block = { uid ->
            transactions(uid).document(item.id).delete()
                .addOnSuccessListener { onDone(null) }
                .addOnFailureListener { onDone(it.localizedMessage ?: "ลบรายการไม่สำเร็จ") }
        },
    )

    companion object {
        fun documentIdFor(draft: DraftTransaction): String = "slip_${draft.slipFingerprint.ifBlank { fingerprintFor(draft) }}"
        fun fingerprintFor(draft: DraftTransaction): String {
            val canonical = draft.rawText.lowercase().replace(Regex("\\s+"), " ").trim()
                .ifBlank { "${draft.amount}|${draft.occurredAt}|${draft.category}" }
            return MessageDigest.getInstance("SHA-256").digest(canonical.toByteArray())
                .joinToString("") { "%02x".format(it) }.take(32)
        }
    }
}

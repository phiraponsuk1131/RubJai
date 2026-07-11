package app.rubjai.mobile

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.ServerTimestamp
import java.util.Date
import java.util.UUID

data class MoneyTransaction(
    val id: String = "",
    val amount: Double = 0.0,
    val title: String = "",
    val type: String = "EXPENSE",
    val source: String = "manual",
    val rawText: String = "",
    @ServerTimestamp val createdAt: Date? = null,
)

class TransactionRepository {
    private val auth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    private fun withUser(block: (String) -> Unit) {
        auth.currentUser?.uid?.let(block) ?: auth.signInAnonymously().addOnSuccessListener { block(it.user!!.uid) }
    }

    fun observe(onChange: (List<MoneyTransaction>) -> Unit) = withUser { uid ->
        db.collection("users").document(uid).collection("transactions")
            .orderBy("createdAt", Query.Direction.DESCENDING).limit(200)
            .addSnapshotListener { snapshot, _ -> onChange(snapshot?.toObjects(MoneyTransaction::class.java).orEmpty()) }
    }

    fun add(draft: DraftTransaction, onDone: (String?) -> Unit) = withUser { uid ->
        val id = UUID.randomUUID().toString()
        val item = MoneyTransaction(id, draft.amount.toDouble(), draft.title.trim(), draft.type.name, draft.source, draft.rawText.take(3000), Date())
        db.collection("users").document(uid).collection("transactions").document(id).set(item)
            .addOnSuccessListener { onDone(null) }.addOnFailureListener { onDone(it.localizedMessage ?: "บันทึกไม่สำเร็จ") }
    }
}

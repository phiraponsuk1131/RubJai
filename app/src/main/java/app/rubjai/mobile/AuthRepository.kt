package app.rubjai.mobile

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.QuerySnapshot

class AuthRepository {
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun createAccount(email: String, password: String, name: String, done: (String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email.trim(), password).addOnSuccessListener { result ->
            result.user?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build())
                ?.addOnCompleteListener { result.user?.sendEmailVerification()?.addOnSuccessListener { done(null) }?.addOnFailureListener { done(it.localizedMessage) } }
                ?: done("สร้างบัญชีไม่สำเร็จ")
        }.addOnFailureListener { done(it.localizedMessage ?: "สร้างบัญชีไม่สำเร็จ") }
    }

    fun signIn(email: String, password: String, done: (String?) -> Unit) =
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { done(null) }.addOnFailureListener { done(it.localizedMessage ?: "เข้าสู่ระบบไม่สำเร็จ") }

    fun resendVerification(done: (String?) -> Unit) = auth.currentUser?.sendEmailVerification()
        ?.addOnSuccessListener { done(null) }?.addOnFailureListener { done(it.localizedMessage) } ?: done("ไม่พบบัญชี")

    fun refreshUser(done: (Boolean, String?) -> Unit) = auth.currentUser?.reload()
        ?.addOnSuccessListener { done(auth.currentUser?.isEmailVerified == true, null) }
        ?.addOnFailureListener { done(false, it.localizedMessage) } ?: done(false, "ไม่พบบัญชี")

    fun updateProfile(name: String, phone: String, done: (String?) -> Unit) {
        val user = auth.currentUser ?: return done("ยังไม่ได้เข้าสู่ระบบ")
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).addOnSuccessListener {
            db.collection("users").document(user.uid).set(mapOf("displayName" to name.trim(), "phone" to phone.trim(), "email" to (user.email ?: "")), com.google.firebase.firestore.SetOptions.merge())
                .addOnSuccessListener { done(null) }.addOnFailureListener { done(it.localizedMessage) }
        }.addOnFailureListener { done(it.localizedMessage) }
    }

    fun loadProfile(done: (String, String) -> Unit) {
        val user = auth.currentUser ?: return done("", "")
        db.collection("users").document(user.uid).get().addOnSuccessListener { doc -> done(doc.getString("displayName") ?: user.displayName.orEmpty(), doc.getString("phone").orEmpty()) }.addOnFailureListener { done(user.displayName.orEmpty(), "") }
    }

    fun signOut() = auth.signOut()

    fun checkAdmin(done: (Boolean) -> Unit) {
        auth.currentUser?.getIdToken(true)?.addOnSuccessListener { done(it.claims["admin"] == true) }
            ?.addOnFailureListener { done(false) } ?: done(false)
    }

    fun deleteAccountAndData(done: (String?) -> Unit) {
        val user = auth.currentUser ?: return done("ไม่พบบัญชี")
        val root = db.collection("users").document(user.uid)
        val transactionTask = root.collection("transactions").get()
        val debtTask = root.collection("debts").get()
        Tasks.whenAll(transactionTask, debtTask).addOnSuccessListener {
            val paymentTasks: List<Task<QuerySnapshot>> = debtTask.result.documents.map { it.reference.collection("payments").get() }
            Tasks.whenAll(paymentTasks).addOnSuccessListener {
                val batch = db.batch()
                transactionTask.result.documents.forEach { batch.delete(it.reference) }
                paymentTasks.forEach { task -> task.result.documents.forEach { batch.delete(it.reference) } }
                debtTask.result.documents.forEach { batch.delete(it.reference) }
                batch.delete(root)
                batch.commit().addOnSuccessListener {
                    user.delete().addOnSuccessListener { done(null) }.addOnFailureListener { done("ลบข้อมูลแล้ว แต่ลบบัญชีไม่สำเร็จ กรุณาเข้าสู่ระบบใหม่แล้วลองอีกครั้ง") }
                }.addOnFailureListener { done(it.localizedMessage ?: "ลบข้อมูลไม่สำเร็จ") }
            }.addOnFailureListener { done(it.localizedMessage ?: "อ่านข้อมูลชำระหนี้ไม่สำเร็จ") }
        }.addOnFailureListener { done(it.localizedMessage ?: "อ่านข้อมูลบัญชีไม่สำเร็จ") }
    }
}

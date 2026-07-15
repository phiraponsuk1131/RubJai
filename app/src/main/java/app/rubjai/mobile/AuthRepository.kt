package app.rubjai.mobile

import android.app.Activity
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.PhoneAuthCredential
import com.google.firebase.auth.PhoneAuthOptions
import com.google.firebase.auth.PhoneAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.firestore.QuerySnapshot
import com.google.firebase.firestore.SetOptions
import java.util.concurrent.TimeUnit

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

    fun requestPhoneOtp(
        activity: Activity,
        phone: String,
        codeSent: (String, PhoneAuthProvider.ForceResendingToken) -> Unit,
        verified: (PhoneAuthCredential) -> Unit,
        failed: (String) -> Unit,
    ) {
        val normalized = normalizeThaiPhone(phone)
        if (!Regex("^\\+66[0-9]{9}$").matches(normalized)) return failed("กรุณากรอกเบอร์มือถือไทย 10 หลัก")
        val callbacks = object : PhoneAuthProvider.OnVerificationStateChangedCallbacks() {
            override fun onVerificationCompleted(credential: PhoneAuthCredential) = verified(credential)
            override fun onVerificationFailed(exception: com.google.firebase.FirebaseException) =
                failed(exception.localizedMessage ?: "ส่ง OTP ไม่สำเร็จ")
            override fun onCodeSent(id: String, token: PhoneAuthProvider.ForceResendingToken) = codeSent(id, token)
        }
        PhoneAuthProvider.verifyPhoneNumber(
            PhoneAuthOptions.newBuilder(auth)
                .setPhoneNumber(normalized)
                .setTimeout(60L, TimeUnit.SECONDS)
                .setActivity(activity)
                .setCallbacks(callbacks)
                .build(),
        )
    }

    fun confirmPhoneOtp(verificationId: String, code: String, displayName: String, done: (String?) -> Unit) {
        if (code.length != 6) return done("กรุณากรอก OTP 6 หลัก")
        signInWithPhoneCredential(PhoneAuthProvider.getCredential(verificationId, code), displayName, done)
    }

    fun signInWithPhoneCredential(credential: PhoneAuthCredential, displayName: String, done: (String?) -> Unit) {
        auth.signInWithCredential(credential).addOnSuccessListener { result ->
            val user = result.user ?: return@addOnSuccessListener done("เข้าสู่ระบบไม่สำเร็จ")
            val name = displayName.trim().take(100)
            val profileTask = if (name.isNotBlank()) user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name).build()) else null
            val save = {
                if (name.isBlank()) done(null) else db.collection("users").document(user.uid)
                    .set(mapOf("displayName" to name, "phone" to user.phoneNumber.orEmpty().take(30)), SetOptions.merge())
                    .addOnSuccessListener { done(null) }.addOnFailureListener { done(it.localizedMessage) }
            }
            profileTask?.addOnSuccessListener { save() }?.addOnFailureListener { done(it.localizedMessage) } ?: save()
        }.addOnFailureListener { done(it.localizedMessage ?: "OTP ไม่ถูกต้องหรือหมดอายุ") }
    }

    private fun normalizeThaiPhone(value: String): String {
        val digits = value.filter(Char::isDigit)
        return when {
            digits.startsWith("66") && digits.length == 11 -> "+$digits"
            digits.startsWith("0") && digits.length == 10 -> "+66${digits.drop(1)}"
            else -> value.trim()
        }
    }

    fun resendVerification(done: (String?) -> Unit) = auth.currentUser?.sendEmailVerification()
        ?.addOnSuccessListener { done(null) }?.addOnFailureListener { done(it.localizedMessage) } ?: done("ไม่พบบัญชี")

    fun refreshUser(done: (Boolean, String?) -> Unit) = auth.currentUser?.reload()
        ?.addOnSuccessListener { done(auth.currentUser?.isEmailVerified == true, null) }
        ?.addOnFailureListener { done(false, it.localizedMessage) } ?: done(false, "ไม่พบบัญชี")

    fun updateProfile(name: String, phone: String, done: (String?) -> Unit) {
        val user = auth.currentUser ?: return done("ยังไม่ได้เข้าสู่ระบบ")
        user.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build()).addOnSuccessListener {
            db.collection("users").document(user.uid).set(mapOf("displayName" to name.trim().take(100), "phone" to phone.trim().take(30)), SetOptions.merge())
                .addOnSuccessListener { done(null) }.addOnFailureListener { done(it.localizedMessage) }
        }.addOnFailureListener { done(it.localizedMessage) }
    }

    fun loadProfile(done: (String, String) -> Unit) {
        val user = auth.currentUser ?: return done("", "")
        db.collection("users").document(user.uid).get().addOnSuccessListener { doc -> done(doc.getString("displayName") ?: user.displayName.orEmpty(), doc.getString("phone").orEmpty()) }.addOnFailureListener { done(user.displayName.orEmpty(), "") }
    }

    fun signOut() = auth.signOut()

    fun ensureCleanStartV2(done: (String?) -> Unit) {
        val user = auth.currentUser ?: return done("ไม่พบบัญชี")
        val root = db.collection("users").document(user.uid)
        root.get().addOnSuccessListener { snapshot ->
            if ((snapshot.getLong("dataVersion") ?: 0L) >= 2L) return@addOnSuccessListener done(null)
            clearUsageData { error ->
                if (error != null) return@clearUsageData done(error)
                root.set(mapOf("dataVersion" to 2L), SetOptions.merge())
                    .addOnSuccessListener { done(null) }
                    .addOnFailureListener { done(it.localizedMessage ?: "เตรียมข้อมูลเวอร์ชันใหม่ไม่สำเร็จ") }
            }
        }.addOnFailureListener { done(it.localizedMessage ?: "ตรวจเวอร์ชันข้อมูลไม่สำเร็จ") }
    }

    fun clearUsageData(done: (String?) -> Unit) {
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
                batch.commit().addOnSuccessListener { done(null) }.addOnFailureListener { done(it.localizedMessage ?: "ลบข้อมูลไม่สำเร็จ") }
            }.addOnFailureListener { done(it.localizedMessage ?: "อ่านข้อมูลชำระหนี้ไม่สำเร็จ") }
        }.addOnFailureListener { done(it.localizedMessage ?: "อ่านข้อมูลบัญชีไม่สำเร็จ") }
    }

    fun deleteOwnAccount(done: (String?) -> Unit) {
        val user = auth.currentUser ?: return done("ไม่พบบัญชี")
        clearUsageData { clearError ->
            if (clearError != null) return@clearUsageData done(clearError)
            user.delete().addOnSuccessListener { done(null) }.addOnFailureListener {
                done(if (it.message?.contains("recent", true) == true) "เพื่อความปลอดภัย กรุณาออกจากระบบ เข้าใหม่ด้วย OTP แล้วลบบัญชีอีกครั้ง" else it.localizedMessage ?: "ลบบัญชีไม่สำเร็จ")
            }
        }
    }
}

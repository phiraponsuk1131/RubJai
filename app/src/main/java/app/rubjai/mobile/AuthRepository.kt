package app.rubjai.mobile

import com.google.android.gms.auth.api.signin.GoogleSignInAccount
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore

class AuthRepository {
    val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val db = FirebaseFirestore.getInstance()

    fun createAccount(email: String, password: String, name: String, done: (String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email.trim(), password).addOnSuccessListener { result ->
            result.user?.updateProfile(UserProfileChangeRequest.Builder().setDisplayName(name.trim()).build())
                ?.addOnCompleteListener { saveProfile(name, done) } ?: done("สร้างบัญชีไม่สำเร็จ")
        }.addOnFailureListener { done(it.localizedMessage ?: "สร้างบัญชีไม่สำเร็จ") }
    }

    fun signIn(email: String, password: String, done: (String?) -> Unit) =
        auth.signInWithEmailAndPassword(email.trim(), password)
            .addOnSuccessListener { done(null) }.addOnFailureListener { done(it.localizedMessage ?: "เข้าสู่ระบบไม่สำเร็จ") }

    fun signInAnonymously(done: (String?) -> Unit) = auth.signInAnonymously()
        .addOnSuccessListener { done(null) }.addOnFailureListener { done(it.localizedMessage ?: "เข้าใช้งานไม่ได้") }

    fun signInGoogle(account: GoogleSignInAccount, done: (String?) -> Unit) {
        val token = account.idToken ?: return done("ไม่ได้รับ Google ID token กรุณาดาวน์โหลด google-services.json ใหม่หลังเปิด Google Sign-in")
        auth.signInWithCredential(GoogleAuthProvider.getCredential(token, null))
            .addOnSuccessListener { saveProfile(it.user?.displayName.orEmpty(), done) }
            .addOnFailureListener { done(it.localizedMessage ?: "เข้าสู่ระบบด้วย Google ไม่สำเร็จ") }
    }

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
    private fun saveProfile(name: String, done: (String?) -> Unit) {
        val user: FirebaseUser = auth.currentUser ?: return done("ไม่พบบัญชีผู้ใช้")
        db.collection("users").document(user.uid).set(mapOf("displayName" to name, "email" to (user.email ?: "")), com.google.firebase.firestore.SetOptions.merge())
            .addOnSuccessListener { done(null) }.addOnFailureListener { done(null) }
    }
}

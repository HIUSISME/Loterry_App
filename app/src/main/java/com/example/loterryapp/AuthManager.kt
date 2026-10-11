package com.example.loterryapp

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.firestore
import com.google.firebase.ktx.Firebase

class AuthManager {
    private val auth: FirebaseAuth = Firebase.auth
    private val db: FirebaseFirestore = Firebase.firestore
    
    var userRole: String? = null
        private set

    fun isLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    fun logout() {
        auth.signOut()
        userRole = null
    }

    fun getCurrentUserEmail(): String? {
        return auth.currentUser?.email
    }

    fun getCurrentUserUid(): String? {
        return auth.currentUser?.uid
    }

    /**
     * Lấy role từ Firestore (users/{uid}.role). Đây là nguồn duy nhất xác định quyền admin;
     * role admin chỉ được cấp thủ công trên Firebase Console và được bảo vệ bởi firestore.rules.
     */
    fun fetchUserRole(onResult: (String?) -> Unit) {
        val email = getCurrentUserEmail()
        val uid = getCurrentUserUid()

        if (uid == null) {
            onResult(null)
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                if (document.exists()) {
                    userRole = document.getString("role") ?: "customer" // Mặc định là customer nếu thiếu field role
                    onResult(userRole)
                } else {
                    // Lần đầu đăng nhập (VD: qua Google) chưa có hồ sơ trong DB: tạo mới với role customer
                    saveUserRole(uid, email ?: "") { success ->
                        onResult(if (success) userRole else null)
                    }
                }
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    /** Tạo hồ sơ cho user mới. App chỉ được tạo role customer (firestore.rules cũng bắt buộc điều này) */
    fun saveUserRole(uid: String, email: String, onComplete: (Boolean) -> Unit) {
        val finalRole = "customer"

        val user = hashMapOf(
            "email" to email,
            "role" to finalRole
        )
        db.collection("users").document(uid).set(user)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    userRole = finalRole
                    onComplete(true)
                } else {
                    onComplete(false)
                }
            }
    }

    companion object {
        @Volatile
        private var INSTANCE: AuthManager? = null

        fun getInstance(): AuthManager {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: AuthManager().also { INSTANCE = it }
            }
        }
    }
}

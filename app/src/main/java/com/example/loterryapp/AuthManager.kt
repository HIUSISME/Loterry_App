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

    // Danh sách email admin mặc định (không thể tạo qua Register)
    private val ADMIN_EMAILS = listOf("admin@lottery.com", "quanly@lottery.com")

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

    fun fetchUserRole(onResult: (String?) -> Unit) {
        val email = getCurrentUserEmail()
        val uid = getCurrentUserUid()

        // Ưu tiên kiểm tra email admin mặc định
        if (email != null && ADMIN_EMAILS.contains(email)) {
            userRole = "admin"
            onResult("admin")
            return
        }

        if (uid == null) {
            onResult(null)
            return
        }

        db.collection("users").document(uid).get()
            .addOnSuccessListener { document ->
                val role = document.getString("role")
                userRole = role ?: "customer" // Mặc định là customer nếu không có trong DB
                onResult(userRole)
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    fun saveUserRole(uid: String, email: String, role: String, onComplete: (Boolean) -> Unit) {
        // Bảo vệ: Nếu cố tình lưu role admin từ client, chúng ta sẽ từ chối hoặc ép về customer
        val finalRole = if (ADMIN_EMAILS.contains(email)) "admin" else "customer"
        
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

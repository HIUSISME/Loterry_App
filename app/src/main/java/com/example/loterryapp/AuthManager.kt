package com.example.loterryapp

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.UserProfileChangeRequest
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class AuthManager {
    private val auth: FirebaseAuth = Firebase.auth

    fun isLoggedIn(): Boolean {
        return auth.currentUser != null
    }

    fun logout() {
        auth.signOut()
    }

    fun getCurrentUserEmail(): String? {
        return auth.currentUser?.email
    }

    fun getCurrentUserUid(): String? {
        return auth.currentUser?.uid
    }

    /** Tên hiển thị của tài khoản Firebase (VD: tên tài khoản Google, hoặc họ tên người dùng tự điền) */
    fun getDisplayName(): String? {
        return auth.currentUser?.displayName?.takeIf { it.isNotBlank() }
    }

    /** Cập nhật tên hiển thị lên tài khoản Firebase, để giữ được họ tên khi đăng nhập trên máy khác */
    fun updateDisplayName(name: String, onComplete: (Boolean) -> Unit) {
        val user = auth.currentUser ?: return onComplete(false)
        val request = UserProfileChangeRequest.Builder().setDisplayName(name).build()
        user.updateProfile(request).addOnCompleteListener { onComplete(it.isSuccessful) }
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

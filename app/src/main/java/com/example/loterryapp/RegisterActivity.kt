package com.example.loterryapp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.loterryapp.databinding.ActivityRegisterBinding
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth
    private val authManager = AuthManager.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth

        setupPasswordValidation()

        binding.btnRegister.setOnClickListener {
            val email = binding.etEmail.text.toString().trim()
            val password = binding.etPassword.text.toString().trim()
            val confirmPassword = binding.etConfirmPassword.text.toString().trim()

            if (email.isEmpty() || password.isEmpty() || confirmPassword.isEmpty()) {
                Toast.makeText(this, "Hãy điền thông tin vào các ô", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password != confirmPassword) {
                Toast.makeText(this, "Mật khẩu không đúng", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Kiểm tra bảo mật mật khẩu
            val passwordError = validatePassword(password)
            if (passwordError != null) {
                Toast.makeText(this, passwordError, Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val user = task.result?.user
                        if (user != null) {
                            // Chặn việc đăng ký bằng email admin nếu muốn (Tùy chọn)
                            // Ở đây chúng ta vẫn cho đăng ký nhưng ép role là "customer"
                            authManager.saveUserRole(user.uid, email, "customer") { success ->
                                if (success) {
                                    Toast.makeText(this, "Đăng ký thành công", Toast.LENGTH_SHORT).show()
                                    startActivity(Intent(this, MainActivity::class.java))
                                    finish()
                                } else {
                                    Toast.makeText(this, "Không lưu được thông tin người dùng", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    } else {
                        Toast.makeText(this, "Đăng ký thất bại: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }

        binding.btnGoToLogin.setOnClickListener {
            finish()
        }
    }

    private fun setupPasswordValidation() {
        binding.etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val password = s.toString()
                
                binding.cbLength.isChecked = password.length >= 8
                binding.cbUppercase.isChecked = password.isNotEmpty() && password[0].isUpperCase()
                binding.cbDigit.isChecked = password.any { it.isDigit() }
                
                val specialChars = "!@#$%^&*()_+-=[]{}|;':\",./<>?"
                binding.cbSpecial.isChecked = password.any { it in specialChars }
            }
        })
    }

    private fun validatePassword(password: String): String? {
        if (password.length < 8) {
            return "Mật khẩu phải có ít nhất 8 ký tự"
        }
        if (!password[0].isUpperCase()) {
            return "Ký tự đầu tiên của mật khẩu phải là chữ viết hoa"
        }
        if (!password.any { it.isDigit() }) {
            return "Mật khẩu phải chứa ít nhất một chữ số"
        }
        val specialChars = "!@#$%^&*()_+-=[]{}|;':\",./<>?"
        if (!password.any { it in specialChars }) {
            return "Mật khẩu phải chứa ít nhất một ký tự đặc biệt"
        }
        return null
    }
}

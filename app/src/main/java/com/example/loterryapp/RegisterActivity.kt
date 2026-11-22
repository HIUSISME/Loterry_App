package com.example.loterryapp

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Patterns
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.example.loterryapp.databinding.ActivityRegisterBinding
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class RegisterActivity : AppCompatActivity() {
    private lateinit var binding: ActivityRegisterBinding
    private lateinit var auth: FirebaseAuth

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityRegisterBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth

        setupPasswordValidation()

        // Xoá thông báo lỗi của ô khi người dùng sửa lại
        binding.etEmail.doAfterTextChanged { binding.tilEmail.error = null }
        binding.etConfirmPassword.doAfterTextChanged { binding.tilConfirmPassword.error = null }

        // Bấm "Xong" trên bàn phím ở ô cuối = bấm Tạo tài khoản
        binding.etConfirmPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.btnRegister.performClick()
                true
            } else {
                false
            }
        }

        binding.btnRegister.setOnClickListener { register() }

        binding.btnGoToLogin.setOnClickListener {
            finish()
        }
    }

    private fun register() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()
        val confirmPassword = binding.etConfirmPassword.text.toString().trim()

        var valid = true
        when {
            email.isEmpty() -> {
                binding.tilEmail.error = "Vui lòng nhập email"
                valid = false
            }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                binding.tilEmail.error = "Email không hợp lệ"
                valid = false
            }
        }

        // Kiểm tra bảo mật mật khẩu
        val passwordError = if (password.isEmpty()) "Vui lòng nhập mật khẩu" else validatePassword(password)
        if (passwordError != null) {
            binding.tilPassword.error = passwordError
            valid = false
        }

        when {
            confirmPassword.isEmpty() -> {
                binding.tilConfirmPassword.error = "Vui lòng nhập lại mật khẩu"
                valid = false
            }
            password != confirmPassword -> {
                binding.tilConfirmPassword.error = "Mật khẩu nhập lại không khớp"
                valid = false
            }
        }
        if (!valid) return

        setLoading(true)
        auth.createUserWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    Toast.makeText(this, "Đăng ký thành công", Toast.LENGTH_SHORT).show()
                    // Xoá LoginActivity khỏi back stack để nút Back không quay lại màn đăng nhập
                    val intent = Intent(this, MainActivity::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                } else {
                    setLoading(false)
                    val e = task.exception
                    val message = authErrorMessage(e)
                    when (e) {
                        is FirebaseAuthUserCollisionException -> binding.tilEmail.error = message
                        is FirebaseAuthWeakPasswordException -> binding.tilPassword.error = message
                        else -> Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
                    }
                }
            }
    }

    /** Khi đang tạo tài khoản: khoá các nút/ô nhập, ẩn chữ nút chính và hiện vòng xoay. */
    private fun setLoading(loading: Boolean) {
        binding.btnRegister.isEnabled = !loading
        binding.btnGoToLogin.isEnabled = !loading
        binding.etEmail.isEnabled = !loading
        binding.etPassword.isEnabled = !loading
        binding.etConfirmPassword.isEnabled = !loading
        binding.btnRegister.text = if (loading) "" else "Tạo tài khoản"
        binding.progressRegister.visibility = if (loading) View.VISIBLE else View.GONE
    }

    /** Đổi lỗi Firebase thành câu tiếng Việt dễ hiểu. */
    private fun authErrorMessage(e: Exception?): String = when (e) {
        is FirebaseNetworkException -> "Không có kết nối Internet"
        is FirebaseTooManyRequestsException -> "Bạn đã thử quá nhiều lần, vui lòng thử lại sau"
        is FirebaseAuthUserCollisionException -> "Email này đã được đăng ký"
        // WeakPassword là lớp con của InvalidCredentials nên phải kiểm tra trước
        is FirebaseAuthWeakPasswordException -> "Mật khẩu cần ít nhất 6 ký tự"
        is FirebaseAuthInvalidCredentialsException -> "Email không hợp lệ"
        else -> "Đăng ký thất bại, vui lòng thử lại"
    }

    private fun setupPasswordValidation() {
        binding.etPassword.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                val password = s.toString()
                binding.tilPassword.error = null

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

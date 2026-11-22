package com.example.loterryapp

import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.widget.doAfterTextChanged
import com.example.loterryapp.databinding.ActivityLoginBinding
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.auth.api.signin.GoogleSignInStatusCodes
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.FirebaseTooManyRequestsException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.GoogleAuthProvider
import com.google.firebase.auth.ktx.auth
import com.google.firebase.ktx.Firebase

class LoginActivity : AppCompatActivity() {
    private lateinit var binding: ActivityLoginBinding
    private lateinit var auth: FirebaseAuth
    private lateinit var googleSignInClient: GoogleSignInClient

    private val googleSignInLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
            try {
                val account = task.getResult(ApiException::class.java)!!
                firebaseAuthWithGoogle(account.idToken!!)
            } catch (e: ApiException) {
                when (e.statusCode) {
                    // Người dùng tự đóng hộp chọn tài khoản: không cần báo lỗi
                    GoogleSignInStatusCodes.SIGN_IN_CANCELLED -> Unit
                    CommonStatusCodes.NETWORK_ERROR -> showToast("Không có kết nối Internet")
                    else -> showToast("Đăng nhập Google thất bại, vui lòng thử lại")
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityLoginBinding.inflate(layoutInflater)
        setContentView(binding.root)

        auth = Firebase.auth

        // Configure Google Sign In
        val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken(getString(R.string.default_web_client_id))
            .requestEmail()
            .build()

        googleSignInClient = GoogleSignIn.getClient(this, gso)

        // Xoá thông báo lỗi của ô khi người dùng sửa lại
        binding.etEmail.doAfterTextChanged { binding.tilEmail.error = null }
        binding.etPassword.doAfterTextChanged { binding.tilPassword.error = null }

        // Bấm "Xong" trên bàn phím ở ô mật khẩu = bấm Đăng nhập
        binding.etPassword.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                binding.btnLogin.performClick()
                true
            } else {
                false
            }
        }

        binding.btnLogin.setOnClickListener { signInWithEmail() }

        binding.btnGoogleSignIn.setOnClickListener {
            val signInIntent = googleSignInClient.signInIntent
            googleSignInLauncher.launch(signInIntent)
        }

        binding.btnGoToRegister.setOnClickListener {
            startActivity(Intent(this, RegisterActivity::class.java))
        }
    }

    private fun signInWithEmail() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString().trim()

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
        when {
            password.isEmpty() -> {
                binding.tilPassword.error = "Vui lòng nhập mật khẩu"
                valid = false
            }
            password.length < 6 -> {
                binding.tilPassword.error = "Mật khẩu cần ít nhất 6 ký tự"
                valid = false
            }
        }
        if (!valid) return

        setLoading(true)
        auth.signInWithEmailAndPassword(email, password)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    navigateToMain()
                } else {
                    setLoading(false)
                    showToast(authErrorMessage(task.exception))
                }
            }
    }

    private fun firebaseAuthWithGoogle(idToken: String) {
        setLoading(true)
        val credential = GoogleAuthProvider.getCredential(idToken, null)
        auth.signInWithCredential(credential)
            .addOnCompleteListener(this) { task ->
                if (task.isSuccessful) {
                    navigateToMain()
                } else {
                    setLoading(false)
                    showToast(
                        if (task.exception is FirebaseNetworkException) "Không có kết nối Internet"
                        else "Đăng nhập Google thất bại, vui lòng thử lại"
                    )
                }
            }
    }

    /** Khi đang đăng nhập: khoá các nút, ẩn chữ nút chính và hiện vòng xoay. */
    private fun setLoading(loading: Boolean) {
        binding.btnLogin.isEnabled = !loading
        binding.btnGoogleSignIn.isEnabled = !loading
        binding.btnGoToRegister.isEnabled = !loading
        binding.etEmail.isEnabled = !loading
        binding.etPassword.isEnabled = !loading
        binding.btnLogin.text = if (loading) "" else "Đăng nhập"
        binding.progressLogin.visibility = if (loading) View.VISIBLE else View.GONE
    }

    /** Đổi lỗi Firebase thành câu tiếng Việt dễ hiểu. */
    private fun authErrorMessage(e: Exception?): String = when (e) {
        is FirebaseNetworkException -> "Không có kết nối Internet"
        is FirebaseTooManyRequestsException -> "Bạn đã thử quá nhiều lần, vui lòng thử lại sau"
        is FirebaseAuthInvalidUserException,
        is FirebaseAuthInvalidCredentialsException -> "Email hoặc mật khẩu không đúng"
        else -> "Đăng nhập thất bại, vui lòng thử lại"
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun navigateToMain() {
        val intent = Intent(this, MainActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

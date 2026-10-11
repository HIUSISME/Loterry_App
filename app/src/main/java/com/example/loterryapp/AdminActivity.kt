package com.example.loterryapp

import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.loterryapp.databinding.ActivityAdminBinding

class AdminActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAdminBinding
    private val authManager = AuthManager.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAdminBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBack.setOnClickListener {
            finish()
        }

        // Kiểm tra lại quyền từ Firestore mỗi lần mở, không dựa vào role đã lưu trong bộ nhớ.
        // Bảo vệ dữ liệu thật sự nằm ở firestore.rules; bước này chỉ để chặn giao diện.
        authManager.fetchUserRole { role ->
            if (role != "admin") {
                Toast.makeText(this, "Bạn không có quyền truy cập trang quản trị", Toast.LENGTH_SHORT).show()
                finish()
            }
        }
    }
}

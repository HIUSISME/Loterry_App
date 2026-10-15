package com.example.loterryapp

import android.Manifest
import android.content.Intent
import android.os.Bundle
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import com.example.loterryapp.databinding.ActivityMainBinding
import com.example.loterryapp.notification.LotteryNotificationHelper

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val authManager = AuthManager.getInstance()

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (!granted) {
                // Người dùng từ chối: tắt hẳn để không chạy lịch kiểm tra vô ích; có thể bật lại ở màn Hồ sơ
                LotteryNotificationHelper.setNotificationEnabled(this, false)
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Check if user is logged in
        if (!authManager.isLoggedIn()) {
            startLoginActivity()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupDrawer()

        // Đặt lịch kiểm tra kết quả ngay khi mở app, không phụ thuộc việc người dùng có vào màn Hồ sơ hay không
        LotteryNotificationHelper.scheduleIfEnabled(this)

        // Thông báo bật sẵn theo mặc định, nên xin quyền một lần duy nhất (Android 13+) để thông báo hiển thị được
        if (LotteryNotificationHelper.shouldAskPermissionOnStartup(this)) {
            LotteryNotificationHelper.markStartupPermissionAsked(this)
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        // Handle back press to close drawer
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
                    binding.drawerLayout.closeDrawer(GravityCompat.START)
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed()
                    isEnabled = true
                }
            }
        })
    }

    private fun setupDrawer() {
        binding.ivMenu.setOnClickListener {
            binding.drawerLayout.openDrawer(GravityCompat.START)
        }

        binding.ivAccount.setOnClickListener {
            startActivity(Intent(this, ProfileActivity::class.java))
        }

        // Xổ số kiến thiết
        binding.btnMienNam.setOnClickListener { openVietnamLottery("MN", "Xổ Số Miền Nam") }
        binding.btnMienTrung.setOnClickListener { openVietnamLottery("MT", "Xổ Số Miền Trung") }
        binding.btnMienBac.setOnClickListener { openVietnamLottery("MB", "Xổ Số Miền Bắc") }
        binding.btnThongKe.setOnClickListener { startActivity(Intent(this, ThongKeActivity::class.java)) }

        // Xổ số điện toán
        binding.btnMega.setOnClickListener { openVietlott("MEGA645", "Mega 6/45") }
        binding.btnPower.setOnClickListener { openVietlott("POWER655", "Power 6/55") }
        binding.btnMax3DPro.setOnClickListener { openVietlott("MAX3DPRO", "Max3D PRO") }
        binding.btnKeno.setOnClickListener { openVietlott("KENO", "KENO") }
        binding.btnXsdt.setOnClickListener { startActivity(Intent(this, DienToanActivity::class.java)) }
        binding.btnMax3D.setOnClickListener { openVietlott("MAX3D", "Max3D") }

        binding.navView.setNavigationItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_home -> {}
                R.id.nav_ai_assistant -> startActivity(Intent(this, AiAssistantActivity::class.java))
                R.id.nav_mien_bac -> openVietnamLottery("MB", "Xổ Số Miền Bắc")
                R.id.nav_mien_trung -> openVietnamLottery("MT", "Xổ Số Miền Trung")
                R.id.nav_mien_nam -> openVietnamLottery("MN", "Xổ Số Miền Nam")
                R.id.nav_thong_ke -> startActivity(Intent(this, ThongKeActivity::class.java))
                R.id.nav_mega -> openVietlott("MEGA645", "Xổ Số Mega 6/45")
                R.id.nav_power -> openVietlott("POWER655", "Xổ Số Power 6/55")
                R.id.nav_max3d_pro -> openVietlott("MAX3DPRO", "Max3D PRO")
                R.id.nav_keno -> openVietlott("KENO", "Xổ Số KENO")
                R.id.nav_xsdt -> startActivity(Intent(this, DienToanActivity::class.java))
                R.id.nav_max3d -> openVietlott("MAX3D", "Max3D")
            }
            binding.drawerLayout.closeDrawer(GravityCompat.START)
            true
        }
    }

    private fun openVietnamLottery(region: String, title: String) {
        val intent = Intent(this, VietnamLotteryActivity::class.java)
        intent.putExtra("REGION", region)
        intent.putExtra("TITLE", title)
        startActivity(intent)
    }

    private fun openVietlott(type: String, title: String) {
        val intent = Intent(this, VietlottActivity::class.java)
        intent.putExtra("TYPE", type)
        intent.putExtra("TITLE", title)
        startActivity(intent)
    }

    private fun setupUI() {
        binding.btnLogout.setOnClickListener {
            authManager.logout()
            LotteryNotificationHelper.onLogout(this)
            startLoginActivity()
        }
    }

    private fun startLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

package com.example.loterryapp

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.loterryapp.databinding.ActivityProfileBinding
import com.example.loterryapp.notification.LotteryNotificationHelper

class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding
    private val authManager = AuthManager.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupNotificationFeature()
    }

    private fun setupUI() {
        binding.tvEmail.text = "Email: ${authManager.getCurrentUserEmail()}"

        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.btnLogout.setOnClickListener {
            authManager.logout()
            LotteryNotificationHelper.onLogout(this)
            val intent = Intent(this, LoginActivity::class.java)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            startActivity(intent)
            finish()
        }
    }

    private fun setupNotificationFeature() {
        // Không tự xin quyền khi mở màn hình; chỉ xin khi người dùng bật công tắc (xem onNotificationSwitchChanged)
        binding.btnTestNotification.setOnClickListener {
            val sent = LotteryNotificationHelper.sendNotification(
                this,
                "🎉 Kết Quả Xổ Số Mới Nhất!",
                "Đã có kết quả Xổ Số Miền Bắc & Vietlott hôm nay. Bấm vào đây để kiểm tra ngay số trúng thưởng của bạn!"
            )
            if (sent) {
                Toast.makeText(this, "Đã gửi thông báo thử nghiệm tới thiết bị!", Toast.LENGTH_SHORT).show()
            } else {
                showOpenSettingsDialog()
            }
        }

        // Nút debug: chạy ngay Worker kiểm tra kết quả thật (gọi API + gửi thông báo), không cần chờ đến 19h
        binding.btnRunCheckNow.visibility = if (BuildConfig.DEBUG) View.VISIBLE else View.GONE
        binding.btnRunCheckNow.setOnClickListener {
            if (!LotteryNotificationHelper.canPostNotifications(this)) {
                showOpenSettingsDialog()
                return@setOnClickListener
            }
            LotteryNotificationHelper.runTestCheckNow(this)
            Toast.makeText(this, "Đang kiểm tra kết quả, thông báo sẽ hiện sau vài giây", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onResume() {
        super.onResume()
        // Cập nhật lại mỗi khi quay về màn hình, vì người dùng có thể vừa đổi quyền trong Cài đặt
        refreshNotificationSwitch()
    }

    /** Công tắc chỉ hiện "bật" khi người dùng đã bật VÀ thông báo thực sự hiển thị được */
    private fun refreshNotificationSwitch() {
        setSwitchSilently(
            LotteryNotificationHelper.isNotificationEnabled(this) &&
                LotteryNotificationHelper.canPostNotifications(this)
        )
    }

    // Đổi trạng thái công tắc bằng code mà không kích hoạt listener (tránh hiện Toast / lưu cài đặt nhầm)
    private fun setSwitchSilently(checked: Boolean) {
        binding.switchNotification.setOnCheckedChangeListener(null)
        binding.switchNotification.isChecked = checked
        binding.switchNotification.setOnCheckedChangeListener { _, isChecked -> onNotificationSwitchChanged(isChecked) }
    }

    private fun onNotificationSwitchChanged(isChecked: Boolean) {
        if (!isChecked) {
            LotteryNotificationHelper.setNotificationEnabled(this, false)
            Toast.makeText(this, "Đã tắt thông báo kết quả", Toast.LENGTH_SHORT).show()
            return
        }

        when {
            LotteryNotificationHelper.canPostNotifications(this) -> enableNotifications()
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED -> {
                // Chưa có quyền: xin quyền, kết quả xử lý trong notificationPermissionLauncher
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
            else -> {
                // Đã có quyền nhưng người dùng tắt thông báo của app trong Cài đặt hệ thống
                setSwitchSilently(false)
                showOpenSettingsDialog()
            }
        }
    }

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted && LotteryNotificationHelper.canPostNotifications(this)) {
                enableNotifications()
            } else {
                setSwitchSilently(false)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
                    !shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                    // Đã bị từ chối vĩnh viễn: Android không hiện hộp thoại xin quyền nữa, phải vào Cài đặt
                    showOpenSettingsDialog()
                } else {
                    Toast.makeText(this, "Cần cấp quyền thông báo để nhận kết quả xổ số", Toast.LENGTH_SHORT).show()
                }
            }
        }

    private fun enableNotifications() {
        LotteryNotificationHelper.setNotificationEnabled(this, true)
        Toast.makeText(this, "Đã bật thông báo kết quả hằng ngày!", Toast.LENGTH_SHORT).show()
    }

    private fun showOpenSettingsDialog() {
        AlertDialog.Builder(this)
            .setTitle("Thông báo đang bị tắt")
            .setMessage("Ứng dụng chưa được phép hiển thị thông báo. Mở Cài đặt để bật quyền thông báo cho VLottery?")
            .setPositiveButton("Mở Cài đặt") { _, _ ->
                startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                })
            }
            .setNegativeButton("Để sau", null)
            .show()
    }
}

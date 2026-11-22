package com.example.loterryapp

import android.Manifest
import android.app.DatePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.view.inputmethod.InputMethodManager
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.loterryapp.data.FirestoreSyncManager
import com.example.loterryapp.data.UserProfile
import com.example.loterryapp.data.UserProfileStore
import com.example.loterryapp.databinding.ActivityProfileBinding
import com.example.loterryapp.notification.LotteryNotificationHelper
import java.time.LocalDate
import java.time.Period
import java.time.format.DateTimeFormatter

class ProfileActivity : AppCompatActivity() {
    private lateinit var binding: ActivityProfileBinding
    private val authManager = AuthManager.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProfileBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupUI()
        setupPersonalInfo()
        setupNotificationFeature()
        setupDarkModeSwitch()
    }

    /** Công tắc chế độ tối: hiện đúng giao diện đang dùng; bật / tắt thì đổi ngay toàn app và lưu lại */
    private fun setupDarkModeSwitch() {
        binding.switchDarkMode.isChecked = ThemeManager.isDarkNow(this)
        binding.tvDarkModeHint.text = when {
            ThemeManager.isFollowingSystem(this) -> "Đang theo cài đặt của điện thoại"
            ThemeManager.isDarkNow(this) -> "Nền tối, dịu mắt khi xem buổi tối"
            else -> "Nền sáng"
        }
        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            // Màn hình sẽ được tạo lại với màu mới; công tắc đọc lại trạng thái ở setupDarkModeSwitch
            ThemeManager.setDarkMode(this, isChecked)
        }
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

    // ---------- Thông tin cá nhân ----------

    private fun setupPersonalInfo() {
        val uid = authManager.getCurrentUserUid() ?: return
        val store = UserProfileStore(this, uid)
        val profile = store.load()

        binding.actGender.setAdapter(ArrayAdapter(this, android.R.layout.simple_list_item_1, GENDERS))
        showProfile(store, profile)

        binding.etBirthday.setOnClickListener { showBirthdayPicker() }
        binding.layoutBirthday.setEndIconOnClickListener { showBirthdayPicker() }
        binding.btnSaveProfile.setOnClickListener { saveProfile(store) }

        // Máy này chưa có hồ sơ (máy mới / vừa cài lại app): tải hồ sơ đã lưu trên tài khoản (Firestore) về
        if (profile == UserProfile()) {
            FirestoreSyncManager.fetchUserProfile(uid) { remote ->
                if (remote != null && !isFinishing) {
                    store.save(remote)
                    showProfile(store, remote)
                }
            }
        }
    }

    /** Điền hồ sơ lên các ô; họ tên chưa điền thì lấy tên của tài khoản Firebase (VD: tên Google) */
    private fun showProfile(store: UserProfileStore, profile: UserProfile) {
        binding.etFullName.setText(profile.fullName.ifEmpty { authManager.getDisplayName().orEmpty() })
        binding.etPhone.setText(profile.phone)
        birthday = profile.birthday.takeIf { it.isNotEmpty() }?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        binding.etBirthday.setText(birthday?.format(DISPLAY_DATE).orEmpty())
        binding.actGender.setText(profile.gender, false)
        binding.etProvince.setText(profile.province)
        bindAiConsentSwitch(store, profile.aiConsent == true)
    }

    /** Công tắc cho phép Trợ lý AI dùng thông tin: đổi ý bất cứ lúc nào */
    private fun bindAiConsentSwitch(store: UserProfileStore, checked: Boolean) {
        // Gỡ listener trước khi đặt trạng thái, để không hiện Toast khi app tự đổi công tắc
        binding.switchAiConsent.setOnCheckedChangeListener(null)
        binding.switchAiConsent.isChecked = checked
        binding.switchAiConsent.setOnCheckedChangeListener { _, isChecked ->
            store.setAiConsent(isChecked)
            authManager.getCurrentUserUid()?.let { uid -> FirestoreSyncManager.syncUserProfile(uid, store.load()) }
            Toast.makeText(
                this,
                if (isChecked) "Trợ lý AI sẽ dùng thông tin của bạn khi trả lời" else "Trợ lý AI sẽ không dùng thông tin cá nhân của bạn",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private var birthday: LocalDate? = null

    private fun showBirthdayPicker() {
        // Mặc định mở ở năm của người 25 tuổi cho đỡ phải lùi nhiều năm
        val initial = birthday ?: LocalDate.now().minusYears(25)
        DatePickerDialog(this, { _, year, month, day ->
            birthday = LocalDate.of(year, month + 1, day)
            binding.etBirthday.setText(birthday?.format(DISPLAY_DATE))
            binding.layoutBirthday.error = null
        }, initial.year, initial.monthValue - 1, initial.dayOfMonth).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    private fun saveProfile(store: UserProfileStore) {
        val fullName = binding.etFullName.text?.toString()?.trim().orEmpty()
        val phone = binding.etPhone.text?.toString()?.trim().orEmpty()

        // Kiểm tra dữ liệu; các ô đều không bắt buộc, chỉ kiểm tra khi đã điền
        binding.layoutPhone.error = if (phone.isNotEmpty() && !PHONE_REGEX.matches(phone)) "Số điện thoại gồm 10 chữ số, bắt đầu bằng 0" else null
        binding.layoutBirthday.error = birthday?.let {
            if (Period.between(it, LocalDate.now()).years < MIN_AGE) "Bạn phải đủ $MIN_AGE tuổi để tham gia xổ số" else null
        }
        if (binding.layoutPhone.error != null || binding.layoutBirthday.error != null) return

        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(binding.root.windowToken, 0)

        val profile = UserProfile(
            fullName = fullName,
            phone = phone,
            birthday = birthday?.toString().orEmpty(),
            gender = binding.actGender.text?.toString().orEmpty(),
            province = binding.etProvince.text?.toString()?.trim().orEmpty(),
            aiConsent = store.load().aiConsent
        )

        if (profile.aiConsent == true) {
            // Đã đồng ý từ trước: lưu luôn, không hỏi lại (đổi ý bằng công tắc bên dưới)
            persistProfile(store, profile)
        } else {
            askAiConsent { consent -> persistProfile(store, profile.copy(aiConsent = consent)) }
        }
    }

    /** Hỏi người dùng có cho Trợ lý AI dùng thông tin cá nhân không */
    private fun askAiConsent(onAnswer: (Boolean) -> Unit) {
        AlertDialog.Builder(this)
            .setTitle("Cho phép Trợ lý AI dùng thông tin?")
            .setMessage(
                "Thông tin của bạn sẽ được AI sử dụng để tiện hỗ trợ cho các câu hỏi.\n\n" +
                    "Họ tên, tuổi, giới tính và tỉnh/thành sẽ được gửi kèm câu hỏi tới Trợ lý AI (dịch vụ Google Gemini). " +
                    "Số điện thoại không được gửi.\n\n" +
                    "Bạn có thể đổi ý bất cứ lúc nào bằng công tắc trong màn Hồ sơ."
            )
            .setCancelable(false)
            .setPositiveButton("Đồng ý") { _, _ -> onAnswer(true) }
            .setNegativeButton("Từ chối") { _, _ -> onAnswer(false) }
            .show()
    }

    private fun persistProfile(store: UserProfileStore, profile: UserProfile) {
        store.save(profile)
        bindAiConsentSwitch(store, profile.aiConsent == true)

        // Họ tên lưu thêm lên tài khoản Firebase; lỗi mạng cũng không sao vì đã lưu trên máy
        if (profile.fullName.isNotEmpty() && profile.fullName != authManager.getDisplayName()) {
            authManager.updateDisplayName(profile.fullName) { }
        }

        // Lưu hồ sơ lên tài khoản (Firestore users/{uid}) để dùng được trên máy khác
        val uid = authManager.getCurrentUserUid()
        if (uid == null) {
            Toast.makeText(this, "Đã lưu thông tin cá nhân", Toast.LENGTH_SHORT).show()
            return
        }
        FirestoreSyncManager.syncUserProfile(uid, profile) { synced ->
            Toast.makeText(
                this,
                if (synced) "Đã lưu thông tin cá nhân" else "Đã lưu trên máy, chưa lưu được lên tài khoản. Hãy kiểm tra mạng rồi bấm Lưu lại.",
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    // ---------- Thông báo ----------

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

    companion object {
        private val GENDERS = listOf("Nam", "Nữ", "Khác")
        /** Số di động Việt Nam: 10 chữ số, bắt đầu bằng 0 */
        private val PHONE_REGEX = Regex("""^0\d{9}$""")
        /** Tuổi tối thiểu được tham gia xổ số */
        private const val MIN_AGE = 18
        private val DISPLAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")
    }
}

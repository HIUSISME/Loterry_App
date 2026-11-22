package com.example.loterryapp

import android.Manifest
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.activity.OnBackPressedCallback
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.GravityCompat
import androidx.lifecycle.lifecycleScope
import com.example.loterryapp.data.DrawRepository
import com.example.loterryapp.data.UserProfileStore
import com.example.loterryapp.databinding.ActivityMainBinding
import com.example.loterryapp.network.models.ProvinceDraw
import com.example.loterryapp.notification.LotteryNotificationHelper
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter

class MainActivity : AppCompatActivity() {
    private lateinit var binding: ActivityMainBinding
    private val authManager = AuthManager.getInstance()
    private var todayJob: Job? = null

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

        binding.btnTicketCheck.setOnClickListener { startActivity(Intent(this, TicketCheckActivity::class.java)) }
        binding.btnAiAssistant.setOnClickListener { startActivity(Intent(this, AiAssistantActivity::class.java)) }

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
                R.id.nav_ticket_check -> startActivity(Intent(this, TicketCheckActivity::class.java))
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

    override fun onResume() {
        super.onResume()
        if (!::binding.isInitialized) return
        showGreeting()
        loadTodayResults()
    }

    // ---------- Lời chào ----------

    private fun showGreeting() {
        val uid = authManager.getCurrentUserUid()
        val fullName = uid?.let { UserProfileStore(this, it).load().fullName }.orEmpty()
        // Gọi bằng tên (từ cuối của họ tên), VD "Nguyễn Văn An" -> "An"
        val name = fullName.ifBlank { authManager.getDisplayName().orEmpty() }.trim().split(" ").lastOrNull().orEmpty()
        binding.tvGreeting.text = if (name.isNotEmpty()) "Xin chào, $name 👋" else "Xin chào 👋"

        val today = LocalDate.now(LotteryNotificationHelper.VN_ZONE)
        binding.tvToday.text = "${weekdayName(today.dayOfWeek)}, ${today.format(DATE_FORMAT)}"
    }

    // ---------- Kết quả hôm nay ----------

    /**
     * Giải Đặc Biệt hôm nay của 3 miền. Đọc từ database trước; chỉ gọi API khi đã đến giờ quay mà
     * database chưa đủ, và mỗi miền tối đa 5 phút một lần (mở Trang chủ nhiều lần không tốn lượt API).
     */
    private fun loadTodayResults() {
        val today = LocalDate.now(LotteryNotificationHelper.VN_ZONE).toString()
        val repository = DrawRepository(this)
        todayJob?.cancel()
        todayJob = lifecycleScope.launch {
            for (schedule in TODAY_ROWS) {
                val saved = repository.getSavedVietnamDraws(schedule.region, today)
                renderTodayRow(schedule, saved)
            }
            for (schedule in TODAY_ROWS) {
                val saved = repository.getSavedVietnamDraws(schedule.region, today)
                if (isComplete(saved) || !isDrawStarted(schedule) || !canRefresh(schedule.region)) continue
                lastRefresh[schedule.region] = System.currentTimeMillis()
                val fresh = try {
                    repository.fetchVietnamDraws(schedule.region, today, 10)
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    continue // Lỗi mạng: giữ nguyên dòng đang hiện
                }
                renderTodayRow(schedule, fresh)
            }
        }
    }

    private fun renderTodayRow(schedule: TodayRow, items: List<JsonObject>) {
        val container = binding.layoutToday
        val tag = "today_${schedule.region}"
        val row = container.findViewWithTag(tag) ?: createTodayRow(schedule).also {
            it.tag = tag
            container.addView(it)
        }
        val draws = items.map { gson.fromJson(it, ProvinceDraw::class.java) }
            .sortedBy { it.id?.takeIf { id -> id.isJsonPrimitive }?.asString?.toLongOrNull() ?: Long.MAX_VALUE }
        val status = row.findViewById<TextView>(R.id.tvTodayStatus)
        val numbers = row.findViewById<TextView>(R.id.tvTodayNumbers)

        if (draws.isNotEmpty() && draws.all { it.getPrizeNumbers("DB").isNotEmpty() }) {
            numbers.visibility = View.VISIBLE
            status.text = if (schedule.region == "MB") "Đã có kết quả" else draws.joinToString(" · ") { it.getProvinceName() }
            numbers.text = draws.joinToString("   ") { it.getPrizeNumbers("DB").joinToString(" ") }
        } else {
            numbers.visibility = View.GONE
            status.text = when {
                !isDrawStarted(schedule) -> "Quay lúc ${schedule.drawTime}"
                draws.isNotEmpty() -> "Đang quay trực tiếp..."
                else -> "Đang chờ kết quả..."
            }
        }
    }

    private fun createTodayRow(schedule: TodayRow): View {
        val row = layoutInflater.inflate(R.layout.item_today_result, binding.layoutToday, false)
        row.findViewById<TextView>(R.id.tvTodayBadge).apply {
            text = schedule.region
            backgroundTintList = ColorStateList.valueOf(ContextCompat.getColor(context, schedule.colorRes))
        }
        row.findViewById<TextView>(R.id.tvTodayRegion).text = schedule.name
        row.setOnClickListener { openVietnamLottery(schedule.region, "Xổ Số ${schedule.name}") }
        return row
    }

    private fun isComplete(items: List<JsonObject>): Boolean =
        items.isNotEmpty() && items.all { gson.fromJson(it, ProvinceDraw::class.java).getPrizeNumbers("DB").isNotEmpty() }

    private fun isDrawStarted(schedule: TodayRow): Boolean =
        !LocalTime.now(LotteryNotificationHelper.VN_ZONE).isBefore(schedule.drawTime)

    private fun canRefresh(region: String): Boolean =
        System.currentTimeMillis() - (lastRefresh[region] ?: 0L) > REFRESH_INTERVAL_MS

    private fun weekdayName(day: DayOfWeek) = when (day) {
        DayOfWeek.MONDAY -> "Thứ Hai"
        DayOfWeek.TUESDAY -> "Thứ Ba"
        DayOfWeek.WEDNESDAY -> "Thứ Tư"
        DayOfWeek.THURSDAY -> "Thứ Năm"
        DayOfWeek.FRIDAY -> "Thứ Sáu"
        DayOfWeek.SATURDAY -> "Thứ Bảy"
        DayOfWeek.SUNDAY -> "Chủ Nhật"
    }

    private data class TodayRow(val region: String, val name: String, val drawTime: LocalTime, val colorRes: Int)

    companion object {
        private val TODAY_ROWS = listOf(
            TodayRow("MN", "Miền Nam", LocalTime.of(16, 15), R.color.region_mn_end),
            TodayRow("MT", "Miền Trung", LocalTime.of(17, 15), R.color.region_mt_end),
            TodayRow("MB", "Miền Bắc", LocalTime.of(18, 15), R.color.region_mb_end)
        )
        private const val REFRESH_INTERVAL_MS = 5 * 60 * 1000L
        /** Lần gọi API gần nhất của từng miền (giữ qua các lần mở lại Trang chủ trong cùng phiên) */
        private val lastRefresh = mutableMapOf<String, Long>()
        private val DATE_FORMAT: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM/yyyy")
        private val gson = Gson()
    }

    private fun startLoginActivity() {
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }
}

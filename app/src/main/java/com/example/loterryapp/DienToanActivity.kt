package com.example.loterryapp

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.loterryapp.data.DrawRepository
import com.example.loterryapp.databinding.ActivityDienToanBinding
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class DienToanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDienToanBinding
    private val calendar = Calendar.getInstance()
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    private var loadJob: Job? = null
    private val repository by lazy { DrawRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDienToanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.rvDienToan.layoutManager = LinearLayoutManager(this)

        setupListeners()
        loadData(dateQuery = null)
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener { finish() }

        binding.btnPrev.setOnClickListener {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            updateDateDisplay()
            loadData(apiDateFormat.format(calendar.time))
        }

        binding.btnNext.setOnClickListener {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            updateDateDisplay()
            loadData(apiDateFormat.format(calendar.time))
        }

        val openDatePicker = View.OnClickListener { showDatePicker() }
        binding.ivCalendar.setOnClickListener(openDatePicker)
        binding.tvCurrentDate.setOnClickListener(openDatePicker)

        updateDateDisplay()
    }

    private fun showDatePicker() {
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(year, month, dayOfMonth)
                updateDateDisplay()
                loadData(apiDateFormat.format(calendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun updateDateDisplay() {
        binding.tvCurrentDate.text = displayDateFormat.format(calendar.time)
    }

    private fun loadData(dateQuery: String?) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmptyState.visibility = View.GONE
        binding.rvDienToan.adapter = DienToanAdapter(emptyList())

        // Huỷ request cũ để kết quả của ngày trước không ghi đè ngày vừa chọn
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            // 1. Hiện ngay kết quả đã lưu trên máy (nếu có), không cần chờ mạng
            val saved = repository.getSavedVietnamDraws(REGION, dateQuery)
            val showedSaved = showGames(saved)
            // Đã có dữ liệu để xem thì ẩn vòng xoay tải, tránh đẩy nội dung xuống rồi giật lên khi API trả về
            if (showedSaved) binding.progressBar.visibility = View.GONE
            // Ngày đã qua và đã quay xong: không cần gọi API
            if (repository.isFinalVietnam(REGION, dateQuery, saved)) return@launch

            // 2. Gọi API để lấy kết quả mới; repository tự lưu những kỳ mới / thay đổi vào database
            try {
                val fresh = repository.fetchVietnamDraws(region = REGION, date = dateQuery, limit = 10)
                binding.progressBar.visibility = View.GONE

                if (!showGames(fresh) && !showedSaved) {
                    showEmptyState("Chưa có kết quả ngày ${displayDateFormat.format(calendar.time)}")
                }
            } catch (e: CancellationException) {
                throw e // Request bị huỷ do người dùng đổi ngày: không hiển thị lỗi
            } catch (e: Exception) {
                Log.e("DienToanActivity", "Error loading draws", e)
                binding.progressBar.visibility = View.GONE
                if (showedSaved) {
                    Toast.makeText(this@DienToanActivity, "Không cập nhật được, đang hiện kết quả đã lưu", Toast.LENGTH_SHORT).show()
                } else {
                    showEmptyState(DrawRepository.errorMessage(e))
                }
            }
        }
    }

    /** Hiện các trò của ngày mới nhất trong danh sách. Trả về false nếu không có gì để hiện */
    private fun showGames(items: List<JsonObject>): Boolean {
        val draws = parseDraws(items)
        // API có thể trả về nhiều ngày: chỉ hiển thị kỳ quay mới nhất (phần tử đầu tiên)
        val latestDate = draws.firstOrNull()?.first
        val latestGames = draws.filter { it.first == latestDate }.map { it.second }
        if (latestGames.isEmpty()) return false

        if (latestDate != null) {
            syncCalendarWithApiDate(latestDate)
        }
        binding.tvEmptyState.visibility = View.GONE
        val dateLabel = buildDateLabel(latestDate)
        binding.rvDienToan.adapter = DienToanAdapter(latestGames.map { it.copy(date = dateLabel) })
        return true
    }

    private fun showEmptyState(message: String) {
        binding.rvDienToan.adapter = DienToanAdapter(emptyList())
        binding.tvEmptyState.visibility = View.VISIBLE
        binding.tvEmptyState.text = message
    }

    /**
     * Đọc các kỳ quay dạng:
     * { date: "2026-10-14T00:00:00.000Z", province: { code: "DT123" }, results: [...] }
     * Trả về danh sách (ngày quay gốc từ API, trò chơi)
     */
    private fun parseDraws(items: List<JsonObject>): List<Pair<String?, DienToanGame>> {
        val draws = mutableListOf<Triple<String?, GameInfo, DienToanGame>>()
        for (obj in items) {
            val gameCode = obj.get("province")?.takeIf { it.isJsonObject }?.asJsonObject?.getString("code") ?: continue
            val info = gameInfo(gameCode) ?: continue
            val numbers = extractNumbers(obj.get("results"))
            if (numbers.isNotEmpty()) {
                draws.add(Triple(obj.getString("date"), info, DienToanGame(info.title, "", numbers, info.description)))
            }
        }

        // Ngày mới nhất lên đầu (loadData lấy ngày của phần tử đầu tiên), cùng ngày thì theo thứ tự 123 → 6x36 → Thần tài
        return draws
            .sortedWith(compareByDescending<Triple<String?, GameInfo, DienToanGame>> { it.first.orEmpty() }.thenBy { it.second.order })
            .map { it.first to it.third }
    }

    private fun JsonObject.getString(key: String): String? {
        val element = get(key) ?: return null
        return if (element.isJsonPrimitive) element.asString else null
    }

    /** results: [ { prizeCode: "G1", prizeOrder: 1, values: ["8"] }, ... ] → các số theo thứ tự giải */
    private fun extractNumbers(results: JsonElement?): List<String> {
        if (results == null || !results.isJsonArray) return emptyList()
        return results.asJsonArray
            .filter { it.isJsonObject }
            .map { it.asJsonObject }
            .sortedBy { it.get("prizeOrder")?.takeIf { order -> order.isJsonPrimitive }?.asInt ?: Int.MAX_VALUE }
            .flatMap { prize ->
                prize.get("values")?.takeIf { it.isJsonArray }?.asJsonArray
                    ?.filter { it.isJsonPrimitive }?.map { it.asString }
                    .orEmpty()
            }
    }

    private data class GameInfo(val order: Int, val title: String, val description: String)

    /** Tên hiển thị và cơ cấu giải theo mã trò của API; null nếu không phải trò điện toán đã biết */
    private fun gameInfo(code: String): GameInfo? {
        return when (code.uppercase()) {
            "DT123" -> GameInfo(0, "ĐIỆN TOÁN 123", "Cơ cấu giải: Trúng khớp 1, 2 hoặc 3 bộ số")
            "DT6X36" -> GameInfo(1, "ĐIỆN TOÁN 6X36", "Cơ cấu giải: Chọn 6 cặp số từ 01 đến 36")
            "TT4" -> GameInfo(2, "THẦN TÀI 4", "Cơ cấu giải: Trúng khớp 4 chữ số may mắn")
            else -> null
        }
    }

    private fun parseApiDate(dateStr: String): Date? {
        return try {
            val parts = dateStr.split("-")
            if (parts.size == 3 && parts[0].length == 4) apiDateFormat.parse(dateStr)
            else if (parts.size == 3) displayDateFormat.parse(dateStr)
            else null
        } catch (e: Exception) {
            null
        }
    }

    private fun syncCalendarWithApiDate(dateStr: String) {
        val parsedDate = parseApiDate(dateStr) ?: return
        calendar.time = parsedDate
        updateDateDisplay()
    }

    /** VD: "Thứ Tư, 08-10-2026" (tính thứ theo ngày quay thực tế) */
    private fun buildDateLabel(dateStr: String?): String {
        val date = dateStr?.let { parseApiDate(it) } ?: return dateStr ?: displayDateFormat.format(calendar.time)
        val dayCalendar = Calendar.getInstance().apply { time = date }
        val dayOfWeek = when (dayCalendar.get(Calendar.DAY_OF_WEEK)) {
            Calendar.MONDAY -> "Thứ Hai"
            Calendar.TUESDAY -> "Thứ Ba"
            Calendar.WEDNESDAY -> "Thứ Tư"
            Calendar.THURSDAY -> "Thứ Năm"
            Calendar.FRIDAY -> "Thứ Sáu"
            Calendar.SATURDAY -> "Thứ Bảy"
            else -> "Chủ Nhật"
        }
        return "$dayOfWeek, ${displayDateFormat.format(date)}"
    }

    companion object {
        /** Mã vùng Điện toán của API (Điện toán 123, 6x36, Thần tài 4) */
        private const val REGION = "DIEN_TOAN"
    }

    data class DienToanGame(
        val title: String,
        val date: String,
        val numbers: List<String>,
        val description: String
    )

    class DienToanAdapter(private val games: List<DienToanGame>) : RecyclerView.Adapter<DienToanAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvGameTitle)
            val tvDate: TextView = view.findViewById(R.id.tvGameDate)
            val tvNumbers: TextView = view.findViewById(R.id.tvGameNumbers)
            val tvDesc: TextView = view.findViewById(R.id.tvGameDesc)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_dien_toan_card, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val game = games[position]
            holder.tvTitle.text = game.title
            holder.tvDate.text = game.date
            holder.tvNumbers.text = game.numbers.joinToString("   -   ")
            holder.tvDesc.text = game.description
        }

        override fun getItemCount() = games.size
    }
}

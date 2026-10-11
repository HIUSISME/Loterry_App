package com.example.loterryapp

import android.app.DatePickerDialog
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.loterryapp.databinding.ActivityDienToanBinding
import com.example.loterryapp.network.ApiClient
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
            try {
                val response = ApiClient.xosoService.getDienToanDraws(date = dateQuery, limit = 10)
                binding.progressBar.visibility = View.GONE

                if (!response.isSuccessful) {
                    showEmptyState("Lỗi kết nối API (${response.code()})")
                    return@launch
                }

                val draws = parseDraws(response.body())
                // API có thể trả về nhiều ngày: chỉ hiển thị kỳ quay mới nhất (phần tử đầu tiên)
                val latestDate = draws.firstOrNull()?.first
                val latestGames = draws.filter { it.first == latestDate }.map { it.second }

                if (latestDate != null) {
                    syncCalendarWithApiDate(latestDate)
                }

                if (latestGames.isNotEmpty()) {
                    val dateLabel = buildDateLabel(latestDate)
                    binding.rvDienToan.adapter = DienToanAdapter(latestGames.map { it.copy(date = dateLabel) })
                } else {
                    showEmptyState("Chưa có kết quả ngày ${displayDateFormat.format(calendar.time)}")
                }
            } catch (e: CancellationException) {
                throw e // Request bị huỷ do người dùng đổi ngày: không hiển thị lỗi
            } catch (e: Exception) {
                Log.e("DienToanActivity", "Error loading draws", e)
                binding.progressBar.visibility = View.GONE
                showEmptyState("Lỗi mạng hoặc không thể tải dữ liệu: ${e.localizedMessage ?: "Vui lòng thử lại"}")
            }
        }
    }

    private fun showEmptyState(message: String) {
        binding.rvDienToan.adapter = DienToanAdapter(emptyList())
        binding.tvEmptyState.visibility = View.VISIBLE
        binding.tvEmptyState.text = message
    }

    /**
     * Đọc kết quả từ response, chấp nhận các dạng:
     * - { data: [ { type: "DT123", date: "...", results: [...] }, ... ] }
     * - { data: { date: "...", dt123: [...], dt6x36: [...], tt4: [...] } }
     * Trả về danh sách (ngày quay gốc từ API, trò chơi)
     */
    private fun parseDraws(root: JsonElement?): List<Pair<String?, DienToanGame>> {
        if (root == null || root.isJsonNull) return emptyList()
        val data = if (root.isJsonObject && root.asJsonObject.has("data")) root.asJsonObject.get("data") else root

        val items = when {
            data.isJsonArray -> data.asJsonArray.filter { it.isJsonObject }.map { it.asJsonObject }
            data.isJsonObject -> listOf(data.asJsonObject)
            else -> emptyList()
        }

        val result = mutableListOf<Pair<String?, DienToanGame>>()
        for (obj in items) {
            val date = obj.getString("date") ?: obj.getString("draw_date")
            val gameCode = obj.getString("type") ?: obj.getString("game") ?: obj.getString("code")

            if (gameCode != null) {
                // Dạng mỗi phần tử là một trò chơi
                val numbers = extractNumbers(obj.get("results") ?: obj.get("numbers") ?: obj.get("result"))
                if (numbers.isNotEmpty()) {
                    val info = gameInfo(gameCode)
                    result.add(date to DienToanGame(info?.first ?: gameCode.uppercase(), "", numbers, info?.second ?: ""))
                }
            } else {
                // Dạng mỗi phần tử là một ngày, các trò chơi nằm trong các key riêng
                for ((key, value) in obj.entrySet()) {
                    val info = gameInfo(key) ?: continue
                    val numbers = extractNumbers(value)
                    if (numbers.isNotEmpty()) {
                        result.add(date to DienToanGame(info.first, "", numbers, info.second))
                    }
                }
            }
        }
        return result
    }

    private fun JsonObject.getString(key: String): String? {
        val element = get(key) ?: return null
        return if (element.isJsonPrimitive) element.asString else null
    }

    private fun extractNumbers(element: JsonElement?): List<String> {
        return when {
            element == null || element.isJsonNull -> emptyList()
            element.isJsonArray -> element.asJsonArray.filter { it.isJsonPrimitive }.map { it.asString }
            element.isJsonPrimitive -> element.asString.split(Regex("[\\s,;-]+")).filter { it.isNotEmpty() }
            else -> emptyList()
        }
    }

    /** Tên hiển thị và cơ cấu giải theo mã trò chơi; null nếu không phải trò chơi điện toán đã biết */
    private fun gameInfo(code: String): Pair<String, String>? {
        val normalized = code.uppercase().replace(Regex("[^A-Z0-9]"), "")
        return when {
            normalized.contains("6X36") -> "ĐIỆN TOÁN 6X36" to "Cơ cấu giải: Chọn 6 cặp số từ 01 đến 36"
            normalized.contains("123") -> "ĐIỆN TOÁN 123" to "Cơ cấu giải: Trúng khớp 1, 2 hoặc 3 bộ số"
            normalized.contains("THANTAI") || normalized == "TT4" -> "THẦN TÀI 4" to "Cơ cấu giải: Trúng khớp 4 chữ số may mắn"
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

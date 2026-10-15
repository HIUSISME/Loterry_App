package com.example.loterryapp

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.loterryapp.data.DrawRepository
import com.example.loterryapp.databinding.ActivityVietnamLotteryBinding
import com.example.loterryapp.network.models.ProvinceDraw
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class VietnamLotteryActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVietnamLotteryBinding
    private val calendar = Calendar.getInstance()
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    private val gson = Gson()

    private var cachedProvinces: List<ProvinceDraw> = emptyList()
    private var currentFilter: String = "ALL"
    private var region: String = "MN" // Default
    private var regionTitle: String = "Xổ Số Miền Nam"
    private var loadJob: Job? = null
    private val repository by lazy { DrawRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVietnamLotteryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        region = intent.getStringExtra("REGION") ?: "MN"
        regionTitle = intent.getStringExtra("TITLE") ?: "Xổ Số Miền Nam"
        
        binding.tvToolbarTitle.text = regionTitle
        // Hiện ngày hôm nay ngay khi mở, thay cho chữ mẫu trong layout (sẽ được cập nhật theo ngày quay của API)
        updateDateDisplay()

        setupListeners()
        loadData(dateQuery = null)
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            finish()
        }

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

        binding.btnFilterAll.setOnClickListener { setFilterMode("ALL") }
        binding.btnFilter3.setOnClickListener { setFilterMode("LAST3") }
        binding.btnFilter2.setOnClickListener { setFilterMode("LAST2") }
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

    private fun setFilterMode(mode: String) {
        currentFilter = mode
        val activeBg = Color.parseColor("#C62828")
        val inactiveBg = Color.parseColor("#F1F5F9")
        val activeText = Color.WHITE
        val inactiveText = Color.parseColor("#1E293B")
        
        binding.btnFilterAll.apply { 
            backgroundTintList = ColorStateList.valueOf(if (mode == "ALL") activeBg else inactiveBg)
            setTextColor(if (mode == "ALL") activeText else inactiveText)
        }
        binding.btnFilter3.apply { 
            backgroundTintList = ColorStateList.valueOf(if (mode == "LAST3") activeBg else inactiveBg)
            setTextColor(if (mode == "LAST3") activeText else inactiveText)
        }
        binding.btnFilter2.apply { 
            backgroundTintList = ColorStateList.valueOf(if (mode == "LAST2") activeBg else inactiveBg)
            setTextColor(if (mode == "LAST2") activeText else inactiveText)
        }

        if (cachedProvinces.isNotEmpty()) renderTable(cachedProvinces)
    }

    private fun loadData(dateQuery: String?) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmptyState.visibility = View.GONE
        binding.cardResult.visibility = View.GONE

        // Huỷ request cũ để kết quả của ngày trước không ghi đè ngày vừa chọn
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            // 1. Hiện ngay kết quả đã lưu trên máy (nếu có), không cần chờ mạng
            val showedSaved = showDraws(repository.getSavedVietnamDraws(region, dateQuery))
            // Đã có dữ liệu để xem thì ẩn vòng xoay tải, tránh đẩy nội dung xuống rồi giật lên khi API trả về
            if (showedSaved) binding.progressBar.visibility = View.GONE

            // 2. Gọi API để lấy kết quả mới; repository tự lưu những kỳ mới / thay đổi vào database
            try {
                Log.d("VietnamLottery", "Fetching data for region: $region, date: $dateQuery")
                // Không có ngày: API gom theo ngày nên limit = 1 ngày. Có ngày: limit đếm theo đài (tối đa 4 đài/ngày)
                val limit = if (dateQuery == null) 1 else 10
                val fresh = repository.fetchVietnamDraws(region = region, date = dateQuery, limit = limit)
                binding.progressBar.visibility = View.GONE

                if (!showDraws(fresh) && !showedSaved) {
                    showEmptyState("Chưa có kết quả cho khu vực này")
                }
            } catch (e: CancellationException) {
                throw e // Request bị huỷ do người dùng đổi ngày: không hiển thị lỗi
            } catch (e: Exception) {
                Log.e("VietnamLottery", "Error: ", e)
                binding.progressBar.visibility = View.GONE
                if (showedSaved) {
                    Toast.makeText(this@VietnamLotteryActivity, "Không cập nhật được, đang hiện kết quả đã lưu", Toast.LENGTH_SHORT).show()
                } else {
                    showEmptyState(if (e is DrawRepository.ApiException) e.message.orEmpty() else "Lỗi: ${e.message}")
                }
            }
        }
    }

    /** Hiện danh sách kỳ quay (các đài của cùng một ngày). Trả về false nếu không có gì để hiện */
    private fun showDraws(items: List<JsonObject>): Boolean {
        if (items.isEmpty()) return false
        // Chỉ hiện ngày mới nhất trong danh sách
        val latestDate = items.mapNotNull { it.get("date")?.takeIf { d -> d.isJsonPrimitive }?.asString }.maxOrNull()
        val provinces = items
            .filter { latestDate == null || it.get("date")?.asString == latestDate }
            // Sắp theo id kỳ quay để thứ tự các đài giống nhau dù dữ liệu lấy từ database hay từ API
            // (nếu không, các cột sẽ nhảy chỗ khi kết quả API thay cho kết quả đã lưu)
            .sortedBy { it.get("id")?.takeIf { id -> id.isJsonPrimitive }?.asString?.toLongOrNull() ?: Long.MAX_VALUE }
            .map { gson.fromJson(it, ProvinceDraw::class.java) }

        if (!latestDate.isNullOrEmpty()) syncCalendarWithApiDate(latestDate)
        binding.tvEmptyState.visibility = View.GONE
        cachedProvinces = provinces
        renderTable(provinces)
        return true
    }

    private fun showEmptyState(message: String) {
        cachedProvinces = emptyList()
        binding.cardResult.visibility = View.GONE
        binding.tvEmptyState.apply {
            visibility = View.VISIBLE
            text = message
        }
    }

    private fun syncCalendarWithApiDate(dateStr: String) {
        try {
            val parsedDate = if (dateStr.contains("-")) {
                val parts = dateStr.split("-")
                if (parts[0].length == 4) apiDateFormat.parse(dateStr) else displayDateFormat.parse(dateStr)
            } else null
            if (parsedDate != null) {
                calendar.time = parsedDate
                updateDateDisplay()
            }
        } catch (e: Exception) {}
    }

    private fun renderTable(provinces: List<ProvinceDraw>) {
        if (provinces.isEmpty()) return
        when (region) {
            // Miền Bắc chỉ có một đài mỗi ngày: thẻ một cột, có dòng Mã Đặc Biệt
            "MB" -> renderMienBac(provinces.first())
            // Miền Trung / Miền Nam: thẻ nhiều cột (mỗi đài một cột), chỉ khác tiêu đề và màu header
            "MT" -> renderMultiProvince("🏆  XỔ SỐ MIỀN TRUNG", R.drawable.bg_mt_header, provinces)
            else -> renderMultiProvince("🏆  XỔ SỐ MIỀN NAM", R.drawable.bg_mn_header, provinces)
        }
    }

    /** Lọc "3 Số" / "2 Số": chỉ giữ các chữ số cuối */
    private fun applyFilter(numbers: List<String>): List<String> = numbers.map {
        when (currentFilter) {
            "LAST2" -> if (it.length >= 2) it.takeLast(2) else it
            "LAST3" -> if (it.length >= 3) it.takeLast(3) else it
            else -> it
        }
    }

    /** Hiện thẻ kết quả với tiêu đề và màu header riêng của từng miền */
    private fun showResultCard(title: String, headerBackground: Int) {
        binding.cardResult.visibility = View.VISIBLE
        binding.tvResultTitle.text = title
        binding.tvResultTitle.setBackgroundResource(headerBackground)
        binding.layoutResultRows.removeAllViews()
    }

    // ---------- Thẻ kết quả Miền Bắc ----------

    private fun renderMienBac(draw: ProvinceDraw) {
        showResultCard("🏆  XỔ SỐ MIỀN BẮC", R.drawable.bg_mb_header)

        // Dòng "Mã Đặc Biệt: 12HT - 15HT - ..." (không áp dụng bộ lọc số cuối)
        val maDb = draw.getPrizeNumbers("MA_DB")
        binding.tvMaDb.visibility = if (maDb.isEmpty()) View.GONE else View.VISIBLE
        binding.tvMaDb.text = SpannableStringBuilder("Mã Đặc Biệt:  ").apply {
            val start = length
            append(maDb.joinToString(" - "))
            setSpan(StyleSpan(Typeface.BOLD), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            setSpan(ForegroundColorSpan(Color.parseColor("#111827")), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }

        MB_PRIZES.forEachIndexed { index, (prizeCode, label) ->
            binding.layoutResultRows.addView(createDivider(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)))
            binding.layoutResultRows.addView(createMbRow(label, applyFilter(draw.getPrizeNumbers(prizeCode)), prizeCode, index))
        }
    }

    // ---------- Thẻ kết quả Miền Trung / Miền Nam (nhiều đài, mỗi đài một cột) ----------

    private fun renderMultiProvince(title: String, headerBackground: Int, provinces: List<ProvinceDraw>) {
        showResultCard(title, headerBackground)
        binding.tvMaDb.visibility = View.GONE

        // Ngày có 4 đài thì thu nhỏ chữ để vừa màn hình điện thoại
        val compact = provinces.size >= 4

        // Hàng tiêu đề: "Giải" + tên các đài
        binding.layoutResultRows.addView(createMultiRow(
            label = "Giải",
            labelBold = true,
            background = Color.parseColor("#F3F4F6"),
            cells = provinces.map { province ->
                createCellText(province.getProvinceName(), Color.parseColor("#374151"), if (compact) 13f else 14f, bold = false)
            }
        ))

        MULTI_PROVINCE_PRIZES.forEach { (prizeCode, label) ->
            binding.layoutResultRows.addView(createDivider(LinearLayout.LayoutParams.MATCH_PARENT, dpToPx(1)))
            val background = when (prizeCode) {
                "DB" -> Color.parseColor("#FFF1F2")
                "G8" -> Color.parseColor("#F8FAFC")
                else -> Color.WHITE
            }
            binding.layoutResultRows.addView(createMultiRow(
                label = label,
                labelBold = false,
                background = background,
                cells = provinces.map { province ->
                    createNumberColumn(applyFilter(province.getPrizeNumbers(prizeCode)), prizeCode, compact)
                }
            ))
        }
    }

    /** Một hàng của bảng nhiều cột: cột tên giải + mỗi đài một cột, có đường kẻ dọc giữa các cột */
    private fun createMultiRow(label: String, labelBold: Boolean, background: Int, cells: List<View>): LinearLayout {
        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL

            addView(TextView(this@VietnamLotteryActivity).apply {
                text = label
                gravity = Gravity.CENTER
                // Hàng tiêu đề dùng chung nền xám; các hàng giải có cột tên giải nền trắng
                setBackgroundColor(if (labelBold) background else Color.WHITE)
                setTextColor(Color.parseColor(if (labelBold) "#111827" else "#6B7280"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                if (labelBold) setTypeface(null, Typeface.BOLD)
                setPadding(dpToPx(4), dpToPx(12), dpToPx(4), dpToPx(12))
                layoutParams = LinearLayout.LayoutParams(dpToPx(68), LinearLayout.LayoutParams.MATCH_PARENT)
            })

            cells.forEach { cell ->
                addView(createDivider(dpToPx(1), LinearLayout.LayoutParams.MATCH_PARENT))
                cell.setBackgroundColor(background)
                cell.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                addView(cell)
            }
        }
    }

    /** Các số của một đài trong một giải, xếp dọc từng số một dòng */
    private fun createNumberColumn(numbers: List<String>, prizeCode: String, compact: Boolean): LinearLayout {
        val (color, size) = when (prizeCode) {
            "DB" -> Color.parseColor("#E60012") to if (compact) 17f else 20f
            "G8" -> Color.parseColor("#EF4444") to if (compact) 16f else 17f
            else -> Color.parseColor("#111827") to if (compact) 14f else 15f
        }
        return LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(0, dpToPx(8), 0, dpToPx(8))
            (numbers.ifEmpty { listOf("-") }).forEach { number ->
                addView(createCellText(number, color, size, bold = true).apply {
                    letterSpacing = if (compact) 0.04f else 0.08f
                    setPadding(0, dpToPx(3), 0, dpToPx(3))
                })
            }
        }
    }

    private fun createCellText(text: String, color: Int, sizeSp: Float, bold: Boolean): TextView {
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setTextColor(color)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
            if (bold) setTypeface(null, Typeface.BOLD)
            maxLines = 1
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
    }

    private fun createMbRow(label: String, numbers: List<String>, prizeCode: String, index: Int): LinearLayout {
        val rowBg = when {
            prizeCode == "DB" -> Color.parseColor("#FFF1F2")
            index % 2 == 0 -> Color.parseColor("#F8FAFC")
            else -> Color.WHITE
        }

        return LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(rowBg)
            minimumHeight = dpToPx(if (prizeCode == "DB") 72 else 56)

            // Cột tên giải
            addView(TextView(this@VietnamLotteryActivity).apply {
                text = label
                gravity = Gravity.CENTER
                setTextColor(Color.parseColor("#374151"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                layoutParams = LinearLayout.LayoutParams(dpToPx(84), LinearLayout.LayoutParams.MATCH_PARENT)
            })

            addView(createDivider(dpToPx(1), LinearLayout.LayoutParams.MATCH_PARENT))

            // Cột các số trúng thưởng, chia thành nhiều dòng để vừa màn hình điện thoại
            addView(LinearLayout(this@VietnamLotteryActivity).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(dpToPx(4), dpToPx(10), dpToPx(4), dpToPx(10))
                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)

                if (numbers.isEmpty()) {
                    addView(createMbNumber("-", prizeCode))
                } else {
                    for (line in splitIntoLines(numbers)) {
                        addView(LinearLayout(this@VietnamLotteryActivity).apply {
                            orientation = LinearLayout.HORIZONTAL
                            gravity = Gravity.CENTER
                            line.forEach { addView(createMbNumber(it, prizeCode)) }
                        })
                    }
                }
            })
        }
    }

    private fun createMbNumber(number: String, prizeCode: String): TextView {
        return TextView(this).apply {
            text = number
            gravity = Gravity.CENTER
            setTypeface(null, Typeface.BOLD)
            when (prizeCode) {
                "DB" -> {
                    setTextColor(Color.parseColor("#E60012"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 30f)
                    letterSpacing = 0.18f
                }
                "G7" -> {
                    setTextColor(Color.parseColor("#EF4444"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                    letterSpacing = 0.1f
                }
                else -> {
                    setTextColor(Color.parseColor("#111827"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                    letterSpacing = 0.1f
                }
            }
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dpToPx(8), dpToPx(2), dpToPx(8), dpToPx(2)) }
        }
    }

    /**
     * Chia các số thành các dòng đều nhau: số dài (4-5 chữ số) tối đa 3 số/dòng, số ngắn tối đa 4 số/dòng.
     * VD: G.3 (6 số) -> 2 dòng x 3; G.4 (4 số) -> 2 dòng x 2; G.7 (4 số 2 chữ số) -> 1 dòng
     */
    private fun splitIntoLines(numbers: List<String>): List<List<String>> {
        val maxPerLine = if ((numbers.maxOfOrNull { it.length } ?: 0) >= 4) 3 else 4
        val lineCount = (numbers.size + maxPerLine - 1) / maxPerLine
        val perLine = (numbers.size + lineCount - 1) / lineCount
        return numbers.chunked(perLine)
    }

    private fun createDivider(width: Int, height: Int): View {
        return View(this).apply {
            setBackgroundColor(Color.parseColor("#E5E7EB"))
            layoutParams = LinearLayout.LayoutParams(width, height)
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()

    companion object {
        private val MB_PRIZES = listOf(
            "DB" to "Đặc Biệt",
            "G1" to "Giải Nhất",
            "G2" to "Giải Nhì",
            "G3" to "Giải Ba",
            "G4" to "Giải Tư",
            "G5" to "Giải Năm",
            "G6" to "Giải Sáu",
            "G7" to "Giải Bảy"
        )

        /** Thứ tự giải của Miền Trung và Miền Nam (từ Giải Tám lên Đặc Biệt) */
        private val MULTI_PROVINCE_PRIZES = listOf(
            "G8" to "Giải Tám",
            "G7" to "Giải Bảy",
            "G6" to "Giải Sáu",
            "G5" to "Giải Năm",
            "G4" to "Giải Tư",
            "G3" to "Giải Ba",
            "G2" to "Giải Nhì",
            "G1" to "Giải Nhất",
            "DB" to "Đặc Biệt"
        )
    }
}

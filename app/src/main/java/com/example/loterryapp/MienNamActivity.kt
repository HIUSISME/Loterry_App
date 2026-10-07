package com.example.loterryapp

import android.app.DatePickerDialog
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.loterryapp.databinding.ActivityMienNamBinding
import com.example.loterryapp.network.ApiClient
import com.example.loterryapp.network.models.ProvinceDraw
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MienNamActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMienNamBinding
    private val calendar = Calendar.getInstance()
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    private val gson = Gson()

    private var cachedProvinces: List<ProvinceDraw> = emptyList()
    private var currentFilter: String = "ALL" // "ALL", "LAST3", "LAST2"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMienNamBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        // Lần đầu mở màn hình: truyền null để API tự động trả về kỳ quay mới nhất hiện có
        loadData(dateQuery = null)
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            finish()
        }

        // Chuyển ngày trước
        binding.btnPrev.setOnClickListener {
            calendar.add(Calendar.DAY_OF_YEAR, -1)
            updateDateDisplay()
            loadData(apiDateFormat.format(calendar.time))
        }

        // Chuyển ngày sau
        binding.btnNext.setOnClickListener {
            calendar.add(Calendar.DAY_OF_YEAR, 1)
            updateDateDisplay()
            loadData(apiDateFormat.format(calendar.time))
        }

        // Chọn ngày từ lịch
        val openDatePicker = View.OnClickListener {
            showDatePicker()
        }
        binding.ivCalendar.setOnClickListener(openDatePicker)
        binding.tvCurrentDate.setOnClickListener(openDatePicker)

        // Các nút lọc số cuối
        binding.btnFilterAll.setOnClickListener {
            setFilterMode("ALL")
        }

        binding.btnFilter3.setOnClickListener {
            setFilterMode("LAST3")
        }

        binding.btnFilter2.setOnClickListener {
            setFilterMode("LAST2")
        }
    }

    private fun showDatePicker() {
        val datePicker = DatePickerDialog(
            this,
            { _, year, month, dayOfMonth ->
                calendar.set(Calendar.YEAR, year)
                calendar.set(Calendar.MONTH, month)
                calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                updateDateDisplay()
                loadData(apiDateFormat.format(calendar.time))
            },
            calendar.get(Calendar.YEAR),
            calendar.get(Calendar.MONTH),
            calendar.get(Calendar.DAY_OF_MONTH)
        )
        datePicker.show()
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

        binding.btnFilterAll.backgroundTintList = ColorStateList.valueOf(if (mode == "ALL") activeBg else inactiveBg)
        binding.btnFilterAll.setTextColor(if (mode == "ALL") activeText else inactiveText)

        binding.btnFilter3.backgroundTintList = ColorStateList.valueOf(if (mode == "LAST3") activeBg else inactiveBg)
        binding.btnFilter3.setTextColor(if (mode == "LAST3") activeText else inactiveText)

        binding.btnFilter2.backgroundTintList = ColorStateList.valueOf(if (mode == "LAST2") activeBg else inactiveBg)
        binding.btnFilter2.setTextColor(if (mode == "LAST2") activeText else inactiveText)

        if (cachedProvinces.isNotEmpty()) {
            renderTable(cachedProvinces)
        }
    }

    private fun loadData(dateQuery: String?) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmptyState.visibility = View.GONE
        binding.tableLayout.removeAllViews()

        lifecycleScope.launch {
            try {
                Log.d("MienNamActivity", "Gọi API lấy kết quả Miền Nam, date: $dateQuery")
                val response = ApiClient.xosoService.getVietnamDraws(
                    region = "MN",
                    date = dateQuery,
                    limit = 1
                )

                binding.progressBar.visibility = View.GONE
                Log.d("MienNamActivity", "API Code: ${response.code()}, body: ${response.body()}")

                if (response.isSuccessful) {
                    val body = response.body()
                    val dataElement = body?.data
                    val provincesList = mutableListOf<ProvinceDraw>()
                    var returnedDate: String? = null

                    if (dataElement != null && dataElement.isJsonArray) {
                        val jsonArray = dataElement.asJsonArray
                        if (jsonArray.size() > 0) {
                            val firstElement = jsonArray[0]
                            if (firstElement.isJsonObject) {
                                val firstObj = firstElement.asJsonObject

                                // TH1: Dạng lồng { date: "...", provinces: [...] }
                                if (firstObj.has("provinces")) {
                                    returnedDate = firstObj.get("date")?.asString
                                    val provArray = firstObj.getAsJsonArray("provinces")
                                    for (p in provArray) {
                                        val pDraw = gson.fromJson(p, ProvinceDraw::class.java)
                                        provincesList.add(pDraw)
                                    }
                                }
                                // TH2: Dạng phẳng mảng các tỉnh [{ province: {...}, prizes: {...}, date: "..." }]
                                else {
                                    returnedDate = firstObj.get("date")?.asString
                                    for (item in jsonArray) {
                                        val pDraw = gson.fromJson(item, ProvinceDraw::class.java)
                                        provincesList.add(pDraw)
                                    }
                                }
                            }
                        }
                    } else if (dataElement != null && dataElement.isJsonObject) {
                        val jsonObject = dataElement.asJsonObject
                        returnedDate = jsonObject.get("date")?.asString
                        if (jsonObject.has("provinces")) {
                            val provArray = jsonObject.getAsJsonArray("provinces")
                            for (p in provArray) {
                                val pDraw = gson.fromJson(p, ProvinceDraw::class.java)
                                provincesList.add(pDraw)
                            }
                        }
                    }

                    // Cập nhật ngày quay thực tế từ API lên UI
                    if (!returnedDate.isNullOrEmpty()) {
                        syncCalendarWithApiDate(returnedDate)
                    }

                    if (provincesList.isNotEmpty()) {
                        cachedProvinces = provincesList
                        renderTable(provincesList)
                    } else {
                        cachedProvinces = emptyList()
                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.tvEmptyState.text = "Chưa có kết quả ngày ${displayDateFormat.format(calendar.time)}"
                    }
                } else {
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.tvEmptyState.text = "Lỗi kết nối API (${response.code()}): Vui lòng thử lại"
                }
            } catch (e: Exception) {
                Log.e("MienNamActivity", "Exception when fetching data", e)
                binding.progressBar.visibility = View.GONE
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.tvEmptyState.text = "Lỗi mạng hoặc không thể tải dữ liệu: ${e.localizedMessage ?: "Vui lòng thử lại"}"
            }
        }
    }

    private fun syncCalendarWithApiDate(dateStr: String) {
        try {
            val parsedDate = if (dateStr.contains("-")) {
                val parts = dateStr.split("-")
                if (parts[0].length == 4) {
                    apiDateFormat.parse(dateStr)
                } else {
                    displayDateFormat.parse(dateStr)
                }
            } else null

            if (parsedDate != null) {
                calendar.time = parsedDate
                updateDateDisplay()
            } else {
                binding.tvCurrentDate.text = dateStr
            }
        } catch (e: Exception) {
            binding.tvCurrentDate.text = dateStr
        }
    }

    private fun renderTable(provinces: List<ProvinceDraw>) {
        binding.tableLayout.removeAllViews()
        binding.tvEmptyState.visibility = View.GONE
        binding.tableLayout.visibility = View.VISIBLE

        val context = this

        // 1. Dựng Header Row (Tên các đài/tỉnh)
        val headerRow = TableRow(context).apply {
            setBackgroundColor(Color.parseColor("#FFFFE0"))
            val params = TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(1)
            }
            layoutParams = params
        }

        // Cột đầu tiên của header: "Giải"
        val headerColTitle = TextView(context).apply {
            text = "Giải"
            gravity = Gravity.CENTER
            setPadding(dpToPx(6), dpToPx(10), dpToPx(6), dpToPx(10))
            setTypeface(null, Typeface.BOLD)
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        }
        headerRow.addView(headerColTitle)

        // Các cột tỉnh
        for (provinceDraw in provinces) {
            val pName = provinceDraw.getProvinceName()
            val pCode = provinceDraw.getProvinceCode()
            val displayText = if (pCode.isNotEmpty()) "$pName\n$pCode" else pName

            val provinceHeader = TextView(context).apply {
                text = displayText
                gravity = Gravity.CENTER
                setPadding(dpToPx(6), dpToPx(10), dpToPx(6), dpToPx(10))
                setTypeface(null, Typeface.BOLD)
                setTextColor(Color.BLACK)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                val lp = TableRow.LayoutParams().apply {
                    marginStart = dpToPx(1)
                }
                layoutParams = lp
            }
            headerRow.addView(provinceHeader)
        }
        binding.tableLayout.addView(headerRow)

        // 2. Danh sách 9 giải chuẩn của Xổ Số Miền Nam
        val prizeDefinitions = listOf(
            "G8" to "G.8",
            "G7" to "G.7",
            "G6" to "G.6",
            "G5" to "G.5",
            "G4" to "G.4",
            "G3" to "G.3",
            "G2" to "G.2",
            "G1" to "G.1",
            "DB" to "ĐB"
        )

        for ((index, prizePair) in prizeDefinitions.withIndex()) {
            val (prizeCode, prizeLabel) = prizePair

            val rowBg = when {
                prizeCode == "DB" -> Color.parseColor("#FFFBEB")
                index % 2 == 0 -> Color.WHITE
                else -> Color.parseColor("#F9F9F9")
            }

            val tableRow = TableRow(context).apply {
                setBackgroundColor(rowBg)
                val params = TableRow.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT,
                    TableRow.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(1)
                }
                layoutParams = params
            }

            // Cột tên giải
            val tvPrizeName = TextView(context).apply {
                text = prizeLabel
                gravity = Gravity.CENTER
                setPadding(dpToPx(6), dpToPx(8), dpToPx(6), dpToPx(8))
                setTypeface(null, Typeface.BOLD)
                if (prizeCode == "DB" || prizeCode == "G8") {
                    setTextColor(Color.parseColor("#A60000"))
                } else {
                    setTextColor(Color.parseColor("#333333"))
                }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            }
            tableRow.addView(tvPrizeName)

            // Cột kết quả cho từng tỉnh
            for (provinceDraw in provinces) {
                val rawNumbers = provinceDraw.getPrizeNumbers(prizeCode)

                val filteredNumbers = rawNumbers.map { num ->
                    when (currentFilter) {
                        "LAST2" -> if (num.length >= 2) num.takeLast(2) else num
                        "LAST3" -> if (num.length >= 3) num.takeLast(3) else num
                        else -> num
                    }
                }

                val displayText = if (filteredNumbers.isEmpty()) "-" else filteredNumbers.joinToString("\n")

                val tvNumber = TextView(context).apply {
                    text = displayText
                    gravity = Gravity.CENTER
                    setPadding(dpToPx(6), dpToPx(8), dpToPx(6), dpToPx(8))
                    setTypeface(null, Typeface.BOLD)

                    when (prizeCode) {
                        "DB" -> {
                            setTextColor(Color.parseColor("#A60000"))
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                        }
                        "G8" -> {
                            setTextColor(Color.parseColor("#A60000"))
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
                        }
                        "G7" -> {
                            setTextColor(Color.parseColor("#00008B"))
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 19f)
                        }
                        else -> {
                            setTextColor(Color.parseColor("#1A1A1A"))
                            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                        }
                    }

                    val lp = TableRow.LayoutParams().apply {
                        marginStart = dpToPx(1)
                    }
                    layoutParams = lp
                }
                tableRow.addView(tvNumber)
            }

            binding.tableLayout.addView(tableRow)
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }
}

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
import com.example.loterryapp.databinding.ActivityMienBacBinding
import com.example.loterryapp.network.ApiClient
import com.example.loterryapp.network.models.ProvinceDraw
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class MienBacActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMienBacBinding
    private val calendar = Calendar.getInstance()
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())
    private val gson = Gson()

    private var cachedDraw: ProvinceDraw? = null
    private var currentFilter: String = "ALL" // "ALL", "LAST3", "LAST2"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMienBacBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        val openDatePicker = View.OnClickListener {
            showDatePicker()
        }
        binding.ivCalendar.setOnClickListener(openDatePicker)
        binding.tvCurrentDate.setOnClickListener(openDatePicker)

        binding.btnFilterAll.setOnClickListener { setFilterMode("ALL") }
        binding.btnFilter3.setOnClickListener { setFilterMode("LAST3") }
        binding.btnFilter2.setOnClickListener { setFilterMode("LAST2") }
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

        cachedDraw?.let { renderTable(it) }
    }

    private fun loadData(dateQuery: String?) {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmptyState.visibility = View.GONE
        binding.tableLayout.removeAllViews()

        lifecycleScope.launch {
            try {
                Log.d("MienBacActivity", "Gọi API Miền Bắc, date: $dateQuery")
                val response = ApiClient.xosoService.getVietnamDraws(
                    region = "MB",
                    date = dateQuery,
                    limit = 1
                )

                binding.progressBar.visibility = View.GONE

                if (response.isSuccessful) {
                    val body = response.body()
                    val dataElement = body?.data
                    var mbDraw: ProvinceDraw? = null
                    var returnedDate: String? = null

                    if (dataElement != null && dataElement.isJsonArray) {
                        val jsonArray = dataElement.asJsonArray
                        if (jsonArray.size() > 0) {
                            val firstElement = jsonArray[0]
                            if (firstElement.isJsonObject) {
                                val firstObj = firstElement.asJsonObject
                                if (firstObj.has("provinces")) {
                                    returnedDate = firstObj.get("date")?.asString
                                    val provArray = firstObj.getAsJsonArray("provinces")
                                    if (provArray.size() > 0) {
                                        mbDraw = gson.fromJson(provArray[0], ProvinceDraw::class.java)
                                    }
                                } else {
                                    returnedDate = firstObj.get("date")?.asString
                                    mbDraw = gson.fromJson(firstObj, ProvinceDraw::class.java)
                                }
                            }
                        }
                    } else if (dataElement != null && dataElement.isJsonObject) {
                        val firstObj = dataElement.asJsonObject
                        returnedDate = firstObj.get("date")?.asString
                        if (firstObj.has("provinces")) {
                            val provArray = firstObj.getAsJsonArray("provinces")
                            if (provArray.size() > 0) {
                                mbDraw = gson.fromJson(provArray[0], ProvinceDraw::class.java)
                            }
                        } else {
                            mbDraw = gson.fromJson(firstObj, ProvinceDraw::class.java)
                        }
                    }

                    if (!returnedDate.isNullOrEmpty()) {
                        syncCalendarWithApiDate(returnedDate)
                    }

                    if (mbDraw != null) {
                        cachedDraw = mbDraw
                        renderTable(mbDraw)
                    } else {
                        cachedDraw = null
                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.tvEmptyState.text = "Chưa có kết quả ngày ${displayDateFormat.format(calendar.time)}"
                    }
                } else {
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.tvEmptyState.text = "Lỗi kết nối API (${response.code()})"
                }
            } catch (e: Exception) {
                Log.e("MienBacActivity", "Error", e)
                binding.progressBar.visibility = View.GONE
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.tvEmptyState.text = "Lỗi mạng hoặc không thể tải: ${e.localizedMessage ?: "Vui lòng thử lại"}"
            }
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
            } else {
                binding.tvCurrentDate.text = dateStr
            }
        } catch (e: Exception) {
            binding.tvCurrentDate.text = dateStr
        }
    }

    private fun renderTable(draw: ProvinceDraw) {
        binding.tableLayout.removeAllViews()
        binding.tvEmptyState.visibility = View.GONE
        binding.tableLayout.visibility = View.VISIBLE

        val context = this

        // Danh sách giải Miền Bắc
        val prizeList = listOf(
            "MA_DB" to "Mã ĐB",
            "DB" to "Đặc Biệt",
            "G1" to "Giải Nhất",
            "G2" to "Giải Nhì",
            "G3" to "Giải Ba",
            "G4" to "Giải Tư",
            "G5" to "Giải Năm",
            "G6" to "Giải Sáu",
            "G7" to "Giải Bảy"
        )

        for ((index, pair) in prizeList.withIndex()) {
            val (prizeCode, prizeLabel) = pair
            val rawNumbers = draw.getPrizeNumbers(prizeCode)

            // Bỏ qua Mã ĐB nếu không có
            if (prizeCode == "MA_DB" && rawNumbers.isEmpty()) continue

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
            val tvLabel = TextView(context).apply {
                text = prizeLabel
                gravity = Gravity.CENTER
                setPadding(dpToPx(8), dpToPx(10), dpToPx(8), dpToPx(10))
                setTypeface(null, Typeface.BOLD)
                setTextColor(if (prizeCode == "DB" || prizeCode == "G7") Color.parseColor("#A60000") else Color.parseColor("#444444"))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                val lp = TableRow.LayoutParams().apply {
                    width = dpToPx(90)
                }
                layoutParams = lp
            }
            tableRow.addView(tvLabel)

            // Cột số trúng thưởng
            val filteredNumbers = rawNumbers.map { num ->
                if (prizeCode == "MA_DB") num
                else when (currentFilter) {
                    "LAST2" -> if (num.length >= 2) num.takeLast(2) else num
                    "LAST3" -> if (num.length >= 3) num.takeLast(3) else num
                    else -> num
                }
            }

            val displayText = if (filteredNumbers.isEmpty()) "-" else {
                // Nhóm số hiển thị đẹp mắt: nếu nhiều hơn 3 số thì hiển thị 3 số mỗi dòng
                if (filteredNumbers.size > 3) {
                    filteredNumbers.chunked(3).joinToString("\n") { it.joinToString("   -   ") }
                } else {
                    filteredNumbers.joinToString("   -   ")
                }
            }

            val tvValues = TextView(context).apply {
                text = displayText
                gravity = Gravity.CENTER
                setPadding(dpToPx(8), dpToPx(10), dpToPx(8), dpToPx(10))
                setTypeface(null, Typeface.BOLD)

                when (prizeCode) {
                    "DB" -> {
                        setTextColor(Color.parseColor("#A60000"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                    }
                    "G7" -> {
                        setTextColor(Color.parseColor("#A60000"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    }
                    "G1" -> {
                        setTextColor(Color.parseColor("#00008B"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                    }
                    else -> {
                        setTextColor(Color.parseColor("#1F2937"))
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                    }
                }
                val lp = TableRow.LayoutParams().apply {
                    marginStart = dpToPx(1)
                }
                layoutParams = lp
            }
            tableRow.addView(tvValues)

            binding.tableLayout.addView(tableRow)
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }
}

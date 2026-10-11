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
import com.example.loterryapp.databinding.ActivityVietnamLotteryBinding
import com.example.loterryapp.network.ApiClient
import com.example.loterryapp.network.models.ProvinceDraw
import com.google.gson.Gson
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVietnamLotteryBinding.inflate(layoutInflater)
        setContentView(binding.root)

        region = intent.getStringExtra("REGION") ?: "MN"
        regionTitle = intent.getStringExtra("TITLE") ?: "Xổ Số Miền Nam"
        
        binding.tvToolbarTitle.text = regionTitle
        
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
        binding.tableLayout.removeAllViews()

        // Huỷ request cũ để kết quả của ngày trước không ghi đè ngày vừa chọn
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            try {
                Log.d("VietnamLottery", "Fetching data for region: $region, date: $dateQuery")
                val response = ApiClient.xosoService.getVietnamDraws(
                    region = region,
                    date = dateQuery,
                    limit = 1
                )

                binding.progressBar.visibility = View.GONE
                
                if (response.isSuccessful) {
                    val body = response.body()
                    Log.d("VietnamLottery", "API Response: ${gson.toJson(body)}")
                    
                    if (body?.success == true) {
                        val dataElement = body.data
                        val provincesList = mutableListOf<ProvinceDraw>()
                        var returnedDate: String? = null

                        if (dataElement != null && dataElement.isJsonArray) {
                            val jsonArray = dataElement.asJsonArray
                            if (jsonArray.size() > 0) {
                                val firstElement = jsonArray[0]
                                if (firstElement.isJsonObject) {
                                    val firstObj = firstElement.asJsonObject
                                    if (firstObj.has("provinces")) {
                                        returnedDate = firstObj.get("date")?.asString
                                        firstObj.getAsJsonArray("provinces").forEach {
                                            provincesList.add(gson.fromJson(it, ProvinceDraw::class.java))
                                        }
                                    } else {
                                        returnedDate = firstObj.get("date")?.asString
                                        jsonArray.forEach {
                                            provincesList.add(gson.fromJson(it, ProvinceDraw::class.java))
                                        }
                                    }
                                }
                            }
                        } else if (dataElement != null && dataElement.isJsonObject) {
                            val jsonObj = dataElement.asJsonObject
                            returnedDate = jsonObj.get("date")?.asString
                            if (jsonObj.has("provinces")) {
                                jsonObj.getAsJsonArray("provinces").forEach {
                                    provincesList.add(gson.fromJson(it, ProvinceDraw::class.java))
                                }
                            }
                        }

                        if (!returnedDate.isNullOrEmpty()) syncCalendarWithApiDate(returnedDate)

                        if (provincesList.isNotEmpty()) {
                            cachedProvinces = provincesList
                            renderTable(provincesList)
                        } else {
                            binding.tvEmptyState.apply {
                                visibility = View.VISIBLE
                                text = "Chưa có kết quả cho khu vực này"
                            }
                        }
                    } else {
                        binding.tvEmptyState.apply {
                            visibility = View.VISIBLE
                            text = "API trả về thất bại: ${body?.message ?: "Không rõ nguyên nhân"}"
                        }
                    }
                } else {
                    binding.tvEmptyState.apply {
                        visibility = View.VISIBLE
                        text = "Lỗi kết nối API: ${response.code()}"
                    }
                }
            } catch (e: CancellationException) {
                throw e // Request bị huỷ do người dùng đổi ngày: không hiển thị lỗi
            } catch (e: Exception) {
                Log.e("VietnamLottery", "Error: ", e)
                binding.progressBar.visibility = View.GONE
                binding.tvEmptyState.apply {
                    visibility = View.VISIBLE
                    text = "Lỗi: ${e.message}"
                }
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
            }
        } catch (e: Exception) {}
    }

    private fun renderTable(provinces: List<ProvinceDraw>) {
        binding.tableLayout.removeAllViews()
        binding.tableLayout.visibility = View.VISIBLE

        val headerRow = TableRow(this).apply {
            setBackgroundColor(Color.parseColor("#FFFFE0"))
            setPadding(0, 0, 0, dpToPx(1))
        }

        headerRow.addView(createCell("Giải", true))
        provinces.forEach { 
            val title = if (it.getProvinceCode().isNotEmpty()) "${it.getProvinceName()}\n${it.getProvinceCode()}" else it.getProvinceName()
            headerRow.addView(createCell(title, true))
        }
        binding.tableLayout.addView(headerRow)

        val prizeDefinitions = if (region == "MB") {
            listOf("DB" to "ĐB", "G1" to "G.1", "G2" to "G.2", "G3" to "G.3", "G4" to "G.4", "G5" to "G.5", "G6" to "G.6", "G7" to "G.7")
        } else {
            listOf("G8" to "G.8", "G7" to "G.7", "G6" to "G.6", "G5" to "G.5", "G4" to "G.4", "G3" to "G.3", "G2" to "G.2", "G1" to "G.1", "DB" to "ĐB")
        }

        prizeDefinitions.forEachIndexed { index, (prizeCode, prizeLabel) ->
            val tableRow = TableRow(this).apply {
                setBackgroundColor(if (prizeCode == "DB") Color.parseColor("#FFFBEB") else if (index % 2 == 0) Color.WHITE else Color.parseColor("#F9F9F9"))
                setPadding(0, 0, 0, dpToPx(1))
            }

            tableRow.addView(createCell(prizeLabel, true, prizeCode))
            
            provinces.forEach { provinceDraw ->
                val rawNumbers = provinceDraw.getPrizeNumbers(prizeCode)
                val filtered = rawNumbers.joinToString("\n") { 
                    when(currentFilter) {
                        "LAST2" -> if (it.length >= 2) it.takeLast(2) else it
                        "LAST3" -> if (it.length >= 3) it.takeLast(3) else it
                        else -> it
                    }
                }
                tableRow.addView(createCell(if (filtered.isEmpty()) "-" else filtered, false, prizeCode))
            }
            binding.tableLayout.addView(tableRow)
        }
    }

    private fun createCell(text: String, isBold: Boolean, prizeCode: String? = null): TextView {
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setPadding(dpToPx(6), dpToPx(8), dpToPx(6), dpToPx(8))
            if (isBold) setTypeface(null, Typeface.BOLD)
            
            setTextColor(when (prizeCode) {
                "DB", "G8" -> Color.parseColor("#A60000")
                "G7" -> if (region != "MB") Color.parseColor("#00008B") else Color.parseColor("#1A1A1A")
                else -> Color.parseColor("#1A1A1A")
            })
            
            setTextSize(TypedValue.COMPLEX_UNIT_SP, when (prizeCode) {
                "DB", "G8" -> 22f
                "G7" -> 19f
                else -> 16f
            })

            layoutParams = TableRow.LayoutParams().apply { marginStart = dpToPx(1) }
        }
    }

    private fun dpToPx(dp: Int): Int = (dp * resources.displayMetrics.density).toInt()
}

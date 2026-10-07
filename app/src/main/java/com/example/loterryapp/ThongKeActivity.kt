package com.example.loterryapp

import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.loterryapp.databinding.ActivityThongKeBinding
import com.example.loterryapp.network.ApiClient
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.launch

class ThongKeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThongKeBinding
    private var currentTab: String = "HOT" // "HOT" or "GAP"
    private var currentRegion: String = "MB" // "MB", "MT", or "MN"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityThongKeBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setupListeners()
        updateRegionUI()
        updateTabUI()
        loadStatsData()
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener {
            finish()
        }

        binding.btnRegionMB.setOnClickListener {
            if (currentRegion != "MB") {
                currentRegion = "MB"
                updateRegionUI()
                updateTabUI()
                loadStatsData()
            }
        }

        binding.btnRegionMT.setOnClickListener {
            if (currentRegion != "MT") {
                currentRegion = "MT"
                updateRegionUI()
                updateTabUI()
                loadStatsData()
            }
        }

        binding.btnRegionMN.setOnClickListener {
            if (currentRegion != "MN") {
                currentRegion = "MN"
                updateRegionUI()
                updateTabUI()
                loadStatsData()
            }
        }

        binding.btnTabHot.setOnClickListener {
            if (currentTab != "HOT") {
                currentTab = "HOT"
                updateTabUI()
                loadStatsData()
            }
        }

        binding.btnTabGap.setOnClickListener {
            if (currentTab != "GAP") {
                currentTab = "GAP"
                updateTabUI()
                loadStatsData()
            }
        }
    }

    private fun updateRegionUI() {
        val activeBg = Color.parseColor("#0D9488")
        val inactiveBg = Color.parseColor("#334155")
        val activeText = Color.WHITE
        val inactiveText = Color.parseColor("#94A3B8")

        binding.btnRegionMB.backgroundTintList = ColorStateList.valueOf(if (currentRegion == "MB") activeBg else inactiveBg)
        binding.btnRegionMB.setTextColor(if (currentRegion == "MB") activeText else inactiveText)

        binding.btnRegionMT.backgroundTintList = ColorStateList.valueOf(if (currentRegion == "MT") activeBg else inactiveBg)
        binding.btnRegionMT.setTextColor(if (currentRegion == "MT") activeText else inactiveText)

        binding.btnRegionMN.backgroundTintList = ColorStateList.valueOf(if (currentRegion == "MN") activeBg else inactiveBg)
        binding.btnRegionMN.setTextColor(if (currentRegion == "MN") activeText else inactiveText)
    }

    private fun updateTabUI() {
        val activeBg = Color.parseColor("#C62828")
        val inactiveBg = Color.parseColor("#334155")
        val activeText = Color.WHITE
        val inactiveText = Color.parseColor("#94A3B8")

        if (currentTab == "HOT") {
            binding.btnTabHot.backgroundTintList = ColorStateList.valueOf(activeBg)
            binding.btnTabHot.setTextColor(activeText)
            binding.btnTabGap.backgroundTintList = ColorStateList.valueOf(inactiveBg)
            binding.btnTabGap.setTextColor(inactiveText)
            binding.tvGridTitle.text = "🔥 Số Nóng - Hay Về Nhất (${getRegionName(currentRegion)})"
        } else {
            binding.btnTabGap.backgroundTintList = ColorStateList.valueOf(activeBg)
            binding.btnTabGap.setTextColor(activeText)
            binding.btnTabHot.backgroundTintList = ColorStateList.valueOf(inactiveBg)
            binding.btnTabHot.setTextColor(inactiveText)
            binding.tvGridTitle.text = "❄️ Lô Gan - Lâu Chưa Về (${getRegionName(currentRegion)})"
        }
    }

    private fun getRegionName(code: String): String {
        return when (code) {
            "MB" -> "Miền Bắc"
            "MT" -> "Miền Trung"
            "MN" -> "Miền Nam"
            else -> "Miền Bắc"
        }
    }

    private fun loadStatsData() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmptyState.visibility = View.GONE
        binding.gridTopNumbers.removeAllViews()
        binding.tableStats.removeAllViews()

        lifecycleScope.launch {
            try {
                Log.d("ThongKeActivity", "Calling API for region: $currentRegion, tab: $currentTab")
                val response = if (currentTab == "HOT") {
                    ApiClient.xosoService.getHotNumbers(region = currentRegion, limit = 20)
                } else {
                    ApiClient.xosoService.getGapStats(region = currentRegion)
                }

                binding.progressBar.visibility = View.GONE

                if (response.isSuccessful) {
                    val root = response.body()?.asJsonObject
                    val dataElement = root?.get("data")

                    val items = mutableListOf<StatItem>()

                    if (dataElement != null && dataElement.isJsonArray) {
                        for (item in dataElement.asJsonArray) {
                            if (item.isJsonObject) {
                                val obj = item.asJsonObject
                                val num = obj.get("number")?.asString 
                                    ?: obj.get("num")?.asString 
                                    ?: obj.get("so")?.asString 
                                    ?: ""
                                
                                val value = if (currentTab == "HOT") {
                                    val count = obj.get("count")?.asString 
                                        ?: obj.get("frequency")?.asString 
                                        ?: obj.get("times")?.asString 
                                        ?: ""
                                    if (count.isNotEmpty()) "$count lần" else ""
                                } else {
                                    val gap = obj.get("gap")?.asString 
                                        ?: obj.get("days")?.asString 
                                        ?: obj.get("gan")?.asString 
                                        ?: ""
                                    if (gap.isNotEmpty()) "$gap ngày" else ""
                                }

                                val lastDate = obj.get("last_seen")?.asString 
                                    ?: obj.get("last_date")?.asString 
                                    ?: obj.get("date")?.asString 
                                    ?: ""

                                if (num.isNotEmpty()) {
                                    items.add(StatItem(num, value, lastDate))
                                }
                            }
                        }
                    }

                    if (items.isNotEmpty()) {
                        renderTopCards(items.take(9))
                        renderStatsTable(items)
                    } else {
                        binding.tvEmptyState.visibility = View.VISIBLE
                        binding.tvEmptyState.text = "Chưa có dữ liệu thống kê cho khu vực này"
                    }
                } else {
                    binding.tvEmptyState.visibility = View.VISIBLE
                    binding.tvEmptyState.text = "Lỗi kết nối API (${response.code()})"
                }
            } catch (e: Exception) {
                Log.e("ThongKeActivity", "Error loading stats", e)
                binding.progressBar.visibility = View.GONE
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.tvEmptyState.text = "Lỗi mạng hoặc không thể tải dữ liệu: ${e.localizedMessage ?: "Vui lòng thử lại"}"
            }
        }
    }

    private fun renderTopCards(topItems: List<StatItem>) {
        binding.gridTopNumbers.removeAllViews()

        val chunked = topItems.chunked(3)
        chunked.forEach { rowItems ->
            val rowLayout = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
                )
            }

            rowItems.forEachIndexed { itemIndex, item ->
                val globalIndex = topItems.indexOf(item)

                val cardView = MaterialCardView(this).apply {
                    radius = dpToPx(12).toFloat()
                    cardElevation = dpToPx(2).toFloat()
                    setCardBackgroundColor(Color.parseColor("#1E293B"))
                    strokeColor = Color.parseColor("#334155")
                    strokeWidth = dpToPx(1)

                    val param = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                        setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                    }
                    layoutParams = param
                }

                val container = FrameLayout(this).apply {
                    setPadding(dpToPx(6), dpToPx(10), dpToPx(6), dpToPx(10))
                }

                // TOP 1 / TOP 2 / TOP 3 Badge
                if (globalIndex < 3) {
                    val badgeColor = when (globalIndex) {
                        0 -> Color.parseColor("#DC2626")
                        1 -> Color.parseColor("#E11D48")
                        else -> Color.parseColor("#F43F5E")
                    }

                    val badge = TextView(this).apply {
                        text = "TOP ${globalIndex + 1}"
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 9f)
                        setTypeface(null, Typeface.BOLD)
                        setBackgroundColor(badgeColor)
                        setPadding(dpToPx(4), dpToPx(1), dpToPx(4), dpToPx(1))

                        val badgeLp = FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT
                        ).apply {
                            gravity = Gravity.TOP or Gravity.END
                        }
                        layoutParams = badgeLp
                    }
                    container.addView(badge)
                }

                val contentLayout = LinearLayout(this).apply {
                    orientation = LinearLayout.VERTICAL
                    gravity = Gravity.CENTER
                }

                // Number
                val tvNumber = TextView(this).apply {
                    text = item.number
                    setTextColor(Color.WHITE)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                }

                // Subtitle
                val tvValue = TextView(this).apply {
                    text = item.value
                    setTextColor(Color.parseColor("#94A3B8"))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    gravity = Gravity.CENTER
                    setPadding(0, dpToPx(2), 0, 0)
                }

                contentLayout.addView(tvNumber)
                contentLayout.addView(tvValue)
                container.addView(contentLayout)

                cardView.addView(container)
                rowLayout.addView(cardView)
            }

            // Fill empty slots if row has < 3 items
            if (rowItems.size < 3) {
                for (i in 0 until (3 - rowItems.size)) {
                    val dummyView = View(this).apply {
                        layoutParams = LinearLayout.LayoutParams(0, 0, 1f).apply {
                            setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4))
                        }
                    }
                    rowLayout.addView(dummyView)
                }
            }

            binding.gridTopNumbers.addView(rowLayout)
        }
    }

    private fun renderStatsTable(items: List<StatItem>) {
        binding.tableStats.removeAllViews()
        binding.tvEmptyState.visibility = View.GONE

        val context = this

        // Header Row
        val headerRow = TableRow(context).apply {
            setBackgroundColor(Color.parseColor("#0F172A"))
            val params = TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(1)
            }
            layoutParams = params
        }

        headerRow.addView(createCell("STT", isHeader = true, widthDp = 45))
        headerRow.addView(createCell("Bộ Số", isHeader = true, widthDp = 65))
        headerRow.addView(createCell(if (currentTab == "HOT") "Số Lần Về" else "Số Ngày Gan", isHeader = true))
        headerRow.addView(createCell("Lần Về Gần Nhất", isHeader = true))
        binding.tableStats.addView(headerRow)

        // Data Rows
        for ((index, item) in items.withIndex()) {
            val rowBg = if (index % 2 == 0) Color.parseColor("#1E293B") else Color.parseColor("#0F172A")

            val row = TableRow(context).apply {
                setBackgroundColor(rowBg)
                val params = TableRow.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT,
                    TableRow.LayoutParams.WRAP_CONTENT
                ).apply {
                    bottomMargin = dpToPx(1)
                }
                layoutParams = params
            }

            val c1 = createCell("${index + 1}", widthDp = 45, textColor = Color.parseColor("#94A3B8"))
            val c2 = createCell(item.number, widthDp = 65, textColor = Color.parseColor("#EF4444"), isBold = true, textSizeSp = 17f)
            val c3 = createCell(item.value, isBold = true, textColor = Color.parseColor("#2DD4BF"))
            val c4 = createCell(if (item.lastDate.isNotEmpty()) item.lastDate else "-", textColor = Color.parseColor("#CBD5E1"))

            row.addView(c1)
            row.addView(c2)
            row.addView(c3)
            row.addView(c4)
            binding.tableStats.addView(row)
        }
    }

    private fun createCell(
        text: String,
        isHeader: Boolean = false,
        widthDp: Int? = null,
        textColor: Int = Color.WHITE,
        isBold: Boolean = false,
        textSizeSp: Float = 13f
    ): TextView {
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setPadding(dpToPx(6), dpToPx(10), dpToPx(6), dpToPx(10))
            if (isHeader || isBold) setTypeface(null, Typeface.BOLD)
            setTextColor(if (isHeader) Color.parseColor("#E2E8F0") else textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isHeader) 12f else textSizeSp)

            val lp = TableRow.LayoutParams().apply {
                marginStart = dpToPx(1)
                if (widthDp != null) width = dpToPx(widthDp)
            }
            layoutParams = lp
        }
    }

    private fun dpToPx(dp: Int): Int {
        val density = resources.displayMetrics.density
        return (dp * density).toInt()
    }

    data class StatItem(
        val number: String,
        val value: String,
        val lastDate: String
    )
}

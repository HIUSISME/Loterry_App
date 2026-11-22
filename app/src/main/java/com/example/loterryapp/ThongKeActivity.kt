package com.example.loterryapp

import android.content.res.ColorStateList
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.example.loterryapp.databinding.ActivityThongKeBinding
import com.example.loterryapp.network.ApiClient
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

class ThongKeActivity : AppCompatActivity() {

    private lateinit var binding: ActivityThongKeBinding
    private var currentTab: String = "HOT" // "HOT" or "GAP"
    private var currentRegion: String = "MB" // "MB", "MT", or "MN"
    private var loadJob: Job? = null

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

    private fun color(@ColorRes id: Int): Int = ContextCompat.getColor(this, id)

    private fun updateRegionUI() {
        val activeBg = color(R.color.header_red)
        val inactiveBg = color(R.color.surface_variant)
        val activeText = color(R.color.on_brand)
        val inactiveText = color(R.color.text_secondary)

        binding.btnRegionMB.backgroundTintList = ColorStateList.valueOf(if (currentRegion == "MB") activeBg else inactiveBg)
        binding.btnRegionMB.setTextColor(if (currentRegion == "MB") activeText else inactiveText)

        binding.btnRegionMT.backgroundTintList = ColorStateList.valueOf(if (currentRegion == "MT") activeBg else inactiveBg)
        binding.btnRegionMT.setTextColor(if (currentRegion == "MT") activeText else inactiveText)

        binding.btnRegionMN.backgroundTintList = ColorStateList.valueOf(if (currentRegion == "MN") activeBg else inactiveBg)
        binding.btnRegionMN.setTextColor(if (currentRegion == "MN") activeText else inactiveText)
    }

    private fun updateTabUI() {
        // Tab dùng kiểu tonal (nền đỏ nhạt) để phân biệt với nút miền (nền đỏ đậm)
        val activeBg = color(R.color.brand_container)
        val inactiveBg = color(R.color.surface_variant)
        val activeText = color(R.color.on_brand_container)
        val inactiveText = color(R.color.text_secondary)

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

        // Huỷ request cũ để dữ liệu của miền/tab trước không ghi đè lựa chọn hiện tại
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
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
            } catch (e: CancellationException) {
                throw e // Request bị huỷ do người dùng đổi miền/tab: không hiển thị lỗi
            } catch (e: Exception) {
                Log.e("ThongKeActivity", "Error loading stats", e)
                binding.progressBar.visibility = View.GONE
                binding.tvEmptyState.visibility = View.VISIBLE
                binding.tvEmptyState.text = com.example.loterryapp.data.DrawRepository.errorMessage(e)
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
                    cardElevation = dpToPx(1).toFloat()
                    setCardBackgroundColor(color(R.color.surface))
                    strokeColor = color(R.color.card_stroke)
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
                    // TOP 1 nền đỏ đậm, TOP 2-3 nền đỏ nhạt (tonal)
                    val badgeBg = if (globalIndex == 0) color(R.color.header_red) else color(R.color.brand_container)
                    val badgeText = if (globalIndex == 0) color(R.color.on_brand) else color(R.color.on_brand_container)

                    val badge = TextView(this).apply {
                        text = "TOP ${globalIndex + 1}"
                        setTextColor(badgeText)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 10f)
                        setTypeface(null, Typeface.BOLD)
                        background = GradientDrawable().apply {
                            setColor(badgeBg)
                            cornerRadius = dpToPx(6).toFloat()
                        }
                        setPadding(dpToPx(6), dpToPx(1), dpToPx(6), dpToPx(1))

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
                    // Chừa chỗ cho nhãn TOP để không đè lên con số
                    setPadding(0, dpToPx(12), 0, 0)
                }

                // Number
                val tvNumber = TextView(this).apply {
                    text = item.number
                    setTextColor(color(R.color.result_red))
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
                    setTypeface(null, Typeface.BOLD)
                    gravity = Gravity.CENTER
                }

                // Subtitle
                val tvValue = TextView(this).apply {
                    text = item.value
                    setTextColor(color(R.color.text_secondary))
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
            setBackgroundColor(color(R.color.surface_variant))
            val params = TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = dpToPx(1)
            }
            layoutParams = params
        }

        headerRow.addView(createCell("STT", isHeader = true, widthDp = 45, firstColumn = true))
        headerRow.addView(createCell("Bộ Số", isHeader = true, widthDp = 65))
        headerRow.addView(createCell(if (currentTab == "HOT") "Số Lần Về" else "Số Ngày Gan", isHeader = true))
        headerRow.addView(createCell("Lần Về Gần Nhất", isHeader = true))
        binding.tableStats.addView(headerRow)

        // Data Rows
        for ((index, item) in items.withIndex()) {
            val rowBg = if (index % 2 == 0) color(R.color.surface) else color(R.color.bg_screen)
            val isLast = index == items.lastIndex

            val row = TableRow(context).apply {
                setBackgroundColor(rowBg)
                val params = TableRow.LayoutParams(
                    TableRow.LayoutParams.MATCH_PARENT,
                    TableRow.LayoutParams.WRAP_CONTENT
                ).apply {
                    // Dòng cuối không cần kẻ dưới: viền thẻ đã bao quanh
                    bottomMargin = if (isLast) 0 else dpToPx(1)
                }
                layoutParams = params
            }

            val c1 = createCell("${index + 1}", widthDp = 45, textColor = color(R.color.text_secondary), firstColumn = true)
            val c2 = createCell(item.number, widthDp = 65, textColor = color(R.color.result_red), isBold = true, textSizeSp = 17f)
            val c3 = createCell(item.value, isBold = true, textColor = color(R.color.icon_teal))
            val c4 = createCell(if (item.lastDate.isNotEmpty()) item.lastDate else "-", textColor = color(R.color.text_secondary))

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
        textColor: Int = color(R.color.text_primary),
        isBold: Boolean = false,
        textSizeSp: Float = 13f,
        firstColumn: Boolean = false
    ): TextView {
        return TextView(this).apply {
            this.text = text
            gravity = Gravity.CENTER
            setPadding(dpToPx(6), dpToPx(10), dpToPx(6), dpToPx(10))
            if (isHeader || isBold) setTypeface(null, Typeface.BOLD)
            setTextColor(if (isHeader) color(R.color.text_secondary) else textColor)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, if (isHeader) 12f else textSizeSp)

            val lp = TableRow.LayoutParams().apply {
                // Kẻ dọc 1dp giữa các cột (cột đầu sát viền thẻ nên không cần)
                marginStart = if (firstColumn) 0 else dpToPx(1)
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

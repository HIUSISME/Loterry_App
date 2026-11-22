package com.example.loterryapp

import android.app.DatePickerDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.AbsoluteSizeSpan
import android.text.style.ForegroundColorSpan
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.TextViewCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.loterryapp.data.DrawRepository
import com.example.loterryapp.databinding.ActivityVietlottBinding
import com.example.loterryapp.network.models.VietlottDraw
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class VietlottActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVietlottBinding
    private var type: String = "MEGA645"
    private var title: String = "Mega 6/45"
    private val repository by lazy { DrawRepository(this) }
    /** Ngày đang xem; null = các kỳ mới nhất */
    private var selectedDate: LocalDate? = null
    private var loadJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVietlottBinding.inflate(layoutInflater)
        setContentView(binding.root)

        type = intent.getStringExtra("TYPE") ?: "MEGA645"
        title = intent.getStringExtra("TITLE") ?: "Mega 6/45"

        binding.tvToolbarTitle.text = title
        binding.ivBack.setOnClickListener { finish() }

        binding.rvVietlott.layoutManager = LinearLayoutManager(this)

        binding.ivCalendar.setOnClickListener { showDatePicker() }
        binding.btnShowLatest.setOnClickListener { loadData(date = null) }

        loadData(date = null)
    }

    /** Chọn ngày quay (không cho chọn ngày trong tương lai) */
    private fun showDatePicker() {
        val initial = selectedDate ?: LocalDate.now()
        DatePickerDialog(
            this,
            { _, year, month, dayOfMonth -> loadData(LocalDate.of(year, month + 1, dayOfMonth)) },
            initial.year,
            initial.monthValue - 1,
            initial.dayOfMonth
        ).apply {
            datePicker.maxDate = System.currentTimeMillis()
        }.show()
    }

    /** date = null: các kỳ mới nhất; date = một ngày: các kỳ quay của ngày đó */
    private fun loadData(date: LocalDate?) {
        selectedDate = date
        val dateQuery = date?.toString() // yyyy-MM-dd

        binding.layoutDateFilter.visibility = if (date == null) View.GONE else View.VISIBLE
        binding.tvSelectedDate.text = date?.let { "📅 Ngày ${it.format(DISPLAY_DATE)}" }
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmptyState.visibility = View.GONE
        binding.rvVietlott.adapter = VietlottAdapter(emptyList(), type)

        // Huỷ lần tải trước để kết quả của ngày cũ không ghi đè ngày vừa chọn
        loadJob?.cancel()
        loadJob = lifecycleScope.launch {
            // 1. Hiện ngay các kỳ quay đã lưu trên máy (nếu có), không cần chờ mạng
            val saved = repository.getSavedVietlottDraws(type, LIMIT, dateQuery)
            val showedSaved = showDraws(saved)
            // Đã có dữ liệu để xem thì ẩn vòng xoay tải, tránh đẩy nội dung xuống rồi giật lên khi API trả về
            if (showedSaved) binding.progressBar.visibility = View.GONE
            // Ngày đã qua và đã có đủ kết quả: không cần gọi API
            if (repository.isFinalVietlott(type, dateQuery, saved, LIMIT)) return@launch

            // 2. Gọi API để lấy kỳ mới; repository tự lưu những kỳ mới / thay đổi vào database
            try {
                val fresh = repository.fetchVietlottDraws(type, LIMIT, dateQuery)
                binding.progressBar.visibility = View.GONE
                if (!showDraws(fresh) && !showedSaved) {
                    showEmptyState(if (date == null) "Chưa có kết quả cho $title" else noDrawMessage(date))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("VietlottActivity", "Error loading draws", e)
                binding.progressBar.visibility = View.GONE
                if (showedSaved) {
                    Toast.makeText(this@VietlottActivity, "Không cập nhật được, đang hiện kết quả đã lưu", Toast.LENGTH_SHORT).show()
                } else {
                    showEmptyState(DrawRepository.errorMessage(e))
                }
            }
        }
    }

    /** Trả về false nếu không có gì để hiện */
    private fun showDraws(draws: List<VietlottDraw>): Boolean {
        if (draws.isEmpty()) return false
        binding.tvEmptyState.visibility = View.GONE
        binding.rvVietlott.adapter = VietlottAdapter(draws, type)
        return true
    }

    private fun showEmptyState(message: String) {
        binding.rvVietlott.adapter = VietlottAdapter(emptyList(), type)
        binding.tvEmptyState.visibility = View.VISIBLE
        binding.tvEmptyState.text = message
    }

    /** VD: "Không có kỳ quay Mega 6/45 ngày 10-10-2026. Mega 6/45 quay vào Thứ Tư, Thứ Sáu và Chủ Nhật." */
    private fun noDrawMessage(date: LocalDate): String {
        val message = "Không có kỳ quay $title ngày ${date.format(DISPLAY_DATE)}."
        val schedule = DRAW_DAYS[type] ?: return message
        return "$message\n$title quay vào $schedule."
    }

    companion object {
        /** Số kỳ quay gần nhất hiển thị */
        private const val LIMIT = 20
        private val DISPLAY_DATE: DateTimeFormatter = DateTimeFormatter.ofPattern("dd-MM-yyyy")

        /** Lịch quay của các loại có ngày quay cố định */
        private val DRAW_DAYS = mapOf(
            "MEGA645" to "Thứ Tư, Thứ Sáu và Chủ Nhật",
            "POWER655" to "Thứ Ba, Thứ Năm và Thứ Bảy",
            "MAX3D" to "Thứ Hai, Thứ Tư và Thứ Sáu",
            "MAX3DPRO" to "Thứ Ba, Thứ Năm và Thứ Bảy"
        )
    }

    /** Mega 6/45 dùng thẻ kết quả chi tiết; các loại Vietlott khác dùng danh sách đơn giản */
    class VietlottAdapter(
        private val draws: List<VietlottDraw>,
        private val type: String
    ) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

        class SimpleViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvDate: TextView = view.findViewById(android.R.id.text1)
            val tvResult: TextView = view.findViewById(android.R.id.text2)
        }

        class JackpotViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvDrawTitle)
            val layoutBalls: LinearLayout = view.findViewById(R.id.layoutBalls)
            val tvJackpot: TextView = view.findViewById(R.id.tvJackpot)
            val layoutPrizeTable: LinearLayout = view.findViewById(R.id.layoutPrizeTable)
            val tvFooter: TextView = view.findViewById(R.id.tvDrawFooter)
        }

        /** Cấu hình thẻ của trò đang xem; null = dùng danh sách đơn giản */
        private val jackpotGame = JACKPOT_GAMES[type]

        class Max3dProViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvDrawTitle)
            val layoutPrizeTable: LinearLayout = view.findViewById(R.id.layoutPrizeTable)
        }

        class KenoViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvDrawTitle)
            val layoutBalls: LinearLayout = view.findViewById(R.id.layoutBalls)
            val layoutStats: LinearLayout = view.findViewById(R.id.layoutStats)
        }

        class Max3dViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvTitle: TextView = view.findViewById(R.id.tvDrawTitle)
            val layoutMainTable: LinearLayout = view.findViewById(R.id.layoutMainTable)
            val layoutPlusTable: LinearLayout = view.findViewById(R.id.layoutPlusTable)
        }

        override fun getItemViewType(position: Int) = when {
            jackpotGame != null -> VIEW_TYPE_JACKPOT
            type == "MAX3DPRO" -> VIEW_TYPE_MAX3DPRO
            type == "MAX3D" -> VIEW_TYPE_MAX3D
            type == "KENO" -> VIEW_TYPE_KENO
            else -> VIEW_TYPE_SIMPLE
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
            val inflater = LayoutInflater.from(parent.context)
            return when (viewType) {
                VIEW_TYPE_JACKPOT -> JackpotViewHolder(inflater.inflate(R.layout.item_jackpot_draw, parent, false))
                VIEW_TYPE_MAX3DPRO -> Max3dProViewHolder(inflater.inflate(R.layout.item_max3dpro_draw, parent, false))
                VIEW_TYPE_KENO -> KenoViewHolder(inflater.inflate(R.layout.item_keno_draw, parent, false))
                VIEW_TYPE_MAX3D -> Max3dViewHolder(inflater.inflate(R.layout.item_max3d_draw, parent, false))
                else -> SimpleViewHolder(inflater.inflate(android.R.layout.simple_list_item_2, parent, false))
            }
        }

        override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
            val draw = draws[position]
            when (holder) {
                is JackpotViewHolder -> bindJackpotGame(holder, draw, jackpotGame!!)
                is Max3dProViewHolder -> bindMax3dPro(holder, draw)
                is KenoViewHolder -> bindKeno(holder, draw)
                is Max3dViewHolder -> bindMax3d(holder, draw)
                is SimpleViewHolder -> {
                    // "2026-10-15T00:00:00.000Z" -> "15-10-2026"
                    val date = draw.date?.take(10)?.let { runCatching { LocalDate.parse(it).format(DISPLAY_DATE) }.getOrNull() } ?: draw.date
                    holder.tvDate.text = "Kỳ quay #${draw.drawNumber} - $date"
                    holder.tvResult.text = formatSimpleResults(draw)
                    holder.tvResult.setTextColor(colorOf(holder.itemView.context, R.color.result_red))
                    holder.tvResult.textSize = 18f
                }
            }
        }

        override fun getItemCount() = draws.size

        /**
         * Max 3D / Max 3D Pro: mỗi giải một dòng ("Đặc biệt: 897 - 099");
         * Keno: 20 số chia 2 dòng; các loại khác: các số nối bằng " - "
         */
        private fun formatSimpleResults(draw: VietlottDraw): String {
            val groups = draw.resultsByPrize().filter { it.first.isNotEmpty() }
            return when {
                groups.size > 1 -> groups.joinToString("\n") { (code, values) ->
                    "${PRIZE_LABELS[code] ?: code}: ${values.joinToString(" - ")}"
                }
                draw.numbers().size > 10 -> draw.numbers().chunked(10).joinToString("\n") { it.joinToString(" ") }
                draw.numbers().isNotEmpty() -> draw.numbers().joinToString(" - ")
                else -> "N/A"
            }
        }

        // ---------- Thẻ Max 3D (kèm Max 3D+) ----------

        private fun bindMax3d(holder: Max3dViewHolder, draw: VietlottDraw) {
            val context = holder.itemView.context
            holder.tvTitle.text = "XỔ SỐ MAX 3D ${formatDrawDay(draw.date)}".trim()
            val byPrize = draw.resultsByPrize().toMap()

            // Bảng chính: Giải | Dãy số trúng | SL | Giá trị
            val main = holder.layoutMainTable
            main.removeAllViews()
            main.addView(createGridRow(context, MAX3D_MAIN_WEIGHTS, listOf(
                createCellText(context, "Giải", 13f, colorOf(context, R.color.text_primary), bold = true),
                createCellText(context, "Dãy số trúng", 13f, colorOf(context, R.color.text_primary), bold = true),
                createCellText(context, "SL", 13f, colorOf(context, R.color.text_primary), bold = true),
                createCellText(context, "Giá trị", 13f, colorOf(context, R.color.text_primary), bold = true)
            ), background = colorOf(context, R.color.surface_variant)))

            for ((code, label, perLine) in MAX3D_MAIN_ROWS) {
                val (amount, count) = draw.getPrize("MAX3D_$code")
                val isSpecial = code == "DB"
                main.addView(createDivider(context))
                main.addView(createGridRow(context, MAX3D_MAIN_WEIGHTS, listOf(
                    createCellText(context, label, 13f, colorOf(context, R.color.text_primary), bold = true).apply { setTypeface(null, Typeface.BOLD_ITALIC) },
                    createNumberGrid(
                        context,
                        byPrize[code].orEmpty(),
                        perLine = perLine,
                        sizeSp = if (isSpecial) 26f else 16f,
                        color = if (isSpecial) colorOf(context, R.color.result_red) else colorOf(context, R.color.text_primary)
                    ),
                    createCellText(context, count?.let { formatMoney(it.toLong()) } ?: "-", 13f, colorOf(context, R.color.text_primary)),
                    createMoneyCell(context, amount?.takeIf { it > 0 }?.let { "${formatMoney(it)}đ" })
                )))
            }

            // Bảng Max 3D+: Giải | Kết quả | SL giải | Giá trị (đ)
            val plus = holder.layoutPlusTable
            plus.removeAllViews()
            plus.addView(createGridRow(context, MAX3D_PLUS_WEIGHTS, listOf(
                createCellText(context, "GIẢI", 11f, colorOf(context, R.color.text_primary), bold = true),
                createCellText(context, "KẾT QUẢ", 11f, colorOf(context, R.color.text_primary), bold = true),
                createCellText(context, "SL GIẢI", 11f, colorOf(context, R.color.text_primary), bold = true),
                createCellText(context, "GIÁ TRỊ (Đ)", 11f, colorOf(context, R.color.text_primary), bold = true)
            ), background = colorOf(context, R.color.surface_variant)))

            for ((code, description) in MAX3D_PLUS_ROWS) {
                val (amount, count) = draw.getPrize("MAX3D+_$code")
                plus.addView(createDivider(context))
                plus.addView(createGridRow(context, MAX3D_PLUS_WEIGHTS, listOf(
                    createCellText(context, code, 13f, colorOf(context, R.color.text_primary), bold = true),
                    createCellText(context, description, 12f, colorOf(context, R.color.text_secondary)).apply {
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        setPadding(dp(context, 10), 0, dp(context, 6), 0)
                    },
                    createCellText(context, count?.let { formatMoney(it.toLong()) } ?: "-", 13f, colorOf(context, R.color.text_secondary)),
                    createMoneyCell(context, amount?.takeIf { it > 0 }?.let { formatMoney(it) })
                )))
            }
        }

        /** Một hàng của bảng nhiều cột theo tỉ lệ độ rộng, có đường kẻ dọc giữa các cột */
        private fun createGridRow(context: Context, weights: List<Float>, cells: List<View>, background: Int = colorOf(context, R.color.surface)): LinearLayout {
            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(background)
                minimumHeight = dp(context, 48)
                cells.forEachIndexed { index, cell ->
                    if (index > 0) addView(View(context).apply {
                        setBackgroundColor(colorOf(context, R.color.divider))
                        layoutParams = LinearLayout.LayoutParams(dp(context, 1), LinearLayout.LayoutParams.MATCH_PARENT)
                    })
                    if (cell is TextView && cell.paddingTop == 0) cell.setPadding(cell.paddingLeft, dp(context, 12), cell.paddingRight, dp(context, 12))
                    cell.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, weights[index])
                    addView(cell)
                }
            }
        }

        /** Ô giá trị giải: chữ đỏ đậm, tự thu nhỏ để số tiền dài không xuống dòng */
        private fun createMoneyCell(context: Context, text: String?): TextView {
            return createCellText(context, text ?: "-", 13f, colorOf(context, R.color.result_red), bold = true).apply {
                maxLines = 1
                setPadding(dp(context, 4), 0, dp(context, 4), 0)
                TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 9, 13, 1, TypedValue.COMPLEX_UNIT_SP)
            }
        }

        // ---------- Thẻ Keno ----------

        private fun bindKeno(holder: KenoViewHolder, draw: VietlottDraw) {
            val context = holder.itemView.context

            // "KẾT QUẢ XỔ SỐ VIETLOTT KENO KÌ QUAY #299423 NGÀY 15/10/2026" (mã kỳ màu đỏ)
            val date = draw.date?.take(10)?.let { runCatching { LocalDate.parse(it).format(DateTimeFormatter.ofPattern("dd/MM/yyyy")) }.getOrNull() }
            holder.tvTitle.text = SpannableStringBuilder("KẾT QUẢ XỔ SỐ VIETLOTT KENO KÌ QUAY ").apply {
                val start = length
                append("#${draw.drawNumber ?: "-"}")
                setSpan(ForegroundColorSpan(colorOf(context, R.color.result_special)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                if (date != null) append(" NGÀY $date")
            }

            // 20 quả bóng: 4 hàng x 5 quả cho vừa màn hình điện thoại
            val numbers = draw.numbers()
            holder.layoutBalls.removeAllViews()
            numbers.chunked(KENO_BALLS_PER_ROW).forEach { line ->
                holder.layoutBalls.addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(0, dp(context, 5), 0, dp(context, 5))
                    (0 until KENO_BALLS_PER_ROW).forEach { index ->
                        // Mỗi ô rộng bằng nhau, quả bóng nằm giữa ô
                        addView(FrameLayout(context).apply {
                            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            line.getOrNull(index)?.let { number ->
                                addView(TextView(context).apply {
                                    text = number
                                    gravity = Gravity.CENTER
                                    setTextColor(colorOf(context, R.color.on_gold))
                                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                                    setTypeface(null, Typeface.BOLD)
                                    setBackgroundResource(R.drawable.bg_keno_ball)
                                    layoutParams = FrameLayout.LayoutParams(dp(context, 48), dp(context, 48), Gravity.CENTER)
                                })
                            }
                        })
                    }
                })
            }

            // Thống kê: Chẵn / Lẻ, Lớn (41-80) / Nhỏ (1-40)
            val values = numbers.mapNotNull { it.toIntOrNull() }
            val even = values.count { it % 2 == 0 }
            val big = values.count { it > 40 }
            holder.layoutStats.removeAllViews()
            holder.layoutStats.addView(createStatRow(context,
                createStatBox(context, "C", "#0A0AA8", "Chẵn", even),
                createStatBox(context, ">", "#D00018", "Lớn", big)))
            holder.layoutStats.addView(createStatRow(context,
                createStatBox(context, "L", "#3B9BE0", "Lẻ", values.size - even),
                createStatBox(context, "<", "#F39C12", "Nhỏ", values.size - big)))
        }

        private fun createStatRow(context: Context, left: View, right: View) = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            listOf(left, right).forEachIndexed { index, box ->
                box.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                    setMargins(if (index == 0) 0 else dp(context, 6), dp(context, 6), if (index == 0) dp(context, 6) else 0, dp(context, 6))
                }
                addView(box)
            }
        }

        /** Ô thống kê: biểu tượng tròn có chữ, tên, và số lượng */
        private fun createStatBox(context: Context, symbol: String, color: String, label: String, value: Int): View {
            return MaterialCardView(context).apply {
                radius = dp(context, 10).toFloat()
                cardElevation = dp(context, 1).toFloat()
                setCardBackgroundColor(colorOf(context, R.color.surface))
                strokeWidth = 0
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    setPadding(dp(context, 10), dp(context, 10), dp(context, 14), dp(context, 10))
                    addView(TextView(context).apply {
                        text = symbol
                        gravity = Gravity.CENTER
                        setTextColor(Color.WHITE)
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                        setTypeface(null, Typeface.BOLD)
                        setBackgroundResource(R.drawable.bg_lottery_ball)
                        backgroundTintList = ColorStateList.valueOf(Color.parseColor(color))
                        layoutParams = LinearLayout.LayoutParams(dp(context, 32), dp(context, 32))
                    })
                    addView(createCellText(context, label, 14f, colorOf(context, R.color.text_secondary), bold = true).apply {
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        setPadding(dp(context, 10), 0, 0, 0)
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    })
                    addView(createCellText(context, value.toString(), 20f, colorOf(context, R.color.text_primary), bold = true))
                })
            }
        }

        // ---------- Thẻ Max 3D Pro ----------

        private fun bindMax3dPro(holder: Max3dProViewHolder, draw: VietlottDraw) {
            val context = holder.itemView.context
            // Không ghi mã kỳ: API trả về mã kỳ của Max 3D Pro không chính xác (trùng với số giải Đặc biệt)
            holder.tvTitle.text = "XỔ SỐ MAX 3D PRO ${formatDrawDay(draw.date)}".trim()

            val byPrize = draw.resultsByPrize().toMap()
            val special = byPrize["DB"].orEmpty()
            val table = holder.layoutPrizeTable
            table.removeAllViews()

            // Hàng tiêu đề
            table.addView(createMax3dRow(context, "Giải", createCellText(context, "Dãy số trúng", 14f, colorOf(context, R.color.text_primary), bold = true), "Giải thưởng", isHeader = true))

            for (row in MAX3DPRO_ROWS) {
                table.addView(createDivider(context))
                val content: View = when {
                    // ĐB: 2 bộ số to, màu đỏ; Phụ ĐB: 2 bộ số của ĐB đổi thứ tự
                    row.code == "DB" -> createNumberGrid(context, special, perLine = 2, sizeSp = 28f, color = colorOf(context, R.color.result_red))
                    row.code == "PDB" -> createNumberGrid(context, special.reversed(), perLine = 2, sizeSp = 22f, color = colorOf(context, R.color.result_special))
                    row.numbersKey != null -> LinearLayout(context).apply {
                        orientation = LinearLayout.VERTICAL
                        addView(createCellText(context, row.description, 11f, colorOf(context, R.color.text_secondary)).apply {
                            setPadding(dp(context, 6), dp(context, 8), dp(context, 6), dp(context, 8))
                        })
                        addView(createDivider(context))
                        addView(createNumberGrid(context, byPrize[row.numbersKey].orEmpty(), perLine = 4, sizeSp = 17f, color = colorOf(context, R.color.text_primary)))
                    }
                    // Tư, Năm, Sáu: chỉ có mô tả cách trúng
                    else -> createCellText(context, row.description, 13f, colorOf(context, R.color.text_secondary)).apply {
                        gravity = Gravity.START or Gravity.CENTER_VERTICAL
                        setPadding(dp(context, 12), dp(context, 14), dp(context, 8), dp(context, 14))
                    }
                }
                val amount = draw.getPrize("MAX3DPRO_${row.code}").first?.takeIf { it > 0 } ?: row.defaultAmount
                table.addView(createMax3dRow(
                    context,
                    row.label,
                    content,
                    formatShortMoney(amount),
                    isHeader = false,
                    background = if (row.code == "PDB") colorOf(context, R.color.surface_variant) else colorOf(context, R.color.surface)
                ))
            }
        }

        /** Một hàng 3 cột: tên giải | nội dung | giải thưởng, có đường kẻ dọc giữa các cột */
        private fun createMax3dRow(
            context: Context,
            label: String,
            content: View,
            prize: String,
            isHeader: Boolean,
            background: Int = colorOf(context, R.color.surface)
        ): LinearLayout {
            fun verticalDivider() = View(context).apply {
                setBackgroundColor(colorOf(context, R.color.divider))
                layoutParams = LinearLayout.LayoutParams(dp(context, 1), LinearLayout.LayoutParams.MATCH_PARENT)
            }
            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                setBackgroundColor(background)
                addView(createCellText(context, label, 14f, if (isHeader) colorOf(context, R.color.text_primary) else colorOf(context, R.color.text_secondary), bold = true).apply {
                    setPadding(dp(context, 4), dp(context, 14), dp(context, 4), dp(context, 14))
                    layoutParams = LinearLayout.LayoutParams(dp(context, 58), LinearLayout.LayoutParams.MATCH_PARENT)
                })
                addView(verticalDivider())
                content.layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, 1f)
                if (content is TextView && isHeader) content.gravity = Gravity.CENTER
                addView(content)
                addView(verticalDivider())
                addView(createCellText(context, prize, 14f, colorOf(context, R.color.text_primary), bold = true).apply {
                    setPadding(dp(context, 4), dp(context, 14), dp(context, 4), dp(context, 14))
                    layoutParams = LinearLayout.LayoutParams(dp(context, 64), LinearLayout.LayoutParams.MATCH_PARENT)
                })
            }
        }

        /** Các bộ số xếp thành lưới, mỗi hàng perLine bộ, các cột rộng bằng nhau */
        private fun createNumberGrid(context: Context, numbers: List<String>, perLine: Int, sizeSp: Float, @ColorInt color: Int): LinearLayout {
            return LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                setPadding(0, dp(context, 8), 0, dp(context, 8))
                numbers.ifEmpty { listOf("-") }.chunked(perLine).forEach { line ->
                    addView(LinearLayout(context).apply {
                        orientation = LinearLayout.HORIZONTAL
                        (0 until perLine).forEach { index ->
                            addView(createCellText(context, line.getOrElse(index) { "" }, sizeSp, color, bold = true).apply {
                                letterSpacing = 0.1f
                                setPadding(0, dp(context, 4), 0, dp(context, 4))
                                layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                            })
                        }
                    })
                }
            }
        }

        private fun colorOf(context: Context, @ColorRes id: Int): Int = ContextCompat.getColor(context, id)

        private fun createCellText(context: Context, text: String, sizeSp: Float, @ColorInt color: Int, bold: Boolean = false): TextView {
            return TextView(context).apply {
                this.text = text
                gravity = Gravity.CENTER
                setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp)
                setTextColor(color)
                if (bold) setTypeface(null, Typeface.BOLD)
            }
        }

        /** 2000000000 -> "2 tỷ", 400000000 -> "400tr", 100000 -> "100k" */
        private fun formatShortMoney(value: Long): String {
            fun trim(number: Double) = if (number % 1.0 == 0.0) number.toLong().toString() else "%.1f".format(Locale.US, number).replace('.', ',')
            return when {
                value >= 1_000_000_000 -> "${trim(value / 1_000_000_000.0)} tỷ"
                value >= 1_000_000 -> "${trim(value / 1_000_000.0)}tr"
                value >= 1_000 -> "${trim(value / 1_000.0)}k"
                else -> value.toString()
            }
        }

        // ---------- Thẻ Mega 6/45 / Power 6/55 ----------

        private fun bindJackpotGame(holder: JackpotViewHolder, draw: VietlottDraw, game: JackpotGame) {
            val context = holder.itemView.context
            val drawNumber = draw.drawNumber?.toString()?.padStart(5, '0') ?: "-"

            // "XỔ SỐ MEGA 6/45 KÌ #01575 THỨ TƯ NGÀY 14-10-2026"
            holder.tvTitle.text = "XỔ SỐ ${game.name} KÌ #$drawNumber ${formatDrawDay(draw.date)}".trim()

            // Các quả bóng số. Power có 7 quả nên thu nhỏ một chút cho vừa màn hình
            val numbers = draw.numbers()
            val ballSize = if (numbers.size > 6) 40 else 46
            holder.layoutBalls.removeAllViews()
            numbers.forEachIndexed { index, number ->
                val isSpecial = game.hasSpecialBall && index == 6
                holder.layoutBalls.addView(TextView(context).apply {
                    text = number
                    gravity = Gravity.CENTER
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, if (numbers.size > 6) 15f else 17f)
                    setTypeface(null, Typeface.BOLD)
                    if (game.hasSpecialBall && !isSpecial) {
                        // Power: 6 số chính là bóng trắng chữ đỏ
                        setBackgroundResource(R.drawable.bg_lottery_ball_white)
                        setTextColor(colorOf(context, R.color.header_red))
                    } else {
                        // Mega: mỗi quả một màu; Power: số đặc biệt màu cam
                        setBackgroundResource(R.drawable.bg_lottery_ball)
                        backgroundTintList = ColorStateList.valueOf(if (isSpecial) SPECIAL_BALL_COLOR else BALL_COLORS[index % BALL_COLORS.size])
                        setTextColor(Color.WHITE)
                    }
                    elevation = dp(context, 3).toFloat()
                    layoutParams = LinearLayout.LayoutParams(dp(context, ballSize), dp(context, ballSize)).apply {
                        setMargins(dp(context, 3), 0, dp(context, 3), 0)
                    }
                })
            }

            // "Giá trị Jackpot: 20.329.744.500 đồng" (Power có 2 dòng: Jackpot 1, Jackpot 2)
            holder.tvJackpot.text = SpannableStringBuilder().apply {
                game.jackpots.forEachIndexed { index, (label, code) ->
                    if (index > 0) append("\n")
                    append("Giá trị $label:  ")
                    val start = length
                    val value = jackpotValue(draw, code, isMain = index == 0)
                    append(if (value != null) "${formatMoney(value)} đồng" else "Đang cập nhật")
                    setSpan(ForegroundColorSpan(colorOf(context, R.color.result_special)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    setSpan(AbsoluteSizeSpan(20, true), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }

            // Bảng giải thưởng
            holder.layoutPrizeTable.removeAllViews()
            holder.layoutPrizeTable.addView(createPrizeRow(context, listOf("Giải thưởng", "Trùng khớp", "Số lượng", "Giá trị (đồng)"), isHeader = true))
            for (prize in game.prizes) {
                val (amount, count) = draw.getPrize(prize.code)
                val isMainJackpot = prize.code == game.jackpots.first().second
                val value = if (prize.isJackpot) jackpotValue(draw, prize.code, isMainJackpot) else amount?.takeIf { it > 0 }
                holder.layoutPrizeTable.addView(createDivider(context))
                holder.layoutPrizeTable.addView(createPrizeRow(
                    context,
                    listOf(
                        prize.label,
                        matchPattern(context, prize),
                        count?.let { formatMoney(it.toLong()) } ?: "-",
                        value?.let { formatMoney(it) } ?: "-"
                    ),
                    isHeader = false
                ))
            }

            holder.tvFooter.text = "Kỳ quay thưởng: #${draw.id?.takeIf { it.isJsonPrimitive }?.asString ?: "-"} • Kết quả chính thức từ Vietlott"
        }

        /** Giá trị Jackpot theo mã giải; Jackpot chính dùng thêm trường "jackpot" của API nếu bảng giải chưa có */
        private fun jackpotValue(draw: VietlottDraw, code: String, isMain: Boolean): Long? {
            return draw.getPrize(code).first?.takeIf { it > 0 }
                ?: if (isMain) draw.jackpotAmount() else null
        }

        /** "●●●●●○" (Mega) hoặc "●●●●●○ | ●" (Power: số đặc biệt màu đỏ sau dấu |) */
        private fun matchPattern(context: Context, prize: PrizeDef): CharSequence {
            val main = "●".repeat(prize.matched) + "○".repeat(6 - prize.matched)
            val special = prize.specialMatched ?: return main
            return SpannableStringBuilder(main).apply {
                append("  |  ")
                val start = length
                append(if (special) "●" else "○")
                setSpan(ForegroundColorSpan(colorOf(context, R.color.result_special)), start, length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        /** Một hàng 4 cột của bảng giải thưởng */
        private fun createPrizeRow(context: Context, cells: List<CharSequence>, isHeader: Boolean): LinearLayout {
            return LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                if (isHeader) setBackgroundColor(colorOf(context, R.color.surface_variant))
                cells.forEachIndexed { column, cell ->
                    if (column > 0) addView(View(context).apply {
                        setBackgroundColor(colorOf(context, R.color.divider))
                        layoutParams = LinearLayout.LayoutParams(dp(context, 1), LinearLayout.LayoutParams.MATCH_PARENT)
                    })
                    addView(TextView(context).apply {
                        text = cell
                        gravity = Gravity.CENTER
                        setPadding(dp(context, 4), dp(context, 12), dp(context, 4), dp(context, 12))
                        setTypeface(null, if (isHeader || column != 2) Typeface.BOLD else Typeface.NORMAL)
                        // Cột "Trùng khớp" (●●●○○○) cần chữ to hơn để các chấm dễ nhìn;
                        // riêng Power ("●●●●●○  |  ○") dài hơn nên nhỏ lại để không xuống dòng
                        val matchSize = if (cell.length > 6) 12f else 15f
                        setTextSize(TypedValue.COMPLEX_UNIT_SP, if (column == 1 && !isHeader) matchSize else 13f)
                        maxLines = if (column == 1 && !isHeader) 1 else Int.MAX_VALUE
                        // Cột giá trị: số tiền rất dài (VD Jackpot 156.701.347.950) tự thu nhỏ chữ để không xuống dòng
                        if (column == 3 && !isHeader) {
                            maxLines = 1
                            TextViewCompat.setAutoSizeTextTypeUniformWithConfiguration(this, 9, 13, 1, TypedValue.COMPLEX_UNIT_SP)
                        }
                        setTextColor(colorOf(context, when {
                            isHeader -> R.color.text_primary
                            column == 1 -> R.color.text_primary
                            column == 2 -> R.color.text_secondary
                            else -> R.color.result_red
                        }))
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.MATCH_PARENT, COLUMN_WEIGHTS[column])
                    })
                }
            }
        }

        private fun createDivider(context: Context) = View(context).apply {
            setBackgroundColor(colorOf(context, R.color.divider))
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, dp(context, 1))
        }

        /** "2026-10-14T00:00:00.000Z" -> "THỨ TƯ NGÀY 14-10-2026" */
        private fun formatDrawDay(date: String?): String {
            val localDate = date?.take(10)?.let { runCatching { LocalDate.parse(it) }.getOrNull() } ?: return ""
            val dayOfWeek = when (localDate.dayOfWeek) {
                DayOfWeek.MONDAY -> "THỨ HAI"
                DayOfWeek.TUESDAY -> "THỨ BA"
                DayOfWeek.WEDNESDAY -> "THỨ TƯ"
                DayOfWeek.THURSDAY -> "THỨ NĂM"
                DayOfWeek.FRIDAY -> "THỨ SÁU"
                DayOfWeek.SATURDAY -> "THỨ BẢY"
                else -> "CHỦ NHẬT"
            }
            return "$dayOfWeek NGÀY ${localDate.format(DateTimeFormatter.ofPattern("dd-MM-yyyy"))}"
        }

        /** 20329744500 -> "20.329.744.500" */
        private fun formatMoney(value: Long): String =
            NumberFormat.getIntegerInstance(Locale.forLanguageTag("vi-VN")).format(value)

        private fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density).toInt()

        /**
         * Một hàng của bảng giải thưởng.
         * @param matched số số chính trùng khớp (trên 6)
         * @param specialMatched có trùng số đặc biệt không (Power); null = trò không có số đặc biệt (Mega)
         */
        private data class PrizeDef(
            val code: String,
            val label: String,
            val matched: Int,
            val specialMatched: Boolean? = null,
            val isJackpot: Boolean = false
        )

        /** Cấu hình thẻ của một trò Vietlott có Jackpot */
        private data class JackpotGame(
            val name: String,
            /** (tên hiển thị, mã giải trong API); phần tử đầu là Jackpot chính */
            val jackpots: List<Pair<String, String>>,
            val prizes: List<PrizeDef>,
            /** Power 6/55: số thứ 7 là số đặc biệt */
            val hasSpecialBall: Boolean
        )

        /**
         * Một hàng của bảng Max 3D Pro.
         * @param numbersKey mã nhóm số trong results (G1, G2, G3); null = hàng không có dãy số
         * @param defaultAmount giá trị giải cố định của Vietlott, dùng khi API chưa có
         */
        private data class Max3dRow(
            val code: String,
            val label: String,
            val description: String,
            val numbersKey: String?,
            val defaultAmount: Long
        )

        companion object {
            private const val VIEW_TYPE_SIMPLE = 0
            private const val VIEW_TYPE_JACKPOT = 1
            private const val VIEW_TYPE_MAX3DPRO = 2
            private const val VIEW_TYPE_KENO = 3
            private const val VIEW_TYPE_MAX3D = 4

            /** Bảng chính Max 3D: (mã nhóm số trong results, tên giải, số bộ số mỗi hàng) */
            private val MAX3D_MAIN_ROWS = listOf(
                Triple("DB", "ĐB", 2),
                Triple("G1", "Nhất", 4),
                Triple("G2", "Nhì", 3),
                Triple("G3", "Ba", 4)
            )
            /** Độ rộng tương đối các cột: Giải, Dãy số trúng, SL, Giá trị */
            private val MAX3D_MAIN_WEIGHTS = listOf(0.6f, 2.5f, 0.6f, 1.2f)

            /** Bảng Max 3D+: (mã giải trong API, cách trúng) */
            private val MAX3D_PLUS_ROWS = listOf(
                "G1" to "Trùng 2 số giải Đặc biệt",
                "G2" to "Trùng 2 số giải Nhất",
                "G3" to "Trùng 2 số giải Nhì",
                "G4" to "Trùng 2 số giải Ba",
                "G5" to "Trùng 2 số bất kỳ của giải Đặc biệt, Nhất, Nhì hoặc Ba",
                "G6" to "Trùng 1 số giải Đặc biệt",
                "G7" to "Trùng 1 số bất kỳ của giải Nhất, Nhì hoặc Ba"
            )
            /** Độ rộng tương đối các cột: Giải, Kết quả, SL giải, Giá trị */
            private val MAX3D_PLUS_WEIGHTS = listOf(0.5f, 2.1f, 0.7f, 1.4f)
            private const val KENO_BALLS_PER_ROW = 5

            private val MAX3DPRO_ROWS = listOf(
                Max3dRow("DB", "ĐB", "", null, 2_000_000_000),
                Max3dRow("PDB", "Phụ ĐB", "", null, 400_000_000),
                Max3dRow("G1", "Nhất", "Trùng 2 bộ ba số bất kỳ trong 4 bộ ba số", "G1", 30_000_000),
                Max3dRow("G2", "Nhì", "Trùng 2 bộ ba số bất kỳ trong 6 bộ ba số", "G2", 10_000_000),
                Max3dRow("G3", "Ba", "Trùng 2 bộ ba số bất kỳ trong 8 bộ ba số", "G3", 4_000_000),
                Max3dRow("G4", "Tư", "Trùng bất kỳ 2 bộ ba số quay thưởng của giải Đặc biệt, Nhất, Nhì hoặc Ba", null, 1_000_000),
                Max3dRow("G5", "Năm", "Trùng 1 bộ ba số quay thưởng giải Đặc biệt bất kỳ", null, 100_000),
                Max3dRow("G6", "Sáu", "Trùng 1 bộ ba số quay thưởng giải Nhất, Nhì hoặc Ba bất kỳ", null, 40_000)
            )

            /** Màu 6 quả bóng Mega: xanh dương, đỏ, vàng, xanh lá, tím, cam */
            private val BALL_COLORS = listOf("#0A7CC4", "#E31E24", "#F5A623", "#3A9D3A", "#8E2A8E", "#F37021").map { Color.parseColor(it) }
            private val SPECIAL_BALL_COLOR = Color.parseColor("#F37021")

            private val JACKPOT_GAMES = mapOf(
                "MEGA645" to JackpotGame(
                    name = "MEGA 6/45",
                    jackpots = listOf("Jackpot" to "MEGA645_JACKPOT"),
                    prizes = listOf(
                        PrizeDef("G1", "Giải nhất", 5),
                        PrizeDef("G2", "Giải nhì", 4),
                        PrizeDef("G3", "Giải ba", 3),
                        PrizeDef("MEGA645_JACKPOT", "Jackpot", 6, isJackpot = true)
                    ),
                    hasSpecialBall = false
                ),
                "POWER655" to JackpotGame(
                    name = "POWER 6/55",
                    jackpots = listOf("Jackpot 1" to "POWER655_JACKPOT1", "Jackpot 2" to "POWER655_JACKPOT2"),
                    prizes = listOf(
                        PrizeDef("G1", "Giải nhất", 5, specialMatched = false),
                        PrizeDef("G2", "Giải nhì", 4, specialMatched = false),
                        PrizeDef("G3", "Giải ba", 3, specialMatched = false),
                        // Jackpot 1: trùng 6 số chính; Jackpot 2: trùng 5 số chính + số đặc biệt
                        PrizeDef("POWER655_JACKPOT1", "Jackpot 1", 6, specialMatched = false, isJackpot = true),
                        PrizeDef("POWER655_JACKPOT2", "Jackpot 2", 5, specialMatched = true, isJackpot = true)
                    ),
                    hasSpecialBall = true
                )
            )

            /** Tên giải của Max 3D / Max 3D Pro theo mã trong API */
            private val PRIZE_LABELS = mapOf(
                "DB" to "Đặc biệt",
                "G1" to "Giải nhất",
                "G2" to "Giải nhì",
                "G3" to "Giải ba"
            )

            /** Độ rộng tương đối của 4 cột: Giải thưởng, Trùng khớp, Số lượng, Giá trị */
            private val COLUMN_WEIGHTS = listOf(0.95f, 1.1f, 0.8f, 1.4f)
        }
    }
}

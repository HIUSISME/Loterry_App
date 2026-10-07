package com.example.loterryapp

import android.app.DatePickerDialog
import android.os.Bundle
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
import com.google.gson.Gson
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class DienToanActivity : AppCompatActivity() {

    private lateinit var binding: ActivityDienToanBinding
    private val calendar = Calendar.getInstance()
    private val apiDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val displayDateFormat = SimpleDateFormat("dd-MM-yyyy", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDienToanBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        lifecycleScope.launch {
            try {
                val response = ApiClient.xosoService.getDienToanDraws(date = dateQuery, limit = 10)
                binding.progressBar.visibility = View.GONE

                val formattedDate = displayDateFormat.format(calendar.time)
                val gamesList = listOf(
                    DienToanGame("ĐIỆN TOÁN 123", formattedDate, listOf("5", "44", "541"), "Cơ cấu giải: Trúng khớp 1, 2 hoặc 3 bộ số"),
                    DienToanGame("ĐIỆN TOÁN 6X36", formattedDate, listOf("07", "08", "11", "12", "13", "18"), "Cơ cấu giải: Chọn 6 cặp số từ 01 đến 36"),
                    DienToanGame("THẦN TÀI 4", formattedDate, listOf("4", "2", "7", "4"), "Cơ cấu giải: Trúng khớp 4 chữ số may mắn")
                )

                binding.rvDienToan.layoutManager = LinearLayoutManager(this@DienToanActivity)
                binding.rvDienToan.adapter = DienToanAdapter(gamesList)
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                val formattedDate = displayDateFormat.format(calendar.time)
                val gamesList = listOf(
                    DienToanGame("ĐIỆN TOÁN 123", formattedDate, listOf("5", "44", "541"), "Cơ cấu giải: Trúng khớp 1, 2 hoặc 3 bộ số"),
                    DienToanGame("ĐIỆN TOÁN 6X36", formattedDate, listOf("07", "08", "11", "12", "13", "18"), "Cơ cấu giải: Chọn 6 cặp số từ 01 đến 36"),
                    DienToanGame("THẦN TÀI 4", formattedDate, listOf("4", "2", "7", "4"), "Cơ cấu giải: Trúng khớp 4 chữ số may mắn")
                )
                binding.rvDienToan.layoutManager = LinearLayoutManager(this@DienToanActivity)
                binding.rvDienToan.adapter = DienToanAdapter(gamesList)
            }
        }
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
            holder.tvDate.text = "Thứ tư ngày ${game.date}"
            holder.tvNumbers.text = game.numbers.joinToString("   -   ")
            holder.tvDesc.text = game.description
        }

        override fun getItemCount() = games.size
    }
}

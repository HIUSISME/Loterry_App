package com.example.loterryapp

import android.graphics.Color
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
import com.example.loterryapp.databinding.ActivityVietlottBinding
import com.example.loterryapp.network.models.VietlottDraw
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

class VietlottActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVietlottBinding
    private var type: String = "MEGA645"
    private var title: String = "Mega 6/45"
    private val repository by lazy { DrawRepository(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityVietlottBinding.inflate(layoutInflater)
        setContentView(binding.root)

        type = intent.getStringExtra("TYPE") ?: "MEGA645"
        title = intent.getStringExtra("TITLE") ?: "Mega 6/45"

        binding.tvToolbarTitle.text = title
        binding.ivBack.setOnClickListener { finish() }

        binding.rvVietlott.layoutManager = LinearLayoutManager(this)
        
        loadData()
    }

    private fun loadData() {
        binding.progressBar.visibility = View.VISIBLE
        binding.tvEmptyState.visibility = View.GONE
        lifecycleScope.launch {
            // 1. Hiện ngay các kỳ quay đã lưu trên máy (nếu có), không cần chờ mạng
            val showedSaved = showDraws(repository.getSavedVietlottDraws(type, LIMIT))
            // Đã có dữ liệu để xem thì ẩn vòng xoay tải, tránh đẩy nội dung xuống rồi giật lên khi API trả về
            if (showedSaved) binding.progressBar.visibility = View.GONE

            // 2. Gọi API để lấy kỳ mới; repository tự lưu những kỳ mới / thay đổi vào database
            try {
                val fresh = repository.fetchVietlottDraws(type, LIMIT)
                binding.progressBar.visibility = View.GONE
                if (!showDraws(fresh) && !showedSaved) {
                    showEmptyState("Chưa có kết quả cho $title")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e("VietlottActivity", "Error loading draws", e)
                binding.progressBar.visibility = View.GONE
                if (showedSaved) {
                    Toast.makeText(this@VietlottActivity, "Không cập nhật được, đang hiện kết quả đã lưu", Toast.LENGTH_SHORT).show()
                } else if (e is DrawRepository.ApiException) {
                    showEmptyState(e.message.orEmpty())
                } else {
                    showEmptyState("Lỗi mạng hoặc không thể tải dữ liệu: ${e.localizedMessage ?: "Vui lòng thử lại"}")
                }
            }
        }
    }

    /** Trả về false nếu không có gì để hiện */
    private fun showDraws(draws: List<VietlottDraw>): Boolean {
        if (draws.isEmpty()) return false
        binding.tvEmptyState.visibility = View.GONE
        binding.rvVietlott.adapter = VietlottAdapter(draws)
        return true
    }

    private fun showEmptyState(message: String) {
        binding.rvVietlott.adapter = VietlottAdapter(emptyList())
        binding.tvEmptyState.visibility = View.VISIBLE
        binding.tvEmptyState.text = message
    }

    companion object {
        /** Số kỳ quay gần nhất hiển thị */
        private const val LIMIT = 20
    }

    class VietlottAdapter(private val draws: List<VietlottDraw>) : RecyclerView.Adapter<VietlottAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvDate: TextView = view.findViewById(android.R.id.text1)
            val tvResult: TextView = view.findViewById(android.R.id.text2)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(android.R.layout.simple_list_item_2, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val draw = draws[position]
            holder.tvDate.text = "Kỳ quay #${draw.drawNumber} - ${draw.date}"
            holder.tvResult.text = draw.results?.joinToString(" - ") ?: "N/A"
            holder.tvResult.setTextColor(Color.RED)
            holder.tvResult.textSize = 18f
        }

        override fun getItemCount() = draws.size
    }
}

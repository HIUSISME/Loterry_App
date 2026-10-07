package com.example.loterryapp

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.loterryapp.databinding.ActivityVietlottBinding
import com.example.loterryapp.network.ApiClient
import com.example.loterryapp.network.models.VietlottDraw
import kotlinx.coroutines.launch

class VietlottActivity : AppCompatActivity() {

    private lateinit var binding: ActivityVietlottBinding
    private var type: String = "MEGA645"
    private var title: String = "Mega 6/45"

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
        lifecycleScope.launch {
            try {
                val response = ApiClient.xosoService.getVietlottDraws(type = type, limit = 20)
                binding.progressBar.visibility = View.GONE
                val body = response.body()
                if (response.isSuccessful && body?.success == true) {
                    val draws = body.data ?: emptyList()
                    binding.rvVietlott.adapter = VietlottAdapter(draws)
                } else {
                    android.widget.Toast.makeText(this@VietlottActivity, "API Error: ${body?.message ?: response.code()}", android.widget.Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = View.GONE
                android.widget.Toast.makeText(this@VietlottActivity, "Network Error: ${e.message}", android.widget.Toast.LENGTH_SHORT).show()
            }
        }
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

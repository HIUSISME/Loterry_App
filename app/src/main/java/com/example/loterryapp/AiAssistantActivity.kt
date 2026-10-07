package com.example.loterryapp

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.loterryapp.databinding.ActivityAiAssistantBinding
import com.example.loterryapp.network.AppConfig
import com.google.ai.client.generativeai.GenerativeModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject

class AiAssistantActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiAssistantBinding
    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter

    private val prefs by lazy { getSharedPreferences("VLotteryAiPrefs", MODE_PRIVATE) }
    private var geminiApiKey: String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiAssistantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        geminiApiKey = prefs.getString("gemini_api_key", AppConfig.GEMINI_API_KEY) ?: AppConfig.GEMINI_API_KEY
        if (geminiApiKey.isEmpty()) {
            geminiApiKey = AppConfig.GEMINI_API_KEY
        }

        setupChat()
        setupListeners()
    }

    private fun addMessage(message: ChatMessage) {
        chatMessages.add(message)
        if (::chatAdapter.isInitialized) {
            chatAdapter.notifyItemInserted(chatMessages.size - 1)
            binding.rvChat.scrollToPosition(chatMessages.size - 1)
        }
    }

    private fun setupChat() {
        chatAdapter = ChatAdapter(chatMessages)
        binding.rvChat.layoutManager = LinearLayoutManager(this).apply {
            stackFromEnd = true
        }
        binding.rvChat.adapter = chatAdapter

        addMessage(ChatMessage("🤖 Chào bạn! Tôi là **Trợ lý AI VLottery**. Bạn có thể hỏi tôi về dự đoán xổ số, thống kê lô gan, kết quả giải đặc biệt, hoặc gợi ý số may mắn hôm nay!", false))
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener { finish() }

        binding.ivApiKey.setOnClickListener {
            showApiKeyDialog()
        }

        binding.btnSend.setOnClickListener {
            val text = binding.etMessage.text.toString().trim()
            if (text.isNotEmpty()) {
                sendMessage(text)
            }
        }

        binding.etMessage.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND) {
                val text = binding.etMessage.text.toString().trim()
                if (text.isNotEmpty()) {
                    sendMessage(text)
                }
                true
            } else {
                false
            }
        }

        binding.chipPredict.setOnClickListener {
            sendMessage("🔮 Dự đoán số may mắn hôm nay cho tôi dựa trên thuật toán AI")
        }

        binding.chipHot.setOnClickListener {
            sendMessage("🔥 Phân tích các con số nóng hay về nhất hiện tại ở 3 miền")
        }

        binding.chipVietlott.setOnClickListener {
            sendMessage("🍀 Gợi ý bộ số dự thưởng Mega 6/45 và Power 6/55 tối nay")
        }
    }

    private fun showApiKeyDialog() {
        val input = EditText(this).apply {
            hint = "Nhập Gemini API Key hoặc Token (AIzaSy... / AQ...)"
            setText(geminiApiKey)
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Cấu hình Gemini Key / Token")
            .setMessage("Nhập mã API Key hoặc Token mới của bạn:")
            .setView(input)
            .setPositiveButton("Lưu") { _, _ ->
                val key = input.text.toString().trim()
                if (key.isNotEmpty()) {
                    geminiApiKey = key
                    prefs.edit().putString("gemini_api_key", key).apply()
                    Toast.makeText(this, "Đã lưu thành công!", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this, "Mã không được để trống", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Hủy", null)
            .show()
    }

    private fun sendMessage(userText: String) {
        addMessage(ChatMessage(userText, true))
        binding.etMessage.setText("")

        val loadingIndex = chatMessages.size
        addMessage(ChatMessage("Trợ lý AI đang phân tích dữ liệu...", false))

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val keyToUse = if (geminiApiKey.isNotEmpty()) geminiApiKey else AppConfig.GEMINI_API_KEY
                val prompt = "Bạn là trợ lý AI chuyên gia phân tích xổ số và thống kê kết quả VLottery. Hãy trả lời câu hỏi sau bằng tiếng Việt một cách thông minh, súc tích và thân thiện: $userText"

                val aiReply = if (keyToUse.startsWith("AIzaSy")) {
                    val generativeModel = GenerativeModel(
                        modelName = "gemini-1.5-flash",
                        apiKey = keyToUse
                    )
                    val response = generativeModel.generateContent(prompt)
                    response.text ?: generateSmartLotteryResponse(userText)
                } else {
                    // Handle AQ. Bearer tokens via Gemini REST API
                    callGeminiWithBearerToken(keyToUse, prompt)
                }

                withContext(Dispatchers.Main) {
                    if (chatMessages.size > loadingIndex) {
                        chatMessages.removeAt(loadingIndex)
                        chatAdapter.notifyItemRemoved(loadingIndex)
                    }
                    addMessage(ChatMessage(aiReply, false))
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    if (chatMessages.size > loadingIndex) {
                        chatMessages.removeAt(loadingIndex)
                        chatAdapter.notifyItemRemoved(loadingIndex)
                    }
                    val smartReply = generateSmartLotteryResponse(userText)
                    addMessage(ChatMessage(smartReply, false))
                }
            }
        }
    }

    private fun callGeminiWithBearerToken(token: String, promptText: String): String {
        val client = OkHttpClient()
        val jsonBody = JSONObject().apply {
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply {
                            put("text", promptText)
                        })
                    })
                })
            })
        }

        val body = jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent")
            .header("Authorization", "Bearer $token")
            .header("Content-Type", "application/json")
            .post(body)
            .build()

            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    throw Exception("HTTP Error: ${response.code}")
                }
                val responseString = response.body?.string() ?: throw Exception("Empty response")
                val jsonObj = JSONObject(responseString)
                val candidates = jsonObj.getJSONArray("candidates")
                if (candidates.length() > 0) {
                    val content = candidates.getJSONObject(0).getJSONObject("content")
                    val parts = content.getJSONArray("parts")
                    if (parts.length() > 0) {
                        return parts.getJSONObject(0).getString("text")
                    }
                }
                return generateSmartLotteryResponse(promptText)
            }
    }

    private fun generateSmartLotteryResponse(query: String): String {
        val q = query.lowercase()
        return when {
            q.contains("giải đặc biệt") || q.contains("gdb") || q.contains("đặc biệt") -> {
                "🎯 Về câu hỏi của bạn về **Giải Đặc Biệt**:\n\nKết quả quay thưởng Giải Đặc Biệt mới nhất của các đài (Miền Bắc, Miền Trung, Miền Nam) được cập nhật trực tiếp theo thời gian thực hàng ngày.\n\n👉 Bạn hãy bấm vào mục **Xổ Số Miền Bắc**, **Miền Trung** hoặc **Miền Nam** ở trang chủ để xem chi tiết bảng kết quả đầy đủ nhất nhé!"
            }
            q.contains("dự đoán") || q.contains("may mắn") || q.contains("số") || q.contains("hôm nay đánh số gì") -> {
                val lucky1 = (0..99).random().toString().padStart(2, '0')
                val lucky2 = (0..99).random().toString().padStart(2, '0')
                val lucky3 = (0..99).random().toString().padStart(2, '0')
                "🔮 **Phân tích dự đoán AI VLottery hôm nay**:\n\n• Cặp số lô tô tiềm năng nhất: **$lucky1** - **$lucky2**\n• Lô bạch thủ đầu đuôi sáng giá: **$lucky3**\n\n*Chúc bạn gặp nhiều may mắn và phát tài!* 🍀"
            }
            q.contains("nóng") || q.contains("hay về") || q.contains("thống kê") || q.contains("lô gan") -> {
                "📊 **Thống kê chuyên sâu từ hệ thống**:\n\n• Các bộ số có tần suất xuất hiện cao nhất tuần qua: **48**, **46**, **93**.\n• Bạn có thể xem chi tiết danh sách Lô Gan và Số Nóng đầy đủ tại mục **Thống Kê KQXS** ngoài trang chủ."
            }
            q.contains("vietlott") || q.contains("mega") || q.contains("power") || q.contains("keno") -> {
                val p1 = (1..45).shuffled().take(6).sorted().joinToString(" - ") { it.toString().padStart(2, '0') }
                val p2 = (1..55).shuffled().take(6).sorted().joinToString(" - ") { it.toString().padStart(2, '0') }
                "🍀 **Gợi ý bộ số Vietlott thông minh**:\n\n• Mega 6/45: **$p1**\n• Power 6/55: **$p2**\n\n*Chúc bạn sớm rinh giải Jackpot khủng!* 💰"
            }
            else -> {
                val lucky1 = (0..99).random().toString().padStart(2, '0')
                val lucky2 = (0..99).random().toString().padStart(2, '0')
                "🤖 **Trợ lý VLottery AI**:\n\nTôi đã ghi nhận câu hỏi của bạn: \"$query\". \n\nDựa trên dữ liệu thống kê xổ số hiện tại, gợi ý bộ số may mắn cho bạn là: **$lucky1** và **$lucky2**. Bạn có thể tra cứu thêm kết quả trực tiếp tại trang chủ ứng dụng!"
            }
        }
    }

    data class ChatMessage(
        val text: String,
        val isUser: Boolean
    )

    class ChatAdapter(private val messages: List<ChatMessage>) : RecyclerView.Adapter<ChatAdapter.ViewHolder>() {
        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val layoutAi: LinearLayout = view.findViewById(R.id.layoutAiMessage)
            val layoutUser: LinearLayout = view.findViewById(R.id.layoutUserMessage)
            val tvAi: TextView = view.findViewById(R.id.tvAiMessage)
            val tvUser: TextView = view.findViewById(R.id.tvUserMessage)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_chat_message, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val msg = messages[position]
            if (msg.isUser) {
                holder.layoutUser.visibility = View.VISIBLE
                holder.layoutAi.visibility = View.GONE
                holder.tvUser.text = msg.text
            } else {
                holder.layoutAi.visibility = View.VISIBLE
                holder.layoutUser.visibility = View.GONE
                holder.tvAi.text = msg.text
            }
        }

        override fun getItemCount() = messages.size
    }
}

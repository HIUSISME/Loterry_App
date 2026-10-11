package com.example.loterryapp

import android.os.Bundle
import android.util.Log
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

class AiAssistantActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiAssistantBinding
    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter

    private val prefs by lazy { getSharedPreferences("VLotteryAiPrefs", MODE_PRIVATE) }
    private var geminiApiKey: String = ""

    // Gemini có thể mất vài chục giây để trả lời, nên tăng timeout so với mặc định 10s
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiAssistantBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Key người dùng tự nhập (nếu có) được ưu tiên hơn key trong local.properties
        geminiApiKey = prefs.getString("gemini_api_key", null).orEmpty().ifEmpty { AppConfig.GEMINI_API_KEY }

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
            hint = "Nhập Gemini API Key (AIza...)"
            setText(geminiApiKey)
            setPadding(48, 32, 48, 32)
        }

        AlertDialog.Builder(this)
            .setTitle("Cấu hình Gemini API Key")
            .setMessage("Tạo key tại aistudio.google.com rồi dán vào đây:")
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

        if (geminiApiKey.isEmpty()) {
            addMessage(ChatMessage("⚠️ Chưa cấu hình Gemini API Key. Bấm biểu tượng chìa khoá ở góc trên để nhập key.", false))
            return
        }

        // Giữ tham chiếu (không dùng index) vì người dùng có thể gửi tiếp tin nhắn khi AI chưa trả lời xong
        val loadingMessage = ChatMessage("Trợ lý AI đang phân tích dữ liệu...", false)
        addMessage(loadingMessage)

        lifecycleScope.launch {
            val aiReply = try {
                withContext(Dispatchers.IO) { callGemini(geminiApiKey, userText) }
            } catch (e: GeminiException) {
                Log.e("AiAssistantActivity", "Gemini error", e)
                "⚠️ ${e.message}"
            } catch (e: IOException) {
                Log.e("AiAssistantActivity", "Network error", e)
                "⚠️ Lỗi mạng, không kết nối được tới Trợ lý AI. Vui lòng thử lại."
            } catch (e: Exception) {
                Log.e("AiAssistantActivity", "Unexpected error", e)
                "⚠️ Trợ lý AI gặp lỗi: ${e.localizedMessage ?: "không rõ nguyên nhân"}"
            }

            val loadingIndex = chatMessages.indexOfFirst { it === loadingMessage }
            if (loadingIndex >= 0) {
                chatMessages.removeAt(loadingIndex)
                chatAdapter.notifyItemRemoved(loadingIndex)
            }
            addMessage(ChatMessage(aiReply, false))
        }
    }

    private fun callGemini(apiKey: String, userText: String): String {
        val jsonBody = JSONObject().apply {
            put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", SYSTEM_PROMPT))))
            put("contents", JSONArray().put(
                JSONObject()
                    .put("role", "user")
                    .put("parts", JSONArray().put(JSONObject().put("text", userText)))
            ))
        }

        val request = Request.Builder()
            .url("https://generativelanguage.googleapis.com/v1beta/models/${AppConfig.GEMINI_MODEL}:generateContent")
            .header("x-goog-api-key", apiKey)
            .post(jsonBody.toString().toRequestBody("application/json; charset=utf-8".toMediaType()))
            .build()

        httpClient.newCall(request).execute().use { response ->
            val responseString = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                Log.e("AiAssistantActivity", "HTTP ${response.code}: $responseString")
                throw GeminiException(
                    when (response.code) {
                        400, 401, 403 -> "Gemini API Key không hợp lệ hoặc đã bị thu hồi. Bấm biểu tượng chìa khoá để nhập key mới."
                        404 -> "Model \"${AppConfig.GEMINI_MODEL}\" không tồn tại hoặc đã ngừng hoạt động. Hãy đổi GEMINI_MODEL trong local.properties."
                        429 -> "Đã vượt giới hạn sử dụng Gemini. Vui lòng thử lại sau ít phút."
                        else -> "Máy chủ Gemini trả về lỗi ${response.code}. Vui lòng thử lại sau."
                    }
                )
            }

            val candidates = JSONObject(responseString).optJSONArray("candidates")
            val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
            val text = buildString {
                for (i in 0 until (parts?.length() ?: 0)) {
                    append(parts!!.optJSONObject(i)?.optString("text").orEmpty())
                }
            }.trim()

            if (text.isEmpty()) {
                // Không có nội dung: thường do câu hỏi bị bộ lọc an toàn của Gemini chặn
                throw GeminiException("Trợ lý AI không thể trả lời câu hỏi này. Bạn thử hỏi theo cách khác nhé.")
            }
            return text
        }
    }

    private class GeminiException(message: String) : Exception(message)

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

    companion object {
        private const val SYSTEM_PROMPT =
            "Bạn là trợ lý AI chuyên gia phân tích xổ số và thống kê kết quả VLottery. " +
            "Hãy trả lời bằng tiếng Việt một cách thông minh, súc tích và thân thiện. " +
            "Xổ số là ngẫu nhiên: khi gợi ý số, hãy nói rõ đó chỉ là gợi ý tham khảo, không đảm bảo trúng thưởng."
    }
}

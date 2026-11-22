package com.example.loterryapp

import android.graphics.Typeface
import android.os.Bundle
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.loterryapp.ai.LotteryAiTools
import com.example.loterryapp.data.UserProfileStore
import com.example.loterryapp.databinding.ActivityAiAssistantBinding
import com.example.loterryapp.network.AppConfig
import com.example.loterryapp.notification.LotteryNotificationHelper
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.Period
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.concurrent.TimeUnit

class AiAssistantActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAiAssistantBinding
    private val chatMessages = mutableListOf<ChatMessage>()
    private lateinit var chatAdapter: ChatAdapter

    /** Key lấy từ local.properties (qua BuildConfig) */
    private val geminiApiKey: String = AppConfig.GEMINI_API_KEY

    /** Các hàm tra cứu kết quả (database / API) mà Gemini được phép gọi */
    private val aiTools by lazy { LotteryAiTools(this) }

    /**
     * Lịch sử hội thoại gửi kèm mỗi lần hỏi để AI nhớ ngữ cảnh ("thế còn miền Bắc?").
     * Gồm cả lượt gọi hàm và kết quả hàm, nên AI dùng lại được số liệu đã tra mà không phải gọi lại.
     * Chỉ lưu trong bộ nhớ khi màn hình đang mở; chỉ thêm vào khi một lượt hỏi đáp thành công.
     */
    private val history = mutableListOf<JSONObject>()

    /** Xử lý lần lượt từng câu hỏi, để lịch sử luôn đúng thứ tự hỏi - đáp */
    private val historyMutex = Mutex()

    // Gemini có thể mất vài chục giây để trả lời, nên tăng timeout so với mặc định 10s
    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAiAssistantBinding.inflate(layoutInflater)
        setContentView(binding.root)

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

        addMessage(ChatMessage("🤖 Chào bạn! Tôi là **Trợ lý AI VLottery**. Bạn có thể hỏi tôi về kết quả xổ số, giải đặc biệt, thống kê các con số, hoặc xin vài con số tham khảo. Lưu ý: xổ số là ngẫu nhiên, không ai dự đoán được kết quả.", false))
    }

    private fun setupListeners() {
        binding.ivBack.setOnClickListener { finish() }

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

        // Xổ số là ngẫu nhiên: chỉ xin gợi ý tham khảo, không gọi là "dự đoán" để tránh gây hiểu lầm
        binding.chipSuggest.setOnClickListener {
            sendMessage("🎲 Gợi ý cho tôi vài con số tham khảo hôm nay")
        }

        binding.chipHot.setOnClickListener {
            sendMessage("🔥 Phân tích các con số nóng hay về nhất hiện tại ở 3 miền")
        }

        binding.chipVietlott.setOnClickListener {
            sendMessage("🍀 Gợi ý bộ số dự thưởng Mega 6/45 và Power 6/55 tối nay")
        }
    }

    private fun sendMessage(userText: String) {
        addMessage(ChatMessage(userText, true))
        binding.etMessage.setText("")

        if (geminiApiKey.isEmpty()) {
            addMessage(ChatMessage("⚠️ Trợ lý AI chưa được cấu hình (thiếu Gemini API Key). Vui lòng thử lại sau.", false))
            return
        }

        // Giữ tham chiếu (không dùng index) vì người dùng có thể gửi tiếp tin nhắn khi AI chưa trả lời xong
        val loadingMessage = ChatMessage("Trợ lý AI đang phân tích dữ liệu...", false)
        addMessage(loadingMessage)

        lifecycleScope.launch {
            val aiReply = try {
                withContext(Dispatchers.IO) { historyMutex.withLock { askGemini(geminiApiKey, userText) } }
            } catch (e: CancellationException) {
                throw e
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

    /**
     * Hỏi Gemini, cho phép Gemini gọi các hàm tra cứu kết quả (function calling):
     * Gemini trả về functionCall -> app chạy hàm (database / API) -> gửi functionResponse -> Gemini trả lời bằng chữ.
     * Gửi kèm lịch sử các lượt trước để AI hiểu câu hỏi nối tiếp.
     */
    private suspend fun askGemini(apiKey: String, userText: String): String {
        // Các nội dung của lượt này; chỉ ghép vào history khi có câu trả lời (lỗi thì bỏ cả lượt)
        val turn = mutableListOf(
            JSONObject()
                .put("role", "user")
                .put("parts", JSONArray().put(JSONObject().put("text", userText)))
        )
        val contents = JSONArray()
        history.forEach { contents.put(it) }
        contents.put(turn.first())

        repeat(MAX_TOOL_ROUNDS) {
            val body = JSONObject()
                .put("system_instruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", buildSystemPrompt()))))
                .put("tools", aiTools.declarations())
                .put("contents", contents)

            val modelContent = callGemini(apiKey, body)
            val parts = modelContent.optJSONArray("parts") ?: JSONArray()
            val functionCalls = (0 until parts.length()).mapNotNull { parts.optJSONObject(it)?.optJSONObject("functionCall") }

            if (functionCalls.isEmpty()) {
                val text = (0 until parts.length()).joinToString("") { parts.optJSONObject(it)?.optString("text").orEmpty() }.trim()
                if (text.isEmpty()) {
                    // Không có nội dung: thường do câu hỏi bị bộ lọc an toàn của Gemini chặn
                    throw GeminiException("Trợ lý AI không thể trả lời câu hỏi này. Bạn thử hỏi theo cách khác nhé.")
                }
                turn += modelContent.put("role", "model")
                history += turn
                trimHistory()
                return text
            }

            // Gửi lại nguyên nội dung model trả về (kèm thoughtSignature mà Gemini yêu cầu), rồi kết quả của từng hàm
            contents.put(modelContent)
            turn += modelContent
            val responses = JSONArray()
            for (call in functionCalls) {
                val name = call.optString("name")
                val args = call.optJSONObject("args") ?: JSONObject()
                Log.d("AiAssistantActivity", "Gemini gọi hàm $name($args)")
                val functionResponse = JSONObject()
                    .put("name", name)
                    .put("response", aiTools.execute(name, args))
                call.optString("id").takeIf { it.isNotEmpty() }?.let { functionResponse.put("id", it) }
                responses.put(JSONObject().put("functionResponse", functionResponse))
            }
            val responseContent = JSONObject().put("role", "user").put("parts", responses)
            contents.put(responseContent)
            turn += responseContent
        }
        throw GeminiException("Trợ lý AI chưa tra cứu xong dữ liệu. Bạn thử hỏi lại ngắn gọn hơn nhé.")
    }

    /**
     * Chỉ giữ MAX_HISTORY_TURNS lượt hỏi gần nhất để request không quá dài (tốn token, chậm).
     * Cắt tại đầu một câu hỏi của người dùng, không cắt giữa cặp functionCall / functionResponse.
     */
    private fun trimHistory() {
        val questionStarts = history.indices.filter { i ->
            val content = history[i]
            content.optString("role") == "user" &&
                content.optJSONArray("parts")?.optJSONObject(0)?.has("text") == true
        }
        if (questionStarts.size > MAX_HISTORY_TURNS) {
            repeat(questionStarts[questionStarts.size - MAX_HISTORY_TURNS]) { history.removeAt(0) }
        }
    }

    /** Gửi một request tới Gemini, trả về "content" của câu trả lời */
    private fun callGemini(apiKey: String, jsonBody: JSONObject): JSONObject {
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
                        400, 401, 403 -> "Gemini API Key không hợp lệ hoặc đã bị thu hồi. Hãy cập nhật GEMINI_API_KEY trong local.properties."
                        404 -> "Model \"${AppConfig.GEMINI_MODEL}\" không tồn tại hoặc đã ngừng hoạt động. Hãy đổi GEMINI_MODEL trong local.properties."
                        429 -> "Đã vượt giới hạn sử dụng Gemini. Vui lòng thử lại sau ít phút."
                        503 -> "Trợ lý AI đang quá tải do nhiều người dùng cùng lúc. Vui lòng thử lại sau ít phút."
                        else -> "Máy chủ Gemini trả về lỗi ${response.code}. Vui lòng thử lại sau."
                    }
                )
            }

            return JSONObject(responseString).optJSONArray("candidates")?.optJSONObject(0)?.optJSONObject("content")
                ?: throw GeminiException("Trợ lý AI không thể trả lời câu hỏi này. Bạn thử hỏi theo cách khác nhé.")
        }
    }

    /** Hướng dẫn cho AI, kèm ngày giờ hiện tại (giờ Việt Nam) vì mô hình không tự biết hôm nay là ngày nào */
    private fun buildSystemPrompt(): String {
        val now = ZonedDateTime.now(LotteryNotificationHelper.VN_ZONE)
        val dayOfWeek = when (now.dayOfWeek) {
            DayOfWeek.MONDAY -> "Thứ Hai"
            DayOfWeek.TUESDAY -> "Thứ Ba"
            DayOfWeek.WEDNESDAY -> "Thứ Tư"
            DayOfWeek.THURSDAY -> "Thứ Năm"
            DayOfWeek.FRIDAY -> "Thứ Sáu"
            DayOfWeek.SATURDAY -> "Thứ Bảy"
            else -> "Chủ Nhật"
        }
        val dateTime = now.format(DateTimeFormatter.ofPattern("dd/MM/yyyy 'lúc' HH:mm"))
        return """
            Bạn là trợ lý AI chuyên gia phân tích xổ số và thống kê kết quả của ứng dụng VLottery.
            Hôm nay là $dayOfWeek, ngày $dateTime (giờ Việt Nam). Dùng ngày này khi người dùng nói "hôm nay", "hôm qua", "tuần trước"...
            Lịch quay hằng ngày (giờ Việt Nam): Miền Nam khoảng 16h15, Miền Trung khoảng 17h15, Miền Bắc khoảng 18h15.
            Khi người dùng hỏi về kết quả, giải đặc biệt, lô tô, đầu đuôi của bất kỳ ngày nào: LUÔN gọi hàm get_lottery_results
            (hoặc get_vietlott_results cho Vietlott) để lấy dữ liệu thật, rồi trả lời dựa trên dữ liệu đó.
            Tuyệt đối không tự bịa ra con số kết quả. Nếu hàm không có dữ liệu, hãy nói rõ là chưa có kết quả (có thể chưa đến giờ quay).
            Trả lời bằng tiếng Việt, súc tích, thân thiện.
            Xổ số là ngẫu nhiên: khi gợi ý số, hãy nói rõ đó chỉ là gợi ý tham khảo, không đảm bảo trúng thưởng.
        """.trimIndent() + userContext()
    }

    /**
     * Thông tin cá nhân của người dùng cho AI, CHỈ khi người dùng đã đồng ý ở màn Hồ sơ.
     * Chỉ gửi những gì giúp trả lời tốt hơn: tên (để xưng hô), tuổi (không gửi ngày sinh), giới tính, tỉnh/thành.
     * Không gửi số điện thoại.
     */
    private fun userContext(): String {
        val uid = AuthManager.getInstance().getCurrentUserUid() ?: return ""
        val profile = UserProfileStore(this, uid).load()
        if (profile.aiConsent != true) return ""

        val age = profile.birthday.takeIf { it.isNotEmpty() }
            ?.let { runCatching { Period.between(LocalDate.parse(it), LocalDate.now()).years }.getOrNull() }
        val facts = listOfNotNull(
            profile.fullName.takeIf { it.isNotEmpty() }?.let { "Tên: $it" },
            age?.let { "Tuổi: $it" },
            profile.gender.takeIf { it.isNotEmpty() }?.let { "Giới tính: $it" },
            profile.province.takeIf { it.isNotEmpty() }?.let { "Tỉnh/thành đang sống: $it" }
        )
        if (facts.isEmpty()) return ""

        return "\n\nThông tin người dùng (người dùng đã đồng ý chia sẻ với bạn): ${facts.joinToString("; ")}.\n" +
            "Hãy xưng hô phù hợp với tên, tuổi và giới tính. Khi hữu ích, ưu tiên đài xổ số của miền/tỉnh người dùng đang sống. " +
            "Không nhắc lại các thông tin này nếu câu hỏi không cần đến."
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
                holder.tvAi.text = formatMarkdown(msg.text)
            }
        }

        /** Hiển thị **in đậm** và *in nghiêng* (Markdown đơn giản mà Gemini hay dùng) thay vì hiện nguyên dấu sao */
        private fun formatMarkdown(markdown: String): CharSequence {
            // Dòng liệt kê "* abc" / "- abc" -> "• abc"
            val text = markdown.replace(LIST_BULLET, "$1• ")
            val builder = SpannableStringBuilder()
            var index = 0
            for (match in MARKDOWN_STYLE.findAll(text)) {
                builder.append(text, index, match.range.first)
                val isBold = match.groups[1] != null
                val content = match.groups[1]?.value ?: match.groups[2]!!.value
                val start = builder.length
                builder.append(content)
                builder.setSpan(StyleSpan(if (isBold) Typeface.BOLD else Typeface.ITALIC), start, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                index = match.range.last + 1
            }
            builder.append(text, index, text.length)
            return builder
        }

        override fun getItemCount() = messages.size
    }

    companion object {
        /** Số lần tối đa Gemini được gọi hàm trong một câu hỏi, tránh lặp vô hạn */
        private const val MAX_TOOL_ROUNDS = 4

        /** Số lượt hỏi - đáp gần nhất AI được nhớ */
        private const val MAX_HISTORY_TURNS = 10

        /** **đậm** (nhóm 1) hoặc *nghiêng* (nhóm 2) */
        private val LIST_BULLET = Regex("""(?m)^(\s*)[*-]\s+""")

        private val MARKDOWN_STYLE = Regex("""\*\*(.+?)\*\*|(?<!\*)\*(?!\s)([^*\n]+?)\*(?!\*)""")
    }
}

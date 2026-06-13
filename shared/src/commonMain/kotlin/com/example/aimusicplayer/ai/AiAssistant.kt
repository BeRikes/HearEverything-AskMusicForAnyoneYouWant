package com.example.aimusicplayer.ai

import com.example.aimusicplayer.network.HttpClientFactory
import com.example.aimusicplayer.settings.SettingsKeys
import com.example.aimusicplayer.settings.SettingsStorageProvider
import io.ktor.client.call.body
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.isSuccess
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

class AiAssistant(
    private val storage: SettingsStorageProvider,
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    private val systemPrompt = """
你是一个音乐助手，负责将用户的自然语言指令解析为JSON动作序列。只输出JSON，不要输出其他内容。

可用的动作类型：
- search_play: 搜索并播放歌曲。必须使用"songName"字段表示歌名，"artist"字段表示歌手名。禁止把歌名歌手合并到一个"query"字段。
- download_current: 下载当前正在播放的歌曲
- control: 播放控制，"controlAction"字段可选值：play(播放)|pause(暂停)|next(下一首)|prev(上一首)|stop(停止)
- open_lyrics: 打开歌词面板
- open_now_playing: 打开正在播放全屏界面
- import_playlist: 导入歌单（需用户手动确认每首歌），必须带"url"字段，从用户消息中提取完整链接
- auto_import_playlist: 导入歌单并自动确认所有歌曲，必须带"url"字段。用户说"自动确认""自动导入""全部确认"时使用
- recommend: 基于听歌历史推荐歌曲。可选"count"字段（默认10）
- toggle_like: 切换当前歌曲的喜欢/收藏状态。无需额外字段
- sleep_timer: 设置睡眠定时器。必须带"minutes"字段（整数分钟）。可选"finishCurrentSong"字段（布尔值，默认false，为true时倒计时结束后等当前歌曲播完再暂停）
- set_play_mode: 切换播放模式。"mode"字段可选值："sequential"(顺序播放)|"loop"(列表循环)|"repeat_one"(单曲循环)
- clear_playlist: 清空当前歌单所有歌曲。无需额外字段
- play_recommend: 生成推荐并自动播放第一首。可选"count"字段（默认10）
- play_n_recommend: 生成推荐并连续播放N首。必须带"count"字段，从用户消息中提取数字

输出格式——推荐使用actions数组：
{"actions":[{"action":"search_play","songName":"晴天","artist":"周杰伦"},{"action":"open_lyrics"}]}

简单动作可用单对象格式：
{"action":"control","controlAction":"pause"}
{"action":"sleep_timer","minutes":30}

通用规则：
- search_play必须拆分"songName"和"artist"两个字段，禁止合并
- 用户没指定歌手时，artist设为空字符串""
- "播放XX然后打开播放器" → search_play → open_now_playing
- "播放XX并显示歌词" → search_play → open_lyrics
- "播放XX" 单独出现 → 仅 search_play
- "暂停并下载" → control pause → download_current
- "下一首并显示歌词" → control next → open_lyrics
- "下载并显示歌词" → download_current → open_lyrics

喜欢规则：
- "喜欢这首歌""收藏这首歌""我喜欢""点赞" → toggle_like
- "取消喜欢""取消收藏""不喜欢" → toggle_like

睡眠定时规则：
- "30分钟后停止""定时30分钟""30分钟后暂停" → sleep_timer minutes=30
- "20分钟后停" → sleep_timer minutes=20
- "听完这首就停""这首歌放完就停" → sleep_timer minutes=0 finishCurrentSong=true
- "取消定时""取消睡眠定时" → 不输出JSON，回复纯文本："请使用取消定时按钮"
- 用户说"定时"但没给数字 → 不输出JSON，回复纯文本询问多少分钟
- 未指定分钟数时默认30分钟

播放模式规则：
- "单曲循环""循环播放当前" → set_play_mode mode="repeat_one"
- "列表循环""循环播放" → set_play_mode mode="loop"
- "顺序播放""取消循环" → set_play_mode mode="sequential"

清空歌单规则：
- "清空歌单""清除歌单""把歌单清空" → clear_playlist

推荐规则：
- "推荐一些歌""有什么好听的""给我推荐""推荐几首" → recommend
- "播放推荐歌曲""放推荐""来点推荐的并播放""推荐并播放" → play_recommend
- "播放2首推荐歌曲" → {"action":"play_n_recommend","count":2}
- "播放5首推荐" → {"action":"play_n_recommend","count":5}
- "连续播放10首" → {"action":"play_n_recommend","count":10}
- "播放3首" → {"action":"play_n_recommend","count":3}
- 关键：用户消息中的数字就是count的值，务必提取用户说的数字填入count字段。识别模式：N首、N个、N曲、N首歌、播放N首。
- 只有用户完全没说数字时（如"连续播放推荐"），才默认count=5
- "推荐5首周杰伦的歌" → recommend count=5
- 未指定数量时默认10首

导入歌单规则：
- "导入歌单" 但没给链接 → 不输出JSON，回复纯文本："请提供音乐平台的歌单分享链接（支持网易云音乐、QQ音乐、酷狗音乐）"
- "导入歌单 https://xxx" → import_playlist，提取URL
- "导入歌单并自动确认 https://xxx""自动导入歌单 https://xxx" → auto_import_playlist
- "帮我导入这个歌单 https://xxx" → import_playlist
- URL可出现在消息任意位置，务必提取完整URL

最终规则：
- 可执行的动作指令只输出JSON。无法执行的（如导入歌单没给链接、定时没给分钟数）才输出纯文本。
    """.trimIndent()

    suspend fun sendMessage(userText: String): AiResult {
        val apiKey = storage.getString(SettingsKeys.API_KEY)
        if (apiKey.isNullOrBlank() || apiKey.length < SettingsKeys.MIN_KEY_LENGTH) {
            return AiResult.ConfigMissing
        }

        val baseUrl = storage.getString(SettingsKeys.API_BASE_URL)
            ?: SettingsKeys.DEFAULT_BASE_URL

        val requestBody = ChatCompletionRequest(
            model = "deepseek-chat",
            messages = listOf(
                ChatMessage(role = "system", content = systemPrompt),
                ChatMessage(role = "user", content = userText),
            ),
            temperature = 0.3,
            maxTokens = 512,
        )

        val client = HttpClientFactory.create(
            platformKey = "ai_service",
            additionalConfig = null,
        )

        try {
            val response = client.post("$baseUrl/chat/completions") {
                contentType(ContentType.Application.Json)
                header(HttpHeaders.Authorization, "Bearer $apiKey")
                setBody(requestBody)
            }

            if (response.status.value == 401) {
                storage.remove(SettingsKeys.API_KEY)
                return AiResult.AuthError
            }

            if (!response.status.isSuccess()) {
                val errorBody = try {
                    response.bodyAsText()
                } catch (_: Exception) {
                    "HTTP ${response.status.value}"
                }
                return AiResult.Error("API request failed: $errorBody")
            }

            val responseBody: ChatCompletionResponse = response.body()
            val content = responseBody.choices.firstOrNull()?.message?.content
                ?: return AiResult.Error("Empty response from AI model")

            val jsonText = extractJsonObject(content)

            // 调试日志：打印 AI 原始回复和解析出的 JSON
            val aiLog = co.touchlab.kermit.Logger.withTag("AiAssistant")
            aiLog.d { "AI raw response: $content" }
            aiLog.d { "AI parsed JSON: $jsonText" }

            if (jsonText == null) {
                // AI returned plain text (e.g. asking for a playlist link)
                return AiResult.TextOnly(content.trim())
            }

            val command = try {
                json.decodeFromString<AiCommand>(jsonText)
            } catch (e: Exception) {
                return AiResult.Error("Failed to parse AI command: ${e.message}", e)
            }

            return AiResult.Success(command)
        } catch (e: Exception) {
            return AiResult.Error(
                message = e.message ?: "Unknown network error",
                cause = e,
            )
        } finally {
            client.close()
        }
    }

    suspend fun testConnection(): String? {
        val message = sendMessage("ping")
        return when (message) {
            is AiResult.Success -> null
            is AiResult.TextOnly -> null // plain text response is still a valid connection
            is AiResult.ConfigMissing -> "请先配置 API Key"
            is AiResult.AuthError -> "API Key 无效，已被清除，请重新输入"
            is AiResult.Error -> "连接失败: ${message.message}"
        }
    }

    // visible for testing
    @Suppress("unused")
    internal fun extractJsonObject(raw: String): String? = AiAssistant.extractJsonObjectStatic(raw)

    companion object {
        /**
         * Extract the first JSON object or array from text that may contain
         * markdown fences, explanatory text, or other noise.
         */
        internal fun extractJsonObjectStatic(raw: String): String? {
            var text = raw.trim()
            if (text.startsWith("```")) {
                text = text.substringAfter("\n").substringBeforeLast("\n```")
            }
            val start = text.indexOfFirst { it == '{' || it == '[' }
            if (start == -1) return null
            val open = text[start]
            val close = if (open == '{') '}' else ']'
            var depth = 0
            var inString = false
            var escaped = false
            for (i in start until text.length) {
                val ch = text[i]
                if (escaped) { escaped = false; continue }
                if (ch == '\\') { escaped = true; continue }
                if (ch == '"') { inString = !inString; continue }
                if (inString) continue
                if (ch == open) depth++
                if (ch == close) {
                    depth--
                    if (depth == 0) return text.substring(start, i + 1)
                }
            }
            return null
        }
    }
}

@Serializable
private data class ChatCompletionRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val temperature: Double = 0.7,
    @kotlinx.serialization.SerialName("max_tokens")
    val maxTokens: Int = 512,
)

@Serializable
private data class ChatMessage(
    val role: String,
    val content: String,
)

@Serializable
private data class ChatCompletionResponse(
    val choices: List<Choice>,
)

@Serializable
private data class Choice(
    val message: ChatMessage,
    val index: Int = 0,
)

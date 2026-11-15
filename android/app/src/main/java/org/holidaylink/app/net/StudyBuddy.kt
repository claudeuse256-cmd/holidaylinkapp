package org.holidaylink.app.net

import android.content.Context
import com.google.firebase.Timestamp
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

/**
 * Study Buddy: same brain as the website (ai-agent.js).
 *
 * Reads admin config (aiSettings/aiPrompt/aiCommands), builds student
 * context (users, quizzes, papers, videos, posters, ads, quizPayments),
 * calls Gemini directly, and executes the single-{"action"} contract:
 * reply, send_message (writes chats/{sorted}/messages as the user +
 * notifications, like the site), open_page, search_*, clarify, clear_history.
 */
object StudyBuddy {

    // Same defaults as ai-agent.js. Admin aiSettings/config overrides them.
    private const val DEFAULT_KEY = "AIzaSyAaLbdaT8lGeA_O55JvrmDnmUvjIeClYr4"
    private const val DEFAULT_MODEL = "gemini-2.5-flash"
    private const val SUPPORT_LINE = "Contact Sr Ezra on 0745473752 for help."
    private const val HISTORY_KEY_PREFIX = "hlAiHistory_"
    private const val HISTORY_MAX = 200
    private const val HISTORY_SENT = 24
    private const val CONFIG_TTL_MS = 30_000L
    private const val CONTENT_TTL_MS = 45_000L

    data class AdminConfig(
        val apiKey: String = "",
        val model: String = DEFAULT_MODEL,
        val temperature: Double = 0.4,
        val maxTokens: Int = 400,
        val systemPrompt: String = "",
        val rules: List<String> = emptyList(),
        val personality: String = "",
        val quizzesEnabled: Boolean = true,
        val papersEnabled: Boolean = true,
        val notesEnabled: Boolean = true,
        val videosEnabled: Boolean = true,
        val customCommands: List<Pair<String, String>> = emptyList()
    )

    data class Friend(val id: String, val name: String, val username: String)

    sealed interface AiAction {
        data class Reply(val text: String) : AiAction
        data class SendMessage(val userName: String?, val userId: String?, val text: String) : AiAction
        data class OpenPage(val page: String, val presearch: String? = null) : AiAction
        data class Clarify(val text: String) : AiAction
        data object ClearHistory : AiAction
    }

    private var configCache: Pair<Long, AdminConfig>? = null
    private var friendsCache: Pair<Long, List<Friend>>? = null

    suspend fun loadConfig(): AdminConfig {
        val cached = configCache
        if (cached != null && System.currentTimeMillis() - cached.first < CONFIG_TTL_MS) return cached.second
        val db = FirebaseBackend.db
        val settings = runCatching { db.collection("aiSettings").document("config").get().await() }.getOrNull()
        val prompt = runCatching { db.collection("aiPrompt").document("config").get().await() }.getOrNull()
        val commands = runCatching { db.collection("aiCommands").document("config").get().await() }.getOrNull()
        @Suppress("UNCHECKED_CAST")
        val customs = (commands?.get("customCommands") as? List<Map<String, String>>)
            ?.mapNotNull { m -> val c = m["cmd"]; val r = m["response"]; if (c.isNullOrBlank() || r == null) null else c to r }
            ?: emptyList()
        val cfg = AdminConfig(
            apiKey = settings?.getString("apiKey").orEmpty(),
            model = settings?.getString("model")?.takeIf { it.isNotBlank() } ?: DEFAULT_MODEL,
            temperature = settings?.getDouble("temperature") ?: 0.4,
            maxTokens = (settings?.getLong("maxOutputTokens") ?: 400).toInt(),
            systemPrompt = prompt?.getString("systemPrompt").orEmpty(),
            rules = (prompt?.get("rules") as? List<String>) ?: emptyList(),
            personality = prompt?.getString("personality").orEmpty(),
            quizzesEnabled = commands?.getBoolean("quizzesEnabled") ?: true,
            papersEnabled = commands?.getBoolean("papersEnabled") ?: true,
            notesEnabled = commands?.getBoolean("notesEnabled") ?: true,
            videosEnabled = commands?.getBoolean("videosEnabled") ?: true,
            customCommands = customs
        )
        configCache = System.currentTimeMillis() to cfg
        return cfg
    }

    suspend fun loadFriends(): List<Friend> {
        val cached = friendsCache
        if (cached != null && System.currentTimeMillis() - cached.first < CONTENT_TTL_MS) return cached.second
        val found = FirebaseBackend.db.collection("users")
            .whereEqualTo("activated", true)
            .limit(60).get().await()
        val out = found.documents
            .filter { it.getBoolean("disabled") != true }
            .map { Friend(it.id, it.getString("name").orEmpty(), it.getString("username").orEmpty()) }
        friendsCache = System.currentTimeMillis() to out
        return out
    }

    private fun prefs(context: Context, uid: String) =
        context.getSharedPreferences("hlAi", Context.MODE_PRIVATE)

    fun loadHistory(context: Context, uid: String): MutableList<Pair<String, String>> {
        val raw = prefs(context, uid).getString(HISTORY_KEY_PREFIX + uid, null) ?: return mutableListOf()
        return try {
            val arr = org.json.JSONArray(raw)
            MutableList(minOf(arr.length(), HISTORY_MAX)) { i ->
                val o = arr.getJSONObject(i)
                o.optString("role") to o.optString("text")
            }.toMutableList()
        } catch (_: Exception) { mutableListOf() }
    }

    fun saveHistory(context: Context, uid: String, history: List<Pair<String, String>>) {
        val arr = org.json.JSONArray()
        history.takeLast(HISTORY_MAX).forEach { (role, text) ->
            arr.put(JSONObject().put("role", role).put("text", text))
        }
        prefs(context, uid).edit().putString(HISTORY_KEY_PREFIX + uid, arr.toString()).apply()
    }

    fun clearHistory(context: Context, uid: String) {
        prefs(context, uid).edit().remove(HISTORY_KEY_PREFIX + uid).apply()
    }

    /**
     * Ask Study Buddy. Returns the action to execute; the caller renders
     * replies and performs navigation / message sending.
     */
    suspend fun ask(context: Context, uid: String, userText: String): AiAction {
        val cfg = loadConfig()
        val clean = userText.trim()
        cfg.customCommands.firstOrNull { it.first.equals(clean, ignoreCase = true) }?.let {
            return AiAction.Reply(it.second)
        }
        if (Regex("(?i)^\\s*clear( history| chat)?\\s*$").matches(clean)) {
            clearHistory(context, uid)
            return AiAction.ClearHistory
        }

        val history = loadHistory(context, uid)
        val friends = runCatching { loadFriends() }.getOrDefault(emptyList())
        val me = runCatching { FirebaseBackend.loadProfile(uid) }.getOrNull()

        val intro = cfg.systemPrompt.ifBlank {
            "You are HolidayLink AI, the assistant built into the HolidayLink messaging app. " +
                "Help the student with study, messages, plans and drafts. ${cfg.personality}"
        }
        val rulesText = cfg.rules.joinToString("\n- ", prefix = "Rules:\n- ")
        val system =
            "$intro\n$rulesText\n" +
                "Student: ${me?.name}, plan ${if (me?.activated == true) "active" else "basic"}.\n" +
                "Friends (name=@username:id): ${friends.take(20).joinToString { "${it.name}=@${it.username}:${it.id}" }}.\n" +
                "You MUST reply with exactly one JSON object, no other text. Shapes: " +
                "{\"action\":\"reply\",\"text\":\"...\"}, " +
                "{\"action\":\"send_message\",\"userName\":\"...\",\"userId\":\"...\",\"text\":\"...\"}, " +
                "{\"action\":\"open_page\",\"page\":\"quizzes|papers|events|videos|store|feed|chat|profile\",\"presearch\":\"...\"}, " +
                "{\"action\":\"clarify\",\"text\":\"...\"}, " +
                "{\"action\":\"clear_history\"}. " +
                "For help requests you cannot fulfil, include: $SUPPORT_LINE"

        val contents = org.json.JSONArray()
        history.takeLast(HISTORY_SENT).forEach { (role, text) ->
            contents.put(
                JSONObject().put("role", if (role == "assistant") "model" else "user")
                    .put("parts", org.json.JSONArray().put(JSONObject().put("text", text)))
            )
        }
        contents.put(JSONObject().put("role", "user").put("parts", org.json.JSONArray().put(JSONObject().put("text", clean))))

        val key = cfg.apiKey.ifBlank { DEFAULT_KEY }
        val url = "https://generativelanguage.googleapis.com/v1beta/models/${cfg.model}:generateContent"
        val payload = JSONObject()
            .put("systemInstruction", JSONObject().put("parts", org.json.JSONArray().put(JSONObject().put("text", system))))
            .put("contents", contents)
            .put("generationConfig", JSONObject().put("temperature", cfg.temperature).put("maxOutputTokens", cfg.maxTokens))

        val raw = okhttpAsk(url, key, payload.toString()) ?: return AiAction.Reply("I couldn't reach the assistant just now. $SUPPORT_LINE")
        val actionText = try {
            val root = JSONObject(raw)
            val parts = root.getJSONArray("candidates").getJSONObject(0)
                .getJSONObject("content").getJSONArray("parts")
            (0 until parts.length()).joinToString("") { parts.getJSONObject(it).optString("text") }
        } catch (_: Exception) {
            return AiAction.Reply(raw.take(1000))
        }

        history.add("user" to clean)
        val action = parseAction(actionText)
        if (action is AiAction.Reply) history.add("assistant" to action.text)
        saveHistory(context, uid, history)
        return action
    }

    private fun parseAction(text: String): AiAction {
        return try {
            val start = text.indexOf('{')
            val end = text.lastIndexOf('}')
            val o = JSONObject(if (start >= 0 && end > start) text.substring(start, end + 1) else text)
            when (o.optString("action")) {
                "send_message" -> AiAction.SendMessage(
                    o.optString("userName").takeIf { it.isNotBlank() },
                    o.optString("userId").takeIf { it.isNotBlank() },
                    o.optString("text")
                )
                "open_page" -> AiAction.OpenPage(o.optString("page"), o.optString("presearch").takeIf { it.isNotBlank() })
                "clarify" -> AiAction.Clarify(o.optString("text"))
                "clear_history" -> AiAction.ClearHistory
                else -> AiAction.Reply(o.optString("text").ifBlank { text })
            }
        } catch (_: Exception) {
            AiAction.Reply(text)
        }
    }

    /** Sends a chat message AS the user (website send_message parity). */
    suspend fun sendMessageAs(uid: String, targetId: String?, targetName: String?, text: String): String {
        val toId = targetId ?: resolveUser(targetName ?: "")
        ?: throw ApiException("I couldn't find \"$targetName\". Ask for their exact name.")
        val chatId = listOf(uid, toId).sorted().joinToString("_")
        val db = FirebaseBackend.db
        db.collection("chats").document(chatId).set(
            mapOf("users" to listOf(uid, toId), "isGroup" to false)
        ).await()
        db.collection("chats").document(chatId).collection("messages").add(
            mapOf(
                "senderId" to uid, "text" to text, "type" to "text",
                "timestamp" to Timestamp.now(), "read" to false
            )
        ).await()
        val me = loadMyName(uid)
        db.collection("notifications").add(
            mapOf(
                "userId" to toId, "title" to "New Message",
                "message" to "$me: $text", "read" to false,
                "timestamp" to Timestamp.now()
            )
        ).await()
        return toId
    }

    private suspend fun resolveUser(name: String): String? {
        val friends = loadFriends()
        val needle = name.trim().lowercase()
        friends.firstOrNull { it.id == name }?.let { return it.id }
        return friends.firstOrNull {
            it.name.lowercase() == needle || it.username.lowercase() == needle.trimStart('@')
        }?.id ?: friends.firstOrNull {
            it.name.lowercase().contains(needle) || needle.contains(it.name.lowercase())
        }?.id
    }

    private suspend fun loadMyName(uid: String): String {
        return FirebaseBackend.loadProfile(uid)?.name?.takeIf { it.isNotBlank() } ?: "Someone"
    }

    /** Minimal blocking HTTP (Study Buddy calls are already off-main in callers). */
    private fun okhttpAsk(url: String, key: String, json: String): String? {
        return try {
            val client = okhttp3.OkHttpClient.Builder()
                .connectTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
                .build()
            val body = json.toRequestBody("application/json".toMediaType())
            val req = okhttp3.Request.Builder().url(url).addHeader("x-goog-api-key", key).post(body).build()
            client.newCall(req).execute().use { res ->
                if (!res.isSuccessful) return null
                res.body?.string()
            }
        } catch (_: Exception) {
            null
        }
    }
}

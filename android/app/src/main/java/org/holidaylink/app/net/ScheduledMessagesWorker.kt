package org.holidaylink.app.net

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.holidaylink.app.BuildConfig
import org.json.JSONObject

/** WorkManager is retained for compatibility; dispatch is now performed by Render server. */
class ScheduledMessagesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result = Result.success()

    companion object {
        private const val TAG = "hl-scheduled-messages"
        fun enqueue(context: Context) { /* server scheduler owns delivery; no local duplicate sender */ }
    }
}

/** Authenticated client for the Render/Firestore scheduled-message queue. */
object ServerScheduledMessages {
    private val client = OkHttpClient.Builder().callTimeout(java.time.Duration.ofSeconds(15)).build()
    private val jsonType = "application/json; charset=utf-8".toMediaType()

    suspend fun create(id: String, chatId: String, text: String, atMs: Long) = request(
        "/schedule/create", JSONObject().put("id", id).put("chatId", chatId).put("text", text).put("atMs", atMs)
    )

    suspend fun cancel(id: String) = request("/schedule/cancel", JSONObject().put("id", id))

    private suspend fun request(path: String, payload: JSONObject) = withContext(Dispatchers.IO) {
        val user = FirebaseAuth.getInstance().currentUser ?: error("Sign in again to schedule messages.")
        val token = user.getIdToken(false).await().token ?: error("Could not verify your account.")
        val base = BuildConfig.API_BASE_URL.trimEnd('/')
        val body = payload.toString().toRequestBody(jsonType)
        val req = Request.Builder().url("$base$path").header("Authorization", "Bearer $token").post(body).build()
        client.newCall(req).execute().use { response ->
            val responseText = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                val message = runCatching { JSONObject(responseText).optString("error") }.getOrDefault("SERVER_ERROR")
                error("Schedule server error: $message")
            }
            responseText
        }
    }
}

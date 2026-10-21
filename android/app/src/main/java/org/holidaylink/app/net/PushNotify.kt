package org.holidaylink.app.net

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.holidaylink.app.config.AppConfig
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Asks the HolidayLink server to buzz the other side of a chat or call.
 *
 * Firestore listeners only fire while *this* device's process is alive, so a
 * recipient whose app is closed would never hear about a new message on their own.
 * The server looks up their FCM tokens (written by [org.holidaylink.app.push.PushTokens])
 * and pushes for us.
 *
 * Deliberately fire-and-forget and best effort: a failed push never affects the
 * message, which is already safely in Firestore for the recipient to read.
 */
object PushNotify {

    @Volatile
    private var app: Context? = null

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    private val jsonType = "application/json; charset=utf-8".toMediaType()

    /** Called once from [org.holidaylink.app.HolidayLinkApp]. */
    fun init(context: Context) {
        app = context.applicationContext
    }

    /**
     * "Somebody messaged you". The server works out the sender/group names, so the
     * caller only needs the chat and the preview line shown in the notification.
     */
    fun message(chatId: String, preview: String, messageId: String? = null) {
        if (chatId.isBlank()) return
        val payload = JSONObject()
            .put("chatId", chatId)
            .put("type", "message")
            .put("body", preview.take(240))
        if (!messageId.isNullOrBlank()) payload.put("id", messageId)
        scope.launch { post(payload) }
    }

    /** "Somebody is calling you" — this one has to reach a killed app to be useful. */
    fun call(callId: String) {
        if (callId.isBlank()) return
        scope.launch { post(JSONObject().put("callId", callId).put("type", "call")) }
    }

    private suspend fun post(payload: JSONObject) {
        val context = app ?: return
        runCatching {
            val token = FirebaseAuth.getInstance().currentUser?.getIdToken(false)?.await()?.token
                ?: return@runCatching
            val request = Request.Builder()
                .url(AppConfig.resolve(context) + "/push/send")
                .addHeader("Authorization", "Bearer $token")
                .post(payload.toString().toRequestBody(jsonType))
                .build()
            client.newCall(request).execute().use { response ->
                val responseBody = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    Log.w("HolidayLinkPush", "Push relay returned HTTP ${response.code}: ${responseBody.take(300)}")
                } else {
                    Log.d("HolidayLinkPush", "Push relay accepted request: ${responseBody.take(200)}")
                }
            }
            Unit
        }.onFailure { error ->
            // Never swallow a push failure silently: search Logcat for HolidayLinkPush.
            Log.w("HolidayLinkPush", "Could not contact push relay", error)
        }
    }
}

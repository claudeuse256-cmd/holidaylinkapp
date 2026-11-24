package org.holidaylink.app.net

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Source
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.holidaylink.app.BuildConfig
import org.holidaylink.app.data.AppSettings
import org.holidaylink.app.data.ScheduledMessage
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * The phone's side of scheduled messages.
 *
 * The HolidayLink server sends a scheduled message at its time, but a server on a free host
 * goes to sleep when idle and then sends late (or only when something wakes it). So the phone
 * keeps two timers per message, which survive closing the app and rebooting:
 *  - one minute BEFORE: a notification "Your message to X goes out in 1 minute";
 *  - at the scheduled time: if the server has not sent it yet, the phone sends it itself.
 * The stored message has a fixed id (sched_<id>), so the phone and the server can never
 * produce it twice.
 */
class ScheduledMessagesWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.success()
        // Cancelled (or already handled) in the meantime.
        val item = AppSettings.state.scheduledMessages.firstOrNull { it.id == id } ?: return Result.success()
        val ctx = applicationContext
        val notificationId = Notifier.idFor("scheduled-$id")

        if (inputData.getString(KEY_STAGE) == STAGE_SOON) {
            Notifier.scheduled(
                ctx, notificationId,
                "Scheduled message going out in 1 minute",
                "To ${item.chatLabel}: ${item.text.take(100)}",
                item.chatId
            )
            return Result.success()
        }

        // Overdue by more than a day (phone was off for ages): drop it rather than surprise anyone.
        if (System.currentTimeMillis() - item.atMs > 24 * 3_600_000L) {
            AppSettings.removeScheduled(id)
            return Result.success()
        }
        if (FirebaseAuth.getInstance().currentUser == null) return Result.retry()

        val messageId = "sched_$id"
        val alreadySent = runCatching {
            withTimeoutOrNull(10_000) {
                FirebaseFirestore.getInstance().collection("chats").document(item.chatId)
                    .collection("messages").document(messageId).get(Source.SERVER).await().exists()
            }
        }.getOrNull() == true

        if (!alreadySent) {
            val sent = runCatching { FirestoreChat.send(item.chatId, item.text, messageId = messageId) }.isSuccess
            if (!sent) return if (runAttemptCount < 5) Result.retry() else Result.failure()
        }
        AppSettings.removeScheduled(id)
        Notifier.scheduled(ctx, notificationId, "Scheduled message sent", "To ${item.chatLabel}: ${item.text.take(100)}", item.chatId)
        return Result.success()
    }

    companion object {
        private const val KEY_ID = "id"
        private const val KEY_STAGE = "stage"
        private const val STAGE_SOON = "soon"
        private const val STAGE_SEND = "send"
        private const val ADVANCE_NOTICE_MS = 60_000L

        /** Arms the two timers for one message. */
        fun schedule(context: Context, item: ScheduledMessage, replace: Boolean = true) {
            val work = WorkManager.getInstance(context.applicationContext)
            val policy = if (replace) ExistingWorkPolicy.REPLACE else ExistingWorkPolicy.KEEP
            val now = System.currentTimeMillis()
            fun request(stage: String, delayMs: Long) = OneTimeWorkRequestBuilder<ScheduledMessagesWorker>()
                .setInitialDelay(maxOf(0L, delayMs), TimeUnit.MILLISECONDS)
                .setInputData(workDataOf(KEY_ID to item.id, KEY_STAGE to stage))
                .build()
            if (item.atMs - ADVANCE_NOTICE_MS > now) {
                work.enqueueUniqueWork("sched-soon-${item.id}", policy, request(STAGE_SOON, item.atMs - ADVANCE_NOTICE_MS - now))
            }
            work.enqueueUniqueWork("sched-send-${item.id}", policy, request(STAGE_SEND, item.atMs - now))
        }

        fun cancel(context: Context, id: String) {
            val work = WorkManager.getInstance(context.applicationContext)
            work.cancelUniqueWork("sched-soon-$id")
            work.cancelUniqueWork("sched-send-$id")
        }

        /** Re-arms everything still waiting (after an app update, a reboot, or just on start). */
        fun enqueue(context: Context) {
            runCatching {
                AppSettings.state.scheduledMessages.forEach { schedule(context, it, replace = false) }
            }
        }
    }
}

/** Authenticated client for the Render/Firestore scheduled-message queue. */
object ServerScheduledMessages {
    private val client = OkHttpClient.Builder()
        .connectTimeout(45, TimeUnit.SECONDS)
        .callTimeout(java.time.Duration.ofSeconds(60))
        .build()
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

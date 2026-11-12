package org.holidaylink.app.net

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.util.concurrent.TimeUnit
import org.holidaylink.app.data.AppSettings

/**
 * Background worker that fires scheduled messages whose time has come.
 *
 * Scheduled messages live on the device ([AppSettings.addScheduled]). This worker wakes up
 * periodically (even while HolidayLink is closed) and sends any whose `atMs` has passed,
 * provided there's internet. A send that fails simply stays queued for the next run.
 */
class ScheduledMessagesWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uid = FirebaseBackend.uid.orEmpty()
        if (uid.isBlank()) return Result.success()
        val now = System.currentTimeMillis()
        AppSettings.dueScheduled(now).forEach { item ->
            val sent = runCatching { FirestoreChat.send(item.chatId, item.text) }.isSuccess
            if (sent) AppSettings.removeScheduled(item.id)
        }
        return Result.success()
    }

    companion object {
        private const val TAG = "hl-scheduled-messages"

        /** Enqueue a repeating worker so scheduled messages fire even when the app is closed. */
        fun enqueue(context: Context) {
            val req = PeriodicWorkRequestBuilder<ScheduledMessagesWorker>(15, TimeUnit.MINUTES)
                .addTag(TAG)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork(TAG, ExistingPeriodicWorkPolicy.KEEP, req)
        }
    }
}

package org.holidaylink.app.push

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.holidaylink.app.net.AppUpdater
import org.holidaylink.app.net.Notifier
import java.util.concurrent.TimeUnit

/**
 * Looks for a new HolidayLink build a few times a day, even when the app is closed, and posts a
 * notification the first time it finds one. Tapping it opens the app, where the short update
 * pop-up appears (it can be closed, and becomes required 24 hours after the release).
 */
class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val ctx = applicationContext
        val update = runCatching { AppUpdater.check(ctx) }.getOrNull() ?: return Result.success()
        val prefs = ctx.getSharedPreferences("hl_update_notice", Context.MODE_PRIVATE)
        // One notification per version, not one every few hours.
        if (prefs.getInt("notified_code", 0) >= update.code) return Result.success()
        Notifier.updateAvailable(
            ctx,
            "HolidayLink update available",
            if (update.isMandatory()) "Version ${update.versionName} is required. Tap to update."
            else "Version ${update.versionName} is ready. Tap to update."
        )
        prefs.edit().putInt("notified_code", update.code).apply()
        return Result.success()
    }

    companion object {
        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(6, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            WorkManager.getInstance(context.applicationContext)
                .enqueueUniquePeriodicWork("hl-update-check", ExistingPeriodicWorkPolicy.KEEP, request)
        }
    }
}

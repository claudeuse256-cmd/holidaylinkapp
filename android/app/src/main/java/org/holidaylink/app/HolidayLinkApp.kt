package org.holidaylink.app

import android.app.Application
import org.holidaylink.app.data.AppSettings
import org.holidaylink.app.net.Notifier
import org.holidaylink.app.net.PushNotify
import org.holidaylink.app.net.ScheduledMessagesWorker

/**
 * Application entry point.
 *
 * Its only jobs are the things that must exist before any screen does: keep an
 * application Context for the push helper (sending a Firestore chat message has no
 * Activity to borrow one from) and register the notification channels up front so
 * an alert that arrives very early is not dropped.
 */
class HolidayLinkApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // First, so anything below can never take the app down without a trace.
        CrashGuard.install(this)
        // The settings have to be readable even when a push wakes the app without any
        // Activity: the notification switches live here.
        runCatching { AppSettings.init(this) }
        runCatching { PushNotify.init(this) }
        runCatching { Notifier.ensureChannels(this) }
        // Keep the old WorkManager registration harmless for upgrade compatibility; scheduled
        // delivery now runs from the authenticated Render/Firestore server queue.
        runCatching { ScheduledMessagesWorker.enqueue(this) }
    }
}

package org.holidaylink.app

import android.app.Application
import org.holidaylink.app.net.Notifier
import org.holidaylink.app.net.PushNotify

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
        PushNotify.init(this)
        runCatching { Notifier.ensureChannels(this) }
    }
}

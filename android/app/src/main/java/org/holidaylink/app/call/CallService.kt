package org.holidaylink.app.call

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import org.holidaylink.app.R

/**
 * Keeps an active HolidayLink call alive while the user leaves the app, and shows an ongoing
 * call notification so the call is visible from the lock screen / notification shade.
 *
 * Start it with [start] when a call begins and stop it with [stop] when the call ends. While
 * it runs the process is treated as a foreground service, so WebRTC media keeps flowing even
 * if the user switches to another app or turns the screen off.
 *
 * Starting a foreground service can be refused by the OS (missing permission, restricted
 * background start, a camera/microphone type without the runtime grant). That must never
 * take the app down: every failure here is swallowed and the call simply runs without the
 * notification.
 */
class CallService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val peer = intent?.getStringExtra("peer") ?: "HolidayLink call"
        val kind = intent?.getStringExtra("kind") ?: "audio"
        val started = runCatching {
            val notification = buildNotification(peer, kind)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, foregroundTypes(kind))
            } else {
                startForeground(NOTIFICATION_ID, notification)
            }
        }.isSuccess
        if (!started) {
            stopSelf()
        }
        // Not sticky: a restarted service would have no call behind it and would leave a
        // "call in progress" notification that nothing can dismiss.
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    /**
     * The types declared in the manifest are phoneCall|microphone|camera, but Android only
     * accepts a type whose runtime permission is already granted, so only ask for what we hold.
     */
    private fun foregroundTypes(kind: String): Int {
        var types = 0
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_PHONE_CALL
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            if (granted(Manifest.permission.RECORD_AUDIO)) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            }
            if (kind == "video" && granted(Manifest.permission.CAMERA)) {
                types = types or ServiceInfo.FOREGROUND_SERVICE_TYPE_CAMERA
            }
        }
        return types
    }

    private fun granted(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun buildNotification(peer: String, kind: String): Notification {
        val ctx = applicationContext
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, CHANNEL_NAME, NotificationManager.IMPORTANCE_HIGH).apply {
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            mgr.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_holidaylink)
            .setContentTitle(if (kind == "video") "Video call" else "Voice call")
            .setContentText(peer)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    companion object {
        private const val NOTIFICATION_ID = 1
        private const val CHANNEL_ID = "hl_call_ongoing"
        private const val CHANNEL_NAME = "Ongoing call"

        /** Best-effort start of the ongoing-call foreground service; never throws. */
        fun start(context: Context, callId: String, peer: String, kind: String) {
            runCatching {
                val intent = Intent(context, CallService::class.java).apply {
                    putExtra("callId", callId)
                    putExtra("peer", peer)
                    putExtra("kind", kind)
                }
                ContextCompat.startForegroundService(context, intent)
            }
        }

        /** Best-effort stop of the ongoing-call foreground service; never throws. */
        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, CallService::class.java)) }
        }
    }
}

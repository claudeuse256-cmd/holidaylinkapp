package org.holidaylink.app.call

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import org.holidaylink.app.R

/**
 * Keeps an active HolidayLink call alive while the user leaves the app, and shows an ongoing
 * call notification so the call is visible from the lock screen / notification shade.
 *
 * Start it when a call is answered ([startForeground]) and stop it when the call ends
 * ([stopForeground] / stopSelf). While it runs the process is treated as a foreground service,
 * so WebRTC media keeps flowing even if the user switches to another app or turns the screen
 * off.
 */
class CallService : Service() {

    private val channelId = "hl_call_ongoing"
    private val channelName = "Ongoing call"

    /** The running instance, so the UI can stop the foreground service when a call ends. */
    private var instance: CallService? = null
        private set

    override fun onCreate() {
        super.onCreate()
        instance = this
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val peer = intent?.getStringExtra("peer") ?: "HolidayLink call"
        val kind = intent?.getStringExtra("kind") ?: "audio"
        startForeground(1, buildNotification(peer, kind))
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun buildNotification(peer: String, kind: String): Notification {
        val ctx = applicationContext
        val mgr = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, channelName, NotificationManager.IMPORTANCE_HIGH).apply {
                lockscreenVisibility = Notification.VISIBILITY_PRIVATE
            }
            mgr.createNotificationChannel(channel)
        }
        return NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(R.drawable.ic_stat_holidaylink)
            .setContentTitle(if (kind == "video") "Video call" else "Voice call")
            .setContentText(peer)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            .build()
    }

    fun stopService() {
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    companion object {
        /** Best-effort stop of the ongoing-call foreground service. */
        fun stop(context: Context) {
            context.stopService(Intent(context, CallService::class.java))
        }
    }
}

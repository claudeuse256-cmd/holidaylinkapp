package org.holidaylink.app

import android.content.Context
import android.os.Build
import android.os.Process
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Last-resort crash handling.
 *
 * A crash while the app was starting used to leave no trace on the phone at all: the
 * window just closed, and every relaunch closed the same way with nothing to go on. This
 * writes the stack trace to a private file on the way down, and [lastCrash] lets the next
 * launch show it — so a crash loop becomes a readable report the user can copy instead of
 * a silently dead app. [resetLocalState] is the escape hatch: it drops the saved session
 * and settings, which is what usually breaks a startup loop, and the app opens at sign-in.
 */
object CrashGuard {

    private const val FILE_NAME = "hl_last_crash.txt"

    @Volatile
    private var installed = false

    /** Installs the handler. Safe to call more than once. */
    fun install(context: Context) {
        if (installed) return
        installed = true
        val app = context.applicationContext
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { write(app, thread, error) }
            if (previous != null) {
                previous.uncaughtException(thread, error)
            } else {
                Process.killProcess(Process.myPid())
            }
        }
    }

    /** The most recent crash report, or null when there is nothing to show. */
    fun lastCrash(context: Context): String? {
        val file = File(context.filesDir, FILE_NAME)
        if (!file.exists()) return null
        val text = runCatching { file.readText() }.getOrNull().orEmpty()
        return text.takeIf { it.isNotBlank() }
    }

    /** Forget the stored report (the user has read it). */
    fun acknowledge(context: Context) {
        runCatching { File(context.filesDir, FILE_NAME).delete() }
    }

    /**
     * Break out of a crash loop caused by leftover local state: clear the saved session and
     * settings so the next launch starts clean at the sign-in screen. Nothing server-side is
     * touched; the user simply signs in again.
     */
    fun resetLocalState(context: Context) {
        acknowledge(context)
        runCatching {
            context.getSharedPreferences(
                org.holidaylink.app.config.AppConfig.PREFS,
                Context.MODE_PRIVATE
            ).edit().clear().commit()
        }
        runCatching { com.google.firebase.auth.FirebaseAuth.getInstance().signOut() }
    }

    private fun write(context: Context, thread: Thread, error: Throwable) {
        val stack = StringWriter()
        error.printStackTrace(PrintWriter(stack))
        val header = buildString {
            append("HolidayLink crash report\n")
            append("Time    : ")
                .append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date()))
                .append('\n')
            append("App     : ").append(BuildConfig.VERSION_NAME)
                .append(" (").append(BuildConfig.VERSION_CODE).append(")\n")
            append("Android : ").append(Build.VERSION.RELEASE)
                .append(" (API ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Device  : ").append(Build.MANUFACTURER)
                .append(' ').append(Build.MODEL).append('\n')
            append("Thread  : ").append(thread.name).append("\n\n")
        }
        runCatching {
            File(context.filesDir, FILE_NAME).writeText(header + stack.toString())
        }
    }
}

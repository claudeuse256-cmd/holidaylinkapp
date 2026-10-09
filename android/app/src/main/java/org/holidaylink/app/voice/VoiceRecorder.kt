package org.holidaylink.app.voice

import android.content.Context
import android.media.MediaRecorder
import android.os.Build
import android.util.Log
import java.io.File

/** A finished recording plus a coarse amplitude envelope for the waveform UI. */
data class VoiceClip(
    val file: File,
    val durationMs: Long,
    val waveform: String
)

/**
 * Thin wrapper around [MediaRecorder] for voice messages and AI voice input.
 *
 * Audio is written to the app cache directory (not shared storage), so nothing leaks into the
 * user's gallery unless they explicitly send it. Requires RECORD_AUDIO, which the UI requests
 * at the moment the user taps the microphone rather than at startup.
 */
class VoiceRecorder(private val context: Context) {

    private var recorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var startedAt = 0L
    private var pausedTotalMs = 0L
    private var pausedAtMs = 0L
    private val amplitudes = ArrayList<Int>()

    val isRecording: Boolean get() = recorder != null
    var paused: Boolean = false
        private set

    /** Maximum length: 60s keeps the Firestore blob under the 1 MiB document limit. */
    private val maxDurationMs = 60_000L

    fun start(): Boolean {
        if (recorder != null) return true
        val dir = File(context.cacheDir, "voice").apply { mkdirs() }
        val file = File(dir, "clip_${System.currentTimeMillis()}.m4a")
        val instance = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(context)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }
        return try {
            instance.apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
                setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
                setAudioEncodingBitRate(64_000)
                setAudioSamplingRate(44_100)
                setOutputFile(file.absolutePath)
                prepare()
                start()
            }
            recorder = instance
            outputFile = file
            startedAt = System.currentTimeMillis()
            pausedTotalMs = 0L
            pausedAtMs = 0L
            paused = false
            amplitudes.clear()
            true
        } catch (e: Exception) {
            Log.w(TAG, "could not start recording: ${e.message}")
            runCatching { instance.release() }
            recorder = null
            outputFile = null
            false
        }
    }

    /** Current peak amplitude 0..10000, for the live waveform. */
    fun amplitude(): Int {
        val active = recorder ?: return 0
        return runCatching {
            val raw = active.maxAmplitude
            if (raw <= 0) 0 else ((raw / 10000f) * 100).toInt().coerceIn(0, 100)
        }.getOrDefault(0)
    }

    /** Call periodically while recording to build the waveform envelope. */
    fun sample() {
        if (recorder != null) amplitudes.add(amplitude())
    }

    /** True once the clip has reached [maxDurationMs] so the UI can auto-stop. */
    fun isOvertime(): Boolean =
        recorder != null && System.currentTimeMillis() - startedAt > maxDurationMs

    /** Pause/resume mid-recording (WhatsApp-style). API 24+, minSdk is 26. */
    fun setPaused(pause: Boolean): Boolean {
        val active = recorder ?: return false
        return try {
            if (pause) {
                active.pause()
                pausedAtMs = System.currentTimeMillis()
            } else {
                active.resume()
                pausedTotalMs += System.currentTimeMillis() - pausedAtMs
            }
            paused = pause
            true
        } catch (e: Exception) {
            Log.w(TAG, "could not pause recording: ${e.message}")
            false
        }
    }

    /** Elapsed recording time excluding pauses. */
    fun elapsedMs(): Long =
        if (recorder == null) 0L
        else System.currentTimeMillis() - startedAt - pausedTotalMs -
            (if (paused) System.currentTimeMillis() - pausedAtMs else 0L)

    fun stop(): VoiceClip? {
        val active = recorder ?: return null
        val file = outputFile
        val duration = elapsedMs()
        recorder = null
        outputFile = null
        startedAt = 0L
        pausedTotalMs = 0L
        paused = false
        return try {
            active.stop()
            active.release()
            if (file == null || !file.exists() || file.length() == 0L) null else VoiceClip(file, duration, waveform())
        } catch (e: Exception) {
            // stop() throws when the clip was too short to produce any samples.
            Log.w(TAG, "discarding unusable recording: ${e.message}")
            runCatching { active.release() }
            file?.delete()
            null
        }
    }

    fun cancel() {
        val active = recorder ?: return
        recorder = null
        val file = outputFile
        outputFile = null
        startedAt = 0L
        runCatching { active.stop() }
        runCatching { active.release() }
        file?.delete()
    }

    /** Down-samples the recorded amplitudes into a compact "0-100,0-100" string. */
    private fun waveform(): String {
        if (amplitudes.isEmpty()) return ""
        val target = 40
        val step = (amplitudes.size.toFloat() / target).coerceAtLeast(1f)
        val out = StringBuilder()
        var i = 0
        while (i < amplitudes.size) {
            val end = (i + step.toInt()).coerceAtMost(amplitudes.size)
            var peak = 0
            for (j in i until end) peak = maxOf(peak, amplitudes[j])
            if (out.isNotEmpty()) out.append(',')
            out.append(peak)
            i = end
        }
        return out.toString()
    }

    private companion object {
        const val TAG = "HolidayLinkVoice"
    }
}

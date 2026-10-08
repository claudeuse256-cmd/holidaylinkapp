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
    private val amplitudes = ArrayList<Int>()

    val isRecording: Boolean get() = recorder != null

    /** Maximum length enforced so a forgotten recording cannot run forever. */
    private val maxDurationMs = 120_000L

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

    fun stop(): VoiceClip? {
        val active = recorder ?: return null
        val file = outputFile
        val duration = System.currentTimeMillis() - startedAt
        recorder = null
        outputFile = null
        startedAt = 0L
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

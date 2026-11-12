package org.holidaylink.app.net

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.atomic.AtomicBoolean

/**
 * The sounds a phone makes during a call.
 *
 * * Callee: the phone's own ringtone, looping, with the vibration pattern that goes with
 *   it — exactly what a normal phone call does.
 * * Caller: a ringback tone (the "ring… ring…" you hear abroad) so the person who called
 *   knows the call is actually going out, and they can cancel.
 *
 * The ringback tone is generated with [AudioTrack] so the app ships no audio files.
 */
object CallSounds {

    private val playing = AtomicBoolean(false)
    private var player: MediaPlayer? = null
    private var track: AudioTrack? = null
    private var thread: Thread? = null
    private var vibrator: Vibrator? = null

    /** Ringtone for the phone that is being called. */
    fun startIncoming(context: Context) {
        stop()
        if (!playing.compareAndSet(false, true)) return
        val app = context.applicationContext
        val uri: Uri = runCatching {
            RingtoneManager.getActualDefaultRingtoneUri(app, RingtoneManager.TYPE_RINGTONE)
        }.getOrNull() ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE)
        if (uri != null) {
            runCatching {
                player = MediaPlayer().apply {
                    setDataSource(app, uri)
                    isLooping = true
                    setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    prepare()
                    start()
                }
            }
        }
        vibrateInLoop(app)
    }

    /**
     * Ringback tone for the caller: the classic 440 Hz + 480 Hz double tone, two seconds
     * on and four seconds off, repeated until the call is answered or cancelled.
     */
    fun startRingback() {
        stop()
        if (!playing.compareAndSet(false, true)) return
        val sampleRate = 8000
        val worker = Thread {
            val minBuf = AudioTrack.getMinBufferSize(
                sampleRate, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            ).coerceAtLeast(2048)
            val t = try {
                AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            // Ringtone usage, not voice: VOICE_COMMUNICATION routes to the
                            // earpiece (quiet, near the face), which made callers hear
                            // nothing at all while ringing. Ringtone plays on the speaker
                            // like the real network ringback.
                            .setUsage(AudioAttributes.USAGE_NOTIFICATION_RINGTONE)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(minBuf * 2)
                    .setTransferMode(AudioTrack.MODE_STREAM)
                    .build()
            } catch (_: Throwable) {
                playing.set(false)
                return@Thread
            }
            track = t
            val onMs = 2000
            val offMs = 4000
            val burst = buildTone(sampleRate, onMs, 440.0, 480.0)
            val silence = ShortArray(burst.size)
            try {
                t.play()
                while (playing.get()) {
                    t.write(burst, 0, burst.size)
                    var slept = 0L
                    while (playing.get() && slept < offMs) {
                        Thread.sleep(50)
                        slept += 50
                    }
                    if (!playing.get()) break
                    // The silence is implied by the gap; writing it keeps the track fed.
                    t.write(silence, 0, silence.size)
                }
            } catch (_: Throwable) {
                // The device refused the tone; the call itself is unaffected.
            }
        }
        worker.isDaemon = true
        worker.start()
        thread = worker
    }

    /** Stops whichever sound is playing. */
    fun stop() {
        playing.set(false)
        runCatching { player?.stop() }
        runCatching { player?.release() }
        player = null
        runCatching { track?.stop() }
        runCatching { track?.release() }
        track = null
        thread = null
        runCatching { vibrator?.cancel() }
        vibrator = null
    }

    /** True while a call sound is playing (used by the call screen to avoid overlaps). */
    fun isPlaying(): Boolean = playing.get()

    private fun vibrateInLoop(context: Context) {
        runCatching {
            val v = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val manager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                manager?.defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            } ?: return
            vibrator = v
            val pattern = longArrayOf(0, 700, 900)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                v.vibrate(VibrationEffect.createWaveform(pattern, 0))
            } else {
                @Suppress("DEPRECATION")
                v.vibrate(pattern, 0)
            }
        }
    }

    /** A two-tone telephone ringback burst (440 Hz + 480 Hz), as 16-bit mono PCM. */
    private fun buildTone(sampleRate: Int, millis: Int, hz1: Double, hz2: Double): ShortArray {
        val count = (sampleRate * millis / 1000).coerceAtLeast(1)
        val out = ShortArray(count)
        val twoPi = 2.0 * Math.PI
        for (i in 0 until count) {
            val t = i.toDouble() / sampleRate
            val v = 0.42 * (Math.sin(twoPi * hz1 * t) + Math.sin(twoPi * hz2 * t)) / 2.0
            out[i] = (v * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }
}

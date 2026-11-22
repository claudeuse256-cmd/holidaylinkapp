package org.holidaylink.app.net

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import org.holidaylink.app.data.AppSettings

/**
 * The two small sounds of a conversation, like WhatsApp: a soft "pop" when you send a message
 * and a short "ding" when a message arrives while the chat is open.
 *
 * They are generated as tiny sine bursts, so the app ships no audio files. They follow the
 * phone: nothing plays in silent/vibrate mode, during a call, or when the notification-sound
 * switch in Settings is off.
 */
object ChatSounds {

    private const val RATE = 22050

    private class Note(val hz: Double, val startMs: Int, val lenMs: Int, val decay: Double, val volume: Double)

    private val sendPcm: ShortArray by lazy {
        render(
            listOf(
                Note(620.0, 0, 70, 38.0, 0.45),
                Note(930.0, 45, 90, 30.0, 0.45)
            ),
            150
        )
    }

    private val receivePcm: ShortArray by lazy {
        render(
            listOf(
                Note(1046.5, 0, 180, 17.0, 0.40),
                Note(1568.0, 0, 180, 21.0, 0.18)
            ),
            200
        )
    }

    fun playSend(context: Context) = play(context, sendPcm)

    fun playReceive(context: Context) = play(context, receivePcm)

    private fun enabled(context: Context): Boolean = runCatching {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        AppSettings.state.notifySound &&
            am.ringerMode == AudioManager.RINGER_MODE_NORMAL &&
            am.mode == AudioManager.MODE_NORMAL
    }.getOrDefault(false)

    private fun play(context: Context, pcm: ShortArray) {
        if (!enabled(context)) return
        Thread {
            runCatching {
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(RATE)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(pcm.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()
                track.write(pcm, 0, pcm.size)
                track.play()
                Thread.sleep(pcm.size * 1000L / RATE + 120L)
                track.release()
            }
        }.start()
    }

    private fun render(notes: List<Note>, totalMs: Int): ShortArray {
        val total = RATE * totalMs / 1000
        val mix = DoubleArray(total)
        notes.forEach { note ->
            val start = RATE * note.startMs / 1000
            val length = RATE * note.lenMs / 1000
            for (i in 0 until length) {
                val index = start + i
                if (index >= total) break
                val t = i.toDouble() / RATE
                val attack = minOf(1.0, i / (RATE * 0.004))
                mix[index] += Math.sin(2.0 * Math.PI * note.hz * t) * attack * Math.exp(-t * note.decay) * note.volume
            }
        }
        return ShortArray(total) { (mix[it].coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort() }
    }
}

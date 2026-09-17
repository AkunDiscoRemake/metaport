package com.metaport.xr.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import java.util.concurrent.LinkedBlockingQueue
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.sin

/**
 * Fully procedural audio. MetaPort synthesises every UI and gameplay sound at
 * startup into short PCM buffers, so the APK carries no audio assets.
 *
 * Panning uses the azimuth of the sound source relative to the head, which gives
 * a convincing left/right sense of space inside a stereo headset.
 */
object Sfx {
    private const val TAG = "MetaPort.Sfx"
    private const val RATE = 22050

    const val HOVER = 0
    const val SELECT = 1
    const val BACK = 2
    const val ERROR = 3
    const val GRAB = 4
    const val RELEASE = 5
    const val TICK = 6
    const val HIT = 7
    const val SCORE = 8
    const val WHOOSH = 9

    private var track: AudioTrack? = null
    private var thread: Thread? = null
    @Volatile private var running = false
    private val queue = LinkedBlockingQueue<ShortArray>(64)
    private val samples = HashMap<Int, ShortArray>()

    var masterVolume = 0.7f
    var enabled = true

    fun init() {
        if (track != null) return
        try {
            buildSounds()
            val minBuf = AudioTrack.getMinBufferSize(
                RATE, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT
            )
            val buf = maxOf(minBuf, RATE * 2)
            val t = AudioTrack.Builder()
                .setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_GAME)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                        .setSampleRate(RATE)
                        .setChannelMask(AudioFormat.CHANNEL_OUT_STEREO)
                        .build()
                )
                .setBufferSizeInBytes(buf)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()
            t.play()
            track = t
            running = true
            thread = Thread({
                while (running) {
                    val s = try {
                        queue.take()
                    } catch (e: InterruptedException) {
                        break
                    }
                    try {
                        t.write(s, 0, s.size)
                    } catch (e: Throwable) {
                        Log.w(TAG, "write failed", e)
                    }
                }
            }, "MetaPort-Sfx").apply { isDaemon = true; start() }
        } catch (e: Throwable) {
            Log.w(TAG, "audio init failed: ${e.message}")
            enabled = false
        }
    }

    /**
     * @param pan -1 (hard left) .. +1 (hard right)
     */
    fun play(type: Int, pan: Float = 0f, gain: Float = 1f) {
        if (!enabled || track == null) return
        val src = samples[type] ?: return
        val p = pan.coerceIn(-1f, 1f)
        val gl = (1f - p.coerceAtLeast(0f)) * gain * masterVolume
        val gr = (1f + p.coerceAtMost(0f)) * gain * masterVolume
        val out = ShortArray(src.size / 2 * 2)
        var i = 0
        var o = 0
        while (i + 1 < src.size) {
            out[o++] = (src[i] * gl).toInt().coerceIn(-32768, 32767).toShort()
            out[o++] = (src[i + 1] * gr).toInt().coerceIn(-32768, 32767).toShort()
            i += 2
        }
        queue.offer(out)
    }

    /** Converts a world-space source position + head basis into a stereo pan. */
    fun panFrom(sx: Float, sy: Float, sz: Float, headRight: FloatArray, headFwd: FloatArray): Float {
        val rx = sx * headRight[0] + sy * headRight[1] + sz * headRight[2]
        val fx = sx * headFwd[0] + sy * headFwd[1] + sz * headFwd[2]
        val l = kotlin.math.sqrt(rx * rx + fx * fx)
        if (l < 1e-4f) return 0f
        return (rx / l).coerceIn(-1f, 1f)
    }

    private fun buildSounds() {
        samples[HOVER] = tone(0.055, 1320.0, 0.16, 0.0)
        samples[SELECT] = twoTone(0.05, 0.09, 740.0, 1180.0, 0.30)
        samples[BACK] = twoTone(0.05, 0.09, 620.0, 400.0, 0.26)
        samples[ERROR] = buzz(0.22, 150.0, 0.28)
        samples[GRAB] = tone(0.07, 300.0, 0.22, 0.02)
        samples[RELEASE] = tone(0.09, 520.0, 0.18, 0.0)
        samples[TICK] = tone(0.03, 2200.0, 0.10, 0.0)
        samples[HIT] = noiseBurst(0.13, 0.30, 900.0)
        samples[SCORE] = arpeggio(doubleArrayOf(660.0, 880.0, 1320.0), 0.06, 0.22)
        samples[WHOOSH] = noiseBurst(0.30, 0.18, 380.0)
    }

    private fun tone(dur: Double, freq: Double, amp: Double, bend: Double): ShortArray {
        val n = (RATE * dur).toInt()
        val out = ShortArray(n * 2)
        for (i in 0 until n) {
            val t = i / RATE.toDouble()
            val env = exp(-t * 26.0) * kotlin.math.min(1.0, t * 400.0)
            val f = freq * (1.0 + bend * t * 10.0)
            val v = sin(2.0 * PI * f * t) * env * amp
            val s = (v * 32767).toInt().coerceIn(-32768, 32767).toShort()
            out[i * 2] = s
            out[i * 2 + 1] = s
        }
        return out
    }

    private fun twoTone(d1: Double, d2: Double, f1: Double, f2: Double, amp: Double): ShortArray {
        val a = tone(d1, f1, amp, 0.0)
        val b = tone(d2, f2, amp * 0.85, 0.0)
        return a + b
    }

    private operator fun ShortArray.plus(other: ShortArray): ShortArray {
        val out = ShortArray(size + other.size)
        System.arraycopy(this, 0, out, 0, size)
        System.arraycopy(other, 0, out, size, other.size)
        return out
    }

    private fun buzz(dur: Double, freq: Double, amp: Double): ShortArray {
        val n = (RATE * dur).toInt()
        val out = ShortArray(n * 2)
        for (i in 0 until n) {
            val t = i / RATE.toDouble()
            val env = exp(-t * 9.0)
            var v = sin(2.0 * PI * freq * t)
            v += 0.5 * sin(2.0 * PI * freq * 2.03 * t)
            v += 0.25 * sin(2.0 * PI * freq * 3.01 * t)
            val s = (v / 1.75 * env * amp * 32767).toInt().coerceIn(-32768, 32767).toShort()
            out[i * 2] = s
            out[i * 2 + 1] = s
        }
        return out
    }

    private fun noiseBurst(dur: Double, amp: Double, cutoff: Double): ShortArray {
        val n = (RATE * dur).toInt()
        val out = ShortArray(n * 2)
        var lp = 0.0
        val alpha = (cutoff / (RATE / 2.0)).coerceIn(0.01, 0.95)
        var seed = 12345L
        for (i in 0 until n) {
            val t = i / RATE.toDouble()
            val env = exp(-t * (6.0 / dur)) * kotlin.math.min(1.0, t * 120.0)
            seed = (seed * 6364136223846793005L + 1442695040888963407L)
            val white = ((seed ushr 33).toDouble() / 1073741824.0) - 1.0
            lp += alpha * (white - lp)
            val v = lp * env * amp * 3.0
            val s = (v * 32767).toInt().coerceIn(-32768, 32767).toShort()
            out[i * 2] = s
            out[i * 2 + 1] = s
        }
        return out
    }

    private fun arpeggio(freqs: DoubleArray, step: Double, amp: Double): ShortArray {
        var acc = ShortArray(0)
        for (f in freqs) {
            val t = tone(step, f, amp, 0.0)
            val merged = ShortArray(acc.size + t.size)
            System.arraycopy(acc, 0, merged, 0, acc.size)
            System.arraycopy(t, 0, merged, acc.size, t.size)
            acc = merged
        }
        return acc
    }

    /**
     * Volume is applied in software on purpose: MetaPort never touches the
     * device's media stream so it cannot fight the user's own volume setting.
     */
    fun setStreamVolume(fraction: Float) {
        masterVolume = fraction.coerceIn(0f, 1f)
    }

    fun stop() {
        running = false
        thread?.interrupt()
        thread = null
        try {
            track?.stop()
            track?.release()
        } catch (t: Throwable) {
        }
        track = null
    }
}

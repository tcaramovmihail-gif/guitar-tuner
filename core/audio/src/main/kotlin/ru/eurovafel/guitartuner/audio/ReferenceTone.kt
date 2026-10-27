package ru.eurovafel.guitartuner.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.max

/**
 * Проигрывает эталонную ноту открытой струны. Нужен как альтернатива
 * микрофону: гитарист слышит цель и подстраивается на слух.
 *
 * Класс не требует разрешений: звук идёт через медиа-поток.
 */
class ReferenceTone(
    private val sampleRate: Int = 48_000,
) {
    private val lock = Any()

    @Volatile
    private var track: AudioTrack? = null

    /** @return true, если тон удалось запустить. */
    fun play(frequency: Double, seconds: Double = 2.0): Boolean {
        if (frequency <= 0.0 || seconds <= 0.0) return false
        return playSamples(ToneSynth.pluck(frequency, sampleRate, seconds), seconds)
    }

    /** Проигрывает аккорд: струны входят одна за другой, как при переборе. */
    fun playChord(frequencies: List<Double>, seconds: Double = 2.2): Boolean {
        if (frequencies.isEmpty() || seconds <= 0.0) return false
        return playSamples(ChordSynth.strum(frequencies, sampleRate, seconds), seconds)
    }

    private fun playSamples(samples: FloatArray, seconds: Double): Boolean {
        stop()
        if (samples.isEmpty()) return false

        val audioTrack = runCatching { createTrack(samples.size) }.getOrNull() ?: return false

        return runCatching {
            val written = audioTrack.write(
                samples,
                0,
                samples.size,
                AudioTrack.WRITE_BLOCKING,
            )
            check(written == samples.size) { "Reference tone buffer was not written" }
            audioTrack.play()
            synchronized(lock) { track = audioTrack }

            thread(name = "ReferenceTone", isDaemon = true) {
                runCatching { Thread.sleep((seconds * 1_000.0).toLong() + 80L) }
                releaseIfCurrent(audioTrack)
            }
            true
        }.getOrElse {
            synchronized(lock) {
                if (track === audioTrack) track = null
            }
            releaseNow(audioTrack)
            false
        }
    }

    fun stop() {
        val current = synchronized(lock) {
            val value = track
            track = null
            value
        }
        current?.let(::releaseNow)
    }

    private fun releaseIfCurrent(audioTrack: AudioTrack) {
        val current = synchronized(lock) {
            if (track === audioTrack) {
                track = null
                true
            } else {
                false
            }
        }
        if (current) releaseNow(audioTrack)
    }

    private fun releaseNow(audioTrack: AudioTrack) {
        runCatching { audioTrack.pause() }
        runCatching { audioTrack.flush() }
        runCatching { audioTrack.release() }
    }

    private fun createTrack(sampleCount: Int): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        val required = max(minBuffer, sampleCount * Float.SIZE_BYTES)

        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                    .build(),
            )
            .setAudioFormat(
                AudioFormat.Builder()
                    .setEncoding(AudioFormat.ENCODING_PCM_FLOAT)
                    .setSampleRate(sampleRate)
                    .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                    .build(),
            )
            .setBufferSizeInBytes(required)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()
            .also { audioTrack ->
                check(audioTrack.state == AudioTrack.STATE_INITIALIZED) {
                    "AudioTrack is not initialized"
                }
            }
    }
}

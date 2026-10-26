package ru.eurovafel.guitartuner.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import kotlin.concurrent.thread
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.random.Random

enum class MetronomeSound {
    CLICK,
    DRUM,
}

/**
 * Слышимый метроном без аудиофайлов: короткий деревянный стук синтезируется
 * в памяти и проигрывается через [AudioTrack]. Первая доля такта ниже по тону
 * и громче — по ней удобно начинать песню.
 *
 * В режиме [MetronomeSound.DRUM] вместо щелчка звучит простая партия
 * бочка/малый барабан с тарелкой. Класс не знает про UI и не требует разрешений.
 */
class Metronome(
    private val sampleRate: Int = 48_000,
    private val beatsPerBar: Int = 4,
) {
    @Volatile
    private var running = false

    @Volatile
    private var bpm: Int = 90

    @Volatile
    private var sound: MetronomeSound = MetronomeSound.CLICK

    private var worker: Thread? = null
    private var track: AudioTrack? = null

    private val normalKnock = buildKnock(frequency = 190.0, amplitude = 0.65f)
    private val accentKnock = buildKnock(frequency = 155.0, amplitude = 0.95f)
    private val drumBeats = Array(beatsPerBar) { DrumSynth.beat(sampleRate, it) }
    private val maxSoundSize = maxOf(
        normalKnock.size,
        accentKnock.size,
        drumBeats.maxOfOrNull { it.size } ?: 0,
    )

    fun setBpm(value: Int) {
        bpm = value.coerceIn(MIN_BPM, MAX_BPM)
    }

    /** Смена звука применяется со следующей доли, без перезапуска. */
    fun setSound(value: MetronomeSound) {
        sound = value
    }

    /** @return true, если аудиовыход удалось открыть. */
    fun start(): Boolean {
        if (running) return true

        val audioTrack = runCatching { createTrack() }.getOrNull() ?: return false
        track = audioTrack
        running = true
        worker = thread(name = "Metronome", isDaemon = true) {
            playLoop(audioTrack)
        }
        return true
    }

    fun stop() {
        if (!running && worker == null) return

        running = false
        worker?.join(STOP_TIMEOUT_MS)
        worker = null

        track?.let { audioTrack ->
            runCatching { audioTrack.pause() }
            runCatching { audioTrack.flush() }
            runCatching { audioTrack.release() }
        }
        track = null
    }

    private fun playLoop(audioTrack: AudioTrack) {
        var beat = 0
        var nextBeatNanos = System.nanoTime()
        runCatching { audioTrack.play() }

        while (running) {
            val now = System.nanoTime()
            if (now < nextBeatNanos) {
                val sleepMs = ((nextBeatNanos - now) / 1_000_000L).coerceAtLeast(1L)
                runCatching { Thread.sleep(sleepMs) }
                continue
            }

            val buffer = when (sound) {
                MetronomeSound.CLICK -> if (beat == 0) accentKnock else normalKnock
                MetronomeSound.DRUM -> drumBeats[beat % drumBeats.size]
            }
            runCatching {
                audioTrack.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            }

            beat = (beat + 1) % beatsPerBar
            nextBeatNanos += 60_000_000_000L / bpm
            if (nextBeatNanos < now) {
                nextBeatNanos = now + 60_000_000_000L / bpm
            }
        }
    }

    private fun createTrack(): AudioTrack {
        val minBuffer = AudioTrack.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_OUT_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        val required = max(minBuffer, maxSoundSize * Float.SIZE_BYTES * 2)

        return AudioTrack.Builder()
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_MEDIA)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
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

    private fun buildKnock(frequency: Double, amplitude: Float): FloatArray {
        val size = (sampleRate * KNOCK_SECONDS).toInt()
        val attackSamples = (sampleRate * 0.0015).toInt().coerceAtLeast(1)
        val random = Random(KNOCK_SEED)
        var lowPassedNoise = 0.0

        return FloatArray(size) { index ->
            val time = index.toDouble() / sampleRate
            val attack = if (index < attackSamples) {
                index.toDouble() / attackSamples
            } else {
                1.0
            }

            // Низкий тон даёт «тук», а короткий шумовой всплеск — деревянный призвук.
            // Без высоких частот стук не режет слух и не перекрывает гитару.
            val body = sin(2.0 * PI * frequency * time) * exp(-time * 38.0) +
                0.35 * sin(2.0 * PI * frequency * 2.7 * time) * exp(-time * 55.0)
            lowPassedNoise += 0.22 * (random.nextDouble(-1.0, 1.0) - lowPassedNoise)
            val attackNoise = lowPassedNoise * exp(-time * 95.0) * 0.9

            ((body + attackNoise) * attack * amplitude * 0.8)
                .toFloat()
                .coerceIn(-1f, 1f)
        }
    }

    private companion object {
        const val MIN_BPM = 30
        const val MAX_BPM = 300
        const val KNOCK_SECONDS = 0.09
        const val KNOCK_SEED = 0x51A7
        const val STOP_TIMEOUT_MS = 400L
    }
}

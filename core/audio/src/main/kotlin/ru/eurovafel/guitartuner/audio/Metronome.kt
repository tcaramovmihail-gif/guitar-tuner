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
    beatsPerBar: Int = 4,
    onBeat: ((Int) -> Unit)? = null,
) {
    @Volatile
    private var running = false

    @Volatile
    private var bpm: Int = 90

    /** Размер такта: первая доля звучит с акцентом. */
    @Volatile
    private var beatsPerBar: Int = beatsPerBar.coerceIn(1, 12)

    /** Колбэк каждой доли: индекс от 0. Нужен для визуального пульса. */
    @Volatile
    private var beatListener: ((Int) -> Unit)? = onBeat

    @Volatile
    private var sound: MetronomeSound = MetronomeSound.CLICK

    @Volatile
    private var pattern: DrumPattern = DrumPattern.ROCK

    private var worker: Thread? = null
    private var track: AudioTrack? = null

    private val normalKnock = buildKnock(frequency = 190.0, amplitude = 0.65f)
    private val accentKnock = buildKnock(frequency = 155.0, amplitude = 0.95f)
    private val drumSteps: Map<DrumPattern, List<FloatArray>> =
        DrumPattern.entries.associateWith { value ->
            value.steps.mapIndexed { index, step ->
                // Восьмые на сильной доле звучат с акцентом — рисунок живее.
                DrumSynth.step(sampleRate, step, accent = index % 2 == 0)
            }
        }
    private val maxSoundSize = maxOf(
        normalKnock.size,
        accentKnock.size,
        drumSteps.values.maxOfOrNull { steps -> steps.maxOfOrNull { it.size } ?: 0 } ?: 0,
    )

    fun setBpm(value: Int) {
        bpm = value.coerceIn(MIN_BPM, MAX_BPM)
    }

    /** Смена звука применяется со следующей доли, без перезапуска. */
    fun setSound(value: MetronomeSound) {
        sound = value
    }

    /** Размер такта 2/4, 3/4, 4/4… Акцент переедет на новую первую долю. */
    fun setBeatsPerBar(value: Int) {
        beatsPerBar = value.coerceIn(1, 12)
    }

    /** Слушатель долей вызывается из аудиопотока — UI должен сам перейти в main. */
    fun setOnBeat(listener: ((Int) -> Unit)?) {
        beatListener = listener
    }

    /** Смена барабанного рисунка применяется со следующей доли. */
    fun setPattern(value: DrumPattern) {
        pattern = value
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
        var clickBeat = 0
        var drumStep = 0
        var lastSound = sound
        var nextBeatNanos = System.nanoTime()
        runCatching { audioTrack.play() }

        while (running) {
            val now = System.nanoTime()
            if (now < nextBeatNanos) {
                val sleepMs = ((nextBeatNanos - now) / 1_000_000L).coerceAtLeast(1L)
                runCatching { Thread.sleep(sleepMs) }
                continue
            }

            val currentSound = sound
            if (currentSound != lastSound) {
                // После переключения начинаем рисунок с первой доли.
                clickBeat = 0
                drumStep = 0
                lastSound = currentSound
            }

            val beatNanos = 60_000_000_000L / bpm
            val buffer = when (currentSound) {
                MetronomeSound.CLICK -> {
                    val beat = clickBeat
                    val value = if (beat == 0) accentKnock else normalKnock
                    clickBeat = (beat + 1) % beatsPerBar
                    notifyBeat(beat)
                    value
                }
                MetronomeSound.DRUM -> {
                    val steps = drumSteps.getValue(pattern)
                    val value = steps[drumStep % steps.size]
                    // Тарелка/бочка идут восьмыми, пульс показываем по четвертям.
                    if (drumStep % 2 == 0) notifyBeat((drumStep / 2) % beatsPerBar)
                    drumStep = (drumStep + 1) % steps.size
                    value
                }
            }
            runCatching {
                audioTrack.write(buffer, 0, buffer.size, AudioTrack.WRITE_BLOCKING)
            }

            // Барабаны идут восьмыми, щелчок — четвертями. Темп общий,
            // поэтому рисунок ускоряется и замедляется вместе с песней.
            val intervalNanos = if (currentSound == MetronomeSound.DRUM) {
                beatNanos / 2
            } else {
                beatNanos
            }
            nextBeatNanos += intervalNanos
            if (nextBeatNanos < now) {
                nextBeatNanos = now + intervalNanos
            }
        }
    }

    private fun notifyBeat(index: Int) {
        val listener = beatListener ?: return
        runCatching { listener(index) }
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

package ru.eurovafel.guitartuner.ui.tuner

import androidx.lifecycle.ViewModel
import android.util.Log
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.eurovafel.guitartuner.audio.PcmAudioSource
import ru.eurovafel.guitartuner.audio.ReferenceTone
import ru.eurovafel.guitartuner.dsp.PitchDetector
import ru.eurovafel.guitartuner.dsp.PitchSmoother
import ru.eurovafel.guitartuner.dsp.YinPitchDetector
import ru.eurovafel.guitartuner.model.InstrumentTuning
import ru.eurovafel.guitartuner.model.InTuneHold
import ru.eurovafel.guitartuner.model.NoteReading
import ru.eurovafel.guitartuner.model.PitchMath
import ru.eurovafel.guitartuner.model.StringTargeting
import ru.eurovafel.guitartuner.model.StringTracker
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.log2

data class TunerUiState(
    val listening: Boolean = false,
    val reading: NoteReading? = null,
    val clarity: Double = 0.0,
    val level: Float = 0f,
    val tuning: InstrumentTuning = InstrumentTuning.GUITAR_STANDARD,
    val a4: Double = PitchMath.DEFAULT_A4,
    val autoStringMidi: Int? = null,
    val manualStringMidi: Int? = null,
    val detectedStringMidi: Int? = null,
    val stableCapture: Boolean = true,
    val celebrating: Boolean = false,
    /** Струны, которые уже побывали в строю за текущий сеанс. */
    val tunedMidi: Set<Int> = emptySet(),
    val error: String? = null,
) {
    /** Струна, по которой идёт настройка: ручной выбор важнее автоопределения. */
    val activeStringMidi: Int? get() = manualStringMidi ?: autoStringMidi
}

/**
 * Состояние тюнера и жизненный цикл микрофона.
 *
 * Захват живёт в [PcmAudioSource], детектор и сглаживание — в core:dsp.
 * ViewModel не знает про Compose и разрешения: их запрашивает экран.
 */
class TunerViewModel : ViewModel() {

    private val audioSource = PcmAudioSource(sampleRate = SAMPLE_RATE, frameSize = FRAME_SIZE)
    private val detector: PitchDetector = YinPitchDetector(
        sampleRate = SAMPLE_RATE,
        minFrequency = MIN_FREQUENCY,
        maxFrequency = MAX_FREQUENCY,
        minRms = 0.005,
    )
    private val smoother = PitchSmoother(
        medianWindow = 7,
        emaAlpha = 0.18,
        minClarity = 0.65,
        minRms = 0.005,
        silenceFramesToReset = 4,
    )
    private var stringTracker = createStringTracker(stable = true)
    private val referenceTone = ReferenceTone()

    /** Последняя уверенная нота: держим её, пока струна затухает. */
    private var lastReading: NoteReading? = null
    private var weakFrames = 0

    /** Кадры без уверенного сигнала: пауза перед новым щипком. */
    private var silentFrames = 0

    /** Сколько кадров подряд струна держится в строю (затухание не сбрасывает). */
    private val inTuneHold = InTuneHold(
        framesToCelebrate = CELEBRATE_FRAMES,
        inTuneCents = IN_TUNE_CENTS,
        exitCents = CELEBRATE_EXIT_CENTS,
        exitFrames = CELEBRATE_EXIT_FRAMES,
    )
    private var celebrateMidi: Int? = null

    private val _state = MutableStateFlow(TunerUiState())
    val state: StateFlow<TunerUiState> = _state.asStateFlow()

    fun start() {
        if (_state.value.listening) return

        referenceTone.stop()
        smoother.reset()
        stringTracker.reset()
        resetReadingHold()
        resetCelebration()
        _state.update {
            it.copy(
                listening = true,
                reading = null,
                clarity = 0.0,
                level = 0f,
                autoStringMidi = null,
                detectedStringMidi = null,
                celebrating = false,
                error = null,
            )
        }

        val started = runCatching {
            audioSource.start(::onSamples)
        }.getOrElse { false }

        if (!started) {
            _state.update {
                it.copy(
                    listening = false,
                    reading = null,
                    error = "Не удалось открыть микрофон. Проверьте разрешение и что он не занят другим приложением.",
                )
            }
        }
    }

    fun stop() {
        _state.update {
            it.copy(
                listening = false,
                reading = null,
                clarity = 0.0,
                level = 0f,
                autoStringMidi = null,
                detectedStringMidi = null,
                celebrating = false,
            )
        }
        audioSource.stop()
        referenceTone.stop()
        smoother.reset()
        stringTracker.reset()
        resetReadingHold()
        resetCelebration()
    }

    fun setTuning(tuning: InstrumentTuning) {
        referenceTone.stop()
        smoother.reset()
        stringTracker.reset()
        resetReadingHold()
        resetCelebration()
        _state.update {
            it.copy(
                tuning = tuning,
                reading = null,
                autoStringMidi = null,
                manualStringMidi = null,
                detectedStringMidi = null,
                celebrating = false,
                tunedMidi = emptySet(),
            )
        }
    }

    /**
     * «Стабильно» требует более длинного окна и более уверенных кадров:
     * так струна не перескакивает на обертонах и случайном шуме.
     */
    fun setStableCapture(stable: Boolean) {
        if (_state.value.stableCapture == stable) return

        stringTracker = createStringTracker(stable)
        smoother.reset()
        resetReadingHold()
        resetCelebration()
        _state.update {
            it.copy(
                stableCapture = stable,
                reading = null,
                autoStringMidi = null,
                detectedStringMidi = null,
                celebrating = false,
            )
        }
    }

    /**
     * Эталонная нота выбранной струны. Микрофон выключается, чтобы тюнер
     * не слушал собственный динамик, а струна остаётся выбранной.
     */
    fun playReference() {
        val midi = _state.value.activeStringMidi ?: return
        val frequency = PitchMath.midiToFrequency(midi, _state.value.a4)

        stop()
        _state.update {
            it.copy(
                manualStringMidi = midi,
                error = null,
            )
        }

        val started = runCatching { referenceTone.play(frequency) }.getOrDefault(false)
        if (!started) {
            _state.update {
                it.copy(error = "Не удалось воспроизвести эталонный тон.")
            }
        }
    }

    /**
     * Ручной выбор струны. `null` возвращает автоопределение.
     * Выбор сохраняется до смены строя или явного возврата в авторежим.
     */
    fun selectString(midi: Int?) {
        val tuning = _state.value.tuning
        if (midi != null && midi !in tuning.midiNotes) return

        referenceTone.stop()
        smoother.reset()
        stringTracker.reset()
        resetReadingHold()
        resetCelebration()
        _state.update {
            it.copy(
                manualStringMidi = midi,
                autoStringMidi = null,
                detectedStringMidi = null,
                reading = null,
                celebrating = false,
            )
        }
    }

    fun setA4(a4: Double) {        val clamped = a4.coerceIn(MIN_A4, MAX_A4)
        referenceTone.stop()
        resetCelebration()
        _state.update { current ->
            current.copy(
                a4 = clamped,
                celebrating = false,
                reading = current.reading?.let { PitchMath.reading(it.frequency, clamped) },
            )
        }
    }

    /** Вызывается с потока захвата, поэтому только потокобезопасные обновления. */
    private fun onSamples(samples: FloatArray) {
        if (!_state.value.listening) return

        var rawEnergy = 0.0
        var peak = 0f
        for (value in samples) {
            rawEnergy += value.toDouble() * value
            val magnitude = abs(value)
            if (magnitude > peak) peak = magnitude
        }
        val rawRms = kotlin.math.sqrt(rawEnergy / samples.size)
        // Фиксированное ×8 перегружало тракт на низких струнах: клиппинг
        // добавлял гармоники, и детектор уходил на октаву/обертон. Подбираем
        // усиление под кадр, оставляя запас до 0.9 по пику.
        val gain = inputGain(rawRms, peak)
        val amplified = FloatArray(samples.size) { index ->
            (samples[index] * gain).coerceIn(-1f, 1f)
        }
        val result = detector.detect(amplified)
        val level = levelFromRms(rawRms * INPUT_GAIN)

        val a4 = _state.value.a4
        val previousAuto = _state.value.autoStringMidi
        val activeForFold = _state.value.manualStringMidi ?: previousAuto
        val targetForFold = activeForFold?.let { PitchMath.midiToFrequency(it, a4) }

        // Перед сглаживанием сворачиваем октавы и обертоны к цели: иначе EMA
        // между E2 и E3 проходит через 120 Гц, и стрелка улетает на край шкалы.
        // Чужую струну foldNearTarget не трогает, поэтому смена цели работает.
        val foldInput = if (result != null && targetForFold != null) {
            result.copy(frequency = PitchMath.foldNearTarget(result.frequency, targetForFold))
        } else {
            result
        }
        val frequency = smoother.accept(foldInput)
        val stableCapture = _state.value.stableCapture
        val minClarity = if (stableCapture) {
            STABLE_STRING_MIN_CLARITY
        } else {
            FAST_STRING_MIN_CLARITY
        }
        val minRms = if (stableCapture) STABLE_STRING_MIN_RMS else FAST_STRING_MIN_RMS

        // На затухающей струне детектор часто ошибается: экран обновляем только
        // по уверенным кадрам, а в паузах держим последнюю ноту, чтобы шкала не скакала.
        val confident = result != null &&
            frequency != null &&
            result.clarity >= minClarity &&
            rawRms >= minRms

        // Пауза перед щипком — хороший момент перепроверить струну: после неё
        // могла зазвучать другая. Внутри одной ноты цель остаётся зафиксированной.
        val newPluck: Boolean
        if (confident) {
            newPluck = silentFrames >= NEW_PLUCK_SILENCE_FRAMES
            silentFrames = 0
        } else {
            silentFrames++
            newPluck = false
        }

        val reading: NoteReading?
        if (confident) {
            weakFrames = 0
            reading = smoothReading(frequency, a4)
            lastReading = reading
        } else if (lastReading != null && weakFrames < READ_HOLD_FRAMES) {
            weakFrames++
            reading = lastReading
        } else {
            weakFrames++
            reading = null
            lastReading = null
        }

        // Цель фиксируется на выбранной струне: пока нота звучит, струна не меняется.
        // Иначе обертон (A3 при настройке G3) уводил цель на соседнюю струну.
        val tuning = _state.value.tuning
        val detectedFrequency = if (confident) reading?.frequency else null
        val autoString = if (detectedFrequency != null) {
            val candidate = if (newPluck) {
                StringTargeting.freshCandidate(
                    openStrings = tuning.midiNotes,
                    frequency = detectedFrequency,
                    a4 = a4,
                    active = previousAuto,
                )
            } else {
                StringTargeting.resolve(
                    openStrings = tuning.midiNotes,
                    frequency = detectedFrequency,
                    a4 = a4,
                    active = previousAuto,
                )
            }
            stringTracker.update(tuning.midiNotes, candidate, fastSwitch = newPluck)
        } else {
            stringTracker.update(tuning.midiNotes, null)
        }
        if (autoString != previousAuto) {
            // Новая цель — начинаем сглаживание с чистого листа, без бленда
            // между старой и новой струной.
            smoother.reset()
            resetReadingHold()
        }
        val activeMidi = _state.value.manualStringMidi ?: autoString
        val detectedString = detectedFrequency?.let { frequency ->
            StringTargeting.hint(tuning.midiNotes, frequency, a4, active = activeMidi)
        }

        // «Коза»: струна держится в строю ~2.5 с. Затухание между щипками счётчик
        // не сбрасывает, а уверенный и устойчивый промах — сбрасывает.
        if (activeMidi != null && activeMidi != celebrateMidi) {
            celebrateMidi = activeMidi
            inTuneHold.reset()
        }
        val target = activeMidi?.let { PitchMath.midiToFrequency(it, a4) }
        val cents = if (confident && reading != null && target != null) {
            PitchMath.centsToTarget(reading.frequency, target)
        } else {
            null
        }
        // Тишина/затухание — прогресс замирает, а не сбрасывается.
        inTuneHold.accept(cents)
        val tuned = _state.value.tunedMidi
        val updatedTuned = if (inTuneHold.celebrating && activeMidi != null) {
            tuned + activeMidi
        } else {
            tuned
        }

        _state.update {
            it.copy(
                reading = reading,
                clarity = result?.clarity?.coerceIn(0.0, 1.0) ?: 0.0,
                level = level,
                autoStringMidi = autoString,
                detectedStringMidi = detectedString,
                celebrating = inTuneHold.celebrating,
                tunedMidi = updatedTuned,
            )
        }
    }

    /** Сброс галочек «настроена» — начать круг заново. */
    fun resetTuned() {
        _state.update { it.copy(tunedMidi = emptySet(), celebrating = false) }
        resetCelebration()
    }

    /**
     * Дополнительное сглаживание уже сглаженной частоты: на тихом сигнале
     * одиночные центы не должны дёргать стрелку. Скачок больше полутона —
     * это другая нота, её показываем сразу.
     */
    private fun smoothReading(frequency: Double, a4: Double): NoteReading {
        val previous = lastReading
        val target = if (previous != null) {
            val shift = 1200.0 * log2(frequency / previous.frequency)
            if (abs(shift) > 120.0) {
                frequency
            } else {
                previous.frequency + READ_FOLLOW * (frequency - previous.frequency)
            }
        } else {
            frequency
        }
        return PitchMath.reading(target, a4)
    }

    private fun resetReadingHold() {
        lastReading = null
        weakFrames = 0
        silentFrames = 0
    }

    private fun resetCelebration() {
        inTuneHold.reset()
    }

    override fun onCleared() {
        audioSource.stop()
        referenceTone.stop()
        super.onCleared()
    }

    private fun createStringTracker(stable: Boolean): StringTracker = if (stable) {
        StringTracker(acquireFrames = 6, switchFrames = 16, releaseFrames = 60)
    } else {
        StringTracker(acquireFrames = 3, switchFrames = 6, releaseFrames = 24)
    }

    /**
     * Индикатор показывает только то, что тюнер реально слышит как струну:
     * шум микрофона ниже порога захвата даёт 0%, а не половину шкалы.
     */
    private fun levelFromRms(rms: Double): Float {
        if (rms <= 0.0) return 0f
        val db = 20.0 * log10(rms)
        val floorDb = 20.0 * log10(FAST_STRING_MIN_RMS * INPUT_GAIN)
        val ceilDb = -6.0
        return ((db - floorDb) / (ceilDb - floorDb)).coerceIn(0.0, 1.0).toFloat()
    }

    /**
     * Усиление под конкретный кадр: подтягиваем тихую гитару, но не даём
     * пику выйти за [PEAK_CEILING]. Клиппинг на низких струнах добавлял
     * гармоники, из-за которых детектор уходил на октаву или обертон.
     */
    private fun inputGain(rms: Double, peak: Float): Float {
        if (rms <= 1e-6 || peak <= 0f) return MAX_INPUT_GAIN
        val rmsGain = (TARGET_INPUT_RMS / rms).toFloat()
        val peakGain = PEAK_CEILING / peak
        return minOf(rmsGain, peakGain, MAX_INPUT_GAIN).coerceAtLeast(1f)
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val FRAME_SIZE = 4096
        const val INPUT_GAIN = 8f
        /** Максимум адаптивного усиления и целевой RMS кадра. */
        const val MAX_INPUT_GAIN = 8f
        const val TARGET_INPUT_RMS = 0.12
        const val PEAK_CEILING = 0.9f
        const val MIN_FREQUENCY = 40.0
        const val MAX_FREQUENCY = 1400.0
        const val MIN_A4 = 430.0
        const val MAX_A4 = 450.0
        const val STABLE_STRING_MIN_CLARITY = 0.86
        /** Порог RMS исходного сигнала (до усиления): ≈0.010 при ×8. */
        const val STABLE_STRING_MIN_RMS = 0.00125
        const val FAST_STRING_MIN_CLARITY = 0.78
        /** ≈0.006 при ×8. */
        const val FAST_STRING_MIN_RMS = 0.00075
        /** Пауза перед щипком, после которой перепроверяем струну. */
        const val NEW_PLUCK_SILENCE_FRAMES = 4
        /** Сколько кадров (≈85 мс каждый) держим последнюю ноту на затухании. */
        const val READ_HOLD_FRAMES = 12
        /** Доля, с которой экран догоняет новую частоту. */
        const val READ_FOLLOW = 0.55
        /** Кадров в строю до «козы»: 30 × 85 мс ≈ 2.5 с звучания. */
        const val CELEBRATE_FRAMES = 30
        /** Промах, после которого прогресс сбрасывается, центы. */
        const val CELEBRATE_EXIT_CENTS = 9.0
        /** Сколько кадров подряд должен держаться промах (щипок не сбрасывает). */
        const val CELEBRATE_EXIT_FRAMES = 3
    }
}

package ru.eurovafel.guitartuner.ui.tuner

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import ru.eurovafel.guitartuner.audio.PcmAudioSource
import ru.eurovafel.guitartuner.dsp.PitchDetector
import ru.eurovafel.guitartuner.dsp.PitchSmoother
import ru.eurovafel.guitartuner.dsp.YinPitchDetector
import ru.eurovafel.guitartuner.model.InstrumentTuning
import ru.eurovafel.guitartuner.model.NoteReading
import ru.eurovafel.guitartuner.model.PitchMath
import ru.eurovafel.guitartuner.model.StringTracker
import kotlin.math.log10

data class TunerUiState(
    val listening: Boolean = false,
    val reading: NoteReading? = null,
    val clarity: Double = 0.0,
    val level: Float = 0f,
    val tuning: InstrumentTuning = InstrumentTuning.GUITAR_STANDARD,
    val a4: Double = PitchMath.DEFAULT_A4,
    val autoStringMidi: Int? = null,
    val manualStringMidi: Int? = null,
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
        minRms = 0.004,
    )
    private val smoother = PitchSmoother(minClarity = 0.6, minRms = 0.004)
    private val stringTracker = StringTracker()

    private val _state = MutableStateFlow(TunerUiState())
    val state: StateFlow<TunerUiState> = _state.asStateFlow()

    fun start() {
        if (_state.value.listening) return

        smoother.reset()
        stringTracker.reset()
        _state.update {
            it.copy(
                listening = true,
                reading = null,
                clarity = 0.0,
                level = 0f,
                autoStringMidi = null,
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
            )
        }
        audioSource.stop()
        smoother.reset()
        stringTracker.reset()
    }

    fun setTuning(tuning: InstrumentTuning) {
        smoother.reset()
        stringTracker.reset()
        _state.update {
            it.copy(
                tuning = tuning,
                reading = null,
                autoStringMidi = null,
                manualStringMidi = null,
            )
        }
    }

    /**
     * Ручной выбор струны. `null` возвращает автоопределение.
     * Выбор сохраняется до смены строя или явного возврата в авторежим.
     */
    fun selectString(midi: Int?) {
        val tuning = _state.value.tuning
        if (midi != null && midi !in tuning.midiNotes) return

        smoother.reset()
        stringTracker.reset()
        _state.update {
            it.copy(
                manualStringMidi = midi,
                autoStringMidi = null,
                reading = null,
            )
        }
    }

    fun setA4(a4: Double) {
        val clamped = a4.coerceIn(MIN_A4, MAX_A4)
        _state.update { current ->
            current.copy(
                a4 = clamped,
                reading = current.reading?.let { PitchMath.reading(it.frequency, clamped) },
            )
        }
    }

    /** Вызывается с потока захвата, поэтому только потокобезопасные обновления. */
    private fun onSamples(samples: FloatArray) {
        if (!_state.value.listening) return

        // Микрофон телефона часто пишет тише, чем нужно детектору.
        // Фиксированное усиление поднимает тихую гитару, не меняя форму сигнала.
        val amplified = FloatArray(samples.size) { samples[it] * INPUT_GAIN }
        val result = detector.detect(amplified)

        var energy = 0.0
        for (value in amplified) {
            energy += value.toDouble() * value
        }
        val rms = kotlin.math.sqrt(energy / amplified.size)
        val level = levelFromRms(rms)

        val frequency = smoother.accept(result)
        val a4 = _state.value.a4
        val reading = frequency?.let { PitchMath.reading(it, a4) }

        // Струну меняем только по уверенному кадру: тихий фон и обертоны
        // не должны переключать подсветку.
        val detectedMidi = if (
            result != null &&
            result.clarity >= STRING_MIN_CLARITY &&
            result.rms >= STRING_MIN_RMS
        ) {
            PitchMath.reading(result.frequency, a4).midi
        } else {
            null
        }
        val autoString = stringTracker.update(_state.value.tuning.midiNotes, detectedMidi)

        _state.update {
            it.copy(
                reading = reading,
                clarity = result?.clarity?.coerceIn(0.0, 1.0) ?: 0.0,
                level = level,
                autoStringMidi = autoString,
            )
        }
    }

    override fun onCleared() {
        audioSource.stop()
        super.onCleared()
    }

    private fun levelFromRms(rms: Double): Float {
        if (rms <= 1e-5) return 0f
        val db = 20.0 * log10(rms)
        return ((db + 80.0) / 70.0).coerceIn(0.0, 1.0).toFloat()
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
        const val FRAME_SIZE = 4096
        const val INPUT_GAIN = 8f
        const val MIN_FREQUENCY = 40.0
        const val MAX_FREQUENCY = 1400.0
        const val MIN_A4 = 430.0
        const val MAX_A4 = 450.0
        const val STRING_MIN_CLARITY = 0.72
        const val STRING_MIN_RMS = 0.005
    }
}

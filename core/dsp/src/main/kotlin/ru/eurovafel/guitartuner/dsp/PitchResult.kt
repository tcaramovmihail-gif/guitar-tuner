package ru.eurovafel.guitartuner.dsp

/** Оценка высоты тона по одному окну PCM. */
data class PitchResult(
    val frequency: Double,
    val clarity: Double,
    val rms: Double,
)

interface PitchDetector {
    /** @return null, если сигнал тише порога или периодичность не найдена. */
    fun detect(samples: FloatArray): PitchResult?
}

package ru.eurovafel.guitartuner.model

import kotlin.math.abs

/**
 * Удерживает активную струну, чтобы подсветка не перескакивала на каждый
 * случайный кадр детектора.
 *
 * Новая струна принимается только после [acquireFrames] последовательных
 * кадров при первом захвате и после [switchFrames] кадров при смене струны.
 * После пропадания звука струна остаётся активной ещё [releaseFrames] кадров,
 * чтобы пауза между щипками не сбрасывала экран.
 *
 * Класс не зависит от Android и проверяется JVM-тестами.
 */
class StringTracker(
    private val acquireFrames: Int = 5,
    private val switchFrames: Int = 12,
    private val releaseFrames: Int = 12,
    private val ancestorFrames: Int = 2,
) {
    private var activeMidi: Int? = null
    private var candidateMidi: Int? = null
    private var candidateFrames = 0
    private var silentFrames = 0

    /** @return активная струна или null, пока струна ещё не определена. */
    fun update(
        openStrings: List<Int>,
        detectedMidi: Int?,
        fastSwitch: Boolean = false,
    ): Int? {
        if (openStrings.isEmpty()) {
            reset()
            return null
        }

        val matched = detectedMidi?.let { midi ->
            openStrings
                .filter { it % 12 == midi % 12 }
                .minByOrNull { abs(it - midi) }
        }

        if (matched == null) {
            clearCandidate()
            silentFrames++
            if (silentFrames >= releaseFrames) activeMidi = null
            return activeMidi
        }

        silentFrames = 0
        val active = activeMidi
        if (active == null) {
            addCandidate(matched)
            if (candidateFrames >= acquireFrames) {
                activeMidi = matched
                clearCandidate()
            }
            return activeMidi
        }

        if (active == matched) {
            clearCandidate()
            return active
        }

        addCandidate(matched)
        // Активная струна оказалась обертоном новой (E4 = 3×A2, B3 = 3×E2):
        // настоящая струна не может звучать выше своего обертона, поэтому
        // верим нижней ноте сразу, не дожидаясь длинного окна смены.
        val ancestor = PitchMath.harmonicOf(
            PitchMath.midiToFrequency(active),
            PitchMath.midiToFrequency(matched),
        ) != null
        val needed = when {
            ancestor -> ancestorFrames
            fastSwitch -> NEW_PLUCK_FRAMES
            else -> switchFrames
        }
        if (candidateFrames >= needed) {
            activeMidi = matched
            clearCandidate()
        }
        return activeMidi
    }

    fun reset() {
        activeMidi = null
        clearCandidate()
        silentFrames = 0
    }

    private fun addCandidate(midi: Int) {
        if (candidateMidi == midi) {
            candidateFrames++
        } else {
            candidateMidi = midi
            candidateFrames = 1
        }
    }

    private fun clearCandidate() {
        candidateMidi = null
        candidateFrames = 0
    }

    private companion object {
        /** Смена струны на новой щипковой атаке: быстрее обычного окна. */
        const val NEW_PLUCK_FRAMES = 3
    }
}

package ru.eurovafel.guitartuner.model

import kotlin.math.abs

/**
 * Накопитель времени «в строю».
 *
 * Струна считается настроенной после [framesToCelebrate] уверенных кадров
 * в допуске [inTuneCents]. Тишина и затухание прогресс не сбрасывают
 * (кадр `null` просто не считается), а устойчивый промах — сбрасывает.
 * Одиночный резкий кадр (атака щипка) прогресс не обнуляет: нужно
 * [exitFrames] промахов подряд.
 */
class InTuneHold(
    private val framesToCelebrate: Int = 30,
    private val inTuneCents: Double = 8.0,
    private val exitCents: Double = 9.0,
    private val exitFrames: Int = 3,
) {
    private var frames = 0
    private var missFrames = 0

    /** Струна продержалась в строю достаточно долго. */
    val celebrating: Boolean get() = frames >= framesToCelebrate

    fun reset() {
        frames = 0
        missFrames = 0
    }

    /** @param cents отклонение уверенного кадра; `null` — тишина или неуверенный кадр. */
    fun accept(cents: Double?) {
        if (cents == null) return
        when {
            abs(cents) <= inTuneCents -> {
                missFrames = 0
                if (frames < framesToCelebrate) frames++
            }
            abs(cents) > exitCents -> {
                missFrames++
                if (missFrames >= exitFrames) reset()
            }
            else -> missFrames = 0
        }
    }
}

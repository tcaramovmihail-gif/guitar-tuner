package ru.eurovafel.guitartuner.settings

import android.content.Context
import ru.eurovafel.guitartuner.model.InstrumentTuning
import ru.eurovafel.guitartuner.model.PitchMath

/**
 * Настройки приложения, которые должны переживать перезапуск.
 *
 * Один файл SharedPreferences на всё: тема и масштаб текста уже жили здесь,
 * поэтому новые переключатели не плодят хранилища.
 */
object AppSettings {

    const val PREFS = "guitar_tuner_settings"

    private const val KEY_TEXT_SCALE = "song_text_scale"
    private const val KEY_PAUSE_SECONDS = "pause_before_start_seconds"
    private const val KEY_SCROLL_SPEED = "scroll_speed_multiplier"
    private const val KEY_VIBRATE = "vibrate_in_tune"
    private const val KEY_SEQUENTIAL = "sequential_tuning"
    private const val KEY_TUNING = "tuner_tuning"
    private const val KEY_A4 = "tuner_a4"
    private const val KEY_STABLE_CAPTURE = "tuner_stable_capture"
    private const val KEY_POSITION_PREFIX = "song_position_"

    const val DEFAULT_TEXT_SCALE = 0.75f
    const val DEFAULT_PAUSE_SECONDS = 5
    const val DEFAULT_SCROLL_SPEED = 1f

    /** Допустимые паузы перед автопрокруткой, секунды. */
    val PAUSE_OPTIONS = listOf(0, 3, 5, 10)

    /** Множители скорости прокрутки. */
    val SPEED_OPTIONS = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)

    private fun prefs(context: Context) =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun textScale(context: Context): Float =
        prefs(context).getFloat(KEY_TEXT_SCALE, DEFAULT_TEXT_SCALE).coerceIn(0.75f, 1.75f)

    fun setTextScale(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_TEXT_SCALE, value.coerceIn(0.75f, 1.75f)).apply()
    }

    fun pauseSeconds(context: Context): Int =
        prefs(context).getInt(KEY_PAUSE_SECONDS, DEFAULT_PAUSE_SECONDS)
            .takeIf { it in PAUSE_OPTIONS } ?: DEFAULT_PAUSE_SECONDS

    fun setPauseSeconds(context: Context, seconds: Int) {
        prefs(context).edit().putInt(KEY_PAUSE_SECONDS, seconds).apply()
    }

    fun scrollSpeed(context: Context): Float =
        prefs(context).getFloat(KEY_SCROLL_SPEED, DEFAULT_SCROLL_SPEED)
            .takeIf { it in SPEED_OPTIONS } ?: DEFAULT_SCROLL_SPEED

    fun setScrollSpeed(context: Context, value: Float) {
        prefs(context).edit().putFloat(KEY_SCROLL_SPEED, value).apply()
    }

    fun vibrateInTune(context: Context): Boolean =
        prefs(context).getBoolean(KEY_VIBRATE, true)

    fun setVibrateInTune(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_VIBRATE, enabled).apply()
    }

    fun sequentialTuning(context: Context): Boolean =
        prefs(context).getBoolean(KEY_SEQUENTIAL, false)

    fun setSequentialTuning(context: Context, enabled: Boolean) {
        prefs(context).edit().putBoolean(KEY_SEQUENTIAL, enabled).apply()
    }

    fun tuning(context: Context): InstrumentTuning {
        val saved = prefs(context).getString(KEY_TUNING, null)
        return InstrumentTuning.entries.firstOrNull { it.name == saved }
            ?: InstrumentTuning.GUITAR_STANDARD
    }

    fun setTuning(context: Context, tuning: InstrumentTuning) {
        prefs(context).edit().putString(KEY_TUNING, tuning.name).apply()
    }

    fun a4(context: Context): Double =
        prefs(context).getFloat(KEY_A4, PitchMath.DEFAULT_A4.toFloat()).toDouble()
            .coerceIn(430.0, 450.0)

    fun setA4(context: Context, a4: Double) {
        prefs(context).edit().putFloat(KEY_A4, a4.toFloat()).apply()
    }

    fun stableCapture(context: Context): Boolean =
        prefs(context).getBoolean(KEY_STABLE_CAPTURE, true)

    fun setStableCapture(context: Context, stable: Boolean) {
        prefs(context).edit().putBoolean(KEY_STABLE_CAPTURE, stable).apply()
    }

    /** Позиция текста запоминается отдельно для каждой песни. */
    fun songPosition(context: Context, songId: String): Int =
        prefs(context).getInt(KEY_POSITION_PREFIX + songId.hashCode(), 0)

    fun setSongPosition(context: Context, songId: String, position: Int) {
        prefs(context).edit().putInt(KEY_POSITION_PREFIX + songId.hashCode(), position).apply()
    }
}

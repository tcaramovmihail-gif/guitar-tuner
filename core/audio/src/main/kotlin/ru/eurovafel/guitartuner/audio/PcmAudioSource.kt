package ru.eurovafel.guitartuner.audio

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.concurrent.thread

/**
 * Захват PCM с микрофона. По умолчанию используем UNPROCESSED: агрессивные
 * шумоподавители телефона съедают низкие частоты гитары. Если источник недоступен,
 * откатываемся на MIC.
 *
 * Все вызовы — с фонового потока; колбэк приходит не в main thread.
 */
class PcmAudioSource(
    private val sampleRate: Int = 48_000,
    private val frameSize: Int = 4096,
) {
    private var record: AudioRecord? = null
    private var worker: Thread? = null
    private var active: AtomicBoolean? = null

    val isRunning: Boolean get() = active?.get() == true

    @SuppressLint("MissingPermission")
    fun start(onFrame: (FloatArray) -> Unit): Boolean {
        if (isRunning) return false

        // MIC — самый совместимый источник. UNPROCESSED на части прошивок
        // отдаёт тишину, поэтому он только запасной вариант.
        val recorder = createRecorder(MediaRecorder.AudioSource.MIC)
            ?: createRecorder(MediaRecorder.AudioSource.UNPROCESSED)
            ?: return false

        return try {
            recorder.startRecording()
            record = recorder

            val flag = AtomicBoolean(true)
            active = flag
            worker = thread(name = "guitar-pcm-capture", isDaemon = true) {
                val buffer = FloatArray(frameSize)
                var filled = 0
                while (flag.get()) {
                    val read = try {
                        recorder.read(buffer, filled, frameSize - filled, AudioRecord.READ_BLOCKING)
                    } catch (_: Exception) {
                        -1
                    }
                    if (read > 0) {
                        filled += read
                        if (filled == frameSize) {
                            try {
                                onFrame(buffer.copyOf())
                            } catch (_: Throwable) {
                                flag.set(false)
                                break
                            }
                            filled = 0
                        }
                    } else if (read < 0) {
                        break
                    }
                }
            }
            true
        } catch (_: Throwable) {
            runCatching { recorder.release() }
            false
        }
    }

    /**
     * Останавливает захват, не блокируя вызывающий поток. Освобождение AudioRecord
     * выполняется после выхода читающего потока в отдельном потоке.
     */
    fun stop() {
        val flag = active
        active = null
        flag?.set(false)

        val recorder = record
        record = null
        val threadToJoin = worker
        worker = null

        if (recorder != null) {
            runCatching { recorder.stop() }
            thread(name = "guitar-pcm-release", isDaemon = true) {
                runCatching { threadToJoin?.join(1_000) }
                runCatching { recorder.release() }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun createRecorder(source: Int): AudioRecord? {
        val minBuffer = AudioRecord.getMinBufferSize(
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
        )
        if (minBuffer <= 0) return null

        val recorder = AudioRecord(
            source,
            sampleRate,
            AudioFormat.CHANNEL_IN_MONO,
            AudioFormat.ENCODING_PCM_FLOAT,
            maxOf(minBuffer, frameSize * Float.SIZE_BYTES * 2),
        )
        if (recorder.state != AudioRecord.STATE_INITIALIZED) {
            recorder.release()
            return null
        }
        Log.i("PcmAudioSource", "created source=$source rate=$sampleRate frame=$frameSize")
        return recorder
    }
}

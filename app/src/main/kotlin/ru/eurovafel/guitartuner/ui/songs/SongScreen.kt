package ru.eurovafel.guitartuner.ui.songs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.eurovafel.guitartuner.audio.Metronome
import ru.eurovafel.guitartuner.data.Song
import ru.eurovafel.guitartuner.model.ChordTransposer
import kotlin.math.roundToInt

/**
 * Просмотр песни с автопрокруткой по темпу.
 *
 * Скорость = BPM / 60 * «высота одного удара» (22 dp). Такой вариант не требует
 * разметки каждой строки: темп задаёт музыкант, а высота строки подстраивается
 * под шрифт. Точная синхронизация по долям/секциям — следующий этап (см. docs/PLAN.md).
 */
@Composable
fun SongScreen(song: Song, onBack: () -> Unit) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val pixelsPerBeat = with(density) { 22.dp.toPx() }

    var bpm by rememberSaveable(song.id) { mutableFloatStateOf(song.bpm.toFloat()) }
    var playing by rememberSaveable(song.id) { mutableStateOf(false) }
    var metronomeOn by rememberSaveable(song.id) { mutableStateOf(false) }
    var transpose by rememberSaveable(song.id) { mutableIntStateOf(0) }

    val originalKey = remember(song.id, song.chords, song.key) {
        song.key ?: ChordTransposer.detectKey(song.chords)
    }
    val currentKey = originalKey?.let { ChordTransposer.transposeKey(it, transpose) }
    val displayChords = remember(song.chords, transpose) {
        ChordTransposer.transposeText(song.chords, transpose)
    }

    BackHandler(onBack = onBack)

    val metronome = remember { Metronome() }
    val view = LocalView.current

    // Метроном живёт отдельно от автопрокрутки, но использует тот же BPM.
    // При смене темпа достаточно обновить setBpm — перезапуск не нужен.
    LaunchedEffect(metronomeOn, bpm) {
        metronome.setBpm(bpm.roundToInt())
        if (metronomeOn) {
            if (!metronome.start()) {
                metronomeOn = false
            }
        } else {
            metronome.stop()
        }
    }

    DisposableEffect(metronome) {
        onDispose { metronome.stop() }
    }

    DisposableEffect(playing, metronomeOn, view) {
        view.keepScreenOn = playing || metronomeOn
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(playing, bpm, pixelsPerBeat) {
        if (!playing) return@LaunchedEffect
        var lastFrame = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now ->
                val deltaSeconds = (now - lastFrame) / 1_000_000_000f
                lastFrame = now
                val deltaPx = bpm / 60f * pixelsPerBeat * deltaSeconds
                scrollState.dispatchRawDelta(deltaPx)
                if (scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue) {
                    playing = false
                }
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("song_screen")
            .padding(horizontal = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = onBack) { Text("← Назад") }
            Column(Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${song.artist} · ${bpm.roundToInt()} BPM",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(onClick = { playing = !playing }) {
                Text(if (playing) "Пауза" else "Играть")
            }
            OutlinedButton(onClick = {
                scope.launch { scrollState.scrollTo(0) }
            }) {
                Text("В начало")
            }
        }

        Slider(
            value = bpm,
            onValueChange = { bpm = it },
            valueRange = 40f..220f,
            modifier = Modifier.testTag("bpm_slider"),
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Метроном",
                style = MaterialTheme.typography.bodyMedium,
            )
            Switch(
                checked = metronomeOn,
                onCheckedChange = { metronomeOn = it },
                modifier = Modifier
                    .padding(start = 8.dp)
                    .testTag("metronome_switch"),
            )
            Spacer(Modifier.weight(1f))
            Text(
                text = "4/4 · ${bpm.roundToInt()} BPM",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        Text(
            text = "Щелчки звучат через медиа-громкость телефона.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Тональность",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = when {
                        originalKey == null -> "не определена"
                        transpose == 0 -> originalKey
                        else -> "$currentKey · было $originalKey"
                    },
                    style = MaterialTheme.typography.titleMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }

            OutlinedButton(
                onClick = { transpose = (transpose - 1).coerceAtLeast(-6) },
                enabled = transpose > -6,
            ) {
                Text("−")
            }
            Text(
                text = if (transpose > 0) "+$transpose" else "$transpose",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            OutlinedButton(
                onClick = { transpose = (transpose + 1).coerceAtMost(6) },
                enabled = transpose < 6,
            ) {
                Text("+")
            }
        }

        when {
            transpose > 0 -> Text(
                text = "Капо на $transpose лад — и можно играть исходные аккорды.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            transpose < 0 -> Text(
                text = "Для понижения играйте транспонированные аккорды без капо.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (transpose != 0) {
            TextButton(onClick = { transpose = 0 }) {
                Text("Вернуть исходную тональность")
            }
        }

        HorizontalDivider()

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scrollState)
                .padding(vertical = 12.dp),
        ) {
            Text(
                text = displayChords,
                fontFamily = FontFamily.Monospace,
                fontSize = 16.sp,
                lineHeight = 28.sp,
            )
            Spacer(Modifier.height(640.dp))
        }
    }
}

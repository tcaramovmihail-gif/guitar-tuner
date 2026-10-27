package ru.eurovafel.guitartuner.ui.songs

import android.content.Context
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.eurovafel.guitartuner.audio.DrumPattern
import ru.eurovafel.guitartuner.audio.Metronome
import ru.eurovafel.guitartuner.audio.MetronomeSound
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
fun SongScreen(
    song: Song,
    onBack: () -> Unit,
    onDelete: () -> Unit = {},
    onPlay: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    val pixelsPerBeat = with(density) { 22.dp.toPx() }

    var bpm by rememberSaveable(song.id) { mutableFloatStateOf(song.bpm.toFloat()) }
    var playing by rememberSaveable(song.id) { mutableStateOf(false) }
    var metronomeOn by rememberSaveable(song.id) { mutableStateOf(false) }
    var drumMode by rememberSaveable(song.id) { mutableStateOf(false) }
    var drumPattern by rememberSaveable(song.id) { mutableStateOf(DrumPattern.ROCK) }
    var transpose by rememberSaveable(song.id) { mutableIntStateOf(0) }
    var confirmDelete by rememberSaveable(song.id) { mutableStateOf(false) }
    var textScale by remember { mutableFloatStateOf(loadTextScale(context)) }
    var headerMenuOpen by remember { mutableStateOf(false) }
    var soundMenuOpen by remember { mutableStateOf(false) }

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
    LaunchedEffect(metronomeOn, bpm, drumMode, drumPattern) {
        metronome.setBpm(bpm.roundToInt())
        metronome.setSound(if (drumMode) MetronomeSound.DRUM else MetronomeSound.CLICK)
        metronome.setPattern(drumPattern)
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

    LaunchedEffect(playing, bpm, pixelsPerBeat, textScale) {
        if (!playing) return@LaunchedEffect
        // Пять секунд на то, чтобы взяться за гитару и начать первую строку.
        delay(5_000)
        var lastFrame = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now ->
                val deltaSeconds = (now - lastFrame) / 1_000_000_000f
                lastFrame = now
                val deltaPx = bpm / 60f * pixelsPerBeat * textScale * deltaSeconds
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
            TextButton(
                onClick = onBack,
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp),
            ) {
                Text("←", style = MaterialTheme.typography.titleLarge)
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 6.dp),
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${song.artist} · ${bpm.roundToInt()} BPM",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Звук и рисунок — в верхнем меню, чтобы не занимать строки текста.
            Box {
                TextButton(
                    onClick = { soundMenuOpen = true },
                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                    modifier = Modifier
                        .height(36.dp)
                        .semantics { contentDescription = "Звук и рисунок" },
                ) {
                    Text(
                        text = "♩ Звук",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (metronomeOn) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                DropdownMenu(
                    expanded = soundMenuOpen,
                    onDismissRequest = { soundMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Щелчок") },
                        trailingIcon = { if (!drumMode) Text("✓") },
                        onClick = {
                            drumMode = false
                            soundMenuOpen = false
                        },
                        modifier = Modifier.testTag("metronome_sound_click"),
                    )
                    DropdownMenuItem(
                        text = { Text("Барабаны") },
                        trailingIcon = { if (drumMode) Text("✓") },
                        onClick = { drumMode = true },
                        modifier = Modifier.testTag("metronome_sound_drum"),
                    )
                    if (drumMode) {
                        HorizontalDivider()
                        Text(
                            text = "Рисунок",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                        )
                        DrumPattern.entries.forEach { pattern ->
                            DropdownMenuItem(
                                text = { Text(pattern.title) },
                                trailingIcon = { if (drumPattern == pattern) Text("✓") },
                                onClick = { drumPattern = pattern },
                                modifier = Modifier.testTag("drum_pattern_${pattern.name}"),
                            )
                        }
                    }
                    HorizontalDivider()
                    Text(
                        text = "Размер 4/4 · ${bpm.roundToInt()} BPM",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    )
                }
            }
            // Удаление убрано в меню, чтобы не занимать строку рабочего экрана.
            Box {
                TextButton(
                    onClick = { headerMenuOpen = true },
                    contentPadding = PaddingValues(6.dp),
                    modifier = Modifier.height(36.dp),
                ) {
                    Text("⋮", style = MaterialTheme.typography.titleLarge)
                }
                DropdownMenu(
                    expanded = headerMenuOpen,
                    onDismissRequest = { headerMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Удалить песню") },
                        onClick = {
                            headerMenuOpen = false
                            confirmDelete = true
                        },
                        modifier = Modifier.testTag("song_delete"),
                    )
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    // Начало игры — единственный момент, когда место записывается.
                    if (!playing) onPlay()
                    playing = !playing
                },
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp),
            ) {
                Text(if (playing) "❚❚ Пауза" else "▶ Играть")
            }
            OutlinedButton(
                onClick = { scope.launch { scrollState.scrollTo(0) } },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(36.dp),
            ) {
                Text("↺ В начало")
            }
            Spacer(Modifier.weight(1f))
            Text(
                text = "♩",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Switch(
                checked = metronomeOn,
                onCheckedChange = { metronomeOn = it },
                modifier = Modifier
                    .scale(0.82f)
                    .testTag("metronome_switch"),
            )
        }

        // Масштаб — выше темпа: он меняет высоту текста и нужен чаще.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Масштаб",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = textScale,
                onValueChange = { textScale = it },
                onValueChangeFinished = { saveTextScale(context, textScale) },
                valueRange = 0.75f..1.75f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .testTag("song_scale"),
            )
            Text(
                text = "${(textScale * 100).roundToInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(46.dp),
            )
        }

        // Темп ниже и с подписью, чтобы слайдеры не путались.
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Темп",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Slider(
                value = bpm,
                onValueChange = { bpm = it },
                valueRange = 40f..220f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .testTag("bpm_slider"),
            )
            Text(
                text = "${bpm.roundToInt()} BPM",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(62.dp),
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Тональность",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = originalKey?.let { if (transpose == 0) it else currentKey } ?: "—",
                style = MaterialTheme.typography.titleSmall,
                fontFamily = FontFamily.Monospace,
            )
            if (transpose != 0 && originalKey != null) {
                Text(
                    text = " · было $originalKey",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.weight(1f))
            TextButton(
                onClick = { transpose = (transpose - 1).coerceAtLeast(-6) },
                enabled = transpose > -6,
                contentPadding = PaddingValues(6.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("transpose_down"),
            ) {
                Text("−", style = MaterialTheme.typography.titleMedium)
            }
            Text(
                text = if (transpose > 0) "+$transpose" else "$transpose",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleSmall,
                modifier = Modifier.padding(horizontal = 4.dp),
            )
            TextButton(
                onClick = { transpose = (transpose + 1).coerceAtMost(6) },
                enabled = transpose < 6,
                contentPadding = PaddingValues(6.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("transpose_up"),
            ) {
                Text("+", style = MaterialTheme.typography.titleMedium)
            }
            if (transpose != 0) {
                TextButton(
                    onClick = { transpose = 0 },
                    contentPadding = PaddingValues(4.dp),
                    modifier = Modifier.height(32.dp),
                ) {
                    Text("↺")
                }
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
                fontSize = (16f * textScale).sp,
                lineHeight = (28f * textScale).sp,
            )
            Spacer(Modifier.height(640.dp))
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить песню?") },
            text = {
                Text(
                    "«${song.title}» исчезнет из песенника и поиска. " +
                        "Её можно снова найти в онлайн-каталоге.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Отмена")
                }
            },
        )
    }
}

private const val SONG_PREFS = "guitar_tuner_settings"
private const val TEXT_SCALE_KEY = "song_text_scale"

/** Масштаб текста общий для всех песен: последний выбор запоминается. */
private fun loadTextScale(context: Context): Float =
    context.getSharedPreferences(SONG_PREFS, Context.MODE_PRIVATE)
        .getFloat(TEXT_SCALE_KEY, 0.75f)
        .coerceIn(0.75f, 1.75f)

private fun saveTextScale(context: Context, value: Float) {
    context.getSharedPreferences(SONG_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putFloat(TEXT_SCALE_KEY, value)
        .apply()
}

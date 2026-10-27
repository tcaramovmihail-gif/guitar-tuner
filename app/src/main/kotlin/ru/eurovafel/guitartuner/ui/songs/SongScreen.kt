package ru.eurovafel.guitartuner.ui.songs

import android.os.SystemClock
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import ru.eurovafel.guitartuner.BuildConfig
import ru.eurovafel.guitartuner.R
import ru.eurovafel.guitartuner.audio.DrumPattern
import ru.eurovafel.guitartuner.audio.Metronome
import ru.eurovafel.guitartuner.audio.MetronomeSound
import ru.eurovafel.guitartuner.data.Song
import ru.eurovafel.guitartuner.model.ChordShapes
import ru.eurovafel.guitartuner.model.ChordTransposer
import ru.eurovafel.guitartuner.settings.AppSettings
import ru.eurovafel.guitartuner.ui.chords.ChordDiagram
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
    onBpmChange: (Int) -> Unit = {},
    onEdit: () -> Unit = {},
    onOpenMetronome: () -> Unit = {},
) {
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val pixelsPerBeat = with(density) { 22.dp.toPx() }

    var bpm by rememberSaveable(song.id, song.bpm) { mutableFloatStateOf(song.bpm.toFloat()) }
    var playing by rememberSaveable(song.id) { mutableStateOf(false) }
    var metronomeOn by rememberSaveable(song.id) { mutableStateOf(false) }
    var drumMode by rememberSaveable(song.id) { mutableStateOf(false) }
    var drumPattern by rememberSaveable(song.id) { mutableStateOf(DrumPattern.ROCK) }
    var transpose by rememberSaveable(song.id) { mutableIntStateOf(0) }
    var confirmDelete by rememberSaveable(song.id) { mutableStateOf(false) }
    var textScale by remember { mutableFloatStateOf(AppSettings.textScale(context)) }
    var speed by remember { mutableFloatStateOf(AppSettings.scrollSpeed(context)) }
    var headerMenuOpen by remember { mutableStateOf(false) }
    var soundMenuOpen by remember { mutableStateOf(false) }
    var countdown by remember { mutableIntStateOf(0) }
    var chordDialog by remember { mutableStateOf<String?>(null) }
    var tapTimes by remember { mutableStateOf<List<Long>>(emptyList()) }
    var pulseOn by remember { mutableStateOf(false) }
    var beatPulse by remember { mutableIntStateOf(0) }

    val pauseSeconds = remember { AppSettings.pauseSeconds(context) }
    val originalKey = remember(song.id, song.chords, song.key) {
        song.key ?: ChordTransposer.detectKey(song.chords)
    }
    val currentKey = originalKey?.let { ChordTransposer.transposeKey(it, transpose) }
    val displayChords = remember(song.chords, transpose) {
        ChordTransposer.transposeText(song.chords, transpose)
    }
    val currentBpm by rememberUpdatedState(bpm)
    val currentSpeed by rememberUpdatedState(speed)

    BackHandler(onBack = onBack)

    val metronome = remember { Metronome() }

    LaunchedEffect(metronome) {
        metronome.setOnBeat { beat ->
            view.post {
                beatPulse++
                if (beat == 0) pulseOn = true
            }
        }
    }
    LaunchedEffect(beatPulse) {
        if (beatPulse == 0) return@LaunchedEffect
        pulseOn = true
        delay(90)
        pulseOn = false
    }

    // Метроном живёт отдельно от автопрокрутки, но использует тот же BPM.
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
        onDispose {
            metronome.setOnBeat(null)
            metronome.stop()
        }
    }

    DisposableEffect(playing, metronomeOn, view) {
        view.keepScreenOn = playing || metronomeOn
        onDispose { view.keepScreenOn = false }
    }

    // Позиция текста запоминается отдельно для каждой песни.
    val initialPosition = remember(song.id) { AppSettings.songPosition(context, song.id) }
    var positionRestored by remember(song.id) { mutableStateOf(false) }
    LaunchedEffect(scrollState.maxValue, song.id) {
        if (!positionRestored && scrollState.maxValue > 0) {
            if (initialPosition > 0) {
                scrollState.scrollTo(initialPosition.coerceAtMost(scrollState.maxValue))
            }
            positionRestored = true
        }
    }
    LaunchedEffect(scrollState) {
        snapshotFlow { scrollState.value }.collectLatest { value ->
            delay(500)
            if (positionRestored) {
                AppSettings.setSongPosition(context, song.id, value)
            }
        }
    }

    // Один эффект на всё проигрывание: пауза перед стартом не сбрасывается,
    // если подвигать темп или скорость — они читаются через rememberUpdatedState.
    LaunchedEffect(playing) {
        if (!playing) {
            countdown = 0
            return@LaunchedEffect
        }
        countdown = pauseSeconds
        repeat(pauseSeconds) {
            delay(1_000)
            countdown = pauseSeconds - it - 1
        }
        countdown = 0

        var lastFrame = withFrameNanos { it }
        while (isActive) {
            withFrameNanos { now ->
                val deltaSeconds = (now - lastFrame) / 1_000_000_000f
                lastFrame = now
                val deltaPx = currentBpm / 60f * pixelsPerBeat * textScale *
                    currentSpeed * deltaSeconds
                scrollState.dispatchRawDelta(deltaPx)
                if (scrollState.maxValue > 0 && scrollState.value >= scrollState.maxValue) {
                    playing = false
                }
            }
        }
    }

    // Ручной скролл — тоже пауза: музыкант хочет посмотреть дальше.
    LaunchedEffect(scrollState.isScrollInProgress) {
        if (scrollState.isScrollInProgress && playing) playing = false
    }

    fun applyBpm(value: Int) {
        val clamped = value.coerceIn(40, 300)
        bpm = clamped.toFloat()
        onBpmChange(clamped)
    }

    fun registerTap() {
        val now = SystemClock.elapsedRealtime()
        val taps = (tapTimes.filter { now - it <= 3_000 } + now).takeLast(5)
        tapTimes = taps
        if (taps.size < 2) return
        val intervals = taps.zipWithNext { first, second -> second - first }
        val average = intervals.average()
        if (average > 0) applyBpm((60_000.0 / average).roundToInt())
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("song_screen")
            .padding(horizontal = 12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("song_back"),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = "Назад",
                )
            }
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 4.dp),
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(song.artist)
                        append(" · ")
                        append(song.source ?: "Из каталога")
                        append(" · ")
                        append(bpm.roundToInt())
                        append(" BPM")
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Box {
                IconButton(
                    onClick = { soundMenuOpen = true },
                    modifier = Modifier
                        .size(40.dp)
                        .semantics { contentDescription = "Звук и рисунок" },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_metronome),
                        contentDescription = null,
                        tint = if (metronomeOn) {
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
                    DropdownMenuItem(
                        text = { Text("Открыть метроном") },
                        onClick = {
                            soundMenuOpen = false
                            onOpenMetronome()
                        },
                    )
                }
            }
            Box {
                IconButton(
                    onClick = { headerMenuOpen = true },
                    modifier = Modifier
                        .size(40.dp)
                        .semantics { contentDescription = "Ещё" },
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_more),
                        contentDescription = null,
                    )
                }
                DropdownMenu(
                    expanded = headerMenuOpen,
                    onDismissRequest = { headerMenuOpen = false },
                ) {
                    DropdownMenuItem(
                        text = { Text("Редактировать") },
                        onClick = {
                            headerMenuOpen = false
                            onEdit()
                        },
                        modifier = Modifier.testTag("song_edit"),
                    )
                    DropdownMenuItem(
                        text = { Text("Скорость прокрутки ×${formatSpeed(speed)}") },
                        onClick = { speed = nextSpeed(speed) },
                        modifier = Modifier.testTag("song_speed"),
                    )
                    DropdownMenuItem(
                        text = { Text("Метроном") },
                        onClick = {
                            headerMenuOpen = false
                            onOpenMetronome()
                        },
                    )
                    HorizontalDivider()
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
                .height(44.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Button(
                onClick = {
                    // Начало игры — единственный момент, когда место записывается.
                    if (!playing) onPlay()
                    playing = !playing
                },
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(38.dp)
                    .testTag("song_play"),
            ) {
                Icon(
                    painter = painterResource(
                        if (playing) R.drawable.ic_pause else R.drawable.ic_play,
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    when {
                        playing && countdown > 0 -> "Старт $countdown"
                        playing -> "Пауза"
                        else -> "Играть"
                    },
                )
            }
            OutlinedButton(
                onClick = {
                    playing = false
                    scope.launch { scrollState.scrollTo(0) }
                },
                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
                modifier = Modifier.height(38.dp),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_restart),
                    contentDescription = "В начало",
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            val pulseScale by animateFloatAsState(
                targetValue = if (pulseOn) 1.35f else 1f,
                animationSpec = tween(durationMillis = if (pulseOn) 70 else 160),
                label = "song-pulse",
            )
            Box(
                Modifier
                    .size(12.dp)
                    .scale(pulseScale)
                    .background(
                        color = if (metronomeOn) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        shape = CircleShape,
                    )
                    .testTag("song_pulse"),
            )
            Switch(
                checked = metronomeOn,
                onCheckedChange = { metronomeOn = it },
                modifier = Modifier
                    .scale(0.78f)
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
                onValueChangeFinished = { AppSettings.setTextScale(context, textScale) },
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
                onValueChangeFinished = { applyBpm(bpm.roundToInt()) },
                valueRange = 40f..240f,
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
                    .testTag("bpm_slider"),
            )
            Text(
                text = "${bpm.roundToInt()} BPM",
                style = MaterialTheme.typography.labelSmall,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(58.dp),
            )
            TextButton(
                onClick = { registerTap() },
                contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                modifier = Modifier
                    .height(32.dp)
                    .testTag("tap_tempo"),
            ) {
                Text("Тап")
            }
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
            Text(
                text = "×${formatSpeed(speed)} · пауза $pauseSeconds с",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
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

        val chordLayout = remember { mutableStateOf<TextLayoutResult?>(null) }
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
                onTextLayout = { chordLayout.value = it },
                modifier = Modifier.pointerInput(displayChords, textScale) {
                    detectTapGestures { position ->
                        // Касание текста — пауза, даже если аккорд не распознан.
                        if (playing) playing = false
                        val layout = chordLayout.value ?: return@detectTapGestures
                        val offset = layout.getOffsetForPosition(position)
                        chordAt(displayChords, offset)?.let { chordDialog = it }
                    }
                },
            )
            Spacer(Modifier.height(480.dp))
            Text(
                text = "${song.source ?: "Из каталога"} · Guitar Tuner ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Нажмите на аккорд в тексте, чтобы увидеть аппликатуру",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }

    chordDialog?.let { name ->
        val shape = remember(name) { ChordShapes.find(name) }
        AlertDialog(
            onDismissRequest = { chordDialog = null },
            title = { Text(name) },
            text = {
                if (shape == null) {
                    Text("Аппликатуры для этого аккорда нет в справочнике.")
                } else {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        ChordDiagram(
                            frets = shape.frets,
                            modifier = Modifier
                                .width(180.dp)
                                .height(210.dp),
                        )
                        Text(
                            text = shape.noteNames().joinToString(" "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = "Стандартный строй, E A D G B E",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { chordDialog = null }) { Text("Закрыть") }
            },
        )
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

/** «×1», «×1.25»: без лишних нулей после запятой. */
private fun formatSpeed(value: Float): String =
    if (value == value.toInt().toFloat()) value.toInt().toString() else value.toString()

private fun nextSpeed(current: Float): Float {
    val options = AppSettings.SPEED_OPTIONS
    val index = options.indexOf(current)
    return options[(index + 1).mod(options.size)]
}

/**
 * Аккорд под курсором. Опираемся на [ChordShapes]: если слово похоже на
 * аккорд и для него есть схема — показываем, иначе считаем это словом песни.
 */
internal fun chordAt(text: String, offset: Int): String? {
    if (offset !in text.indices) return null
    fun isTokenChar(char: Char): Boolean =
        char.isLetterOrDigit() || char == '#' || char == 'b' || char == '♯' ||
            char == '♭' || char == '+' || char == '/' || char == '°'

    var start = offset
    while (start > 0 && isTokenChar(text[start - 1])) start--
    var end = offset
    while (end < text.length && isTokenChar(text[end])) end++
    val token = text.substring(start, end).trim()
    if (token.isEmpty() || token.length > 10) return null
    return token.takeIf { ChordShapes.find(it) != null }
}

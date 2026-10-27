package ru.eurovafel.guitartuner.ui.metronome

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import ru.eurovafel.guitartuner.R
import ru.eurovafel.guitartuner.audio.DrumPattern
import ru.eurovafel.guitartuner.audio.Metronome
import ru.eurovafel.guitartuner.audio.MetronomeSound
import kotlin.math.roundToInt

/** Полноэкранный метроном: темп, размер, звук и крупный визуальный пульс. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MetronomeScreen(onBack: () -> Unit) {
    var bpm by rememberSaveable { mutableIntStateOf(90) }
    var beatsPerBar by rememberSaveable { mutableIntStateOf(4) }
    var running by rememberSaveable { mutableStateOf(false) }
    var drumMode by rememberSaveable { mutableStateOf(false) }
    var pattern by rememberSaveable { mutableStateOf(DrumPattern.ROCK) }
    var beat by remember { mutableIntStateOf(-1) }
    var pulseOn by remember { mutableStateOf(false) }
    val view = LocalView.current

    val metronome = remember { Metronome() }

    LaunchedEffect(metronome) {
        metronome.setOnBeat { index ->
            view.post {
                beat = index
                pulseOn = true
            }
        }
    }
    LaunchedEffect(pulseOn) {
        if (!pulseOn) return@LaunchedEffect
        delay(100)
        pulseOn = false
    }
    LaunchedEffect(running, bpm, beatsPerBar, drumMode, pattern) {
        metronome.setBpm(bpm)
        metronome.setBeatsPerBar(beatsPerBar)
        metronome.setSound(if (drumMode) MetronomeSound.DRUM else MetronomeSound.CLICK)
        metronome.setPattern(pattern)
        if (running) {
            if (!metronome.start()) running = false
        } else {
            metronome.stop()
            beat = -1
        }
    }
    DisposableEffect(metronome) {
        onDispose {
            metronome.setOnBeat(null)
            metronome.stop()
        }
    }

    BackHandler {
        metronome.stop()
        onBack()
    }

    val pulseScale by animateFloatAsState(
        targetValue = if (pulseOn) 1.18f else 1f,
        animationSpec = tween(durationMillis = if (pulseOn) 70 else 170),
        label = "metronome-pulse",
    )
    val beatColor = if (beat == 0) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.primaryContainer
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("metronome_screen"),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    painter = painterResource(R.drawable.ic_back),
                    contentDescription = "Назад",
                )
            }
            Text(
                text = "Метроном",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
            Icon(
                painter = painterResource(R.drawable.ic_metronome),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(end = 16.dp),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(200.dp)
                    .scale(pulseScale)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                beatColor,
                                MaterialTheme.colorScheme.surfaceVariant,
                            ),
                        ),
                        shape = CircleShape,
                    )
                    .semantics {
                        contentDescription = if (running) {
                            "Доля ${beat + 1} из $beatsPerBar"
                        } else {
                            "Метроном остановлен"
                        }
                    }
                    .testTag("metronome_pulse"),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = bpm.toString(),
                        fontSize = 52.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = "BPM · $beatsPerBar/4",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                (0 until beatsPerBar).forEach { index ->
                    Box(
                        modifier = Modifier
                            .size(if (index == 0) 16.dp else 12.dp)
                            .background(
                                color = when {
                                    !running -> MaterialTheme.colorScheme.surfaceVariant
                                    beat == index -> MaterialTheme.colorScheme.primary
                                    else -> MaterialTheme.colorScheme.primaryContainer
                                },
                                shape = CircleShape,
                            ),
                    )
                }
            }

            Button(
                onClick = { running = !running },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .testTag("metronome_toggle"),
            ) {
                Icon(
                    painter = painterResource(
                        if (running) R.drawable.ic_pause else R.drawable.ic_play,
                    ),
                    contentDescription = null,
                    modifier = Modifier.size(22.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(if (running) "Остановить" else "Запустить")
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(
                    onClick = { bpm = (bpm - 5).coerceAtLeast(30) },
                    modifier = Modifier.testTag("metronome_minus"),
                ) {
                    Text("−5")
                }
                Slider(
                    value = bpm.toFloat(),
                    onValueChange = { bpm = it.roundToInt() },
                    valueRange = 30f..240f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .testTag("metronome_bpm"),
                )
                OutlinedButton(
                    onClick = { bpm = (bpm + 5).coerceAtMost(240) },
                    modifier = Modifier.testTag("metronome_plus"),
                ) {
                    Text("+5")
                }
            }

            Text(
                text = "Размер такта",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                listOf(2, 3, 4, 6).forEach { value ->
                    FilterChip(
                        selected = beatsPerBar == value,
                        onClick = { beatsPerBar = value },
                        label = { Text("$value/4") },
                        modifier = Modifier.testTag("metronome_size_$value"),
                    )
                }
            }

            Text(
                text = "Звук",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.fillMaxWidth(),
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                FilterChip(
                    selected = !drumMode,
                    onClick = { drumMode = false },
                    label = { Text("Щелчок") },
                    modifier = Modifier.testTag("metronome_sound_click"),
                )
                FilterChip(
                    selected = drumMode,
                    onClick = { drumMode = true },
                    label = { Text("Барабаны") },
                    modifier = Modifier.testTag("metronome_sound_drum"),
                )
            }

            if (drumMode) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    DrumPattern.entries.forEach { item ->
                        FilterChip(
                            selected = pattern == item,
                            onClick = { pattern = item },
                            label = { Text(item.title) },
                            modifier = Modifier.testTag("metronome_pattern_${item.name}"),
                        )
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        text = "Первая доля такта звучит ниже и громче — по ней удобно " +
                            "начинать песню. Темп можно задать и в самой песне.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    TextButton(onClick = onBack) {
                        Text("Вернуться")
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

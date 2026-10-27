package ru.eurovafel.guitartuner.ui.chords

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import ru.eurovafel.guitartuner.audio.ReferenceTone
import ru.eurovafel.guitartuner.model.ChordGroup
import ru.eurovafel.guitartuner.model.ChordLibrary
import ru.eurovafel.guitartuner.model.ChordShape
import androidx.compose.foundation.shape.RoundedCornerShape

/**
 * Библиотека базовых аккордов с аппликатурами и прослушиванием.
 *
 * Схема рисуется по номерам ладов, поэтому не зависит от картинок и работает
 * офлайн. Звук синтезируется тем же движком, что и эталонный тон тюнера.
 */
@Composable
fun ChordsScreen(modifier: Modifier = Modifier) {
    val tone = remember { ReferenceTone() }
    val scope = rememberCoroutineScope()
    var playing by rememberSaveable { mutableStateOf<String?>(null) }

    DisposableEffect(tone) {
        onDispose { tone.stop() }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .testTag("chords_screen"),
    ) {
        Column(Modifier.padding(horizontal = 20.dp, vertical = 12.dp)) {
            Text(
                text = "Аккорды",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )
            Text(
                text = "Нажмите на аккорд, чтобы услышать звучание",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            ChordGroup.entries.forEach { group ->
                item(
                    key = "group_${group.name}",
                    span = { GridItemSpan(maxLineSpan) },
                ) {
                    Text(
                        text = group.title,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(top = 12.dp, bottom = 2.dp),
                    )
                }
                items(
                    items = ChordLibrary.byGroup(group),
                    key = { "chord_${it.name}" },
                ) { chord ->
                    ChordCard(
                        chord = chord,
                        playing = playing == chord.name,
                        onClick = {
                            playing = chord.name
                            tone.playChord(chord.frequencies())
                            scope.launch {
                                delay(2_200)
                                if (playing == chord.name) playing = null
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun ChordCard(
    chord: ChordShape,
    playing: Boolean,
    onClick: () -> Unit,
) {
    val container = if (playing) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val content = if (playing) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("chord_${chord.name}"),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = container, contentColor = content),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = chord.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    maxLines = 1,
                )
                if (playing) {
                    Text(
                        text = " ♪",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            ChordDiagram(
                frets = chord.frets,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(0.86f)
                    .padding(top = 4.dp),
            )
            Text(
                text = chord.noteNames().joinToString(" "),
                fontSize = 9.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/** Схема аккорда: 6 струн, 5 ладов, точки и открытые/заглушённые струны. */
@Composable
private fun ChordDiagram(
    frets: List<Int>,
    modifier: Modifier = Modifier,
) {
    val gridColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val dotColor = MaterialTheme.colorScheme.primary
    val textColor = MaterialTheme.colorScheme.onSurfaceVariant
    val maxFret = frets.filter { it > 0 }.maxOrNull() ?: 0
    // Если аккорд выше пятого лада, показываем окно вокруг него.
    val baseFret = if (maxFret > 5) maxFret - 4 else 1

    Canvas(modifier = modifier) {
        val top = 14.dp.toPx()
        val left = 10.dp.toPx()
        val right = size.width - 10.dp.toPx()
        val bottom = size.height - 2.dp.toPx()
        val stringGap = (right - left) / 5f
        val fretGap = (bottom - top) / 5f
        val dotRadius = (stringGap * 0.28f).coerceAtLeast(2.5.dp.toPx())

        for (string in 0..5) {
            val x = left + string * stringGap
            drawLine(
                color = gridColor,
                start = Offset(x, top),
                end = Offset(x, bottom),
                strokeWidth = 1.dp.toPx(),
            )
        }

        for (fretLine in 0..5) {
            val y = top + fretLine * fretGap
            drawLine(
                color = gridColor,
                start = Offset(left, y),
                end = Offset(right, y),
                strokeWidth = if (fretLine == 0 && baseFret == 1) 3.dp.toPx() else 1.dp.toPx(),
            )
        }

        frets.forEachIndexed { index, fret ->
            val x = left + index * stringGap
            when {
                fret < 0 -> {
                    val d = 2.2.dp.toPx()
                    drawLine(textColor, Offset(x - d, top - 7.dp.toPx()), Offset(x + d, top - 2.5.dp.toPx()), 1.4.dp.toPx())
                    drawLine(textColor, Offset(x - d, top - 2.5.dp.toPx()), Offset(x + d, top - 7.dp.toPx()), 1.4.dp.toPx())
                }
                fret == 0 -> drawCircle(
                    color = textColor,
                    radius = dotRadius * 0.72f,
                    center = Offset(x, top - 5.dp.toPx()),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(1.2.dp.toPx()),
                )
                else -> {
                    val relative = (fret - baseFret + 1).coerceIn(1, 5)
                    val y = top + (relative - 0.5f) * fretGap
                    drawCircle(color = dotColor, radius = dotRadius, center = Offset(x, y))
                }
            }
        }
    }
}

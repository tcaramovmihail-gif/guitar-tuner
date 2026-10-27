package ru.eurovafel.guitartuner.ui.chords

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/** Схема аккорда: 6 струн, 5 ладов, точки и открытые/заглушённые струны. */
@Composable
fun ChordDiagram(
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
                    drawLine(
                        textColor,
                        Offset(x - d, top - 7.dp.toPx()),
                        Offset(x + d, top - 2.5.dp.toPx()),
                        1.4.dp.toPx(),
                    )
                    drawLine(
                        textColor,
                        Offset(x - d, top - 2.5.dp.toPx()),
                        Offset(x + d, top - 7.dp.toPx()),
                        1.4.dp.toPx(),
                    )
                }
                fret == 0 -> drawCircle(
                    color = textColor,
                    radius = dotRadius * 0.72f,
                    center = Offset(x, top - 5.dp.toPx()),
                    style = Stroke(1.2.dp.toPx()),
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

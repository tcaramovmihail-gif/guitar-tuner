package ru.eurovafel.guitartuner.ui.tuner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.eurovafel.guitartuner.model.InstrumentTuning
import ru.eurovafel.guitartuner.model.PitchMath
import ru.eurovafel.guitartuner.ui.theme.GuitarTunerTheme
import kotlin.math.abs
import kotlin.math.log2
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt

@Composable
fun TunerScreen(viewModel: TunerViewModel = viewModel()) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    var hasPermission by remember { mutableStateOf(context.hasRecordPermission()) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
        if (granted) viewModel.start()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_START) {
        hasPermission = context.hasRecordPermission()
    }
    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.stop()
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission) permissionRequested = false
    }
    DisposableEffect(state.listening) {
        view.keepScreenOn = state.listening
        onDispose { view.keepScreenOn = false }
    }

    TunerContent(
        state = state,
        hasPermission = hasPermission,
        permissionRequested = permissionRequested,
        onStart = {
            if (hasPermission) {
                viewModel.start()
            } else {
                permissionRequested = true
                permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        },
        onStop = viewModel::stop,
        onOpenSettings = { context.openAppSettings() },
        onTuningChange = viewModel::setTuning,
        onA4Change = viewModel::setA4,
        onSelectString = viewModel::selectString,
        modifier = Modifier.testTag("tuner_screen"),
    )
}

@Composable
fun TunerContent(
    state: TunerUiState,
    hasPermission: Boolean,
    permissionRequested: Boolean,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onOpenSettings: () -> Unit,
    onTuningChange: (InstrumentTuning) -> Unit,
    onA4Change: (Double) -> Unit,
    onSelectString: (Int?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val reading = state.reading

    // Куда сейчас попадает нота. Это ещё не активная струна: её удерживает
    // ViewModel, чтобы подсветка не перескакивала на одиночных выбросах.
    val detectedStringMidi = reading?.let { note ->
        state.tuning.midiNotes
            .filter { it % 12 == note.midi % 12 }
            .minByOrNull { abs(it - note.midi) }
    }
    val stringMidi = state.activeStringMidi
    val manualSelection = state.manualStringMidi != null
    val matchesActive = stringMidi == null ||
        detectedStringMidi == null ||
        detectedStringMidi == stringMidi

    val targetFrequency = stringMidi?.let { PitchMath.midiToFrequency(it, state.a4) }
    val stringNumber = stringMidi?.let { midi ->
        val index = state.tuning.midiNotes.indexOf(midi)
        if (index >= 0) state.tuning.midiNotes.size - index else null
    }
    val noteName = stringMidi?.let { midi ->
        PitchMath.reading(PitchMath.midiToFrequency(midi), state.a4).fullName
    } ?: reading?.fullName ?: "—"
    val detectedNoteName = detectedStringMidi?.let { midi ->
        PitchMath.reading(PitchMath.midiToFrequency(midi), state.a4).fullName
    } ?: reading?.fullName
    val detectedTargetFrequency = detectedStringMidi?.let {
        PitchMath.midiToFrequency(it, state.a4)
    }

    val octaveShift = if (reading != null && targetFrequency != null && matchesActive) {
        round(log2(reading.frequency / targetFrequency))
    } else {
        0.0
    }
    val foldedFrequency = reading?.frequency?.div(2.0.pow(octaveShift))
    val cents = when {
        reading == null -> null
        stringMidi == null -> reading.cents
        !matchesActive -> null
        targetFrequency == null || foldedFrequency == null -> null
        else -> 1200.0 * log2(foldedFrequency / targetFrequency)
    }

    val action = when {
        cents == null -> null
        abs(cents) <= 3.0 -> "В строю"
        cents < 0 -> "Подтянуть"
        else -> "Ослабить"
    }
    val statusText = when {
        reading == null && !state.listening -> "Нажмите «Включить тюнер»"
        reading == null -> if (stringMidi != null) {
            "Слушаю… Играйте струну $noteName"
        } else {
            "Слушаю… Играйте открытую струну"
        }
        !matchesActive -> if (manualSelection) {
            "Слышу $detectedNoteName · выбрана $noteName"
        } else {
            "Слышу $detectedNoteName · переключаюсь…"
        }
        cents == null -> ""
        else -> {
            val prefix = if (stringNumber != null) "Струна $stringNumber ($noteName) · " else ""
            val amount = if (abs(cents) > 3.0) " · ${abs(cents).roundToInt()} ¢" else ""
            "$prefix${action ?: ""}$amount"
        }
    }
    val inTune = cents != null && abs(cents) <= 3.0
    // Если играет другая струна, показываем её частоту, а не сырое значение
    // относительно выбранной: иначе октавная ошибка выглядит как «55 Гц».
    val detectedOctaveShift = if (reading != null && detectedTargetFrequency != null) {
        round(log2(reading.frequency / detectedTargetFrequency))
    } else {
        0.0
    }
    val detectedFoldedFrequency = reading?.frequency?.div(2.0.pow(detectedOctaveShift))
    val displayFrequency = if (matchesActive) {
        foldedFrequency ?: reading?.frequency
    } else {
        detectedFoldedFrequency ?: reading.frequency
    }
    val displayTargetFrequency = if (matchesActive) {
        targetFrequency ?: reading?.targetFrequency
    } else {
        detectedTargetFrequency
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "Тюнер",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f),
            )
            TuningMenu(
                current = state.tuning,
                onSelect = onTuningChange,
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = "A4",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = { onA4Change(state.a4 - 1.0) },
                enabled = state.a4 > 430.0,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text("−")
            }
            Text(
                text = "${state.a4.roundToInt()} Гц",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.titleSmall,
            )
            TextButton(
                onClick = { onA4Change(state.a4 + 1.0) },
                enabled = state.a4 < 450.0,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
            ) {
                Text("+")
            }
        }

        Text(
            text = noteName,
            fontSize = 88.sp,
            lineHeight = 96.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
            color = if (stringMidi == null && reading == null) {
                MaterialTheme.colorScheme.onSurfaceVariant
            } else {
                MaterialTheme.colorScheme.primary
            },
            modifier = Modifier.testTag("tuner_note"),
        )

        Text(
            text = statusText,
            style = MaterialTheme.typography.titleMedium,
            color = if (inTune) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            textAlign = TextAlign.Center,
            modifier = Modifier.testTag("tuner_status"),
        )

        Button(
            onClick = if (state.listening) onStop else onStart,
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .testTag("tuner_toggle"),
        ) {
            Text(
                text = when {
                    !hasPermission -> "Разрешить микрофон"
                    state.listening -> "Остановить"
                    else -> "Включить тюнер"
                },
            )
        }

        CentsGauge(
            cents = cents,
            listening = state.listening,
            modifier = Modifier
                .fillMaxWidth()
                .height(68.dp),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text("−50", style = MaterialTheme.typography.labelSmall)
            Text("0", style = MaterialTheme.typography.labelSmall)
            Text("+50", style = MaterialTheme.typography.labelSmall)
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = displayFrequency?.let { "%.1f Гц".format(it) } ?: "— Гц",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = displayTargetFrequency?.let { "цель %.1f Гц".format(it) } ?: "",
                fontFamily = FontFamily.Monospace,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        if (state.tuning.midiNotes.isNotEmpty()) {
            GuitarDiagram(
                tuning = state.tuning,
                selectedMidi = stringMidi,
                onSelectString = { onSelectString(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(124.dp),
            )
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(0.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center,
                ) {
                    Text(
                        text = when {
                            stringNumber == null -> if (state.listening) {
                                "Играйте открытую струну — подсвечу её на грифе"
                            } else {
                                "Включите тюнер — покажу, какую струну вы настраиваете"
                            }
                            manualSelection -> {
                                "Выбрана вручную: ${stringOrdinal(stringNumber)} струна · $noteName"
                            }
                            else -> {
                                "Сейчас настраивается: ${stringOrdinal(stringNumber)} струна · $noteName"
                            }
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = if (stringNumber != null) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        textAlign = TextAlign.Center,
                        modifier = Modifier.testTag("tuner_current_string"),
                    )
                    if (manualSelection) {
                        TextButton(
                            onClick = { onSelectString(null) },
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        ) {
                            Text("Авто")
                        }
                    }
                }
            }
            StringRow(
                tuning = state.tuning,
                selectedMidi = stringMidi,
                onSelectString = onSelectString,
            )
        }

        SignalRow(level = state.level, clarity = state.clarity)

        state.error?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                textAlign = TextAlign.Center,
            )
        }

        if (!hasPermission) {
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(
                        text = "Нужен доступ к микрофону",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Звук обрабатывается на устройстве и не отправляется в сеть.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    if (permissionRequested) {
                        TextButton(
                            onClick = onOpenSettings,
                            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 4.dp),
                        ) {
                            Text("Открыть настройки приложения")
                        }
                    }
                }
            }
        }

        Text(
            text = "Одна нота за раз. Для низких струн держите телефон ближе к деке.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TuningMenu(
    current: InstrumentTuning,
    onSelect: (InstrumentTuning) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        TextButton(
            onClick = { expanded = true },
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
        ) {
            Text(
                text = current.displayName,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.widthIn(max = 190.dp),
            )
            Text(" ▾")
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            InstrumentTuning.entries.forEach { tuning ->
                DropdownMenuItem(
                    text = { Text(tuning.displayName) },
                    onClick = {
                        expanded = false
                        onSelect(tuning)
                    },
                )
            }
        }
    }
}

private fun stringOrdinal(number: Int): String = when (number) {
    1 -> "1-я"
    2 -> "2-я"
    3 -> "3-я"
    4 -> "4-я"
    5 -> "5-я"
    6 -> "6-я"
    else -> "$number-я"
}

/** Ручной выбор струны: доступная альтернатива нажатию на гриф. */
@Composable
private fun StringRow(
    tuning: InstrumentTuning,
    selectedMidi: Int?,
    onSelectString: (Int?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        tuning.midiNotes.forEach { midi ->
            val number = tuning.midiNotes.size - tuning.midiNotes.indexOf(midi)
            val note = PitchMath.reading(PitchMath.midiToFrequency(midi)).fullName
            FilterChip(
                selected = midi == selectedMidi,
                onClick = { onSelectString(midi) },
                label = {
                    Text(
                        text = "$number",
                        fontFamily = FontFamily.Monospace,
                    )
                },
                modifier = Modifier
                    .weight(1f)
                    .testTag("tuner_string_$midi")
                    .semantics {
                        contentDescription = "Струна $number, $note"
                    },
            )
        }
    }
}

/**
 * Стилизованная гитара в профиль. Струны идут от головки через гриф к подставке,
 * текущая струна подсвечивается акцентным цветом. Подписи слева показывают
 * номер струны и её открытую ноту.
 */
@Composable
private fun GuitarDiagram(
    tuning: InstrumentTuning,
    selectedMidi: Int?,
    onSelectString: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = tuning.midiNotes
    if (strings.isEmpty()) return

    val selectedIndex = strings.indexOf(selectedMidi)
    val textMeasurer = rememberTextMeasurer()
    val currentOnSelectString by rememberUpdatedState(onSelectString)

    val neckColor = Color(0xFF6B4526)
    val headstockColor = Color(0xFF3A2415)
    val bodyColor = Color(0xFF4B2D1B)
    val bodyOutline = Color(0xFFC49A6C).copy(alpha = 0.55f)
    val stringColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.42f)
    val accent = MaterialTheme.colorScheme.primary
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val scale = MaterialTheme.colorScheme.onSurface

    Canvas(
        modifier = modifier
            .testTag("tuner_guitar")
            .pointerInput(strings) {
                detectTapGestures { offset ->
                    if (strings.size <= 1) return@detectTapGestures

                    val centerY = size.height / 2f
                    val gap = min(13.dp.toPx(), size.height * 0.42f / (strings.size - 1))
                    val top = centerY - gap * (strings.size - 1) / 2f
                    val bottom = top + gap * (strings.size - 1)
                    if (offset.y < top - gap || offset.y > bottom + gap) {
                        return@detectTapGestures
                    }

                    val index = ((offset.y - top) / gap)
                        .roundToInt()
                        .coerceIn(0, strings.lastIndex)
                    currentOnSelectString(strings[index])
                }
            },
    ) {
        val labelWidth = 44.dp.toPx()
        val headstockLeft = labelWidth + 5.dp.toPx()
        val headstockWidth = 48.dp.toPx()
        val nutX = headstockLeft + headstockWidth
        val bodyRight = size.width - 2.dp.toPx()
        val bodyLeft = (size.width * 0.60f).coerceAtLeast(nutX + 54.dp.toPx())
        val centerY = size.height / 2f
        val gap = if (strings.size > 1) {
            min(13.dp.toPx(), size.height * 0.42f / (strings.size - 1))
        } else {
            0f
        }
        val stringSpan = gap * (strings.size - 1)
        val topStringY = centerY - stringSpan / 2f
        val neckHalfHeight = stringSpan / 2f + 8.dp.toPx()
        val headstockTop = centerY - neckHalfHeight - 11.dp.toPx()
        val headstockBottom = centerY + neckHalfHeight + 11.dp.toPx()

        // Головка грифа и колки: по три с каждой стороны.
        drawRoundRect(
            color = headstockColor,
            topLeft = Offset(headstockLeft, headstockTop),
            size = Size(headstockWidth, headstockBottom - headstockTop),
            cornerRadius = CornerRadius(8.dp.toPx()),
        )
        for (peg in 0..2) {
            val pegX = headstockLeft + headstockWidth * (peg + 0.5f) / 3f
            listOf(
                Offset(pegX, headstockTop - 4.dp.toPx()),
                Offset(pegX, headstockBottom + 4.dp.toPx()),
            ).forEach { pegCenter ->
                drawLine(
                    color = bodyOutline,
                    start = pegCenter,
                    end = if (pegCenter.y < centerY) {
                        Offset(pegX, headstockTop + 2.dp.toPx())
                    } else {
                        Offset(pegX, headstockBottom - 2.dp.toPx())
                    },
                    strokeWidth = 1.dp.toPx(),
                )
                drawCircle(
                    color = bodyOutline,
                    radius = 2.7.dp.toPx(),
                    center = pegCenter,
                )
            }
        }

        // Гриф и ладовые порожки.
        drawRoundRect(
            color = neckColor,
            topLeft = Offset(nutX - 2.dp.toPx(), centerY - neckHalfHeight),
            size = Size(bodyLeft - nutX + 18.dp.toPx(), neckHalfHeight * 2f),
            cornerRadius = CornerRadius(3.dp.toPx()),
        )
        for (fret in 1..7) {
            val fretX = nutX + (bodyLeft - nutX) * fret / 8f
            drawLine(
                color = bodyOutline.copy(alpha = 0.30f),
                start = Offset(fretX, centerY - neckHalfHeight + 2.dp.toPx()),
                end = Offset(fretX, centerY + neckHalfHeight - 2.dp.toPx()),
                strokeWidth = 1.dp.toPx(),
            )
        }

        // Корпус: верхняя и нижняя деки с сужением в талии.
        val bodyWidth = bodyRight - bodyLeft
        val bodyHalfHeight = stringSpan / 2f + 20.dp.toPx()
        val body = Path().apply {
            moveTo(bodyLeft, centerY)
            cubicTo(
                bodyLeft + bodyWidth * 0.08f, centerY - bodyHalfHeight * 0.84f,
                bodyLeft + bodyWidth * 0.20f, centerY - bodyHalfHeight,
                bodyLeft + bodyWidth * 0.31f, centerY - bodyHalfHeight * 0.80f,
            )
            cubicTo(
                bodyLeft + bodyWidth * 0.37f, centerY - bodyHalfHeight * 0.68f,
                bodyLeft + bodyWidth * 0.35f, centerY - bodyHalfHeight * 0.42f,
                bodyLeft + bodyWidth * 0.45f, centerY - bodyHalfHeight * 0.40f,
            )
            cubicTo(
                bodyLeft + bodyWidth * 0.66f, centerY - bodyHalfHeight,
                bodyLeft + bodyWidth * 0.92f, centerY - bodyHalfHeight * 0.94f,
                bodyRight, centerY - bodyHalfHeight * 0.50f,
            )
            cubicTo(
                bodyRight + 2.dp.toPx(), centerY - bodyHalfHeight * 0.16f,
                bodyRight + 2.dp.toPx(), centerY + bodyHalfHeight * 0.16f,
                bodyRight, centerY + bodyHalfHeight * 0.50f,
            )
            cubicTo(
                bodyLeft + bodyWidth * 0.92f, centerY + bodyHalfHeight * 0.94f,
                bodyLeft + bodyWidth * 0.66f, centerY + bodyHalfHeight,
                bodyLeft + bodyWidth * 0.45f, centerY + bodyHalfHeight * 0.40f,
            )
            cubicTo(
                bodyLeft + bodyWidth * 0.35f, centerY + bodyHalfHeight * 0.42f,
                bodyLeft + bodyWidth * 0.37f, centerY + bodyHalfHeight * 0.68f,
                bodyLeft + bodyWidth * 0.31f, centerY + bodyHalfHeight * 0.80f,
            )
            cubicTo(
                bodyLeft + bodyWidth * 0.20f, centerY + bodyHalfHeight,
                bodyLeft + bodyWidth * 0.08f, centerY + bodyHalfHeight * 0.84f,
                bodyLeft, centerY,
            )
            close()
        }
        drawPath(body, bodyColor)
        drawPath(body, bodyOutline, style = Stroke(width = 1.4.dp.toPx()))

        val soundHole = Offset(bodyLeft + bodyWidth * 0.38f, centerY)
        drawCircle(color = Color(0xFF24150D), radius = 15.dp.toPx(), center = soundHole)
        drawCircle(
            color = bodyOutline,
            radius = 15.dp.toPx(),
            center = soundHole,
            style = Stroke(width = 1.2.dp.toPx()),
        )
        drawCircle(
            color = bodyOutline.copy(alpha = 0.55f),
            radius = 18.dp.toPx(),
            center = soundHole,
            style = Stroke(width = 1.dp.toPx()),
        )

        // Порожек и подставка.
        drawLine(
            color = Color(0xFFE7D9C2),
            start = Offset(nutX, centerY - neckHalfHeight),
            end = Offset(nutX, centerY + neckHalfHeight),
            strokeWidth = 3.dp.toPx(),
        )
        drawRoundRect(
            color = headstockColor,
            topLeft = Offset(bodyRight - 18.dp.toPx(), centerY - 13.dp.toPx()),
            size = Size(9.dp.toPx(), 26.dp.toPx()),
            cornerRadius = CornerRadius(2.dp.toPx()),
        )

        val bridgeX = bodyRight - 13.dp.toPx()
        strings.forEachIndexed { index, midi ->
            val y = topStringY + index * gap
            val selected = index == selectedIndex
            val thickness = (0.9f + (strings.size - 1 - index) * 0.22f).dp.toPx()

            if (selected) {
                drawLine(
                    color = accent.copy(alpha = 0.24f),
                    start = Offset(nutX, y),
                    end = Offset(bridgeX, y),
                    strokeWidth = thickness + 7.dp.toPx(),
                )
            }
            drawLine(
                color = if (selected) accent else stringColor,
                start = Offset(nutX, y),
                end = Offset(bridgeX, y),
                strokeWidth = if (selected) thickness + 0.9.dp.toPx() else thickness,
            )

            val number = strings.size - index
            val note = PitchMath.reading(PitchMath.midiToFrequency(midi)).fullName
            val labelStyle = TextStyle(
                color = if (selected) accent else labelColor,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            )
            val label = textMeasurer.measure(AnnotatedString("$number $note"), labelStyle)
            val labelTop = y - label.size.height / 2f
            if (selected) {
                drawRoundRect(
                    color = accent.copy(alpha = 0.16f),
                    topLeft = Offset(0f, labelTop - 1.dp.toPx()),
                    size = Size(labelWidth - 3.dp.toPx(), label.size.height + 2.dp.toPx()),
                    cornerRadius = CornerRadius(4.dp.toPx()),
                )
            }
            drawText(
                textLayoutResult = label,
                topLeft = Offset(
                    x = labelWidth - label.size.width - 5.dp.toPx(),
                    y = labelTop,
                ),
            )
        }

        // На активной струне — точка у подставки, чтобы взгляд находил её быстрее.
        if (selectedIndex >= 0) {
            drawCircle(
                color = accent,
                radius = 3.dp.toPx(),
                center = Offset(bodyRight - 4.dp.toPx(), topStringY + selectedIndex * gap),
            )
        }

        // Лёгкий блик на корпусе.
        drawCircle(
            color = scale.copy(alpha = 0.035f),
            radius = 24.dp.toPx(),
            center = Offset(bodyLeft + bodyWidth * 0.74f, centerY - 22.dp.toPx()),
        )
    }
}

@Composable
private fun SignalRow(level: Float, clarity: Double) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "Сигнал",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.width(8.dp))
        LinearProgressIndicator(
            progress = { level },
            modifier = Modifier
                .weight(1f)
                .height(8.dp),
        )
        Spacer(Modifier.width(8.dp))
        Text(
            text = "${(clarity * 100).roundToInt()}%",
            style = MaterialTheme.typography.labelSmall,
            fontFamily = FontFamily.Monospace,
        )
    }
}

@Composable
private fun CentsGauge(
    cents: Double?,
    listening: Boolean,
    modifier: Modifier = Modifier,
) {
    val trackColor = MaterialTheme.colorScheme.surfaceVariant
    val tickColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
    val idleColor = MaterialTheme.colorScheme.outline
    val accent = when {
        cents == null -> idleColor
        abs(cents) <= 5.0 -> Color(0xFF66BB6A)
        abs(cents) <= 15.0 -> Color(0xFFFFC107)
        else -> MaterialTheme.colorScheme.error
    }

    Canvas(modifier) {
        val centerY = size.height * 0.58f
        val trackHeight = 10.dp.toPx()
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, centerY - trackHeight / 2),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2),
        )

        for (tick in -5..5) {
            val x = size.width * (0.5f + tick * 0.1f)
            val half = if (tick == 0) 15.dp.toPx() else 8.dp.toPx()
            drawLine(
                color = tickColor,
                start = Offset(x, centerY - half),
                end = Offset(x, centerY + half),
                strokeWidth = if (tick == 0) 2.dp.toPx() else 1.dp.toPx(),
            )
        }

        if (cents != null) {
            val clamped = cents.coerceIn(-50.0, 50.0)
            val x = size.width * (0.5f + (clamped / 100.0).toFloat())
            drawCircle(
                color = accent,
                radius = 9.dp.toPx(),
                center = Offset(x, centerY),
            )
            val needle = Path().apply {
                moveTo(x, centerY - 14.dp.toPx())
                lineTo(x - 6.dp.toPx(), centerY - 30.dp.toPx())
                lineTo(x + 6.dp.toPx(), centerY - 30.dp.toPx())
                close()
            }
            drawPath(needle, accent)
        } else if (listening) {
            drawCircle(
                color = idleColor,
                radius = 5.dp.toPx(),
                center = Offset(size.width / 2, centerY),
            )
        }
    }
}

private fun Context.hasRecordPermission(): Boolean =
    ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED

private fun Context.openAppSettings() {
    startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", packageName, null)
        },
    )
}

@Preview(showBackground = true, backgroundColor = 0xFF0E1512)
@Composable
private fun TunerPreview() {
    GuitarTunerTheme {
        TunerContent(
            state = TunerUiState(
                listening = true,
                reading = PitchMath.reading(82.41),
                clarity = 0.92,
                level = 0.7f,
            ),
            hasPermission = true,
            permissionRequested = false,
            onStart = {},
            onStop = {},
            onOpenSettings = {},
            onTuningChange = {},
            onA4Change = {},
            onSelectString = {},
        )
    }
}

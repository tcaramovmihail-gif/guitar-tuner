package ru.eurovafel.guitartuner.ui.tuner

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.delay
import ru.eurovafel.guitartuner.R
import ru.eurovafel.guitartuner.model.InstrumentTuning
import ru.eurovafel.guitartuner.model.PitchMath
import ru.eurovafel.guitartuner.settings.AppSettings
import ru.eurovafel.guitartuner.ui.theme.GuitarTunerTheme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log2
import kotlin.math.pow
import kotlin.math.round
import kotlin.math.roundToInt
import kotlin.math.sin

/** Допуск «в строю» по умолчанию, центы. */
internal const val IN_TUNE_CENTS = 8.0

/** Половина шкалы центов: от −50 до +50. */
private const val GAUGE_RANGE_CENTS = 50.0

@Composable
fun TunerScreen(
    onOpenAppSettings: () -> Unit = {},
    onOpenMetronome: () -> Unit = {},
    viewModel: TunerViewModel = viewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val view = LocalView.current
    var hasPermission by remember { mutableStateOf(context.hasRecordPermission()) }
    var permissionRequested by rememberSaveable { mutableStateOf(false) }

    // Настройки переживают перезапуск: строй, калибровка и режим захвата.
    LaunchedEffect(Unit) {
        val savedTuning = AppSettings.tuning(context)
        if (state.tuning != savedTuning) viewModel.setTuning(savedTuning)
        viewModel.setA4(AppSettings.a4(context))
        viewModel.setStableCapture(AppSettings.stableCapture(context))
    }
    val sequential = AppSettings.sequentialTuning(context)
    val vibrate = AppSettings.vibrateInTune(context)

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

    // Вибро — один раз на новую настроенную струну, не на каждый кадр.
    var previousTuned by remember { mutableStateOf(state.tunedMidi) }
    LaunchedEffect(state.tunedMidi) {
        if (vibrate && state.tunedMidi.size > previousTuned.size) {
            context.vibrateInTune()
        }
        previousTuned = state.tunedMidi
    }

    // Последовательная настройка 6 → 1: старт с первой ненастроенной струны.
    LaunchedEffect(sequential, state.listening) {
        if (!sequential || !state.listening || state.activeStringMidi != null) {
            return@LaunchedEffect
        }
        val next = state.tuning.midiNotes.firstOrNull { it !in state.tunedMidi }
        if (next != null) viewModel.selectString(next)
    }
    // Автопереход только после попадания в ноту: 0,9 с видно галочку.
    LaunchedEffect(state.tunedMidi, sequential, state.tuning) {
        if (!sequential) return@LaunchedEffect
        val active = state.activeStringMidi ?: return@LaunchedEffect
        if (active !in state.tunedMidi) return@LaunchedEffect
        delay(900)
        val next = state.tuning.midiNotes.firstOrNull { it !in state.tunedMidi }
        if (next != null) viewModel.selectString(next)
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
        onOpenAppSettings = onOpenAppSettings,
        onOpenMetronome = onOpenMetronome,
        onTuningChange = {
            AppSettings.setTuning(context, it)
            viewModel.setTuning(it)
        },
        onA4Change = {
            AppSettings.setA4(context, it)
            viewModel.setA4(it)
        },
        onSelectString = viewModel::selectString,
        onStableCaptureChange = {
            AppSettings.setStableCapture(context, it)
            viewModel.setStableCapture(it)
        },
        onPlayReference = viewModel::playReference,
        onResetTuned = viewModel::resetTuned,
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
    onOpenAppSettings: () -> Unit = {},
    onOpenMetronome: () -> Unit = {},
    onTuningChange: (InstrumentTuning) -> Unit,
    onA4Change: (Double) -> Unit,
    onSelectString: (Int?) -> Unit,
    onStableCaptureChange: (Boolean) -> Unit,
    onPlayReference: () -> Unit,
    onResetTuned: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val reading = state.reading
    var showA4Dialog by rememberSaveable { mutableStateOf(false) }

    // Куда сейчас попадает нота по уверенному кадру. Это ещё не активная струна:
    // её удерживает ViewModel, чтобы подсветка не перескакивала на выбросах.
    val detectedStringMidi = state.detectedStringMidi
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

    val foldedFrequency = if (reading != null && targetFrequency != null && matchesActive) {
        PitchMath.foldToTarget(reading.frequency, targetFrequency)
    } else {
        reading?.frequency
    }
    val cents = when {
        reading == null -> null
        stringMidi == null -> reading.cents
        !matchesActive -> null
        targetFrequency == null -> null
        else -> PitchMath.centsToTarget(reading.frequency, targetFrequency)
    }

    val action = when {
        cents == null -> null
        abs(cents) <= IN_TUNE_CENTS -> "В строю"
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
            val amount = if (abs(cents) > IN_TUNE_CENTS) " · ${abs(cents).roundToInt()} ¢" else ""
            "$prefix${action ?: ""}$amount"
        }
    }
    val inTune = cents != null && abs(cents) <= IN_TUNE_CENTS
    val statusContainerColor = if (inTune) {
        MaterialTheme.colorScheme.primaryContainer
    } else {
        MaterialTheme.colorScheme.surfaceVariant
    }
    val statusContentColor = if (inTune) {
        MaterialTheme.colorScheme.onPrimaryContainer
    } else {
        MaterialTheme.colorScheme.onSurfaceVariant
    }
    // Если играет другая струна, показываем её частоту, а не сырое значение
    // относительно выбранной: иначе октавная ошибка выглядит как «55 Гц».
    val detectedFoldedFrequency = if (reading != null && detectedTargetFrequency != null) {
        PitchMath.foldToTarget(reading.frequency, detectedTargetFrequency)
    } else {
        reading?.frequency
    }
    val displayFrequency = if (matchesActive) {
        foldedFrequency ?: reading?.frequency
    } else {
        detectedFoldedFrequency ?: reading?.frequency
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
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
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
            IconButton(
                onClick = onOpenMetronome,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("tuner_metronome"),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_metronome),
                    contentDescription = "Метроном",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            IconButton(
                onClick = onOpenAppSettings,
                modifier = Modifier
                    .size(40.dp)
                    .testTag("tuner_settings"),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_settings),
                    contentDescription = "Настройки",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            TuningMenu(
                current = state.tuning,
                a4 = state.a4,
                onSelect = onTuningChange,
                onA4Click = { showA4Dialog = true },
            )
        }

        Surface(
            color = statusContainerColor,
            contentColor = statusContentColor,
            shape = RoundedCornerShape(50),
        ) {
            Text(
                text = statusText,
                style = MaterialTheme.typography.titleSmall,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .padding(horizontal = 18.dp, vertical = 8.dp)
                    .testTag("tuner_status"),
            )
        }

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
                .height(50.dp),
        )

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
            TuningPerson(
                tuning = state.tuning,
                selectedMidi = stringMidi,
                cents = cents,
                celebrating = state.celebrating,
                onSelectString = { onSelectString(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(176.dp)
                    .testTag("tuner_person"),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
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
                    maxLines = 2,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("tuner_current_string"),
                )
                if (manualSelection) {
                    TextButton(
                        onClick = { onSelectString(null) },
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                    ) {
                        Text("Авто")
                    }
                }
                if (stringMidi != null) {
                    TextButton(
                        onClick = onPlayReference,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                        modifier = Modifier.semantics {
                            contentDescription = "Прослушать эталонный тон"
                        },
                    ) {
                        Text("Тон")
                    }
                }
            }
            StringRow(
                tuning = state.tuning,
                selectedMidi = stringMidi,
                tunedMidi = state.tunedMidi,
                onSelectString = onSelectString,
            )
            val allTuned = state.tuning.midiNotes.isNotEmpty() &&
                state.tuning.midiNotes.all { it in state.tunedMidi }
            if (allTuned) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("tuner_all_tuned"),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer,
                    ),
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check),
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = "Все струны настроены",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                            Text(
                                text = "Можно играть. «Заново» сбросит галочки.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        TextButton(onClick = onResetTuned) {
                            Text("Заново")
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (state.stableCapture) "Стабильный захват" else "Быстрый захват",
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = if (state.stableCapture) {
                        "Реже переключает струну"
                    } else {
                        "Быстрее реагирует на смену"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = state.stableCapture,
                onCheckedChange = onStableCaptureChange,
                modifier = Modifier.testTag("tuner_stable_capture"),
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
    }

    if (showA4Dialog) {
        AlertDialog(
            onDismissRequest = { showA4Dialog = false },
            title = { Text("Калибровка A4") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Опорная нота A4. Обычно используется 440 Гц.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        TextButton(
                            onClick = { onA4Change(state.a4 - 1.0) },
                            enabled = state.a4 > 430.0,
                        ) {
                            Text("−")
                        }
                        Text(
                            text = "${state.a4.roundToInt()} Гц",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.padding(horizontal = 16.dp),
                        )
                        TextButton(
                            onClick = { onA4Change(state.a4 + 1.0) },
                            enabled = state.a4 < 450.0,
                        ) {
                            Text("+")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showA4Dialog = false }) {
                    Text("Готово")
                }
            },
        )
    }
}

@Composable
private fun TuningMenu(
    current: InstrumentTuning,
    a4: Double,
    onSelect: (InstrumentTuning) -> Unit,
    onA4Click: () -> Unit,
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
            HorizontalDivider()
            DropdownMenuItem(
                text = { Text("Калибровка A4: ${a4.roundToInt()} Гц") },
                onClick = {
                    expanded = false
                    onA4Click()
                },
            )
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
    tunedMidi: Set<Int>,
    onSelectString: (Int?) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        tuning.midiNotes.forEach { midi ->
            val number = tuning.midiNotes.size - tuning.midiNotes.indexOf(midi)
            val note = PitchMath.reading(PitchMath.midiToFrequency(midi)).fullName
            val tuned = midi in tunedMidi
            FilterChip(
                selected = midi == selectedMidi,
                onClick = { onSelectString(midi) },
                shape = RoundedCornerShape(14.dp),
                label = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "$number",
                                fontFamily = FontFamily.Monospace,
                                fontWeight = FontWeight.Bold,
                                fontSize = 18.sp,
                                lineHeight = 19.sp,
                            )
                            if (tuned) {
                                Icon(
                                    painter = painterResource(R.drawable.ic_check),
                                    contentDescription = "Настроена",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(13.dp),
                                )
                            }
                        }
                        Text(
                            text = note,
                            fontSize = 15.sp,
                            lineHeight = 16.sp,
                        )
                    }
                },
                modifier = Modifier
                    .weight(1f)
                    .height(60.dp)
                    .testTag("tuner_string_$midi")
                    .semantics {
                        contentDescription = "Струна $number, $note"
                    },
            )
        }
    }
}

/**
 * Анимированный гитарист: стоит с гитарой, левой рукой тянется к колку
 * выбранной струны. Пока нота не в строю, кисть «подкручивает» колок.
 * Нажатие на струну или колок выбирает струну вручную.
 *
 * Фигура и гитара нарисованы в одном масштабе: корпус гитары с талией,
 * обечайкой, розеткой, подставкой и ладами — чтобы не читался как укулеле.
 */
@Composable
private fun TuningPerson(
    tuning: InstrumentTuning,
    selectedMidi: Int?,
    cents: Double?,
    celebrating: Boolean,
    onSelectString: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val strings = tuning.midiNotes
    if (strings.isEmpty()) return

    val selectedIndex = strings.indexOf(selectedMidi)
    val outOfTune = cents != null && abs(cents) > IN_TUNE_CENTS

    val handTarget by animateFloatAsState(
        targetValue = if (selectedIndex >= 0) selectedIndex.toFloat() else -1f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "tuner-hand",
    )
    // 1 — рука поднята с «козой», 0 — кисть на струнах.
    val rock by animateFloatAsState(
        targetValue = if (celebrating) 1f else 0f,
        animationSpec = tween(durationMillis = 420, easing = FastOutSlowInEasing),
        label = "tuner-rock",
    )
    val motion = rememberInfiniteTransition(label = "tuner-person")
    val turnPhase by motion.animateFloat(
        initialValue = 0f,
        targetValue = (2.0 * PI).toFloat(),
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "tuner-turn",
    )
    val breath by motion.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2600, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "tuner-breath",
    )

    val jacket = Color(0xFF2C3A34)
    val jacketLine = MaterialTheme.colorScheme.primary.copy(alpha = 0.20f)
    val tee = Color(0xFFF1EEE5)
    val teeInk = Color(0xFF23251F)
    val skin = Color(0xFFD8AF8A)
    val hair = Color(0xFF241A12)
    val ink = Color(0xFF1C140D)
    val wood = Color(0xFF5A3A24)
    val woodEdge = Color(0xFFC49A6C).copy(alpha = 0.55f)
    val darkWood = Color(0xFF3A2415)
    val fretboard = Color(0xFF2E1D12)
    val cream = Color(0xFFE7D9C2)
    val stringColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.55f)
    val accent = MaterialTheme.colorScheme.primary

    // Принт на футболке меряем один раз: внутри Canvas меняется только позиция.
    val textMeasurer = rememberTextMeasurer()
    val rockLabel = remember(textMeasurer) {
        textMeasurer.measure(
            AnnotatedString("rock"),
            style = TextStyle(
                color = teeInk,
                fontSize = 9.sp,
                fontWeight = FontWeight.Bold,
                fontStyle = FontStyle.Italic,
                letterSpacing = 0.3.sp,
            ),
        )
    }

    // Пёс говорит по нажатию: две строчки с паузой в две секунды.
    var dogLine by remember { mutableStateOf<Int?>(null) }
    var dogPulse by remember { mutableIntStateOf(0) }
    LaunchedEffect(dogPulse) {
        if (dogPulse == 0) return@LaunchedEffect
        dogLine = 0
        delay(2_000L)
        dogLine = null
        delay(2_000L)
        dogLine = 1
        delay(2_000L)
        dogLine = null
    }
    val bubbleFill = MaterialTheme.colorScheme.surfaceVariant
    val bubbleInk = MaterialTheme.colorScheme.onSurfaceVariant
    val dogSpeechStyle = TextStyle(
        color = bubbleInk,
        fontSize = 9.sp,
        lineHeight = 11.sp,
        fontWeight = FontWeight.Bold,
        textAlign = TextAlign.Center,
    )
    val dogFirstLine = remember(textMeasurer, bubbleInk) {
        textMeasurer.measure(AnnotatedString("А не спеть ли мне песню...."), style = dogSpeechStyle)
    }
    val dogSecondLine = remember(textMeasurer, bubbleInk) {
        textMeasurer.measure(AnnotatedString("ооооо любви"), style = dogSpeechStyle)
    }
    val dogSpeech = when (dogLine) {
        0 -> dogFirstLine
        1 -> dogSecondLine
        else -> null
    }

    val stringLabel = selectedMidi?.let { midi ->
        val index = strings.indexOf(midi)
        if (index >= 0) "Струна ${strings.size - index}" else null
    }

    Canvas(
        modifier = modifier
            .semantics {
                contentDescription = stringLabel?.let { "Гитарист настраивает: $it" }
                    ?: "Гитарист настраивает гитару"
            }
            .pointerInput(strings) {
                val touchSlop = 20.dp.toPx()
                detectTapGestures { tap ->
                    // Собака слева внизу: касание показывает её реплику.
                    val dogX = size.width * 0.185f
                    val dogGround = size.height * 0.89f
                    if (tap.x in dogX - 22.dp.toPx()..dogX + 22.dp.toPx() &&
                        tap.y in dogGround - 52.dp.toPx()..dogGround + 4.dp.toPx()
                    ) {
                        dogPulse++
                        return@detectTapGestures
                    }
                    val layout = PersonLayout(
                        width = size.width.toFloat(),
                        height = size.height.toFloat(),
                        density = this,
                        stringCount = strings.size,
                    )
                    val peg = layout.nearestPeg(tap, touchSlop)
                    if (peg >= 0) {
                        onSelectString(strings[peg])
                        return@detectTapGestures
                    }
                    val string = layout.nearestString(tap, touchSlop)
                    if (string >= 0) onSelectString(strings[string])
                }
            },
    ) {
        val layout = PersonLayout(size.width, size.height, this, strings.size)
        val bob = breath * 1.1.dp.toPx()
        val personX = layout.personX
        val headCenter = Offset(personX, layout.headCenterY + bob * 0.4f)
        val headR = layout.headRadius
        val shoulderY = layout.shoulderY + bob
        val shoulderHalf = layout.shoulderHalf
        val bottomHalf = size.width * 0.062f
        val hipY = size.height * 0.80f + bob * 0.5f

        // Джинсы: бёдра и две ноги, обрезанные краем рисунка.
        val jeans = Color(0xFF33404F)
        val legGap = 1.5.dp.toPx()
        drawRoundRect(
            color = jeans,
            topLeft = Offset(personX - bottomHalf, hipY - 2.dp.toPx()),
            size = Size(bottomHalf - legGap, size.height - hipY + 2.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx()),
        )
        drawRoundRect(
            color = jeans,
            topLeft = Offset(personX + legGap, hipY - 2.dp.toPx()),
            size = Size(bottomHalf - legGap, size.height - hipY + 2.dp.toPx()),
            cornerRadius = CornerRadius(4.dp.toPx()),
        )

        // Куртка с покатыми плечами.
        val torso = Path().apply {
            moveTo(personX - shoulderHalf, shoulderY)
            quadraticTo(
                personX - shoulderHalf * 1.10f,
                size.height * 0.60f,
                personX - bottomHalf,
                hipY,
            )
            lineTo(personX + bottomHalf, hipY)
            quadraticTo(
                personX + shoulderHalf * 1.10f,
                size.height * 0.60f,
                personX + shoulderHalf,
                shoulderY,
            )
            quadraticTo(
                personX,
                shoulderY - size.height * 0.035f,
                personX - shoulderHalf,
                shoulderY,
            )
            close()
        }
        drawPath(torso, jacket)
        drawPath(torso, jacketLine, style = Stroke(1.2.dp.toPx()))

        // Белая футболка под расстёгнутой курткой.
        val teeTop = shoulderY - size.height * 0.004f
        val teeTopHalf = shoulderHalf * 0.58f
        val teeBottomHalf = bottomHalf * 0.62f
        val teeEdge = Color(0xFFC9C4B5).copy(alpha = 0.75f)
        val teePath = Path().apply {
            val radius = 3.dp.toPx()
            moveTo(personX - teeTopHalf, teeTop + radius)
            quadraticTo(personX - teeTopHalf, teeTop, personX - teeTopHalf + radius, teeTop)
            lineTo(personX + teeTopHalf - radius, teeTop)
            quadraticTo(personX + teeTopHalf, teeTop, personX + teeTopHalf, teeTop + radius)
            lineTo(personX + teeBottomHalf, hipY - 2.dp.toPx())
            lineTo(personX - teeBottomHalf, hipY - 2.dp.toPx())
            close()
        }
        drawPath(teePath, tee)
        drawPath(teePath, teeEdge, style = Stroke(1.dp.toPx()))

        // Ворот футболки.
        val neckline = Path().apply {
            moveTo(personX - 5.5.dp.toPx(), teeTop)
            quadraticTo(personX, teeTop + 6.dp.toPx(), personX + 5.5.dp.toPx(), teeTop)
        }
        drawPath(neckline, teeEdge, style = Stroke(1.3.dp.toPx(), cap = StrokeCap.Round))

        // Принт «rock» на груди.
        drawText(
            textLayoutResult = rockLabel,
            topLeft = Offset(
                personX - rockLabel.size.width / 2f,
                teeTop + (hipY - teeTop) * 0.20f,
            ),
        )

        drawRect(
            color = Color(0xFF1F2A35),
            topLeft = Offset(personX - bottomHalf, hipY - 3.dp.toPx()),
            size = Size(bottomHalf * 2f, 4.dp.toPx()),
        )

        // Шея и голова.
        drawRoundRect(
            color = skin,
            topLeft = Offset(personX - 4.5.dp.toPx(), headCenter.y + headR * 0.55f),
            size = Size(9.dp.toPx(), shoulderY - headCenter.y - headR * 0.45f),
            cornerRadius = CornerRadius(4.dp.toPx()),
        )
        drawCircle(
            color = skin,
            radius = 2.6.dp.toPx(),
            center = Offset(personX - headR * 0.92f, headCenter.y + 1.dp.toPx()),
        )
        drawCircle(
            color = skin,
            radius = 2.6.dp.toPx(),
            center = Offset(personX + headR * 0.92f, headCenter.y + 1.dp.toPx()),
        )
        drawOval(
            color = skin,
            topLeft = Offset(personX - headR * 0.82f, headCenter.y - headR),
            size = Size(headR * 1.64f, headR * 2.0f),
        )

        // Короткая стрижка.
        val hairPath = Path().apply {
            moveTo(personX - headR * 0.86f, headCenter.y - headR * 0.10f)
            quadraticTo(
                personX - headR * 0.96f,
                headCenter.y - headR * 1.16f,
                personX,
                headCenter.y - headR * 1.06f,
            )
            quadraticTo(
                personX + headR * 0.96f,
                headCenter.y - headR * 1.16f,
                personX + headR * 0.86f,
                headCenter.y - headR * 0.10f,
            )
            quadraticTo(
                personX + headR * 0.55f,
                headCenter.y - headR * 0.55f,
                personX,
                headCenter.y - headR * 0.60f,
            )
            quadraticTo(
                personX - headR * 0.55f,
                headCenter.y - headR * 0.55f,
                personX - headR * 0.86f,
                headCenter.y - headR * 0.10f,
            )
            close()
        }
        drawPath(hairPath, hair)

        // Глаза и брови.
        val eyeY = headCenter.y - headR * 0.08f
        drawCircle(ink, 0.8.dp.toPx(), Offset(personX - headR * 0.34f, eyeY))
        drawCircle(ink, 0.8.dp.toPx(), Offset(personX + headR * 0.34f, eyeY))
        drawLine(
            color = ink.copy(alpha = 0.7f),
            start = Offset(personX - headR * 0.52f, eyeY - 4.dp.toPx()),
            end = Offset(personX - headR * 0.20f, eyeY - 4.3.dp.toPx()),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = ink.copy(alpha = 0.7f),
            start = Offset(personX + headR * 0.20f, eyeY - 4.3.dp.toPx()),
            end = Offset(personX + headR * 0.52f, eyeY - 4.dp.toPx()),
            strokeWidth = 1.dp.toPx(),
            cap = StrokeCap.Round,
        )

        // ── Гитара ──────────────────────────────────────────────
        rotate(degrees = layout.tilt, pivot = layout.bodyCenter) {
            val bodyPath = guitarBodyPath(layout.bodyCenter, layout.bodyLong, layout.bodyShort)
            drawPath(bodyPath, wood)
            drawPath(bodyPath, woodEdge, style = Stroke(1.5.dp.toPx()))
            drawPath(
                path = bodyPath,
                color = Color(0xFF2A180E).copy(alpha = 0.35f),
                style = Stroke(3.5.dp.toPx()),
            )

            // Розетка.
            drawCircle(darkWood, 7.5.dp.toPx(), layout.holeLocal)
            drawCircle(woodEdge, 7.5.dp.toPx(), layout.holeLocal, style = Stroke(1.4.dp.toPx()))
            drawCircle(
                color = cream.copy(alpha = 0.45f),
                radius = 10.dp.toPx(),
                center = layout.holeLocal,
                style = Stroke(1.dp.toPx()),
            )

            // Подставка с пинами.
            drawRoundRect(
                color = darkWood,
                topLeft = Offset(
                    layout.bridgeX - 4.dp.toPx(),
                    layout.neckY - 12.dp.toPx(),
                ),
                size = Size(8.dp.toPx(), 24.dp.toPx()),
                cornerRadius = CornerRadius(2.dp.toPx()),
            )
            layout.bridgeLocal.forEach { pin ->
                drawCircle(cream, 1.3.dp.toPx(), pin)
            }

            // Гриф и накладка.
            drawLine(
                color = darkWood,
                start = Offset(layout.neckJointX, layout.neckY),
                end = Offset(layout.nutX, layout.neckY),
                strokeWidth = 9.dp.toPx(),
                cap = StrokeCap.Round,
            )
            drawLine(
                color = fretboard,
                start = Offset(layout.neckJointX, layout.neckY),
                end = Offset(layout.nutX, layout.neckY),
                strokeWidth = 6.dp.toPx(),
                cap = StrokeCap.Round,
            )

            // Лады.
            for (fret in 1..12) {
                val distance = layout.scaleLength * (1.0 - 2.0.pow(-fret / 12.0))
                val x = layout.nutX - distance.toFloat()
                if (x < layout.neckJointX + 2.dp.toPx()) continue
                drawLine(
                    color = cream.copy(alpha = 0.35f),
                    start = Offset(x, layout.neckY - 4.dp.toPx()),
                    end = Offset(x, layout.neckY + 4.dp.toPx()),
                    strokeWidth = 1.dp.toPx(),
                )
            }
            listOf(5, 7, 9, 12).forEach { fret ->
                val distance = layout.scaleLength * (1.0 - 2.0.pow(-fret / 12.0))
                val x = layout.nutX - distance.toFloat() + 3.2.dp.toPx()
                if (x < layout.neckJointX) return@forEach
                drawCircle(cream.copy(alpha = 0.35f), 1.1.dp.toPx(), Offset(x, layout.neckY))
            }

            // Головка.
            val headstockWidth = layout.headstockRight - layout.nutX
            drawRoundRect(
                color = darkWood,
                topLeft = Offset(layout.nutX, layout.neckY - 15.dp.toPx()),
                size = Size(headstockWidth, 30.dp.toPx()),
                cornerRadius = CornerRadius(6.dp.toPx()),
            )
            drawRoundRect(
                color = woodEdge,
                topLeft = Offset(layout.nutX, layout.neckY - 15.dp.toPx()),
                size = Size(headstockWidth, 30.dp.toPx()),
                cornerRadius = CornerRadius(6.dp.toPx()),
                style = Stroke(1.3.dp.toPx()),
            )

            // Порожек.
            drawLine(
                color = cream,
                start = Offset(layout.nutX, layout.neckY - 10.dp.toPx()),
                end = Offset(layout.nutX, layout.neckY + 10.dp.toPx()),
                strokeWidth = 2.6.dp.toPx(),
            )

            // Струны.
            layout.nutLocal.forEachIndexed { index, nut ->
                val bridge = layout.bridgeLocal[index]
                val selected = index == selectedIndex
                val thickness = (0.7f + (strings.size - 1 - index) * 0.14f).dp.toPx()
                if (selected) {
                    drawLine(
                        color = accent.copy(alpha = 0.30f),
                        start = bridge,
                        end = nut,
                        strokeWidth = thickness + 4.dp.toPx(),
                        cap = StrokeCap.Round,
                    )
                }
                drawLine(
                    color = if (selected) accent else stringColor,
                    start = bridge,
                    end = nut,
                    strokeWidth = thickness,
                    cap = StrokeCap.Round,
                )
            }

            // Колки.
            layout.pegLocal.forEachIndexed { index, peg ->
                val selected = index == selectedIndex
                if (selected) {
                    drawCircle(accent.copy(alpha = 0.25f), 6.5.dp.toPx(), peg)
                }
                drawCircle(
                    color = if (selected) accent else cream.copy(alpha = 0.8f),
                    radius = 2.8.dp.toPx(),
                    center = peg,
                    style = Stroke(1.3.dp.toPx()),
                )
                drawCircle(
                    color = if (selected) accent else cream.copy(alpha = 0.5f),
                    radius = 1.2.dp.toPx(),
                    center = peg,
                )
            }

            // Подсказка «кручу колок».
            if (selectedIndex >= 0 && outOfTune) {
                val peg = layout.pegLocal[selectedIndex]
                val arcRadius = 8.dp.toPx()
                drawArc(
                    color = accent.copy(alpha = 0.6f),
                    startAngle = -60f + sin(turnPhase) * 14f,
                    sweepAngle = 130f,
                    useCenter = false,
                    topLeft = Offset(peg.x - arcRadius, peg.y - arcRadius),
                    size = Size(arcRadius * 2f, arcRadius * 2f),
                    style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round),
                )
            }
        }

        // ── Руки ────────────────────────────────────────────────
        // Свободная (щиплющая) рука: на струнах или поднята с «козой».
        val strumRest = layout.holePoint + Offset(0f, -3.dp.toPx())
        val rockFist = Offset(
            personX - shoulderHalf * 1.70f,
            size.height * 0.225f + bob,
        )
        val strumHand = Offset(
            strumRest.x + (rockFist.x - strumRest.x) * rock,
            strumRest.y + (rockFist.y - strumRest.y) * rock,
        )
        val strumControl = Offset(
            personX - shoulderHalf * (1.30f + 0.55f * rock),
            size.height * (0.53f - 0.20f * rock) + bob,
        )
        val strumPath = Path().apply {
            moveTo(personX - shoulderHalf * 0.85f, shoulderY + 2.dp.toPx())
            quadraticTo(strumControl.x, strumControl.y, strumHand.x, strumHand.y)
        }
        drawPath(
            path = strumPath,
            color = jacketLine,
            style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            path = strumPath,
            color = jacket,
            style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round),
        )
        if (rock < 0.98f) {
            val handAlpha = 1f - rock
            drawOval(
                color = skin.copy(alpha = handAlpha),
                topLeft = Offset(strumHand.x - 4.dp.toPx(), strumHand.y - 5.dp.toPx()),
                size = Size(8.dp.toPx(), 10.dp.toPx()),
            )
            for (finger in -1..1) {
                drawLine(
                    color = skin.copy(alpha = handAlpha),
                    start = Offset(strumHand.x + finger * 2.4.dp.toPx(), strumHand.y + 4.dp.toPx()),
                    end = Offset(strumHand.x + finger * 2.8.dp.toPx(), strumHand.y + 8.dp.toPx()),
                    strokeWidth = 1.8.dp.toPx(),
                    cap = StrokeCap.Round,
                )
            }
        }
        if (rock > 0.02f) {
            drawRockHand(
                center = strumHand,
                color = skin.copy(alpha = rock),
                unit = 5.8.dp.toPx(),
            )
        }

        val pegTarget = if (handTarget >= 0f) layout.pegPointAnimated(handTarget) else null
        val wobble = if (outOfTune) sin(turnPhase) * 1.6.dp.toPx() else 0f
        val tuningHand = pegTarget?.let {
            Offset(it.x + 7.dp.toPx() + wobble, it.y + 7.dp.toPx() - abs(wobble) * 0.5f)
        } ?: layout.restHand
        val tunePath = Path().apply {
            moveTo(personX + shoulderHalf * 0.85f, shoulderY + 2.dp.toPx())
            quadraticTo(
                personX + shoulderHalf * 1.55f,
                size.height * 0.38f + bob,
                tuningHand.x,
                tuningHand.y,
            )
        }
        drawPath(
            path = tunePath,
            color = jacketLine,
            style = Stroke(width = 11.dp.toPx(), cap = StrokeCap.Round),
        )
        drawPath(
            path = tunePath,
            color = jacket,
            style = Stroke(width = 9.dp.toPx(), cap = StrokeCap.Round),
        )
        if (pegTarget != null) {
            drawLine(
                color = skin,
                start = tuningHand,
                end = pegTarget,
                strokeWidth = 3.4.dp.toPx(),
                cap = StrokeCap.Round,
            )
        }
        drawOval(
            color = skin,
            topLeft = Offset(tuningHand.x - 4.5.dp.toPx(), tuningHand.y - 5.dp.toPx()),
            size = Size(9.dp.toPx(), 10.dp.toPx()),
        )

        // Пёс сидит слева у ног и по нажатию говорит две строчки.
        drawDog(
            x = size.width * 0.185f,
            groundY = size.height * 0.89f,
            bodyColor = Color(0xFFB07A4A),
            darkColor = Color(0xFF6F4526),
            creamColor = Color(0xFFEAD2AE),
            inkColor = ink,
            speech = dogSpeech,
            bubbleFill = bubbleFill,
            bubbleInk = bubbleInk,
            bubbleBorder = accent,
        )
    }
}

/**
 * «Коза»: кулак, указательный и мизинец вверх, средний и безымянный зажаты,
 * большой палец отставлен в сторону. [unit] — половина ширины кулака.
 */
private fun DrawScope.drawRockHand(center: Offset, color: Color, unit: Float) {
    val finger = unit * 0.44f
    // Указательный палец.
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - unit * 0.72f, center.y - unit * 1.62f),
        size = Size(finger, unit * 1.42f),
        cornerRadius = CornerRadius(finger * 0.48f),
    )
    // Мизинец.
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x + unit * 0.24f, center.y - unit * 1.34f),
        size = Size(finger * 0.86f, unit * 1.18f),
        cornerRadius = CornerRadius(finger * 0.43f),
    )
    // Большой палец в сторону.
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x + unit * 0.52f, center.y - unit * 0.22f),
        size = Size(unit * 0.86f, finger * 0.92f),
        cornerRadius = CornerRadius(finger * 0.46f),
    )
    // Кулак.
    drawRoundRect(
        color = color,
        topLeft = Offset(center.x - unit * 0.62f, center.y - unit * 0.40f),
        size = Size(unit * 1.26f, unit * 1.12f),
        cornerRadius = CornerRadius(unit * 0.40f),
    )
}

/**
 * Пёс у ног гитариста. Над головой появляется облачко с очередной строчкой;
 * какую именно и когда — решает вызывающий код.
 */
private fun DrawScope.drawDog(
    x: Float,
    groundY: Float,
    bodyColor: Color,
    darkColor: Color,
    creamColor: Color,
    inkColor: Color,
    speech: TextLayoutResult?,
    bubbleFill: Color,
    bubbleInk: Color,
    bubbleBorder: Color,
) {
    val headR = 7.dp.toPx()
    val headCenter = Offset(x, groundY - 25.dp.toPx())

    // Хвост.
    val tail = Path().apply {
        moveTo(x + 8.dp.toPx(), groundY - 8.dp.toPx())
        cubicTo(
            x + 18.dp.toPx(),
            groundY - 10.dp.toPx(),
            x + 20.dp.toPx(),
            groundY - 22.dp.toPx(),
            x + 13.dp.toPx(),
            groundY - 26.dp.toPx(),
        )
    }
    drawPath(tail, darkColor, style = Stroke(3.dp.toPx(), cap = StrokeCap.Round))

    // Тело, грудь и лапы.
    drawOval(
        color = bodyColor,
        topLeft = Offset(x - 9.dp.toPx(), groundY - 20.dp.toPx()),
        size = Size(18.dp.toPx(), 20.dp.toPx()),
    )
    drawOval(
        color = creamColor.copy(alpha = 0.9f),
        topLeft = Offset(x - 4.dp.toPx(), groundY - 15.dp.toPx()),
        size = Size(8.dp.toPx(), 12.dp.toPx()),
    )
    drawOval(
        color = bodyColor,
        topLeft = Offset(x - 8.dp.toPx(), groundY - 4.dp.toPx()),
        size = Size(7.dp.toPx(), 4.dp.toPx()),
    )
    drawOval(
        color = bodyColor,
        topLeft = Offset(x + 1.dp.toPx(), groundY - 4.dp.toPx()),
        size = Size(7.dp.toPx(), 4.dp.toPx()),
    )

    // Висячие уши — главный признак собаки.
    drawOval(
        color = darkColor,
        topLeft = Offset(x - headR * 1.55f, headCenter.y - headR * 0.55f),
        size = Size(headR * 0.78f, headR * 1.75f),
    )
    drawOval(
        color = darkColor,
        topLeft = Offset(x + headR * 0.77f, headCenter.y - headR * 0.55f),
        size = Size(headR * 0.78f, headR * 1.75f),
    )
    drawCircle(bodyColor, headR, headCenter)

    // Морда, глаза и нос.
    drawOval(
        color = creamColor,
        topLeft = Offset(x - headR * 0.62f, headCenter.y + headR * 0.02f),
        size = Size(headR * 1.24f, headR * 0.95f),
    )
    drawCircle(
        color = inkColor,
        radius = 0.8.dp.toPx(),
        center = Offset(x - headR * 0.40f, headCenter.y - headR * 0.18f),
    )
    drawCircle(
        color = inkColor,
        radius = 0.8.dp.toPx(),
        center = Offset(x + headR * 0.40f, headCenter.y - headR * 0.18f),
    )
    drawOval(
        color = inkColor,
        topLeft = Offset(x - headR * 0.20f, headCenter.y + headR * 0.12f),
        size = Size(headR * 0.40f, headR * 0.30f),
    )
    // Высунутый язык: губы и правда холодные.
    drawOval(
        color = Color(0xFFD97A7A),
        topLeft = Offset(x - headR * 0.16f, headCenter.y + headR * 0.34f),
        size = Size(headR * 0.32f, headR * 0.34f),
    )

    if (speech == null) return

    // Облачко с репликой над головой.
    val padH = 5.dp.toPx()
    val padV = 3.dp.toPx()
    val bubbleWidth = speech.size.width + padH * 2f
    val bubbleHeight = speech.size.height + padV * 2f
    val maxLeft = (size.width - bubbleWidth - 2.dp.toPx()).coerceAtLeast(2.dp.toPx())
    val bubbleLeft = (x - bubbleWidth / 2f).coerceIn(2.dp.toPx(), maxLeft)
    val bubbleTop = headCenter.y - headR - bubbleHeight - 9.dp.toPx()
    drawRoundRect(
        color = bubbleFill,
        topLeft = Offset(bubbleLeft, bubbleTop),
        size = Size(bubbleWidth, bubbleHeight),
        cornerRadius = CornerRadius(6.dp.toPx()),
    )
    drawRoundRect(
        color = bubbleBorder,
        topLeft = Offset(bubbleLeft, bubbleTop),
        size = Size(bubbleWidth, bubbleHeight),
        cornerRadius = CornerRadius(6.dp.toPx()),
        style = Stroke(1.dp.toPx()),
    )
    val tailX = x.coerceIn(
        bubbleLeft + 6.dp.toPx(),
        bubbleLeft + bubbleWidth - 6.dp.toPx(),
    )
    val bubbleTail = Path().apply {
        moveTo(tailX - 2.5.dp.toPx(), bubbleTop + bubbleHeight)
        lineTo(tailX + 2.5.dp.toPx(), bubbleTop + bubbleHeight)
        lineTo(tailX, bubbleTop + bubbleHeight + 4.dp.toPx())
        close()
    }
    drawPath(bubbleTail, bubbleFill)
    drawText(
        textLayoutResult = speech,
        topLeft = Offset(bubbleLeft + padH, bubbleTop + padV),
    )
}

/**
 * Геометрия рисунка и зоны нажатия. Гитара рисуется в своей системе координат
 * (гриф горизонтальный) и поворачивается целиком, поэтому колки и струны
 * для жестов считаются той же формулой, что и для отрисовки.
 */
private class PersonLayout(
    private val width: Float,
    private val height: Float,
    private val density: Density,
    private val stringCount: Int,
) {
    private fun dp(value: Float): Float = with(density) { value.dp.toPx() }

    val personX = width * 0.49f
    val headCenterY = height * 0.155f
    val headRadius = dp(13f)
    val shoulderY = height * 0.30f
    val shoulderHalf = width * 0.072f

    val tilt = -12f
    // Гитара меньше человека: корпус ~2.5 высоты головы, гриф короче корпуса.
    val bodyCenter = Offset(width * 0.40f, height * 0.68f)
    val bodyLong = height * 0.40f
    val bodyShort = bodyLong * 0.74f
    val neckY = bodyCenter.y
    val neckJointX = bodyCenter.x + bodyLong * 0.30f
    val nutX = width * 0.72f
    val headstockRight = width * 0.82f
    val scaleLength = nutX - (bodyCenter.x - bodyLong * 0.45f)
    val bridgeX = bodyCenter.x - bodyLong * 0.45f
    val holeLocal = Offset(bodyCenter.x + bodyLong * 0.14f, neckY)
    val holePoint = rotated(holeLocal)

    private val topPegCount = (stringCount + 1) / 2

    val pegLocal: List<Offset> = List(stringCount) { index ->
        val onTop = index < topPegCount
        val rowIndex = if (onTop) index else index - topPegCount
        val rowCount = if (onTop) topPegCount else stringCount - topPegCount
        val column = if (rowCount <= 1) 0.5f else (rowIndex + 0.5f) / rowCount
        Offset(
            x = nutX + (headstockRight - nutX) * (0.18f + 0.64f * column),
            y = neckY + if (onTop) -dp(10f) else dp(10f),
        )
    }

    val bridgeLocal: List<Offset> = List(stringCount) { index ->
        Offset(
            x = bodyCenter.x - bodyLong * 0.45f,
            y = neckY + (index - (stringCount - 1) / 2f) * dp(4.2f),
        )
    }

    val nutLocal: List<Offset> = List(stringCount) { index ->
        Offset(
            x = nutX,
            y = neckY + (index - (stringCount - 1) / 2f) * dp(3.2f),
        )
    }

    val pegPoints: List<Offset> = pegLocal.map(::rotated)
    val bridgePoints: List<Offset> = bridgeLocal.map(::rotated)
    val nutPoints: List<Offset> = nutLocal.map(::rotated)

    val restHand = rotated(Offset(nutX - dp(16f), neckY + dp(5f)))

    private fun rotated(point: Offset): Offset {
        val rad = tilt * PI / 180.0
        val cos = kotlin.math.cos(rad).toFloat()
        val sin = kotlin.math.sin(rad).toFloat()
        val dx = point.x - bodyCenter.x
        val dy = point.y - bodyCenter.y
        return Offset(
            x = bodyCenter.x + dx * cos - dy * sin,
            y = bodyCenter.y + dx * sin + dy * cos,
        )
    }

    /** Плавно ведёт кисть между колками при анимации. */
    fun pegPointAnimated(value: Float): Offset {
        val low = floor(value).toInt().coerceIn(0, pegPoints.lastIndex)
        val high = ceil(value).toInt().coerceIn(0, pegPoints.lastIndex)
        val fraction = (value - low).coerceIn(0f, 1f)
        val from = pegPoints[low]
        val to = pegPoints[high]
        return Offset(
            x = from.x + (to.x - from.x) * fraction,
            y = from.y + (to.y - from.y) * fraction,
        )
    }

    fun nearestPeg(point: Offset, radius: Float): Int {
        var best = -1
        var bestDistance = radius
        pegPoints.forEachIndexed { index, peg ->
            val distance = (point - peg).getDistance()
            if (distance <= bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return best
    }

    fun nearestString(point: Offset, radius: Float): Int {
        var best = -1
        var bestDistance = radius
        bridgePoints.forEachIndexed { index, bridge ->
            val distance = distanceToSegment(point, bridge, nutPoints[index])
            if (distance <= bestDistance) {
                bestDistance = distance
                best = index
            }
        }
        return best
    }
}

/** Корпус гитары: верхний овал справа (у грифа), талия, нижний овал слева. */
private fun guitarBodyPath(center: Offset, length: Float, width: Float): Path {
    val hl = length / 2f
    val hw = width / 2f
    fun x(value: Float) = center.x + value * hl
    fun y(value: Float) = center.y + value * hw
    return Path().apply {
        moveTo(x(1f), y(0f))
        cubicTo(x(1f), y(-0.55f), x(0.85f), y(-0.88f), x(0.55f), y(-0.90f))
        cubicTo(x(0.30f), y(-0.92f), x(0.22f), y(-0.55f), x(0.10f), y(-0.55f))
        cubicTo(x(-0.02f), y(-0.55f), x(-0.10f), y(-0.92f), x(-0.40f), y(-0.96f))
        cubicTo(x(-0.75f), y(-1f), x(-1f), y(-0.75f), x(-1f), y(0f))
        cubicTo(x(-1f), y(0.75f), x(-0.75f), y(1f), x(-0.40f), y(0.96f))
        cubicTo(x(-0.10f), y(0.92f), x(-0.02f), y(0.55f), x(0.10f), y(0.55f))
        cubicTo(x(0.22f), y(0.55f), x(0.30f), y(0.92f), x(0.55f), y(0.90f))
        cubicTo(x(0.85f), y(0.88f), x(1f), y(0.55f), x(1f), y(0f))
        close()
    }
}

private fun distanceToSegment(point: Offset, start: Offset, end: Offset): Float {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared == 0f) return (point - start).getDistance()

    val t = (((point.x - start.x) * dx + (point.y - start.y) * dy) / lengthSquared)
        .coerceIn(0f, 1f)
    val projection = Offset(start.x + t * dx, start.y + t * dy)
    return (point - projection).getDistance()
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
    val labelColor = MaterialTheme.colorScheme.onSurfaceVariant
    val idleColor = MaterialTheme.colorScheme.outline
    val inTuneColor = Color(0xFF66BB6A)
    val accent = when {
        cents == null -> idleColor
        abs(cents) <= 5.0 -> inTuneColor
        abs(cents) <= 15.0 -> Color(0xFFFFC107)
        else -> MaterialTheme.colorScheme.error
    }

    // Стрелка догоняет значение плавно: одиночные выбросы детектора не дёргают шкалу.
    val target = cents?.coerceIn(-GAUGE_RANGE_CENTS, GAUGE_RANGE_CENTS)?.toFloat()
    val needle by animateFloatAsState(
        targetValue = target ?: 0f,
        animationSpec = tween(durationMillis = 320, easing = LinearEasing),
        label = "cents-needle",
    )
    val shown = if (target == null) null else needle
    val textMeasurer = rememberTextMeasurer()
    val labelStyle = TextStyle(
        color = labelColor,
        fontSize = 8.sp,
        fontFamily = FontFamily.Monospace,
    )

    Canvas(modifier) {
        val centerY = size.height * 0.44f
        val trackHeight = 7.dp.toPx()
        drawRoundRect(
            color = trackColor,
            topLeft = Offset(0f, centerY - trackHeight / 2),
            size = Size(size.width, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2),
        )

        // Зелёная зона «в строю»: ±3 ¢.
        val tolerance = size.width *
            (IN_TUNE_CENTS / (GAUGE_RANGE_CENTS * 2.0)).toFloat()
        drawRoundRect(
            color = inTuneColor.copy(alpha = 0.22f),
            topLeft = Offset(size.width / 2 - tolerance, centerY - trackHeight / 2),
            size = Size(tolerance * 2, trackHeight),
            cornerRadius = CornerRadius(trackHeight / 2),
        )

        for (tick in -5..5) {
            val x = size.width * (0.5f + tick * 0.1f)
            val half = if (tick == 0) 8.dp.toPx() else 5.dp.toPx()
            drawLine(
                color = tickColor,
                start = Offset(x, centerY - half),
                end = Offset(x, centerY + half),
                strokeWidth = if (tick == 0) 2.dp.toPx() else 1.dp.toPx(),
            )
        }

        if (shown != null) {
            val x = size.width * (0.5f + shown / (GAUGE_RANGE_CENTS * 2.0).toFloat())
            drawCircle(
                color = accent,
                radius = 6.5.dp.toPx(),
                center = Offset(x, centerY),
            )
            val triangle = Path().apply {
                moveTo(x, centerY - 9.dp.toPx())
                lineTo(x - 4.dp.toPx(), centerY - 17.dp.toPx())
                lineTo(x + 4.dp.toPx(), centerY - 17.dp.toPx())
                close()
            }
            drawPath(triangle, accent)
        } else if (listening) {
            drawCircle(
                color = idleColor,
                radius = 4.dp.toPx(),
                center = Offset(size.width / 2, centerY),
            )
        }

        val labels = listOf(
            "−${GAUGE_RANGE_CENTS.toInt()}" to 0f,
            "0" to 0.5f,
            "+${GAUGE_RANGE_CENTS.toInt()}" to 1f,
        )
        labels.forEach { (text, fraction) ->
            val layout = textMeasurer.measure(AnnotatedString(text), labelStyle)
            val x = when (fraction) {
                0f -> 0f
                1f -> size.width - layout.size.width
                else -> size.width * fraction - layout.size.width / 2f
            }
            drawText(
                textLayoutResult = layout,
                topLeft = Offset(x, size.height - layout.size.height),
            )
        }
    }
}

private fun Context.vibrateInTune() {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    } ?: return
    if (!vibrator.hasVibrator()) return
    runCatching {
        vibrator.vibrate(VibrationEffect.createOneShot(28L, 140))
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
            onStableCaptureChange = {},
            onPlayReference = {},
        )
    }
}

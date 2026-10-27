package ru.eurovafel.guitartuner.ui.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
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
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import ru.eurovafel.guitartuner.BuildConfig
import ru.eurovafel.guitartuner.R
import ru.eurovafel.guitartuner.settings.AppSettings
import ru.eurovafel.guitartuner.ui.theme.ThemeChoice
import ru.eurovafel.guitartuner.ui.theme.ThemeMode
import kotlin.math.roundToInt

/** Все настройки в одном месте: тема, текст, прокрутка и поведение тюнера. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    themeChoice: ThemeChoice,
    themeMode: ThemeMode,
    onThemeChoiceChange: (ThemeChoice) -> Unit,
    onThemeModeChange: (ThemeMode) -> Unit,
    onOpenMetronome: () -> Unit,
    onBack: () -> Unit,
    cachedSearches: () -> Int,
    onClearCache: () -> Int,
) {
    val context = LocalContext.current
    var textScale by remember { mutableFloatStateOf(AppSettings.textScale(context)) }
    var pauseSeconds by remember { mutableIntStateOf(AppSettings.pauseSeconds(context)) }
    var scrollSpeed by remember { mutableFloatStateOf(AppSettings.scrollSpeed(context)) }
    var vibrate by remember { mutableStateOf(AppSettings.vibrateInTune(context)) }
    var sequential by remember { mutableStateOf(AppSettings.sequentialTuning(context)) }
    var cacheCount by remember { mutableIntStateOf(cachedSearches()) }

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("settings_screen"),
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
                text = "Настройки",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Section("Оформление")
            Text(
                text = "Схема",
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ThemeChoice.entries.forEach { choice ->
                    FilterChip(
                        selected = themeChoice == choice,
                        onClick = { onThemeChoiceChange(choice) },
                        label = { Text(choice.title) },
                        leadingIcon = {
                            Box(
                                Modifier
                                    .size(14.dp)
                                    .background(choice.preview, CircleShape),
                            )
                        },
                        modifier = Modifier.testTag("settings_theme_${choice.name}"),
                    )
                }
            }
            Text(
                text = "Режим",
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = themeMode == mode,
                        onClick = { onThemeModeChange(mode) },
                        label = { Text(mode.title) },
                        modifier = Modifier.testTag("settings_mode_${mode.name}"),
                    )
                }
            }

            HorizontalDivider()

            Section("Текст песни")
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Масштаб", style = MaterialTheme.typography.labelLarge)
                Slider(
                    value = textScale,
                    onValueChange = { textScale = it },
                    onValueChangeFinished = { AppSettings.setTextScale(context, textScale) },
                    valueRange = 0.75f..1.75f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                        .testTag("settings_text_scale"),
                )
                Text(
                    text = "${(textScale * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                )
            }
            Text(
                text = "Начальный масштаб — 75 %: на экран помещается больше строк.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Text(
                text = "Пауза перед стартом",
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSettings.PAUSE_OPTIONS.forEach { seconds ->
                    FilterChip(
                        selected = pauseSeconds == seconds,
                        onClick = {
                            pauseSeconds = seconds
                            AppSettings.setPauseSeconds(context, seconds)
                        },
                        label = { Text(if (seconds == 0) "Без паузы" else "$seconds с") },
                        modifier = Modifier.testTag("settings_pause_$seconds"),
                    )
                }
            }

            Text(
                text = "Скорость прокрутки",
                style = MaterialTheme.typography.labelLarge,
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AppSettings.SPEED_OPTIONS.forEach { speed ->
                    FilterChip(
                        selected = scrollSpeed == speed,
                        onClick = {
                            scrollSpeed = speed
                            AppSettings.setScrollSpeed(context, speed)
                        },
                        label = { Text("×$speed") },
                        modifier = Modifier.testTag("settings_speed_$speed"),
                    )
                }
            }

            HorizontalDivider()

            Section("Тюнер")
            SwitchRow(
                title = "Вибро в строю",
                subtitle = "Короткий отклик, когда струна попала в ноту",
                checked = vibrate,
                onCheckedChange = {
                    vibrate = it
                    AppSettings.setVibrateInTune(context, it)
                },
                testTag = "settings_vibrate",
            )
            SwitchRow(
                title = "Настраивать по порядку 6 → 1",
                subtitle = "После попадания в ноту тюнер сам перейдёт к следующей струне",
                checked = sequential,
                onCheckedChange = {
                    sequential = it
                    AppSettings.setSequentialTuning(context, it)
                },
                testTag = "settings_sequential",
            )
            Button(
                onClick = onOpenMetronome,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings_metronome"),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_metronome),
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text("Открыть метроном")
            }

            HorizontalDivider()

            Section("Данные")
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                ),
            ) {
                Column(Modifier.padding(14.dp)) {
                    Text(
                        text = "Офлайн-кэш каталога",
                        style = MaterialTheme.typography.titleSmall,
                    )
                    Text(
                        text = "Сохранено запросов: $cacheCount. Кэш помогает искать " +
                            "без сети и не заменяет свежие результаты.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                    TextButton(
                        onClick = {
                            onClearCache()
                            cacheCount = 0
                        },
                        enabled = cacheCount > 0,
                        modifier = Modifier.testTag("settings_clear_cache"),
                    ) {
                        Text("Очистить кэш")
                    }
                }
            }

            HorizontalDivider()

            Section("О приложении")
            Text(
                text = "Guitar Tuner ${BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "Тюнер, песенник с аккордами и метроном. Работает офлайн, " +
                    "звук обрабатывается на устройстве.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextButton(
                onClick = {
                    val url = "https://github.com/tcaramovmihail-gif/guitar-tuner/releases"
                    runCatching {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                    }
                },
            ) {
                Text("Версии и исходный код")
            }
            Spacer(Modifier.padding(bottom = 24.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Column {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.labelLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.testTag(testTag),
        )
    }
}

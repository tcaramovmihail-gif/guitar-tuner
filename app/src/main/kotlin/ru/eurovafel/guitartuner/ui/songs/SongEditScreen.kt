package ru.eurovafel.guitartuner.ui.songs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import ru.eurovafel.guitartuner.R
import ru.eurovafel.guitartuner.data.Song

/**
 * Своя песня: текст с аккордами можно вставить из заметок или набрать вручную.
 * Редактирование открывается из меню песни и сохраняет её id.
 */
@Composable
fun SongEditScreen(
    song: Song?,
    onBack: () -> Unit,
    onSave: (title: String, artist: String, bpm: Int, chords: String, key: String?) -> Unit,
) {
    val key = song?.id ?: "new"
    var title by rememberSaveable(key) { mutableStateOf(song?.title.orEmpty()) }
    var artist by rememberSaveable(key) { mutableStateOf(song?.artist.orEmpty()) }
    var bpmText by rememberSaveable(key) { mutableStateOf((song?.bpm ?: 90).toString()) }
    var songKey by rememberSaveable(key) { mutableStateOf(song?.key.orEmpty()) }
    var chords by rememberSaveable(key) { mutableStateOf(song?.chords.orEmpty()) }

    val bpm = bpmText.toIntOrNull()
    val valid = chords.isNotBlank() && bpm != null && bpm in 20..300

    BackHandler(onBack = onBack)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("song_edit_screen")
            .padding(horizontal = 12.dp),
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
                text = if (song == null) "Своя песня" else "Редактирование",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.weight(1f),
            )
            Button(
                onClick = {
                    onSave(
                        title.trim(),
                        artist.trim(),
                        bpm ?: 90,
                        chords.trimEnd(),
                        songKey.trim().takeIf { it.isNotEmpty() },
                    )
                },
                enabled = valid,
                modifier = Modifier.testTag("song_edit_save"),
            ) {
                Text("Сохранить")
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                label = { Text("Название") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("song_edit_title"),
            )
            OutlinedTextField(
                value = artist,
                onValueChange = { artist = it },
                label = { Text("Исполнитель") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = bpmText,
                    onValueChange = { value -> bpmText = value.filter { it.isDigit() }.take(3) },
                    label = { Text("Темп, BPM") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = bpmText.isNotEmpty() && (bpm == null || bpm !in 20..300),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("song_edit_bpm"),
                )
                OutlinedTextField(
                    value = songKey,
                    onValueChange = { songKey = it.take(6) },
                    label = { Text("Тональность") },
                    placeholder = { Text("авто") },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                )
            }

            Text(
                text = "Текст с аккордами",
                style = MaterialTheme.typography.labelLarge,
            )
            Text(
                text = "Пишите аккорды над словами, как в песеннике. Пробелы сохранятся, " +
                    "а нажатие на аккорд в песне покажет аппликатуру.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                value = chords,
                onValueChange = { chords = it },
                placeholder = {
                    Text("Am            C\nСлова песни начинаются здесь…")
                },
                textStyle = TextStyle(
                    fontFamily = FontFamily.Monospace,
                    fontSize = 15.sp,
                    lineHeight = 22.sp,
                ),
                minLines = 12,
                maxLines = 400,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(560.dp)
                    .testTag("song_edit_chords"),
            )
            if (!valid) {
                Text(
                    text = if (chords.isBlank()) {
                        "Добавьте текст песни"
                    } else {
                        "Темп должен быть от 20 до 300 BPM"
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            TextButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
                Text("Отмена")
            }
        }
    }
}

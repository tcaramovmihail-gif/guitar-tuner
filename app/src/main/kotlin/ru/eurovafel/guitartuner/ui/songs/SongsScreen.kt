package ru.eurovafel.guitartuner.ui.songs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import ru.eurovafel.guitartuner.catalog.RemoteArtist
import ru.eurovafel.guitartuner.catalog.RemoteSong
import ru.eurovafel.guitartuner.data.Song

@Composable
fun SongsScreen(
    viewModel: SongsViewModel,
    onOpenSong: (Song) -> Unit,
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val keyboard = LocalSoftwareKeyboardController.current

    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showImportDialog by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.consumeMessage()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.consumeError()
        }
    }

    BackHandler(enabled = tab == 0 && state.openedArtist != null) {
        viewModel.closeArtist()
    }

    Box(Modifier.fillMaxSize().testTag("songs_screen")) {
        Column(Modifier.fillMaxSize()) {
            PrimaryTabRow(selectedTabIndex = tab) {
                Tab(
                    selected = tab == 0,
                    onClick = { tab = 0 },
                    text = { Text("Мои песни") },
                    modifier = Modifier.testTag("songs_tab_mine"),
                )
                Tab(
                    selected = tab == 1,
                    onClick = { tab = 1 },
                    text = { Text("Онлайн") },
                    modifier = Modifier.testTag("songs_tab_online"),
                )
            }

            state.importProgress?.let { progress ->
                ImportProgressCard(
                    progress = progress,
                    onCancel = viewModel::cancelImport,
                    onDismiss = viewModel::dismissImportProgress,
                )
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                when {
                    state.loading -> LoadingState()
                    tab == 0 -> MySongsContent(
                        state = state,
                        onOpenArtist = viewModel::openArtist,
                        onCloseArtist = viewModel::closeArtist,
                        onOpenSong = onOpenSong,
                        onShowImportDialog = { showImportDialog = true },
                    )
                    else -> OnlineContent(
                        state = state,
                        onQueryChange = viewModel::onQueryChange,
                        onSearch = {
                            keyboard?.hide()
                            viewModel.search()
                        },
                        onClearSearch = viewModel::clearSearch,
                        onOpenRemote = { remote ->
                            viewModel.loadRemoteSong(remote, openAfter = true, onOpened = onOpenSong)
                        },
                        onSaveRemote = { remote ->
                            viewModel.loadRemoteSong(remote, openAfter = false) { }
                        },
                        onImportArtist = viewModel::importArtist,
                        onShowImportDialog = { showImportDialog = true },
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp),
        )
    }

    if (showImportDialog) {
        AlertDialog(
            onDismissRequest = { showImportDialog = false },
            title = { Text("Загрузить пример?") },
            text = {
                Text(
                    "Будут загружены все уникальные песни «Король и Шут» и «Би-2» " +
                        "в папки исполнителей. Это несколько минут и десятки мегабайт трафика. " +
                        "Прервать загрузку можно в любой момент.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showImportDialog = false
                    viewModel.importExamples()
                }) {
                    Text("Загрузить")
                }
            },
            dismissButton = {
                TextButton(onClick = { showImportDialog = false }) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun LoadingState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        CircularProgressIndicator()
    }
}

@Composable
private fun MySongsContent(
    state: SongsUiState,
    onOpenArtist: (String) -> Unit,
    onCloseArtist: () -> Unit,
    onOpenSong: (Song) -> Unit,
    onShowImportDialog: () -> Unit,
) {
    val folder = state.openedFolder
    if (folder == null) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    text = "Мои песни",
                    style = MaterialTheme.typography.headlineMedium,
                )
                Text(
                    text = "Папки по исполнителям",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            if (state.artists.isEmpty()) {
                item {
                    EmptyLibraryCard(onShowImportDialog = onShowImportDialog)
                }
            } else {
                items(state.artists, key = { it.artist }) { artistFolder ->
                    ArtistFolderCard(
                        folder = artistFolder,
                        onClick = { onOpenArtist(artistFolder.artist) },
                    )
                }
            }
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(onClick = onCloseArtist) { Text("← Папки") }
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = folder.artist,
                            style = MaterialTheme.typography.titleLarge,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = songsCount(folder.songs.size),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalDivider(Modifier.padding(top = 4.dp, bottom = 4.dp))
            }

            items(folder.songs, key = { it.id }) { song ->
                LibrarySongCard(song = song, onClick = { onOpenSong(song) })
            }
        }
    }
}

@Composable
private fun EmptyLibraryCard(onShowImportDialog: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Пока нет сохранённых песен", style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Откройте вкладку «Онлайн», найдите песню или загрузите пример " +
                    "с песнями «Король и Шут» и «Би-2».",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Button(onClick = onShowImportDialog) {
                Text("Загрузить пример")
            }
        }
    }
}

@Composable
private fun ArtistFolderCard(folder: ArtistFolder, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("songs_folder_${folder.artist}"),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "♪",
                style = MaterialTheme.typography.headlineSmall,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = folder.artist,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = songsCount(folder.songs.size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun LibrarySongCard(song: Song, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("songs_song_${song.id}"),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(song.title, style = MaterialTheme.typography.titleMedium)
            Text(
                text = buildString {
                    append(song.bpm)
                    append(" BPM")
                    song.key?.let {
                        append(" · ")
                        append(it)
                    }
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun OnlineContent(
    state: SongsUiState,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onClearSearch: () -> Unit,
    onOpenRemote: (RemoteSong) -> Unit,
    onSaveRemote: (RemoteSong) -> Unit,
    onImportArtist: (RemoteArtist) -> Unit,
    onShowImportDialog: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedTextField(
                    value = state.query,
                    onValueChange = onQueryChange,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("songs_search_field"),
                    singleLine = true,
                    label = { Text("Песня или исполнитель") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                )
                Button(
                    onClick = onSearch,
                    enabled = state.query.isNotBlank() && !state.searching,
                    modifier = Modifier.testTag("songs_search_button"),
                ) {
                    Text("Найти")
                }
            }
        }

        if (state.query.isNotBlank()) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(onClick = onClearSearch) { Text("Очистить") }
                }
            }
        }

        if (state.searching) {
            item {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            }
        }

        item {
            ExampleImportCard(
                enabled = state.importProgress == null,
                onShowImportDialog = onShowImportDialog,
            )
        }

        if (state.searched && !state.searching &&
            state.remoteSongs.isEmpty() && state.remoteArtists.isEmpty()
        ) {
            item {
                Text(
                    text = "Ничего не найдено. Попробуйте другое название или исполнителя.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.remoteArtists.isNotEmpty()) {
            item { SectionTitle("Исполнители") }
            items(state.remoteArtists, key = { it.url }) { artist ->
                RemoteArtistCard(
                    artist = artist,
                    enabled = state.importProgress == null,
                    onImport = { onImportArtist(artist) },
                )
            }
        }

        if (state.remoteSongs.isNotEmpty()) {
            item { SectionTitle("Песни") }
            items(state.remoteSongs, key = { it.url }) { remote ->
                RemoteSongCard(
                    remote = remote,
                    saved = state.isSaved(remote),
                    loading = state.loadingUrl == remote.url,
                    onOpen = { onOpenRemote(remote) },
                    onSave = { onSaveRemote(remote) },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        modifier = Modifier.padding(top = 8.dp),
    )
}

@Composable
private fun ExampleImportCard(enabled: Boolean, onShowImportDialog: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Пример: «Король и Шут» и «Би-2»",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "Загрузить все уникальные песни этих групп в папки исполнителей. " +
                    "Повторный запуск пропустит уже сохранённое.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp),
            )
            Button(
                onClick = onShowImportDialog,
                enabled = enabled,
                modifier = Modifier.testTag("songs_import_examples"),
            ) {
                Text("Загрузить все песни")
            }
        }
    }
}

@Composable
private fun RemoteArtistCard(
    artist: RemoteArtist,
    enabled: Boolean,
    onImport: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(artist.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = artist.songCount?.let(::arrangementsCount) ?: "Все песни",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            OutlinedButton(onClick = onImport, enabled = enabled) {
                Text("Все песни")
            }
        }
    }
}

@Composable
private fun RemoteSongCard(
    remote: RemoteSong,
    saved: Boolean,
    loading: Boolean,
    onOpen: () -> Unit,
    onSave: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = remote.title,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = remote.artist,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = onOpen, enabled = !loading) {
                    Text(if (loading) "Загрузка…" else "Открыть")
                }
                TextButton(onClick = onSave, enabled = !loading && !saved) {
                    Text(if (saved) "✓ Сохранено" else "Сохранить")
                }
                if (loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                    )
                }
            }
        }
    }
}

@Composable
private fun ImportProgressCard(
    progress: ImportProgress,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.tertiaryContainer,
        ),
    ) {
        Column(Modifier.padding(16.dp)) {
            Text(
                text = "Импорт ${progress.artistNumber}/${progress.artistsTotal}: " +
                    progress.artistName,
                style = MaterialTheme.typography.titleSmall,
            )

            Spacer(Modifier.height(8.dp))
            if (progress.indeterminate) {
                LinearProgressIndicator(Modifier.fillMaxWidth())
            } else {
                LinearProgressIndicator(
                    progress = { progress.percent },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Text(
                text = if (progress.indeterminate) {
                    "Загружаю список песен…"
                } else {
                    "${progress.done} из ${progress.total} · сохранено ${progress.saved}, " +
                        "пропущено ${progress.skipped}, ошибок ${progress.failed}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onTertiaryContainer,
                modifier = Modifier.padding(top = 8.dp),
            )

            if (progress.currentTitle.isNotEmpty()) {
                Text(
                    text = progress.currentTitle,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                if (progress.finished) {
                    TextButton(onClick = onDismiss) {
                        Text("Скрыть")
                    }
                } else {
                    TextButton(onClick = onCancel) {
                        Text("Отмена")
                    }
                }
            }
        }
    }
}

private fun plural(count: Int, one: String, few: String, many: String): String {
    val mod100 = count % 100
    val mod10 = count % 10
    val word = when {
        mod100 in 11..14 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
    return "$count $word"
}

private fun songsCount(count: Int): String = plural(count, "песня", "песни", "песен")

private fun arrangementsCount(count: Int): String = plural(count, "подбор", "подбора", "подборов")

package ru.eurovafel.guitartuner.ui.songs

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
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
import ru.eurovafel.guitartuner.catalog.CatalogSource
import ru.eurovafel.guitartuner.catalog.RemoteArtist
import ru.eurovafel.guitartuner.catalog.RemoteSong
import ru.eurovafel.guitartuner.data.Song
import ru.eurovafel.guitartuner.model.ChordTransposer

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
    var pendingDelete by remember { mutableStateOf<Song?>(null) }
    var pendingBulkDelete by remember { mutableStateOf(false) }

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

    BackHandler(
        enabled = tab == 0 &&
            (state.selectionMode || state.openedArtist != null || state.favoritesOpen),
    ) {
        when {
            // Сначала выходим из режима выбора, чтобы случайно не закрыть папку.
            state.selectionMode -> viewModel.setSelectionMode(false)
            state.favoritesOpen -> viewModel.closeFavorites()
            else -> viewModel.closeArtist()
        }
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
                        onOpenFavorites = viewModel::openFavorites,
                        onCloseFavorites = viewModel::closeFavorites,
                        onOpenSong = onOpenSong,
                        onShowImportDialog = { showImportDialog = true },
                        onLocalQueryChange = viewModel::onLocalQueryChange,
                        onClearLocalQuery = viewModel::clearLocalQuery,
                        onDeleteSong = { pendingDelete = it },
                        onToggleFavorite = viewModel::toggleFavorite,
                        onToggleSelected = viewModel::toggleSelected,
                        onSetSelectionMode = viewModel::setSelectionMode,
                        onSelectAll = viewModel::selectAll,
                        onClearSelection = viewModel::clearSelection,
                        onDeleteSelected = { pendingBulkDelete = true },
                    )
                    else -> OnlineContent(
                        state = state,
                        onQueryChange = viewModel::onQueryChange,
                        onSelectSource = viewModel::selectSource,
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

    pendingDelete?.let { song ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Удалить песню?") },
            text = {
                Text(
                    "«${song.title}» исчезнет из песенника и поиска. " +
                        "Её можно снова найти в онлайн-каталоге.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingDelete = null
                    viewModel.deleteSong(song)
                }) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("Отмена")
                }
            },
        )
    }

    if (pendingBulkDelete) {
        val count = state.selectedIds.size
        AlertDialog(
            onDismissRequest = { pendingBulkDelete = false },
            title = { Text("Удалить выбранные песни?") },
            text = {
                Text(
                    if (count == 1) {
                        "Одна песня исчезнет из песенника и поиска."
                    } else {
                        "Из песенника исчезнут $count песен. Их можно снова найти " +
                            "в онлайн-каталоге."
                    },
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingBulkDelete = false
                    viewModel.deleteSelected()
                }) {
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingBulkDelete = false }) {
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
    onOpenFavorites: () -> Unit,
    onCloseFavorites: () -> Unit,
    onOpenSong: (Song) -> Unit,
    onShowImportDialog: () -> Unit,
    onLocalQueryChange: (String) -> Unit,
    onClearLocalQuery: () -> Unit,
    onDeleteSong: (Song) -> Unit,
    onToggleFavorite: (Song) -> Unit,
    onToggleSelected: (Song) -> Unit,
    onSetSelectionMode: (Boolean) -> Unit,
    onSelectAll: (List<Song>) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
) {
    val folder = state.openedFolder
    val favoritesOpen = state.favoritesOpen
    val listSongs = when {
        folder != null -> folder.songs
        favoritesOpen -> state.favorites
        else -> emptyList()
    }
    val listMode = folder != null || favoritesOpen
    val selectionMode = state.selectionMode && listMode

    if (!listMode) {
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
            }
            item {
                FavoriteFolderCard(
                    count = state.favorites.size,
                    onClick = onOpenFavorites,
                )
            }
            item {
                LocalSearchField(
                    query = state.localQuery,
                    onQueryChange = onLocalQueryChange,
                    onClear = onClearLocalQuery,
                )
            }

            if (state.localSearching) {
                item {
                    Text(
                        text = if (state.localMatches.isEmpty()) {
                            "Ничего не найдено"
                        } else {
                            "Найдено: ${songsCount(state.localMatches.size)}"
                        },
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.localMatches.isEmpty()) {
                    item {
                        Text(
                            text = "В сохранённых песнях нет совпадений по запросу " +
                                "«${state.localQuery.trim()}».",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.testTag("songs_local_empty"),
                        )
                    }
                } else {
                    items(state.localMatches, key = { it.id }) { song ->
                        LibrarySongCard(
                            song = song,
                            showArtist = true,
                            onClick = { onOpenSong(song) },
                            onDelete = { onDeleteSong(song) },
                            onToggleFavorite = { onToggleFavorite(song) },
                        )
                    }
                }
            } else {
                item {
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
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                ListHeader(
                    title = if (favoritesOpen) "Избранное" else folder?.artist.orEmpty(),
                    subtitle = songsCount(listSongs.size),
                    selectionMode = selectionMode,
                    canSelect = listSongs.isNotEmpty(),
                    onBack = if (favoritesOpen) onCloseFavorites else onCloseArtist,
                    onToggleSelection = {
                        onSetSelectionMode(!selectionMode)
                    },
                )
                if (selectionMode) {
                    SelectionActions(
                        selectedCount = state.selectedIds.size,
                        totalCount = listSongs.size,
                        onSelectAll = { onSelectAll(listSongs) },
                        onClearSelection = onClearSelection,
                        onDeleteSelected = onDeleteSelected,
                    )
                }
                HorizontalDivider(Modifier.padding(top = 4.dp, bottom = 4.dp))
            }

            if (listSongs.isEmpty()) {
                item {
                    Text(
                        text = if (favoritesOpen) {
                            "Пока нет любимых песен. Нажмите ♡ в карточке песни — " +
                                "она появится здесь."
                        } else {
                            "В этой папке нет песен."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.testTag("songs_list_empty"),
                    )
                }
            } else {
                items(listSongs, key = { it.id }) { song ->
                    LibrarySongCard(
                        song = song,
                        showArtist = favoritesOpen,
                        selectionMode = selectionMode,
                        selected = song.id in state.selectedIds,
                        onClick = {
                            if (selectionMode) onToggleSelected(song) else onOpenSong(song)
                        },
                        onDelete = { onDeleteSong(song) },
                        onToggleFavorite = { onToggleFavorite(song) },
                    )
                }
            }
        }
    }
}

@Composable
private fun FavoriteFolderCard(count: Int, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("songs_favorites"),
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "♥",
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = "Избранное",
                    style = MaterialTheme.typography.titleMedium,
                )
                Text(
                    text = songsCount(count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Text("›", style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Composable
private fun ListHeader(
    title: String,
    subtitle: String,
    selectionMode: Boolean,
    canSelect: Boolean,
    onBack: () -> Unit,
    onToggleSelection: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onBack) { Text("← Папки") }
        Column(Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (canSelect) {
            TextButton(
                onClick = onToggleSelection,
                modifier = Modifier.testTag("songs_selection_toggle"),
            ) {
                Text(if (selectionMode) "Готово" else "Выбрать")
            }
        }
    }
}

/** Панель группового удаления: появляется только в режиме выбора. */
@Composable
private fun SelectionActions(
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("songs_selection_actions"),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Выбрано: $selectedCount",
            style = MaterialTheme.typography.labelLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = if (selectedCount == totalCount) onClearSelection else onSelectAll,
        ) {
            Text(if (selectedCount == totalCount) "Снять" else "Выбрать все")
        }
        Button(
            onClick = onDeleteSelected,
            enabled = selectedCount > 0,
            modifier = Modifier.testTag("songs_delete_selected"),
        ) {
            Text("Удалить")
        }
    }
}

/** Поиск только по сохранённым песням: без сети и без задержек. */
@Composable
private fun LocalSearchField(
    query: String,
    onQueryChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .testTag("songs_local_search"),
        singleLine = true,
        label = { Text("Поиск в моих песнях") },
        placeholder = { Text("Название или исполнитель") },
        trailingIcon = {
            if (query.isNotEmpty()) {
                TextButton(
                    onClick = onClear,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                ) {
                    Text("✕")
                }
            }
        },
    )
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
private fun LibrarySongCard(
    song: Song,
    onClick: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    showArtist: Boolean = false,
    selectionMode: Boolean = false,
    selected: Boolean = false,
) {
    val key = remember(song.id, song.key, song.chords) {
        song.key ?: ChordTransposer.detectKey(song.chords)
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag("songs_song_${song.id}"),
        colors = if (selected) {
            CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer,
            )
        } else {
            CardDefaults.cardColors()
        },
    ) {
        Row(
            modifier = Modifier.padding(
                start = if (selectionMode) 4.dp else 16.dp,
                top = 12.dp,
                bottom = 12.dp,
                end = 4.dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onClick() },
                    modifier = Modifier.testTag("songs_checkbox_${song.id}"),
                )
                Spacer(Modifier.width(8.dp))
            }
            Column(Modifier.weight(1f)) {
                if (showArtist) {
                    Text(
                        text = song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(song.title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = buildString {
                        append(song.bpm)
                        append(" BPM")
                        key?.let {
                            append(" · ")
                            append(it)
                        }
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (!selectionMode) {
                TextButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.testTag("songs_favorite_${song.id}"),
                ) {
                    Text(
                        text = if (song.favorite) "♥" else "♡",
                        style = MaterialTheme.typography.titleLarge,
                        color = if (song.favorite) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
                TextButton(
                    onClick = onDelete,
                    modifier = Modifier.testTag("songs_delete_${song.id}"),
                ) {
                    Text("Удалить")
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnlineContent(
    state: SongsUiState,
    onQueryChange: (String) -> Unit,
    onSelectSource: (CatalogSource) -> Unit,
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
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Где искать",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    CatalogSource.entries.forEach { source ->
                        FilterChip(
                            selected = state.source == source,
                            onClick = { onSelectSource(source) },
                            label = { Text(source.title) },
                            enabled = !state.searching,
                            modifier = Modifier.testTag("songs_source_${source.name}"),
                        )
                    }
                }
            }
        }

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
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                    Text(
                        text = "Ищу на ${state.source.title}…",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
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
                    text = "На ${state.source.title} ничего не найдено. Попробуйте другое " +
                        "название, исполнителя или другой источник.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (state.remoteArtists.isNotEmpty()) {
            item { SectionTitle("Исполнители · ${state.source.title}") }
            items(state.remoteArtists, key = { it.url }) { artist ->
                RemoteArtistCard(
                    artist = artist,
                    enabled = state.importProgress == null,
                    onImport = { onImportArtist(artist) },
                )
            }
        }

        if (state.remoteSongs.isNotEmpty()) {
            item { SectionTitle("Песни · ${state.source.title}") }
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
                text = "${remote.artist} · ${remote.sourceName}",
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

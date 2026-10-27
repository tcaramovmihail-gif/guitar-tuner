package ru.eurovafel.guitartuner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.eurovafel.guitartuner.R
import ru.eurovafel.guitartuner.data.Song
import ru.eurovafel.guitartuner.ui.chords.ChordsScreen
import ru.eurovafel.guitartuner.ui.feedback.FeedbackScreen
import ru.eurovafel.guitartuner.ui.feedback.FeedbackViewModel
import ru.eurovafel.guitartuner.ui.metronome.MetronomeScreen
import ru.eurovafel.guitartuner.ui.places.PlacesScreen
import ru.eurovafel.guitartuner.ui.places.PlacesViewModel
import ru.eurovafel.guitartuner.ui.settings.SettingsScreen
import ru.eurovafel.guitartuner.ui.songs.SongEditScreen
import ru.eurovafel.guitartuner.ui.songs.SongScreen
import ru.eurovafel.guitartuner.ui.songs.SongsScreen
import ru.eurovafel.guitartuner.ui.songs.SongsViewModel
import ru.eurovafel.guitartuner.ui.theme.ThemeChoice
import ru.eurovafel.guitartuner.ui.theme.ThemeMode
import ru.eurovafel.guitartuner.ui.tuner.TunerScreen

@Composable
fun AppRoot(
    importExamples: Boolean = false,
    themeChoice: ThemeChoice = ThemeChoice.MINT,
    onThemeChange: (ThemeChoice) -> Unit = {},
    themeMode: ThemeMode = ThemeMode.SYSTEM,
    onThemeModeChange: (ThemeMode) -> Unit = {},
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openedSong by remember { mutableStateOf<Song?>(null) }
    var editorOpen by remember { mutableStateOf(false) }
    var editorSong by remember { mutableStateOf<Song?>(null) }
    var settingsOpen by remember { mutableStateOf(false) }
    var metronomeOpen by remember { mutableStateOf(false) }
    val songsViewModel: SongsViewModel = viewModel()
    val placesViewModel: PlacesViewModel = viewModel()
    val feedbackViewModel: FeedbackViewModel = viewModel()

    val fullScreen = openedSong != null || editorOpen || settingsOpen || metronomeOpen

    LaunchedEffect(importExamples) {
        if (importExamples) {
            songsViewModel.importExamples()
        }
    }

    Scaffold(
        modifier = Modifier.testTag("app_root"),
        bottomBar = {
            if (!fullScreen) {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { NavIcon(R.drawable.ic_tuner) },
                        label = { Text("Тюнер") },
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { NavIcon(R.drawable.ic_songs) },
                        label = { Text("Песни") },
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { NavIcon(R.drawable.ic_chords) },
                        label = { Text("Аккорды") },
                    )
                    NavigationBarItem(
                        selected = tab == 3,
                        onClick = { tab = 3 },
                        icon = { NavIcon(R.drawable.ic_map) },
                        label = { Text("Карта") },
                    )
                    NavigationBarItem(
                        selected = tab == 4,
                        onClick = { tab = 4 },
                        icon = { NavIcon(R.drawable.ic_feedback) },
                        label = { Text("Отзыв") },
                    )
                }
            }
        },
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val song = openedSong
            when {
                metronomeOpen -> MetronomeScreen(onBack = { metronomeOpen = false })

                settingsOpen -> SettingsScreen(
                    themeChoice = themeChoice,
                    themeMode = themeMode,
                    onThemeChoiceChange = onThemeChange,
                    onThemeModeChange = onThemeModeChange,
                    onOpenMetronome = { metronomeOpen = true },
                    onBack = { settingsOpen = false },
                    cachedSearches = songsViewModel::cachedSearches,
                    onClearCache = songsViewModel::clearCatalogCache,
                )

                editorOpen -> SongEditScreen(
                    song = editorSong,
                    onBack = {
                        editorOpen = false
                        editorSong = null
                    },
                    onSave = { title, artist, bpm, chords, key ->
                        songsViewModel.saveManualSong(
                            existing = editorSong,
                            title = title,
                            artist = artist,
                            bpm = bpm,
                            chords = chords,
                            key = key,
                        ) { saved ->
                            editorOpen = false
                            editorSong = null
                            openedSong = saved
                        }
                    },
                )

                song != null -> SongScreen(
                    song = song,
                    onBack = { openedSong = null },
                    onDelete = {
                        songsViewModel.deleteSong(song)
                        openedSong = null
                    },
                    onPlay = {
                        placesViewModel.recordCurrent(song.title)
                        songsViewModel.markPlayed(song)
                    },
                    onBpmChange = { bpm -> songsViewModel.updateBpm(song, bpm) },
                    onEdit = {
                        editorSong = song
                        editorOpen = true
                    },
                    onOpenMetronome = { metronomeOpen = true },
                )

                tab == 0 -> TunerScreen(
                    onOpenAppSettings = { settingsOpen = true },
                    onOpenMetronome = { metronomeOpen = true },
                )
                tab == 1 -> SongsScreen(
                    viewModel = songsViewModel,
                    onOpenSong = { openedSong = it },
                    onAddSong = {
                        editorSong = null
                        editorOpen = true
                    },
                    onEditSong = { songToEdit ->
                        editorSong = songToEdit
                        editorOpen = true
                    },
                )
                tab == 2 -> ChordsScreen()
                tab == 3 -> PlacesScreen(viewModel = placesViewModel)
                else -> FeedbackScreen(viewModel = feedbackViewModel)
            }
        }
    }
}

@Composable
private fun NavIcon(drawable: Int) {
    Icon(
        painter = painterResource(drawable),
        contentDescription = null,
    )
}

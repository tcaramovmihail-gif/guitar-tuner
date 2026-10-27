package ru.eurovafel.guitartuner.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
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
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.eurovafel.guitartuner.data.Song
import ru.eurovafel.guitartuner.ui.chords.ChordsScreen
import ru.eurovafel.guitartuner.ui.feedback.FeedbackScreen
import ru.eurovafel.guitartuner.ui.feedback.FeedbackViewModel
import ru.eurovafel.guitartuner.ui.places.PlacesScreen
import ru.eurovafel.guitartuner.ui.places.PlacesViewModel
import ru.eurovafel.guitartuner.ui.songs.SongScreen
import ru.eurovafel.guitartuner.ui.songs.SongsScreen
import ru.eurovafel.guitartuner.ui.songs.SongsViewModel
import ru.eurovafel.guitartuner.ui.theme.ThemeChoice
import ru.eurovafel.guitartuner.ui.tuner.TunerScreen

@Composable
fun AppRoot(
    importExamples: Boolean = false,
    themeChoice: ThemeChoice = ThemeChoice.MINT,
    onThemeChange: (ThemeChoice) -> Unit = {},
) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openedSong by remember { mutableStateOf<Song?>(null) }
    val songsViewModel: SongsViewModel = viewModel()
    val placesViewModel: PlacesViewModel = viewModel()
    val feedbackViewModel: FeedbackViewModel = viewModel()

    LaunchedEffect(importExamples) {
        if (importExamples) {
            songsViewModel.importExamples()
        }
    }

    Scaffold(
        modifier = Modifier.testTag("app_root"),
        bottomBar = {
            if (openedSong == null) {
                NavigationBar {
                    NavigationBarItem(
                        selected = tab == 0,
                        onClick = { tab = 0 },
                        icon = { Text("♪") },
                        label = { Text("Тюнер") },
                    )
                    NavigationBarItem(
                        selected = tab == 1,
                        onClick = { tab = 1 },
                        icon = { Text("≡") },
                        label = { Text("Песни") },
                    )
                    NavigationBarItem(
                        selected = tab == 2,
                        onClick = { tab = 2 },
                        icon = { Text("♬") },
                        label = { Text("Аккорды") },
                    )
                    NavigationBarItem(
                        selected = tab == 3,
                        onClick = { tab = 3 },
                        icon = { Text("◎") },
                        label = { Text("Карта") },
                    )
                    NavigationBarItem(
                        selected = tab == 4,
                        onClick = { tab = 4 },
                        icon = { Text("★") },
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
                song != null -> SongScreen(
                    song = song,
                    onBack = { openedSong = null },
                    onDelete = {
                        songsViewModel.deleteSong(song)
                        openedSong = null
                    },
                    onPlay = { placesViewModel.recordCurrent(song.title) },
                )

                tab == 0 -> TunerScreen(
                    themeChoice = themeChoice,
                    onThemeChange = onThemeChange,
                )
                tab == 1 -> SongsScreen(
                    viewModel = songsViewModel,
                    onOpenSong = { openedSong = it },
                )
                tab == 2 -> ChordsScreen()
                tab == 3 -> PlacesScreen(viewModel = placesViewModel)
                else -> FeedbackScreen(viewModel = feedbackViewModel)
            }
        }
    }
}

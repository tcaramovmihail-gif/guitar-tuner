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
import ru.eurovafel.guitartuner.ui.songs.SongScreen
import ru.eurovafel.guitartuner.ui.songs.SongsScreen
import ru.eurovafel.guitartuner.ui.songs.SongsViewModel
import ru.eurovafel.guitartuner.ui.tuner.TunerScreen

@Composable
fun AppRoot(importExamples: Boolean = false) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var openedSong by remember { mutableStateOf<Song?>(null) }
    val songsViewModel: SongsViewModel = viewModel()

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
                )

                tab == 0 -> TunerScreen()
                else -> SongsScreen(
                    viewModel = songsViewModel,
                    onOpenSong = { openedSong = it },
                )
            }
        }
    }
}

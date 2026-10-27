package ru.eurovafel.guitartuner.ui.places

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.BoundingBox
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import ru.eurovafel.guitartuner.R
import ru.eurovafel.guitartuner.data.PlayPlace
import ru.eurovafel.guitartuner.data.PlayVisit
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Карта мест, где пользователь играл.
 *
 * Точки в радиусе 300 метров объединены в одну гитару. Касание гитары
 * открывает историю: какие песни здесь играли и когда.
 */
@Composable
fun PlacesScreen(viewModel: PlacesViewModel) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        viewModel.refreshPermission()
        viewModel.refresh()
    }

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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .testTag("places_screen"),
    ) {
        if (!state.permissionGranted) {
            LocationPermissionCard(
                onRequest = {
                    permissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION,
                        ),
                    )
                },
            )
        } else {
            PlacesMap(
                state = state,
                onOpenPlace = viewModel::openPlace,
                onLocatedShown = viewModel::consumeMyLocation,
            )

            Card(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(12.dp)
                    .testTag("places_counter"),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.94f),
                ),
            ) {
                Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                    Text(
                        text = "Мест игры: ${state.places.size} · песен: ${state.totalVisits}",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        text = if (state.places.isEmpty()) {
                            "Нажмите «Играть» в песне — точка появится здесь автоматически."
                        } else {
                            "Точки в радиусе 300 м — одна гитара. Коснитесь её, чтобы " +
                                "увидеть историю."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            ExtendedFloatingActionButton(
                onClick = viewModel::locateMe,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp)
                    .testTag("places_locate"),
                icon = { Text("◎") },
                text = { Text(if (state.locating) "Ищу…" else "Я здесь") },
            )

            if (state.loading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                )
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(8.dp),
        )
    }

    val selected = state.selectedPlace
    if (selected != null) {
        PlaceHistorySheet(
            place = selected,
            history = state.history,
            loading = state.historyLoading,
            onDelete = { viewModel.deletePlace(selected) },
            onDismiss = viewModel::closePlace,
        )
    }
}

@Composable
private fun LocationPermissionCard(onRequest: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Карта мест игры",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = "Приложение отметит гитарой место, где вы нажали «Играть» " +
                        "в песне. Для этого нужен доступ к геолокации устройства.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text(
                    text = "Координаты остаются только на телефоне: без аккаунта и " +
                        "без отправки на сервер.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = onRequest,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("places_request_permission"),
                ) {
                    Text("Разрешить геолокацию")
                }
                Text(
                    text = "Если окно не появилось, разрешите геолокацию в настройках " +
                        "приложения.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun PlacesMap(
    state: PlacesUiState,
    onOpenPlace: (PlayPlace) -> Unit,
    onLocatedShown: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    // MapView создаётся один раз: пересоздание на каждой рекомпозиции рвёт
    // загрузчик тайлов (osmdroid пишет «map shutdown» и карта остаётся серой).
    val map = remember(context) {
        MapView(context).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            setBuiltInZoomControls(false)
            controller.setZoom(4.0)
            controller.setCenter(GeoPoint(55.751244, 37.618423))
        }
    }
    var centeredOnPlaces by remember { mutableStateOf(false) }

    // Карта должна останавливать тайлы вместе с экраном, иначе батарея тает.
    DisposableEffect(lifecycleOwner, map) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> map.onResume()
                Lifecycle.Event.ON_PAUSE -> map.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        map.onResume()
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            map.onPause()
            map.onDetach()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { map },
        update = { view ->
            updateMarkers(view, state.places, onOpenPlace)
        },
    )

    LaunchedEffect(map, state.places) {
        if (centeredOnPlaces || state.places.isEmpty()) return@LaunchedEffect
        val points = state.places.map { GeoPoint(it.latitude, it.longitude) }
        if (points.size == 1) {
            map.controller.setZoom(15.0)
            map.controller.setCenter(points.first())
        } else {
            map.zoomToBoundingBox(BoundingBox.fromGeoPoints(points), true, 140)
        }
        centeredOnPlaces = true
    }

    LaunchedEffect(state.myLatitude, state.myLongitude) {
        val latitude = state.myLatitude
        val longitude = state.myLongitude
        if (latitude == null || longitude == null) return@LaunchedEffect
        map.controller.setZoom(16.0)
        map.controller.animateTo(GeoPoint(latitude, longitude))
        onLocatedShown()
    }
}

/** Маленькие гитары вместо стандартных «капель»: видно, что это места игры. */
private fun updateMarkers(
    map: MapView,
    places: List<PlayPlace>,
    onOpenPlace: (PlayPlace) -> Unit,
) {
    map.overlays.removeAll { overlay -> overlay is Marker }
    places.forEach { place ->
        val marker = Marker(map).apply {
            position = GeoPoint(place.latitude, place.longitude)
            setIcon(ContextCompat.getDrawable(map.context, R.drawable.ic_guitar_marker))
            setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            title = place.lastSong ?: "Место игры"
            snippet = placeDescription(place)
            setOnMarkerClickListener { clicked, _ ->
                onOpenPlace(place)
                clicked.closeInfoWindow()
                true
            }
        }
        map.overlays.add(marker)
    }
    map.invalidate()
}

private fun placeDescription(place: PlayPlace): String {
    val date = formatDateTime(place.updatedAt)
    val songs = when (place.visitCount) {
        0 -> "пока без песен"
        1 -> "1 песня"
        else -> "${place.visitCount} песен"
    }
    return "$date · $songs"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PlaceHistorySheet(
    place: PlayPlace,
    history: List<PlayVisit>,
    loading: Boolean,
    onDelete: () -> Unit,
    onDismiss: () -> Unit,
) {
    var confirmDelete by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        modifier = Modifier.testTag("place_history_sheet"),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "История места",
                    style = MaterialTheme.typography.headlineSmall,
                )
                Text(
                    text = buildString {
                        append(formatDateTime(place.createdAt))
                        append(" · ")
                        append(
                            when (place.visitCount) {
                                0 -> "пока без песен"
                                1 -> "1 песня"
                                else -> "${place.visitCount} песен"
                            },
                        )
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = "Песни, сыгранные в радиусе 300 метров, собраны под одной гитарой.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            HorizontalDivider()

            when {
                loading -> LinearProgressIndicator(Modifier.fillMaxWidth())
                history.isEmpty() -> Text(
                    text = "Здесь ещё не играли. Нажмите «Играть» в песне — она появится " +
                        "в истории этого места.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                else -> LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                ) {
                    items(history, key = { it.id }) { visit ->
                        VisitRow(visit)
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(
                    onClick = { confirmDelete = true },
                    modifier = Modifier.testTag("place_delete"),
                ) {
                    Text(
                        text = "Удалить место",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
                TextButton(onClick = onDismiss) {
                    Text("Закрыть")
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить место?") },
            text = {
                Text(
                    "История песен этого места тоже удалится. Точку нельзя будет " +
                        "восстановить.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    },
                ) {
                    Text(
                        text = "Удалить",
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text("Отмена")
                }
            },
        )
    }
}

@Composable
private fun VisitRow(visit: PlayVisit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "♪",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = visit.songTitle ?: "Песня без названия",
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = formatDateTime(visit.playedAt),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

private fun formatDateTime(timestamp: Long): String {
    val value = if (timestamp > 0) timestamp else System.currentTimeMillis()
    return SimpleDateFormat("d MMMM, HH:mm", Locale("ru")).format(Date(value))
}

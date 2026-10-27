package ru.eurovafel.guitartuner.ui.places

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.eurovafel.guitartuner.data.PlaceRecordResult
import ru.eurovafel.guitartuner.data.PlayPlace
import ru.eurovafel.guitartuner.data.PlayPlaceRepository
import ru.eurovafel.guitartuner.data.PlayVisit

data class PlacesUiState(
    val places: List<PlayPlace> = emptyList(),
    val loading: Boolean = true,
    val permissionGranted: Boolean = false,
    val locating: Boolean = false,
    val myLatitude: Double? = null,
    val myLongitude: Double? = null,
    val selectedPlace: PlayPlace? = null,
    val history: List<PlayVisit> = emptyList(),
    val historyLoading: Boolean = false,
    val message: String? = null,
    val error: String? = null,
) {
    val totalVisits: Int get() = places.sumOf { it.visitCount }
}

/** Карта мест игры: одна гитара на район в радиусе 300 м и история песен. */
class PlacesViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PlayPlaceRepository(application.applicationContext)

    private val _state = MutableStateFlow(
        PlacesUiState(permissionGranted = repository.hasLocationPermission()),
    )
    val state: StateFlow<PlacesUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            repository.places.collect { places ->
                _state.update { current ->
                    val selected = current.selectedPlace
                    val refreshed = selected?.let { place ->
                        places.firstOrNull { it.id == place.id } ?: place
                    }
                    current.copy(
                        places = places,
                        loading = false,
                        selectedPlace = refreshed,
                    )
                }
            }
        }
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            _state.update { it.copy(loading = true) }
            runCatching { repository.refresh() }
                .onFailure { error ->
                    _state.update { it.copy(error = "Не удалось прочитать карту: ${error.message}") }
                }
            _state.update {
                it.copy(
                    loading = false,
                    permissionGranted = repository.hasLocationPermission(),
                )
            }
        }
    }

    /** Вызывается после ответа на системный запрос разрешения. */
    fun refreshPermission() {
        _state.update { it.copy(permissionGranted = repository.hasLocationPermission()) }
    }

    /**
     * Отмечает место, где пользователь начал играть.
     *
     * Вызывается из экрана песни, поэтому карта может быть не открыта:
     * сообщение увидит при следующем заходе на вкладку.
     */
    fun recordCurrent(songTitle: String?) {
        if (!repository.hasLocationPermission()) {
            _state.update {
                it.copy(
                    permissionGranted = false,
                    message = "Разрешите геолокацию на вкладке «Карта», чтобы отмечать места",
                )
            }
            return
        }
        viewModelScope.launch {
            when (val result = repository.recordCurrent(songTitle)) {
                is PlaceRecordResult.Created -> _state.update {
                    it.copy(message = "Новое место добавлено на карту")
                }
                is PlaceRecordResult.Merged -> _state.update {
                    it.copy(
                        message = "«${result.songTitle ?: "Игра"}» добавлена в историю места",
                    )
                }
                PlaceRecordResult.Duplicate -> _state.update {
                    it.copy(message = "Эта песня уже отмечена здесь недавно")
                }
                PlaceRecordResult.NoPermission -> _state.update {
                    it.copy(
                        permissionGranted = false,
                        message = "Разрешите геолокацию, чтобы отмечать места",
                    )
                }
                PlaceRecordResult.NoLocation -> _state.update {
                    it.copy(message = "Не удалось определить место — проверьте GPS")
                }
            }
        }
    }

    fun openPlace(place: PlayPlace) {
        _state.update {
            it.copy(
                selectedPlace = place,
                history = emptyList(),
                historyLoading = true,
            )
        }
        viewModelScope.launch {
            val history = runCatching { repository.history(place.id) }.getOrDefault(emptyList())
            _state.update {
                if (it.selectedPlace?.id == place.id) {
                    it.copy(history = history, historyLoading = false)
                } else {
                    it
                }
            }
        }
    }

    fun closePlace() {
        _state.update {
            it.copy(selectedPlace = null, history = emptyList(), historyLoading = false)
        }
    }

    fun deletePlace(place: PlayPlace) {
        viewModelScope.launch {
            runCatching { repository.delete(place) }
                .onSuccess {
                    _state.update {
                        it.copy(
                            selectedPlace = null,
                            history = emptyList(),
                            historyLoading = false,
                            message = "Место удалено",
                        )
                    }
                }
                .onFailure { error ->
                    _state.update { it.copy(error = "Не удалось удалить место: ${error.message}") }
                }
        }
    }

    /** Показать на карте, где пользователь сейчас. */
    fun locateMe() {
        if (!repository.hasLocationPermission()) {
            _state.update {
                it.copy(
                    permissionGranted = false,
                    message = "Сначала разрешите геолокацию",
                )
            }
            return
        }
        if (_state.value.locating) return
        _state.update { it.copy(locating = true) }
        viewModelScope.launch {
            val location = runCatching { repository.currentLocation() }.getOrNull()
            _state.update {
                if (location != null) {
                    it.copy(
                        locating = false,
                        myLatitude = location.latitude,
                        myLongitude = location.longitude,
                    )
                } else {
                    it.copy(
                        locating = false,
                        message = "Не удалось определить место — проверьте GPS",
                    )
                }
            }
        }
    }

    fun consumeMyLocation() {
        _state.update { it.copy(myLatitude = null, myLongitude = null) }
    }

    fun consumeMessage() {
        _state.update { it.copy(message = null) }
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }
}

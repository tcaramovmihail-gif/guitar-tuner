package ru.eurovafel.guitartuner.ui.feedback

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.eurovafel.guitartuner.data.FeedbackClient

data class FeedbackUiState(
    val rating: Int = 0,
    val opinion: String = "",
    val sending: Boolean = false,
    val sent: Boolean = false,
    val error: String? = null,
)

/** Форма обратной связи: оценка, мнение и скрытая доставка. */
class FeedbackViewModel(application: Application) : AndroidViewModel(application) {

    private val client = FeedbackClient()

    private val _state = MutableStateFlow(FeedbackUiState())
    val state: StateFlow<FeedbackUiState> = _state.asStateFlow()

    fun setRating(rating: Int) {
        _state.update {
            it.copy(
                rating = rating.coerceIn(1, 5),
                sent = false,
                error = null,
            )
        }
    }

    fun onOpinionChange(opinion: String) {
        _state.update {
            it.copy(
                opinion = opinion.take(MAX_OPINION_LENGTH),
                sent = false,
                error = null,
            )
        }
    }

    fun send() {
        val current = _state.value
        if (current.sending || current.rating == 0) return

        viewModelScope.launch {
            _state.update { it.copy(sending = true, error = null) }
            val result = client.send(
                rating = current.rating,
                opinion = current.opinion.trim(),
                versionName = versionName(),
            )
            result
                .onSuccess {
                    _state.update { it.copy(sending = false, sent = true) }
                }
                .onFailure {
                    _state.update {
                        it.copy(
                            sending = false,
                            error = "Не удалось отправить отзыв. Проверьте интернет " +
                                "и попробуйте ещё раз.",
                        )
                    }
                }
        }
    }

    fun reset() {
        _state.value = FeedbackUiState()
    }

    fun consumeError() {
        _state.update { it.copy(error = null) }
    }

    private fun versionName(): String = runCatching {
        val application = getApplication<Application>()
        application.packageManager
            .getPackageInfo(application.packageName, 0)
            .versionName
            .orEmpty()
    }.getOrDefault("")

    private companion object {
        const val MAX_OPINION_LENGTH = 4000
    }
}

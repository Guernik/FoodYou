package com.maksimowiczm.foodyou.app.ui.food.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.result.fold
import com.maksimowiczm.foodyou.food.ai.domain.LlmApiKeyRepository
import com.maksimowiczm.foodyou.food.ai.domain.LogMealItemsUseCase
import com.maksimowiczm.foodyou.food.ai.domain.MealItem
import com.maksimowiczm.foodyou.food.ai.domain.ParseMealDescriptionUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

internal class AiFoodLoggingViewModel(
    private val mealId: Long,
    private val epochDay: Long,
    private val parseMealDescriptionUseCase: ParseMealDescriptionUseCase,
    private val logMealItemsUseCase: LogMealItemsUseCase,
    private val apiKeyRepository: LlmApiKeyRepository,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<AiFoodLoggingUiState>(AiFoodLoggingUiState.Input(hasApiKey = false))
    val uiState: StateFlow<AiFoodLoggingUiState> = _uiState.asStateFlow()

    private val loggedChannel = Channel<Unit>()
    val loggedEvents = loggedChannel.receiveAsFlow()

    init {
        viewModelScope.launch {
            val hasKey = apiKeyRepository.hasKey().first()
            if (_uiState.value is AiFoodLoggingUiState.Input) {
                _uiState.value = AiFoodLoggingUiState.Input(hasApiKey = hasKey)
            }
        }
    }

    fun parse(description: String) {
        viewModelScope.launch {
            _uiState.value = AiFoodLoggingUiState.Loading
            parseMealDescriptionUseCase
                .parse(description)
                .fold(
                    onSuccess = { items -> _uiState.value = AiFoodLoggingUiState.Review(items.toEditable()) },
                    onError = { error -> _uiState.value = AiFoodLoggingUiState.Error(error) },
                )
        }
    }

    fun editItem(id: Long, transform: EditableMealItem.() -> EditableMealItem) {
        val state = _uiState.value as? AiFoodLoggingUiState.Review ?: return
        _uiState.value =
            state.copy(items = state.items.map { if (it.id == id) it.transform() else it })
    }

    fun removeItem(id: Long) {
        val state = _uiState.value as? AiFoodLoggingUiState.Review ?: return
        _uiState.value = state.copy(items = state.items.filterNot { it.id == id })
    }

    fun confirm() {
        val state = _uiState.value as? AiFoodLoggingUiState.Review ?: return
        val items = state.items.map { it.toMealItem() }
        if (items.isEmpty()) return

        viewModelScope.launch {
            _uiState.value = AiFoodLoggingUiState.Logging
            logMealItemsUseCase
                .log(items = items, mealId = mealId, date = LocalDate.fromEpochDays(epochDay))
                .fold(
                    onSuccess = { loggedChannel.send(Unit) },
                    onError = {
                        // Even a partial failure logged some items; surface completion and return.
                        loggedChannel.send(Unit)
                    },
                )
        }
    }

    /** Return to the input screen (e.g. from an error or empty review). */
    fun reset() {
        viewModelScope.launch {
            val hasKey = apiKeyRepository.hasKey().first()
            _uiState.value = AiFoodLoggingUiState.Input(hasApiKey = hasKey)
        }
    }

    private fun List<MealItem>.toEditable(): List<EditableMealItem> =
        mapIndexed { index, item ->
            EditableMealItem(
                id = index.toLong(),
                name = item.name,
                grams = item.estimatedGrams,
                isLiquid = item.isLiquid,
                item = item,
            )
        }
}

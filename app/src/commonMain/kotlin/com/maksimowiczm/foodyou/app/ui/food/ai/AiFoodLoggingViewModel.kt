package com.maksimowiczm.foodyou.app.ui.food.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.result.fold
import com.maksimowiczm.foodyou.food.ai.domain.LlmApiKeyRepository
import com.maksimowiczm.foodyou.food.ai.domain.LogMealItemsUseCase
import com.maksimowiczm.foodyou.food.ai.domain.MealItem
import com.maksimowiczm.foodyou.food.ai.domain.ParseMealDescriptionUseCase
import com.maksimowiczm.foodyou.food.ai.domain.SaveMealItemAsProductUseCase
import com.maksimowiczm.foodyou.food.ai.domain.SaveMealItemsAsRecipeUseCase
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** One-off UI events surfaced as snackbars. */
internal enum class AiFoodLoggingEvent {
    ProductSaved,
    ProductSaveFailed,
    RecipeSaved,
    RecipeSaveFailed,
}

internal class AiFoodLoggingViewModel(
    private val mealId: Long,
    private val epochDay: Long,
    private val parseMealDescriptionUseCase: ParseMealDescriptionUseCase,
    private val logMealItemsUseCase: LogMealItemsUseCase,
    private val saveMealItemAsProductUseCase: SaveMealItemAsProductUseCase,
    private val saveMealItemsAsRecipeUseCase: SaveMealItemsAsRecipeUseCase,
    private val apiKeyRepository: LlmApiKeyRepository,
) : ViewModel() {

    private val _uiState =
        MutableStateFlow<AiFoodLoggingUiState>(AiFoodLoggingUiState.Input(hasApiKey = false))
    val uiState: StateFlow<AiFoodLoggingUiState> = _uiState.asStateFlow()

    private val loggedChannel = Channel<Unit>()
    val loggedEvents = loggedChannel.receiveAsFlow()

    private val eventChannel = Channel<AiFoodLoggingEvent>()
    val events = eventChannel.receiveAsFlow()

    /** The last description sent to [parse], used to prefill the recipe name. */
    var lastDescription: String = ""
        private set

    init {
        viewModelScope.launch {
            val hasKey = apiKeyRepository.hasKey().first()
            if (_uiState.value is AiFoodLoggingUiState.Input) {
                _uiState.value = AiFoodLoggingUiState.Input(hasApiKey = hasKey)
            }
        }
    }

    fun parse(description: String) {
        lastDescription = description.trim()
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

    fun toggleExpanded(id: Long) = editItem(id) { copy(expanded = !expanded) }

    fun removeItem(id: Long) {
        val state = _uiState.value as? AiFoodLoggingUiState.Review ?: return
        _uiState.value = state.copy(items = state.items.filterNot { it.id == id })
    }

    fun saveAsProduct(id: Long) {
        val state = _uiState.value as? AiFoodLoggingUiState.Review ?: return
        val item = state.items.firstOrNull { it.id == id } ?: return

        viewModelScope.launch {
            saveMealItemAsProductUseCase
                .save(item.toMealItem())
                .fold(
                    onSuccess = { productId ->
                        editItem(id) { copy(savedProductId = productId) }
                        eventChannel.send(AiFoodLoggingEvent.ProductSaved)
                    },
                    onError = { eventChannel.send(AiFoodLoggingEvent.ProductSaveFailed) },
                )
        }
    }

    fun saveAsRecipe(name: String) {
        val items = currentItems() ?: return

        viewModelScope.launch {
            val ok = saveRecipe(name, items)
            if (ok) eventChannel.send(AiFoodLoggingEvent.RecipeSaved)
        }
    }

    /** Save the parsed items as a recipe and log them into the diary. */
    fun saveAsRecipeAndLog(name: String) {
        val items = currentItems() ?: return

        viewModelScope.launch {
            val saved = saveRecipe(name, items)
            if (!saved) return@launch
            eventChannel.send(AiFoodLoggingEvent.RecipeSaved)
            logItems(items)
        }
    }

    private suspend fun saveRecipe(name: String, items: List<MealItem>): Boolean =
        saveMealItemsAsRecipeUseCase
            .save(name = name, items = items)
            .fold(
                onSuccess = { true },
                onError = {
                    eventChannel.send(AiFoodLoggingEvent.RecipeSaveFailed)
                    false
                },
            )

    private fun currentItems(): List<MealItem>? {
        val state = _uiState.value as? AiFoodLoggingUiState.Review ?: return null
        val items = state.items.map { it.toMealItem() }
        return items.ifEmpty { null }
    }

    fun confirm() {
        val items = currentItems() ?: return
        viewModelScope.launch { logItems(items) }
    }

    private suspend fun logItems(items: List<MealItem>) {
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
                nutrition = item.nutritionFactsPer100g.toEditable(),
                expanded = false,
                savedProductId = null,
                item = item,
            )
        }

    private fun NutritionFacts.toEditable(): EditableNutrition =
        EditableNutrition(
            energy = energy.value,
            proteins = proteins.value,
            carbohydrates = carbohydrates.value,
            fats = fats.value,
            dietaryFiber = dietaryFiber.value,
            sugars = sugars.value,
            saturatedFats = saturatedFats.value,
            sodium = sodium.value,
        )
}

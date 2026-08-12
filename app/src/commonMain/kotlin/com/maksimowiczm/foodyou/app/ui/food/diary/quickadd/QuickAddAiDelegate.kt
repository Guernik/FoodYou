package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

import com.maksimowiczm.foodyou.common.domain.food.NutrientValue.Companion.toNutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.result.fold
import com.maksimowiczm.foodyou.food.ai.domain.LlmApiKeyRepository
import com.maksimowiczm.foodyou.food.ai.domain.MealItem
import com.maksimowiczm.foodyou.food.ai.domain.ParseMealDescriptionUseCase
import com.maksimowiczm.foodyou.food.ai.domain.ParseMealError
import com.maksimowiczm.foodyou.food.ai.domain.SaveMealItemAsProductUseCase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Shared AI behaviour for the create and update quick add screens.
 *
 * The quick add form stores nutrients as absolute totals for the entry and has no weight field,
 * while the AI returns per 100 g/ml facts plus an estimated portion weight. This delegate owns that
 * conversion in both directions: [analyze] hands out totals for the form, [saveAsProduct] converts
 * whatever is currently in the form back to the per 100 g/ml basis a product requires.
 */
internal class QuickAddAiDelegate(
    private val parseMealDescriptionUseCase: ParseMealDescriptionUseCase,
    private val saveMealItemAsProductUseCase: SaveMealItemAsProductUseCase,
    private val apiKeyRepository: LlmApiKeyRepository,
) {
    private val _state = MutableStateFlow<QuickAddAiState>(QuickAddAiState.Idle(hasApiKey = false))
    val state: StateFlow<QuickAddAiState> = _state.asStateFlow()

    /**
     * The most recent AI result. Kept so [saveAsProduct] knows the estimated portion weight and can
     * preserve the nutrients the quick add form does not expose.
     */
    var lastItem: MealItem? = null
        private set

    /**
     * Observes the API key continuously, so adding a key in AI settings enables the analyze action
     * without having to leave and re-enter the quick add screen.
     */
    fun observeApiKey(scope: CoroutineScope) {
        scope.launch {
            apiKeyRepository.hasKey().collect { hasKey ->
                _state.value =
                    when (val current = _state.value) {
                        is QuickAddAiState.Idle -> current.copy(hasApiKey = hasKey)
                        is QuickAddAiState.Error -> current.copy(hasApiKey = hasKey)
                        QuickAddAiState.Loading -> current
                    }
            }
        }
    }

    /**
     * Analyses [name] as a single combined product and reports the nutrition as totals for the
     * estimated portion, ready to be written into the form. Energy stays in kcal.
     */
    fun analyze(scope: CoroutineScope, name: String, onResult: (QuickAddAiTotals) -> Unit): Job? {
        if (_state.value.isLoading) return null

        return scope.launch {
            _state.value = QuickAddAiState.Loading

            parseMealDescriptionUseCase
                .parse(name, singleProduct = true)
                .fold(
                    onSuccess = { items ->
                        val item = items.firstOrNull()

                        if (item == null) {
                            // singleProduct guarantees one item, so an empty list means the model
                            // recognised nothing usable.
                            _state.value = errorState(ParseMealError.Refused)
                        } else {
                            lastItem = item
                            _state.value = QuickAddAiState.Idle(hasApiKey = true)
                            onResult(item.toTotals())
                        }
                    },
                    onError = { error -> _state.value = errorState(error) },
                )
        }
    }

    /**
     * Saves the current form values as a reusable product.
     *
     * The user may have edited the numbers after an analysis, so the product is always rebuilt from
     * the values passed in rather than from [lastItem] directly. [lastItem] only supplies the
     * portion weight, the liquid flag and the nutrients the quick add form cannot edit.
     *
     * When there was no analysis at all the entry is treated as a 100 g serving, which makes the
     * totals already per 100 g and keeps the option meaningful for hand typed quick adds.
     *
     * @return true when the product was created.
     */
    suspend fun saveAsProduct(
        name: String,
        energyKcal: Double,
        proteins: Double,
        carbohydrates: Double,
        fats: Double,
    ): Boolean {
        val item = lastItem
        val grams = item?.estimatedGrams?.takeIf { it > 0.0 } ?: DEFAULT_SERVING_GRAMS

        // Totals describe `grams` of food; a product stores its facts per 100 g/ml.
        val toPer100g = 100.0 / grams

        val facts =
            (item?.nutritionFactsPer100g?.let { it * (grams / 100.0) } ?: NutritionFacts())
                .copy(
                    energy = energyKcal.toNutrientValue(),
                    proteins = proteins.toNutrientValue(),
                    carbohydrates = carbohydrates.toNutrientValue(),
                    fats = fats.toNutrientValue(),
                ) * toPer100g

        val product =
            MealItem(
                name = name,
                isLiquid = item?.isLiquid == true,
                nutritionFactsPer100g = facts,
                estimatedGrams = grams,
            )

        return saveMealItemAsProductUseCase
            .save(product)
            .fold(onSuccess = { true }, onError = { false })
    }

    /** Clears a previous error, e.g. once the user edits the name again. */
    fun dismissError() {
        val current = _state.value
        if (current is QuickAddAiState.Error) {
            _state.value = QuickAddAiState.Idle(hasApiKey = current.hasApiKey)
        }
    }

    private fun errorState(error: ParseMealError) =
        QuickAddAiState.Error(error = error, hasApiKey = _state.value.hasApiKey)

    private companion object {
        /** Basis used when a product is saved without any AI analysis behind it. */
        const val DEFAULT_SERVING_GRAMS = 100.0
    }
}

/** AI nutrition scaled to the estimated portion, in the units the quick add form uses. */
internal data class QuickAddAiTotals(
    val name: String,
    val energyKcal: Double,
    val proteins: Double,
    val carbohydrates: Double,
    val fats: Double,
)

private fun MealItem.toTotals(): QuickAddAiTotals {
    val totals = nutritionFactsPer100g * (estimatedGrams / 100.0)

    return QuickAddAiTotals(
        name = name,
        energyKcal = totals.energy.value ?: 0.0,
        proteins = totals.proteins.value ?: 0.0,
        carbohydrates = totals.carbohydrates.value ?: 0.0,
        fats = totals.fats.value ?: 0.0,
    )
}

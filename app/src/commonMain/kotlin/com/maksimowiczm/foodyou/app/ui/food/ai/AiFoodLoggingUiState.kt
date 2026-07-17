package com.maksimowiczm.foodyou.app.ui.food.ai

import androidx.compose.runtime.Immutable
import com.maksimowiczm.foodyou.food.ai.domain.MealItem
import com.maksimowiczm.foodyou.food.ai.domain.ParseMealError

@Immutable
sealed interface AiFoodLoggingUiState {
    /** Text input; [hasApiKey] gates whether analysis is possible. */
    data class Input(val hasApiKey: Boolean) : AiFoodLoggingUiState

    data object Loading : AiFoodLoggingUiState

    /** Parsed items awaiting review/confirmation. Empty list = nothing recognized. */
    data class Review(val items: List<EditableMealItem>) : AiFoodLoggingUiState

    data object Logging : AiFoodLoggingUiState

    data class Error(val error: ParseMealError) : AiFoodLoggingUiState
}

@Immutable
data class EditableMealItem(
    val id: Long,
    val name: String,
    val grams: Double,
    val isLiquid: Boolean,
    val item: MealItem,
) {
    fun toMealItem(): MealItem =
        item.copy(name = name.trim(), estimatedGrams = grams, isLiquid = isLiquid)
}

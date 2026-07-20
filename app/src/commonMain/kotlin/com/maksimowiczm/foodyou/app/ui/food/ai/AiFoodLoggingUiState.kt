package com.maksimowiczm.foodyou.app.ui.food.ai

import androidx.compose.runtime.Immutable
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue.Companion.toNutrientValue
import com.maksimowiczm.foodyou.food.ai.domain.MealItem
import com.maksimowiczm.foodyou.food.ai.domain.ParseMealError
import com.maksimowiczm.foodyou.food.domain.entity.FoodId

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

/**
 * The AI-populated per-100g nutrition fields, editable by the user. All values are per 100g/100ml.
 * The other 34 [com.maksimowiczm.foodyou.common.domain.food.NutritionFacts] fields are left at their
 * incomplete defaults.
 */
@Immutable
data class EditableNutrition(
    val energy: Double?,
    val proteins: Double?,
    val carbohydrates: Double?,
    val fats: Double?,
    val dietaryFiber: Double?,
    val sugars: Double?,
    val saturatedFats: Double?,
    val sodium: Double?,
)

@Immutable
data class EditableMealItem(
    val id: Long,
    val name: String,
    val grams: Double,
    val isLiquid: Boolean,
    val nutrition: EditableNutrition,
    /** Whether the collapsible macro editor is open. */
    val expanded: Boolean,
    /** Set once the item has been saved as a reusable product. */
    val savedProductId: FoodId.Product?,
    val item: MealItem,
) {
    fun toMealItem(): MealItem =
        item.copy(
            name = name.trim(),
            estimatedGrams = grams,
            isLiquid = isLiquid,
            nutritionFactsPer100g =
                item.nutritionFactsPer100g.copy(
                    energy = nutrition.energy.toNutrientValue(),
                    proteins = nutrition.proteins.toNutrientValue(),
                    carbohydrates = nutrition.carbohydrates.toNutrientValue(),
                    fats = nutrition.fats.toNutrientValue(),
                    dietaryFiber = nutrition.dietaryFiber.toNutrientValue(),
                    sugars = nutrition.sugars.toNutrientValue(),
                    saturatedFats = nutrition.saturatedFats.toNutrientValue(),
                    sodium = nutrition.sodium.toNutrientValue(),
                ),
        )
}

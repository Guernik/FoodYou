package com.maksimowiczm.foodyou.food.ai.domain

import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts

/**
 * A single food item parsed from a natural-language meal description by the LLM.
 *
 * @param nutritionFactsPer100g Nutrition per 100g/100ml, matching the app-wide per-100 invariant.
 * @param estimatedGrams The LLM's estimate of the described portion, used as the serving weight.
 */
data class MealItem(
    val name: String,
    val isLiquid: Boolean,
    val nutritionFactsPer100g: NutritionFacts,
    val estimatedGrams: Double,
)

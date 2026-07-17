package com.maksimowiczm.foodyou.food.ai.infrastructure

import com.maksimowiczm.foodyou.common.domain.food.NutrientValue.Companion.toNutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.food.ai.domain.MealItem
import com.maksimowiczm.foodyou.food.ai.infrastructure.model.MealItemDto

internal class AiMealMapper {
    /** Maps a DTO to a domain [MealItem], or null if the item is invalid (bad name/grams). */
    fun toMealItem(dto: MealItemDto): MealItem? {
        val name = dto.name.trim()
        if (name.isEmpty()) return null
        if (!dto.estimatedGrams.isFinite() || dto.estimatedGrams <= 0.0) return null

        val n = dto.nutrition
        val nutritionFacts =
            NutritionFacts(
                energy = n.energy.sanitized().toNutrientValue(),
                proteins = n.protein.sanitized().toNutrientValue(),
                carbohydrates = n.carbohydrates.sanitized().toNutrientValue(),
                fats = n.fat.sanitized().toNutrientValue(),
                dietaryFiber = n.dietaryFiber.sanitized().toNutrientValue(),
                sugars = n.sugars.sanitized().toNutrientValue(),
                saturatedFats = n.saturatedFats.sanitized().toNutrientValue(),
                sodium = n.sodium.sanitized().toNutrientValue(),
            )

        return MealItem(
            name = name,
            isLiquid = dto.isLiquid,
            nutritionFactsPer100g = nutritionFacts,
            estimatedGrams = dto.estimatedGrams,
        )
    }

    private fun Double?.sanitized(): Double? =
        this?.takeIf { it.isFinite() && it >= 0.0 }
}

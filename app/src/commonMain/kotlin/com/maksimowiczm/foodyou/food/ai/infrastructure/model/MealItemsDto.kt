package com.maksimowiczm.foodyou.food.ai.infrastructure.model

import kotlinx.serialization.Serializable

/** The schema-shaped payload the model returns inside `choices[0].message.content`. */
@Serializable internal data class MealItemsDto(val items: List<MealItemDto> = emptyList())

@Serializable
internal data class MealItemDto(
    val name: String,
    val isLiquid: Boolean,
    val estimatedGrams: Double,
    val nutrition: NutritionDto,
)

/** Per-100g/100ml nutrition. Energy in kcal; everything else in grams. */
@Serializable
internal data class NutritionDto(
    val energy: Double? = null,
    val protein: Double? = null,
    val carbohydrates: Double? = null,
    val fat: Double? = null,
    val dietaryFiber: Double? = null,
    val sugars: Double? = null,
    val saturatedFats: Double? = null,
    val sodium: Double? = null,
)

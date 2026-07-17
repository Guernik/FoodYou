package com.maksimowiczm.foodyou.food.ai.infrastructure

import com.maksimowiczm.foodyou.food.ai.infrastructure.model.MealItemDto
import com.maksimowiczm.foodyou.food.ai.infrastructure.model.NutritionDto
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AiMealMapperTest {
    private val mapper = AiMealMapper()

    private fun dto(
        name: String = "Egg",
        isLiquid: Boolean = false,
        grams: Double = 100.0,
        nutrition: NutritionDto = NutritionDto(energy = 155.0, protein = 13.0),
    ) = MealItemDto(name = name, isLiquid = isLiquid, estimatedGrams = grams, nutrition = nutrition)

    @Test
    fun `maps per-100g nutrition and portion`() {
        val item = mapper.toMealItem(dto(grams = 120.0))

        assertTrue(item != null)
        assertEquals("Egg", item.name)
        assertEquals(120.0, item.estimatedGrams)
        // Values are stored per-100g, verbatim (not scaled to the portion).
        assertEquals(155.0, item.nutritionFactsPer100g.energy.value)
        assertEquals(13.0, item.nutritionFactsPer100g.proteins.value)
    }

    @Test
    fun `trims name and rejects blank`() {
        assertEquals("Toast", mapper.toMealItem(dto(name = "  Toast "))?.name)
        assertNull(mapper.toMealItem(dto(name = "   ")))
    }

    @Test
    fun `rejects non-positive or non-finite grams`() {
        assertNull(mapper.toMealItem(dto(grams = 0.0)))
        assertNull(mapper.toMealItem(dto(grams = -5.0)))
        assertNull(mapper.toMealItem(dto(grams = Double.NaN)))
    }

    @Test
    fun `drops out-of-range nutrient values but keeps the item`() {
        val item =
            mapper.toMealItem(
                dto(nutrition = NutritionDto(energy = -10.0, protein = 20.0, fat = Double.NaN))
            )

        assertTrue(item != null)
        // Negative energy and NaN fat are dropped; valid protein is kept.
        assertNull(item.nutritionFactsPer100g.energy.value)
        assertEquals(20.0, item.nutritionFactsPer100g.proteins.value)
        assertNull(item.nutritionFactsPer100g.fats.value)
    }
}

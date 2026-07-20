package com.maksimowiczm.foodyou.app.ui.food.ai

import com.maksimowiczm.foodyou.common.domain.food.NutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.food.ai.domain.MealItem
import kotlin.test.Test
import kotlin.test.assertEquals

class EditableMealItemTest {
    private fun editable(
        name: String = "Egg",
        grams: Double = 100.0,
        isLiquid: Boolean = false,
        nutrition: EditableNutrition =
            EditableNutrition(
                energy = 155.0,
                proteins = 13.0,
                carbohydrates = 1.1,
                fats = 11.0,
                dietaryFiber = 0.0,
                sugars = 1.1,
                saturatedFats = 3.3,
                sodium = 0.14,
            ),
    ): EditableMealItem {
        val item =
            MealItem(
                name = name,
                isLiquid = isLiquid,
                nutritionFactsPer100g = NutritionFacts(),
                estimatedGrams = grams,
            )
        return EditableMealItem(
            id = 0L,
            name = name,
            grams = grams,
            isLiquid = isLiquid,
            nutrition = nutrition,
            expanded = false,
            savedProductId = null,
            item = item,
        )
    }

    @Test
    fun `edited macros round-trip into per-100g nutrition facts`() {
        val edited =
            editable().copy(
                name = "  Poached egg  ",
                grams = 120.0,
                nutrition =
                    EditableNutrition(
                        energy = 143.0,
                        proteins = 12.5,
                        carbohydrates = 0.7,
                        fats = 9.5,
                        dietaryFiber = 0.0,
                        sugars = 0.7,
                        saturatedFats = 3.1,
                        sodium = 0.12,
                    ),
            )

        val result = edited.toMealItem()

        assertEquals("Poached egg", result.name)
        assertEquals(120.0, result.estimatedGrams)
        val facts = result.nutritionFactsPer100g
        assertEquals(143.0, facts.energy.value)
        assertEquals(12.5, facts.proteins.value)
        assertEquals(9.5, facts.fats.value)
        assertEquals(0.12, facts.sodium.value)
    }

    @Test
    fun `null macro maps to incomplete`() {
        val result = editable().copy(nutrition = editable().nutrition.copy(sugars = null)).toMealItem()

        assertEquals(NutrientValue.Incomplete(null), result.nutritionFactsPer100g.sugars)
    }
}

package com.maksimowiczm.foodyou.food.ai.domain

import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.result.Err
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.common.result.fold
import com.maksimowiczm.foodyou.food.domain.entity.FoodHistory
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.usecase.CreateRecipeUseCase

sealed interface SaveRecipeError {
    data object EmptyName : SaveRecipeError

    data object EmptyItems : SaveRecipeError

    data object ProductCreationFailed : SaveRecipeError

    data object RecipeCreationFailed : SaveRecipeError
}

/**
 * Combines several parsed [MealItem]s into a reusable recipe. Each item is first persisted as an AI
 * product (via [SaveMealItemAsProductUseCase]); the resulting product ids become recipe ingredients
 * measured in grams (the LLM's estimated portion). A recipe derives its nutrition from its
 * ingredients, so gram measurement preserves the per-100g invariant.
 */
class SaveMealItemsAsRecipeUseCase(
    private val saveMealItemAsProductUseCase: SaveMealItemAsProductUseCase,
    private val createRecipeUseCase: CreateRecipeUseCase,
    private val dateProvider: DateProvider,
) {
    suspend fun save(name: String, items: List<MealItem>): Result<FoodId.Recipe, SaveRecipeError> {
        if (name.isBlank()) return Err(SaveRecipeError.EmptyName)
        if (items.isEmpty()) return Err(SaveRecipeError.EmptyItems)

        val isLiquid = items.all { it.isLiquid }

        val ingredients = mutableListOf<Pair<FoodId, Measurement>>()
        for (item in items) {
            val productId =
                saveMealItemAsProductUseCase.save(item).fold(
                    onSuccess = { it },
                    onError = { return Err(SaveRecipeError.ProductCreationFailed) },
                )
            ingredients += productId to Measurement.Gram(item.estimatedGrams)
        }

        return createRecipeUseCase
            .create(
                name = name.trim(),
                servings = 1,
                note = null,
                isLiquid = isLiquid,
                ingredients = ingredients,
                history = FoodHistory.Created(dateProvider.nowInstant()),
            )
            .fold(
                onSuccess = { Ok(it) },
                onError = { Err(SaveRecipeError.RecipeCreationFailed) },
            )
    }
}

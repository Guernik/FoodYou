package com.maksimowiczm.foodyou.food.ai.domain

import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.result.Err
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.common.result.fold
import com.maksimowiczm.foodyou.food.domain.entity.Food
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.entity.Product
import com.maksimowiczm.foodyou.food.domain.entity.Recipe
import com.maksimowiczm.foodyou.food.domain.entity.RecipeIngredient
import com.maksimowiczm.foodyou.food.domain.usecase.ObserveFoodUseCase
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFood
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipe
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodRecipeIngredient
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.CreateFoodDiaryEntryUseCase
import kotlinx.coroutines.flow.first
import kotlinx.datetime.LocalDate

sealed interface LogRecipeError {
    /** The recipe to log could not be found (e.g. it was not persisted). */
    data object RecipeNotFound : LogRecipeError

    /** Creating the diary entry for the recipe failed. */
    data object EntryCreationFailed : LogRecipeError
}

/**
 * Logs a previously saved recipe (identified by its [FoodId.Recipe]) into the diary as a single
 * [DiaryFoodRecipe] entry. Recipes produced by [SaveMealItemsAsRecipeUseCase] have `servings = 1`,
 * so the whole recipe is logged as [Measurement.Serving] `1.0`. This is the counterpart to the
 * per-item [LogMealItemsUseCase]: instead of one product entry per ingredient, the recipe becomes
 * one diary entry.
 */
class LogRecipeUseCase(
    private val observeFoodUseCase: ObserveFoodUseCase,
    private val createFoodDiaryEntryUseCase: CreateFoodDiaryEntryUseCase,
) {
    suspend fun log(
        recipeId: FoodId.Recipe,
        mealId: Long,
        date: LocalDate,
    ): Result<Unit, LogRecipeError> {
        val recipe = observeFoodUseCase.observe(recipeId).first() as? Recipe
            ?: return Err(LogRecipeError.RecipeNotFound)

        return createFoodDiaryEntryUseCase
            .createDiaryEntry(
                measurement = Measurement.Serving(1.0),
                mealId = mealId,
                date = date,
                food = recipe.toDiaryRecipe(),
            )
            .fold(
                onSuccess = { Ok() },
                onError = { Err(LogRecipeError.EntryCreationFailed) },
            )
    }
}

private fun Food.toDiaryFood(): DiaryFood =
    when (this) {
        is Product -> toDiaryProduct()
        is Recipe -> toDiaryRecipe()
    }

private fun Product.toDiaryProduct(): DiaryFoodProduct =
    DiaryFoodProduct(
        name = headline,
        nutritionFacts = nutritionFacts,
        servingWeight = servingWeight,
        totalWeight = totalWeight,
        isLiquid = isLiquid,
        source = source,
        note = note,
    )

private fun Recipe.toDiaryRecipe(): DiaryFoodRecipe =
    DiaryFoodRecipe(
        name = headline,
        servings = servings,
        ingredients = ingredients.map { it.toDiaryRecipeIngredient() },
        isLiquid = isLiquid,
        note = note,
    )

private fun RecipeIngredient.toDiaryRecipeIngredient(): DiaryFoodRecipeIngredient =
    DiaryFoodRecipeIngredient(food = food.toDiaryFood(), measurement = measurement)

package com.maksimowiczm.foodyou.food.ai.domain

import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.common.result.mapError
import com.maksimowiczm.foodyou.food.domain.entity.FoodHistory
import com.maksimowiczm.foodyou.food.domain.entity.FoodId
import com.maksimowiczm.foodyou.food.domain.usecase.CreateProductError
import com.maksimowiczm.foodyou.food.domain.usecase.CreateProductUseCase

sealed interface SaveMealItemError {
    data object NameEmpty : SaveMealItemError
}

/**
 * Persists a single parsed [MealItem] as a reusable [FoodSource.Type.Ai] product (without logging a
 * diary entry). This is the product half of [LogMealItemsUseCase], reused so the review screen can
 * save an item on its own.
 */
class SaveMealItemAsProductUseCase(
    private val createProductUseCase: CreateProductUseCase,
    private val dateProvider: DateProvider,
) {
    suspend fun save(item: MealItem): Result<FoodId.Product, SaveMealItemError> =
        createProductUseCase
            .create(
                name = item.name,
                brand = null,
                barcode = null,
                note = null,
                isLiquid = item.isLiquid,
                packageWeight = null,
                servingWeight = item.estimatedGrams,
                source = FoodSource(type = FoodSource.Type.Ai),
                nutritionFacts = item.nutritionFactsPer100g,
                history = FoodHistory.Created(dateProvider.nowInstant()),
            )
            .mapError { error ->
                when (error) {
                    CreateProductError.NameEmpty -> SaveMealItemError.NameEmpty
                }
            }
}

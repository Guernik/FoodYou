package com.maksimowiczm.foodyou.food.ai.domain

import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.FoodSource
import com.maksimowiczm.foodyou.common.domain.measurement.Measurement
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.result.Err
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.common.result.fold
import com.maksimowiczm.foodyou.food.domain.entity.FoodHistory
import com.maksimowiczm.foodyou.food.domain.usecase.CreateProductUseCase
import com.maksimowiczm.foodyou.fooddiary.domain.entity.DiaryFoodProduct
import com.maksimowiczm.foodyou.fooddiary.domain.usecase.CreateFoodDiaryEntryUseCase
import kotlinx.datetime.LocalDate

sealed interface LogMealError {
    /** Logging one or more items failed; [failedItems] were not logged. */
    data class PartialFailure(val loggedCount: Int, val failedItems: List<MealItem>) : LogMealError
}

/**
 * Persists confirmed [MealItem]s. Each item becomes a reusable [FoodSource.Type.Ai] product AND a
 * diary entry in the scoped meal/date, logged as [Measurement.Serving] with the serving weight equal
 * to the LLM's estimated grams (preserving the per-100g nutrition invariant).
 */
class LogMealItemsUseCase(
    private val createProductUseCase: CreateProductUseCase,
    private val createFoodDiaryEntryUseCase: CreateFoodDiaryEntryUseCase,
    private val dateProvider: DateProvider,
    private val logger: Logger,
) {
    suspend fun log(
        items: List<MealItem>,
        mealId: Long,
        date: LocalDate,
    ): Result<Unit, LogMealError> {
        val source = FoodSource(type = FoodSource.Type.Ai)
        val failed = mutableListOf<MealItem>()
        var logged = 0

        for (item in items) {
            val ok = logItem(item, mealId, date, source)
            if (ok) logged++ else failed += item
        }

        return if (failed.isEmpty()) {
            Ok()
        } else {
            logger.w(TAG) { "Logged $logged/${items.size} items; ${failed.size} failed" }
            Err(LogMealError.PartialFailure(loggedCount = logged, failedItems = failed))
        }
    }

    private suspend fun logItem(
        item: MealItem,
        mealId: Long,
        date: LocalDate,
        source: FoodSource,
    ): Boolean {
        val createResult =
            createProductUseCase.create(
                name = item.name,
                brand = null,
                barcode = null,
                note = null,
                isLiquid = item.isLiquid,
                packageWeight = null,
                servingWeight = item.estimatedGrams,
                source = source,
                nutritionFacts = item.nutritionFactsPer100g,
                history = FoodHistory.Created(dateProvider.nowInstant()),
            )

        return createResult.fold(
            onSuccess = {
                val food =
                    DiaryFoodProduct(
                        name = item.name,
                        nutritionFacts = item.nutritionFactsPer100g,
                        servingWeight = item.estimatedGrams,
                        totalWeight = null,
                        isLiquid = item.isLiquid,
                        source = source,
                        note = null,
                    )

                createFoodDiaryEntryUseCase
                    .createDiaryEntry(
                        measurement = Measurement.Serving(1.0),
                        mealId = mealId,
                        date = date,
                        food = food,
                    )
                    .fold(onSuccess = { true }, onError = { false })
            },
            onError = { false },
        )
    }

    private companion object {
        const val TAG = "LogMealItemsUseCase"
    }
}

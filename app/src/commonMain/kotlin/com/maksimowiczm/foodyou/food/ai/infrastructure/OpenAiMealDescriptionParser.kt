package com.maksimowiczm.foodyou.food.ai.infrastructure

import com.maksimowiczm.foodyou.common.domain.food.NutrientValue.Companion.toNutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.result.Err
import com.maksimowiczm.foodyou.common.result.Ok
import com.maksimowiczm.foodyou.common.result.Result
import com.maksimowiczm.foodyou.food.ai.domain.LlmApiKeyRepository
import com.maksimowiczm.foodyou.food.ai.domain.LlmSettings
import com.maksimowiczm.foodyou.food.ai.domain.MealDescriptionParser
import com.maksimowiczm.foodyou.food.ai.domain.MealItem
import com.maksimowiczm.foodyou.food.ai.domain.ParseMealError
import kotlinx.coroutines.flow.first

internal class OpenAiMealDescriptionParser(
    private val dataSource: OpenAiRemoteDataSource,
    private val settingsRepository: UserPreferencesRepository<LlmSettings>,
    private val apiKeyRepository: LlmApiKeyRepository,
    private val mapper: AiMealMapper,
    private val logger: Logger,
) : MealDescriptionParser {

    override suspend fun parse(
        description: String,
        singleProduct: Boolean,
    ): Result<List<MealItem>, ParseMealError> {
        val apiKey = apiKeyRepository.loadKey()
        if (apiKey.isNullOrBlank()) {
            return Err(ParseMealError.MissingApiKey)
        }

        val settings = settingsRepository.observe().first()

        val result =
            dataSource.chatCompletion(
                baseUrl = settings.baseUrl,
                model = settings.model,
                apiKey = apiKey,
                description = description,
                singleProduct = singleProduct,
            )

        return result.fold(
            onSuccess = { dto ->
                val items = dto.items.mapNotNull(mapper::toMealItem)
                when {
                    dto.items.isEmpty() -> Err(ParseMealError.Refused)
                    // Items existed but none survived validation → malformed data.
                    items.isEmpty() -> Err(ParseMealError.MalformedResponse)
                    // Single mode is a hard guarantee: if the model still split the dish, merge it.
                    singleProduct && items.size > 1 -> Ok(listOf(items.merged()))
                    else -> Ok(items)
                }
            },
            onFailure = { throwable -> Err(throwable.toParseMealError()) },
        )
    }

    /**
     * Collapses several [MealItem]s into one combined product: grams are summed, and each per-100g
     * nutrient becomes the weight-weighted average across the total grams so it stays a realistic
     * per-100g figure for the finished dish (rather than the sum of the components' per-100g values).
     * A null nutrient contributes nothing; the merged field is null only if every item was null.
     */
    private fun List<MealItem>.merged(): MealItem {
        val totalGrams = sumOf { it.estimatedGrams }.takeIf { it > 0.0 } ?: 1.0

        fun weightedAverage(select: (NutritionFacts) -> Double?): Double? {
            var weightedSum = 0.0
            var anyValue = false
            for (item in this) {
                val value = select(item.nutritionFactsPer100g) ?: continue
                anyValue = true
                weightedSum += value * item.estimatedGrams
            }
            return if (anyValue) weightedSum / totalGrams else null
        }

        val nutrition =
            NutritionFacts(
                energy = weightedAverage { it.energy.value }.toNutrientValue(),
                proteins = weightedAverage { it.proteins.value }.toNutrientValue(),
                carbohydrates = weightedAverage { it.carbohydrates.value }.toNutrientValue(),
                fats = weightedAverage { it.fats.value }.toNutrientValue(),
                dietaryFiber = weightedAverage { it.dietaryFiber.value }.toNutrientValue(),
                sugars = weightedAverage { it.sugars.value }.toNutrientValue(),
                saturatedFats = weightedAverage { it.saturatedFats.value }.toNutrientValue(),
                sodium = weightedAverage { it.sodium.value }.toNutrientValue(),
            )

        return MealItem(
            name = first().name,
            isLiquid = all { it.isLiquid },
            nutritionFactsPer100g = nutrition,
            estimatedGrams = sumOf { it.estimatedGrams },
        )
    }

    private fun Throwable.toParseMealError(): ParseMealError =
        when (this) {
            is AiRemoteException.Unauthorized -> ParseMealError.Unauthorized
            is AiRemoteException.RateLimited -> ParseMealError.RateLimited
            is AiRemoteException.Network -> ParseMealError.Network
            is AiRemoteException.Refused -> ParseMealError.Refused
            is AiRemoteException.Malformed -> ParseMealError.MalformedResponse
            else -> {
                logger.e(TAG, this) { "Unexpected AI parse error" }
                ParseMealError.Unknown(message)
            }
        }

    private companion object {
        const val TAG = "OpenAiMealDescriptionParser"
    }
}

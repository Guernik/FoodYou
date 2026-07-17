package com.maksimowiczm.foodyou.food.ai.domain

import com.maksimowiczm.foodyou.common.result.Result

/** Parses a natural-language meal description into structured [MealItem]s using an LLM. */
interface MealDescriptionParser {
    suspend fun parse(description: String): Result<List<MealItem>, ParseMealError>
}

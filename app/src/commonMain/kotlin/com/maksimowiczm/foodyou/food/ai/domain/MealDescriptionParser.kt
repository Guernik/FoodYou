package com.maksimowiczm.foodyou.food.ai.domain

import com.maksimowiczm.foodyou.common.result.Result

/** Parses a natural-language meal description into structured [MealItem]s using an LLM. */
interface MealDescriptionParser {
    /**
     * @param singleProduct When true, the description is analysed as a single combined product and
     *   at most one [MealItem] is returned; otherwise one item per component.
     */
    suspend fun parse(
        description: String,
        singleProduct: Boolean,
    ): Result<List<MealItem>, ParseMealError>
}

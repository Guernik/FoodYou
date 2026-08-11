package com.maksimowiczm.foodyou.food.ai.domain

import com.maksimowiczm.foodyou.common.log.Logger
import com.maksimowiczm.foodyou.common.log.logAndReturnFailure
import com.maksimowiczm.foodyou.common.result.Result

class ParseMealDescriptionUseCase(
    private val parser: MealDescriptionParser,
    private val logger: Logger,
) {
    suspend fun parse(
        description: String,
        singleProduct: Boolean,
    ): Result<List<MealItem>, ParseMealError> {
        if (description.isBlank()) {
            return logger.logAndReturnFailure(
                tag = TAG,
                throwable = null,
                error = ParseMealError.EmptyInput,
                message = { "Meal description cannot be empty." },
            )
        }

        return parser.parse(description.trim(), singleProduct)
    }

    private companion object {
        const val TAG = "ParseMealDescriptionUseCase"
    }
}

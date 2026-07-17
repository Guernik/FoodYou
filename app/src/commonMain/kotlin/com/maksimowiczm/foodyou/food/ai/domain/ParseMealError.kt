package com.maksimowiczm.foodyou.food.ai.domain

sealed interface ParseMealError {
    data object EmptyInput : ParseMealError

    data object MissingApiKey : ParseMealError

    data object Network : ParseMealError

    data object RateLimited : ParseMealError

    data object Unauthorized : ParseMealError

    /** The LLM returned a response that could not be parsed into valid meal items. */
    data object MalformedResponse : ParseMealError

    /** The LLM refused or returned no recognizable items. */
    data object Refused : ParseMealError

    data class Unknown(val message: String?) : ParseMealError
}

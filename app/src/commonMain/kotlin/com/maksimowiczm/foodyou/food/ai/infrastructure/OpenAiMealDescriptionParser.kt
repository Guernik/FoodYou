package com.maksimowiczm.foodyou.food.ai.infrastructure

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

    override suspend fun parse(description: String): Result<List<MealItem>, ParseMealError> {
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
            )

        return result.fold(
            onSuccess = { dto ->
                val items = dto.items.mapNotNull(mapper::toMealItem)
                when {
                    dto.items.isEmpty() -> Err(ParseMealError.Refused)
                    // Items existed but none survived validation → malformed data.
                    items.isEmpty() -> Err(ParseMealError.MalformedResponse)
                    else -> Ok(items)
                }
            },
            onFailure = { throwable -> Err(throwable.toParseMealError()) },
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

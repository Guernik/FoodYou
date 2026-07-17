package com.maksimowiczm.foodyou.food.ai.infrastructure

import com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepository
import com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepositoryOf
import com.maksimowiczm.foodyou.food.ai.domain.LlmApiKeyRepository
import com.maksimowiczm.foodyou.food.ai.domain.MealDescriptionParser
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.json.Json
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.qualifier.named
import org.koin.dsl.bind

internal fun Module.aiModule() {
    single(named(OpenAiRemoteDataSource::class.qualifiedName!!)) {
        HttpClient {
            install(HttpTimeout)
            install(ContentNegotiation) {
                // encodeDefaults is required: the OpenAI request body relies on default values
                // (response_format.type = "json_schema", strict = true). Without it kotlinx
                // omits them and the API rejects the request with "Missing required parameter".
                json(Json { ignoreUnknownKeys = true; encodeDefaults = true })
            }
        }
    }
    factory {
        OpenAiRemoteDataSource(
            client = get(named(OpenAiRemoteDataSource::class.qualifiedName!!)),
            logger = get(),
        )
    }
    factoryOf(::AiMealMapper)
    factory {
        OpenAiMealDescriptionParser(
            dataSource = get(),
            settingsRepository = userPreferencesRepository(),
            apiKeyRepository = get(),
            mapper = get(),
            logger = get(),
        )
    }
        .bind<MealDescriptionParser>()

    factoryOf(::EncryptedLlmApiKeyRepository).bind<LlmApiKeyRepository>()

    userPreferencesRepositoryOf(::DataStoreLlmSettingsRepository)
}

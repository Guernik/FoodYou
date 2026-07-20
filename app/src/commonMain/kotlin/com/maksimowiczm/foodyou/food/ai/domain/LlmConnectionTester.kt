package com.maksimowiczm.foodyou.food.ai.domain

/**
 * Validates an LLM configuration by making a cheap sample request. Implemented in infrastructure
 * (mirrors [MealDescriptionParser]); the settings screen uses it via [TestLlmConnectionUseCase] to
 * verify network + auth + model before saving.
 */
interface LlmConnectionTester {
    suspend fun test(baseUrl: String, model: String, apiKey: String): TestConnectionResult
}

sealed interface TestConnectionResult {
    data object Success : TestConnectionResult

    data object Unauthorized : TestConnectionResult

    data object Network : TestConnectionResult

    data object RateLimited : TestConnectionResult

    data class Unknown(val message: String?) : TestConnectionResult
}

package com.maksimowiczm.foodyou.food.ai.domain

/**
 * Tests a *pending* LLM configuration (the values currently on the settings screen, not necessarily
 * saved) so the user can validate their settings before persisting them.
 */
class TestLlmConnectionUseCase(private val tester: LlmConnectionTester) {
    suspend fun test(baseUrl: String, model: String, apiKey: String): TestConnectionResult {
        if (baseUrl.isBlank() || model.isBlank() || apiKey.isBlank()) {
            return TestConnectionResult.Unauthorized
        }
        return tester.test(baseUrl = baseUrl.trim(), model = model.trim(), apiKey = apiKey.trim())
    }
}

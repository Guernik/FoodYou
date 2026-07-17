package com.maksimowiczm.foodyou.food.ai.domain

import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferences

/**
 * Plaintext configuration for the OpenAI-compatible LLM endpoint used by AI food logging.
 *
 * The API key is NOT stored here — it is a billable credential kept encrypted via
 * [LlmApiKeyRepository].
 */
data class LlmSettings(val baseUrl: String, val model: String) : UserPreferences {
    companion object {
        const val DEFAULT_BASE_URL = "https://api.openai.com/v1"
        const val DEFAULT_MODEL = "gpt-4o-mini"
    }
}

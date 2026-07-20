package com.maksimowiczm.foodyou.food.ai.domain

/**
 * A preset for a known OpenAI-compatible provider. Purely a UI convenience for the settings
 * screen — only [LlmSettings.baseUrl] and [LlmSettings.model] are persisted, never the vendor.
 *
 * [models] are curated static defaults; the settings screen always allows a manual model override
 * for anything not in the list. [Custom] carries no preset and is selected when the persisted base
 * URL matches no known vendor.
 */
enum class LlmVendor(val baseUrl: String, val models: List<String>) {
    OpenAI(
        baseUrl = "https://api.openai.com/v1",
        models = listOf("gpt-4o-mini", "gpt-4o", "gpt-4.1-mini", "gpt-4.1"),
    ),
    OpenRouter(
        baseUrl = "https://openrouter.ai/api/v1",
        models =
            listOf(
                "openai/gpt-4o-mini",
                "openai/gpt-4.1-mini",
                "anthropic/claude-3.5-sonnet",
                "anthropic/claude-3.5-haiku",
                "google/gemini-2.0-flash-001",
            ),
    ),
    Custom(baseUrl = "", models = emptyList());

    companion object {
        /** Infers the vendor from a persisted base URL, defaulting to [Custom] on no match. */
        fun fromBaseUrl(baseUrl: String): LlmVendor {
            val normalized = baseUrl.trimEnd('/')
            return entries.firstOrNull { it != Custom && it.baseUrl.trimEnd('/') == normalized }
                ?: Custom
        }
    }
}

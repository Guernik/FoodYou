package com.maksimowiczm.foodyou.food.ai.domain

import kotlinx.coroutines.flow.Flow

/**
 * Stores the LLM API key. Implementations must encrypt the key at rest (see
 * `EncryptedLlmApiKeyRepository`).
 */
interface LlmApiKeyRepository {
    suspend fun store(key: String)

    suspend fun clear()

    fun hasKey(): Flow<Boolean>

    suspend fun loadKey(): String?
}

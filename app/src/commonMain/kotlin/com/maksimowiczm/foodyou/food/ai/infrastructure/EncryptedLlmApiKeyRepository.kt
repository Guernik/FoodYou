package com.maksimowiczm.foodyou.food.ai.infrastructure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.byteArrayPreferencesKey
import androidx.datastore.preferences.core.edit
import com.maksimowiczm.foodyou.common.crypto.MasterCrypto
import com.maksimowiczm.foodyou.food.ai.domain.LlmApiKeyRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

internal class EncryptedLlmApiKeyRepository(
    private val dataStore: DataStore<Preferences>,
    private val masterCrypto: MasterCrypto,
) : LlmApiKeyRepository {

    override suspend fun store(key: String) {
        dataStore.edit { it[apiKeyKey] = masterCrypto.encrypt(key.encodeToByteArray()) }
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(apiKeyKey) }
    }

    override fun hasKey(): Flow<Boolean> = dataStore.data.map { apiKeyKey in it }

    override suspend fun loadKey(): String? {
        val preferences = dataStore.data.first()
        val encrypted = preferences[apiKeyKey] ?: return null
        return masterCrypto.decrypt(encrypted).decodeToString()
    }

    private companion object {
        private val apiKeyKey = byteArrayPreferencesKey("ai:api_key")
    }
}

package com.maksimowiczm.foodyou.food.ai.infrastructure

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import com.maksimowiczm.foodyou.common.infrastructure.datastore.AbstractDataStoreUserPreferencesRepository
import com.maksimowiczm.foodyou.common.infrastructure.datastore.set
import com.maksimowiczm.foodyou.food.ai.domain.LlmSettings

internal class DataStoreLlmSettingsRepository(dataStore: DataStore<Preferences>) :
    AbstractDataStoreUserPreferencesRepository<LlmSettings>(dataStore) {

    override fun Preferences.toUserPreferences(): LlmSettings =
        LlmSettings(
            baseUrl = this[Keys.BaseUrl]?.takeIf { it.isNotBlank() } ?: LlmSettings.DEFAULT_BASE_URL,
            model = this[Keys.Model]?.takeIf { it.isNotBlank() } ?: LlmSettings.DEFAULT_MODEL,
        )

    override fun MutablePreferences.applyUserPreferences(updated: LlmSettings) {
        this[Keys.BaseUrl] = updated.baseUrl
        this[Keys.Model] = updated.model
    }

    private object Keys {
        val BaseUrl = stringPreferencesKey("ai:base_url")
        val Model = stringPreferencesKey("ai:model")
    }
}

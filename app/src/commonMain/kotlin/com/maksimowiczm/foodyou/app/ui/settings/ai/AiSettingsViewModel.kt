package com.maksimowiczm.foodyou.app.ui.settings.ai

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.food.ai.domain.LlmApiKeyRepository
import com.maksimowiczm.foodyou.food.ai.domain.LlmSettings
import com.maksimowiczm.foodyou.food.ai.domain.LlmVendor
import com.maksimowiczm.foodyou.food.ai.domain.TestConnectionResult
import com.maksimowiczm.foodyou.food.ai.domain.TestLlmConnectionUseCase
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class AiSettingsViewModel(
    private val settingsRepository: UserPreferencesRepository<LlmSettings>,
    private val apiKeyRepository: LlmApiKeyRepository,
    private val testLlmConnectionUseCase: TestLlmConnectionUseCase,
) : ViewModel() {

    val hasKey: StateFlow<Boolean> =
        apiKeyRepository
            .hasKey()
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), false)

    private val _uiState = MutableStateFlow<AiSettingsUiState?>(null)
    /** Null until the persisted settings are loaded. */
    val uiState: StateFlow<AiSettingsUiState?> = _uiState.asStateFlow()

    private val _testState = MutableStateFlow<TestState>(TestState.Idle)
    val testState: StateFlow<TestState> = _testState.asStateFlow()

    init {
        viewModelScope.launch {
            val settings = settingsRepository.observe().first()
            val vendor = LlmVendor.fromBaseUrl(settings.baseUrl)
            val manualModel = vendor == LlmVendor.Custom || settings.model !in vendor.models
            _uiState.value =
                AiSettingsUiState(
                    vendor = vendor,
                    baseUrl = settings.baseUrl,
                    model = settings.model,
                    manualModel = manualModel,
                    apiKey = "",
                )
        }
    }

    fun selectVendor(vendor: LlmVendor) = update {
        if (vendor == LlmVendor.Custom) {
            copy(vendor = vendor, manualModel = true)
        } else {
            copy(
                vendor = vendor,
                baseUrl = vendor.baseUrl,
                model = vendor.models.firstOrNull() ?: model,
                manualModel = false,
            )
        }
    }

    fun setBaseUrl(value: String) = update {
        // Editing the base URL away from a preset flips selection toward Custom.
        val vendor = LlmVendor.fromBaseUrl(value)
        copy(baseUrl = value, vendor = vendor)
    }

    fun setModel(value: String) = update { copy(model = value) }

    fun setManualModel(manual: Boolean) = update { copy(manualModel = manual) }

    fun setApiKey(value: String) = update { copy(apiKey = value) }

    fun test() {
        val state = _uiState.value ?: return
        viewModelScope.launch {
            _testState.value = TestState.Testing
            // Use the on-screen key if entered; otherwise fall back to the stored one.
            val key = state.apiKey.ifBlank { apiKeyRepository.loadKey().orEmpty() }
            val result =
                testLlmConnectionUseCase.test(
                    baseUrl = state.baseUrl,
                    model = state.model,
                    apiKey = key,
                )
            _testState.value = TestState.Result(result)
        }
    }

    /** Persists settings + (if entered) the API key. Invokes [onSaved] on completion. */
    fun save(onSaved: () -> Unit) {
        val state = _uiState.value ?: return
        viewModelScope.launch {
            settingsRepository.update {
                copy(
                    baseUrl = state.baseUrl.ifBlank { LlmSettings.DEFAULT_BASE_URL },
                    model = state.model.ifBlank { LlmSettings.DEFAULT_MODEL },
                )
            }
            if (state.apiKey.isNotBlank()) {
                apiKeyRepository.store(state.apiKey.trim())
            }
            onSaved()
        }
    }

    fun clearKey() {
        viewModelScope.launch {
            apiKeyRepository.clear()
            update { copy(apiKey = "") }
        }
    }

    private inline fun update(transform: AiSettingsUiState.() -> AiSettingsUiState) {
        val current = _uiState.value ?: return
        _uiState.value = current.transform()
        // Any edit invalidates a previous test result.
        _testState.value = TestState.Idle
    }
}

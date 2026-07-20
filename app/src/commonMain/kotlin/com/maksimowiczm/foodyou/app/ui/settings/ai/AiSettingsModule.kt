package com.maksimowiczm.foodyou.app.ui.settings.ai

import com.maksimowiczm.foodyou.common.infrastructure.koin.userPreferencesRepository
import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModel

fun Module.aiSettings() {
    viewModel {
        AiSettingsViewModel(
            settingsRepository = userPreferencesRepository(),
            apiKeyRepository = get(),
            testLlmConnectionUseCase = get(),
        )
    }
}

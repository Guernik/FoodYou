package com.maksimowiczm.foodyou.app.ui.settings.ai

import androidx.compose.runtime.Immutable
import com.maksimowiczm.foodyou.food.ai.domain.LlmVendor
import com.maksimowiczm.foodyou.food.ai.domain.TestConnectionResult

@Immutable
internal data class AiSettingsUiState(
    val vendor: LlmVendor,
    val baseUrl: String,
    val model: String,
    /** When true the model is a free-text field instead of the vendor dropdown. */
    val manualModel: Boolean,
    /** Pending key entry; blank = leave the stored key unchanged. */
    val apiKey: String,
)

@Immutable
internal sealed interface TestState {
    data object Idle : TestState

    data object Testing : TestState

    data class Result(val result: TestConnectionResult) : TestState
}

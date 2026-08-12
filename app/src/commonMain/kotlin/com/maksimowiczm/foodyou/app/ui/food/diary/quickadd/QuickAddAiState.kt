package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

import com.maksimowiczm.foodyou.food.ai.domain.ParseMealError

/**
 * State of the optional AI analysis inside the quick add form. Unlike the dedicated AI logging
 * screen there is no review step; the parsed values are written straight into the form.
 */
internal sealed interface QuickAddAiState {
    /** @param hasApiKey Whether an AI API key is configured. Gates the analyze action. */
    data class Idle(val hasApiKey: Boolean) : QuickAddAiState

    data object Loading : QuickAddAiState

    data class Error(val error: ParseMealError, val hasApiKey: Boolean) : QuickAddAiState
}

internal val QuickAddAiState.hasApiKey: Boolean
    get() =
        when (this) {
            is QuickAddAiState.Idle -> hasApiKey
            is QuickAddAiState.Error -> hasApiKey
            QuickAddAiState.Loading -> true
        }

internal val QuickAddAiState.isLoading: Boolean
    get() = this is QuickAddAiState.Loading

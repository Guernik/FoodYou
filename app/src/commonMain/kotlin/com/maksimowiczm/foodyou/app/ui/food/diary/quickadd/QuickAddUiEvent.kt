package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

internal sealed interface QuickAddUiEvent {
    /** The diary entry was saved. The screen navigates back on this event. */
    data object Saved : QuickAddUiEvent

    data object ProductSaved : QuickAddUiEvent

    data object ProductSaveFailed : QuickAddUiEvent
}

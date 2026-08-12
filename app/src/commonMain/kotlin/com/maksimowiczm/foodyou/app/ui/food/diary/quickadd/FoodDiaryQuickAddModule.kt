package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

import kotlinx.datetime.LocalDate
import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf

fun Module.foodDiaryQuickAdd() {
    factoryOf(::QuickAddAiDelegate)

    viewModel { (date: LocalDate, mealId: Long) ->
        CreateQuickAddViewModel(
            mealId = mealId,
            date = date,
            manualDiaryEntryRepository = get(),
            dateProvider = get(),
            aiDelegate = get(),
        )
    }
    viewModelOf(::UpdateQuickAddViewModel)
}

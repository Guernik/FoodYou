package com.maksimowiczm.foodyou.app.ui.food.ai

import org.koin.core.module.Module
import org.koin.core.module.dsl.viewModelOf

fun Module.aiFoodLogging() {
    viewModelOf(::AiFoodLoggingViewModel)
}

package com.maksimowiczm.foodyou.food.ai.domain

import org.koin.core.module.Module
import org.koin.core.module.dsl.factoryOf

fun Module.aiDomainModule() {
    factoryOf(::ParseMealDescriptionUseCase)
    factoryOf(::LogMealItemsUseCase)
    factoryOf(::LogRecipeUseCase)
    factoryOf(::TestLlmConnectionUseCase)
    factoryOf(::SaveMealItemAsProductUseCase)
    factoryOf(::SaveMealItemsAsRecipeUseCase)
}

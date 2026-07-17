package com.maksimowiczm.foodyou.food

import com.maksimowiczm.foodyou.food.ai.domain.aiDomainModule
import com.maksimowiczm.foodyou.food.ai.infrastructure.aiModule
import com.maksimowiczm.foodyou.food.domain.foodDomainModule
import com.maksimowiczm.foodyou.food.infrastructure.foodInfrastructureModule
import org.koin.dsl.module

val foodModule = module {
    foodDomainModule()
    foodInfrastructureModule()
    aiDomainModule()
    aiModule()
}

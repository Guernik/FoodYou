package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue.Companion.toNutrientValue
import com.maksimowiczm.foodyou.common.domain.food.NutritionFacts
import com.maksimowiczm.foodyou.fooddiary.domain.repository.ManualDiaryEntryRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

internal class CreateQuickAddViewModel(
    private val mealId: Long,
    private val date: LocalDate,
    private val manualDiaryEntryRepository: ManualDiaryEntryRepository,
    private val dateProvider: DateProvider,
    private val aiDelegate: QuickAddAiDelegate,
) : ViewModel() {

    private val eventChannel = Channel<QuickAddUiEvent>()
    val uiEvents = eventChannel.receiveAsFlow()

    val aiState = aiDelegate.state

    init {
        aiDelegate.observeApiKey(viewModelScope)
    }

    fun analyze(name: String, onResult: (QuickAddAiTotals) -> Unit) =
        aiDelegate.analyze(viewModelScope, name, onResult)

    fun dismissAiError() = aiDelegate.dismissError()

    fun addEntry(
        name: String,
        energy: Double,
        proteins: Double,
        carbohydrates: Double,
        fats: Double,
        saveAsProduct: Boolean,
    ) {
        viewModelScope.launch {
            manualDiaryEntryRepository.insert(
                name = name,
                mealId = mealId,
                date = date,
                nutritionFacts =
                    NutritionFacts(
                        energy = energy.toNutrientValue(),
                        proteins = proteins.toNutrientValue(),
                        carbohydrates = carbohydrates.toNutrientValue(),
                        fats = fats.toNutrientValue(),
                    ),
                createdAt = dateProvider.now(),
            )

            // Saving the product is secondary; a failure must not stop the entry from being logged.
            if (saveAsProduct) {
                val saved =
                    aiDelegate.saveAsProduct(
                        name = name,
                        energyKcal = energy,
                        proteins = proteins,
                        carbohydrates = carbohydrates,
                        fats = fats,
                    )

                eventChannel.send(
                    if (saved) QuickAddUiEvent.ProductSaved else QuickAddUiEvent.ProductSaveFailed
                )
            }

            eventChannel.send(QuickAddUiEvent.Saved)
        }
    }
}

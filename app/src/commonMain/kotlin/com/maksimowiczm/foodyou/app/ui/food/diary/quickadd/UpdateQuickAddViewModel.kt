package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maksimowiczm.foodyou.common.domain.date.DateProvider
import com.maksimowiczm.foodyou.common.domain.food.NutrientValue.Companion.toNutrientValue
import com.maksimowiczm.foodyou.fooddiary.domain.entity.ManualDiaryEntryId
import com.maksimowiczm.foodyou.fooddiary.domain.repository.ManualDiaryEntryRepository
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

internal class UpdateQuickAddViewModel(
    id: ManualDiaryEntryId,
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

    val entry =
        manualDiaryEntryRepository
            .observe(id)
            .stateIn(
                scope = viewModelScope,
                started = SharingStarted.WhileSubscribed(2_000),
                initialValue = null,
            )

    fun analyze(name: String, onResult: (QuickAddAiTotals) -> Unit) =
        aiDelegate.analyze(viewModelScope, name, onResult)

    fun dismissAiError() = aiDelegate.dismissError()

    fun updateEntry(
        name: String,
        energy: Double,
        proteins: Double,
        carbohydrates: Double,
        fats: Double,
        saveAsProduct: Boolean,
    ) {
        val entry = entry.value

        if (entry == null) {
            return
        }

        viewModelScope.launch {
            val updatedEntry =
                entry.copy(
                    name = name,
                    nutritionFacts =
                        entry.nutritionFacts.copy(
                            energy = energy.toNutrientValue(),
                            proteins = proteins.toNutrientValue(),
                            carbohydrates = carbohydrates.toNutrientValue(),
                            fats = fats.toNutrientValue(),
                        ),
                    updatedAt = dateProvider.now(),
                )

            manualDiaryEntryRepository.update(updatedEntry)

            // Saving the product is secondary; a failure must not stop the entry from being saved.
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

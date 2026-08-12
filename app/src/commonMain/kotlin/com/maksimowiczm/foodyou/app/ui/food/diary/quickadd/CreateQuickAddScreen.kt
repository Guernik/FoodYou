package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.common.compose.extension.LaunchedCollectWithLifecycle
import foodyou.app.generated.resources.*
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.getString
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun CreateQuickAddScreen(
    onBack: () -> Unit,
    onSave: () -> Unit,
    mealId: Long,
    date: LocalDate,
    modifier: Modifier = Modifier,
) {
    val viewModel: CreateQuickAddViewModel = koinViewModel { parametersOf(date, mealId) }
    val energyFormatter = LocalEnergyFormatter.current
    val snackbarHostState = remember { SnackbarHostState() }

    val latestOnSave by rememberUpdatedState(onSave)
    LaunchedCollectWithLifecycle(viewModel.uiEvents) {
        when (it) {
            QuickAddUiEvent.Saved -> latestOnSave()
            QuickAddUiEvent.ProductSaved ->
                snackbarHostState.showSnackbar(getString(Res.string.neutral_ai_product_saved))
            QuickAddUiEvent.ProductSaveFailed ->
                snackbarHostState.showSnackbar(getString(Res.string.error_ai_product_save_failed))
        }
    }

    val formState = rememberQuickAddFormState()
    val aiState = viewModel.aiState.collectAsStateWithLifecycle().value

    QuickAddScreen(
        onBack = onBack,
        onSave = {
            val name = formState.name.value
            val energy = formState.energy.value?.let(energyFormatter::toKcal) ?: 0.0
            val proteins = formState.proteins.value ?: 0.0
            val carbohydrates = formState.carbohydrates.value ?: 0.0
            val fats = formState.fats.value ?: 0.0

            viewModel.addEntry(
                name = name,
                energy = energy,
                proteins = proteins,
                carbohydrates = carbohydrates,
                fats = fats,
                saveAsProduct = formState.saveAsProduct,
            )
        },
        aiState = aiState,
        onAnalyze = {
            viewModel.analyze(formState.name.value) { totals ->
                formState.applyTotals(
                    name = totals.name,
                    energy = energyFormatter.fromKcal(totals.energyKcal),
                    proteins = totals.proteins,
                    carbohydrates = totals.carbohydrates,
                    fats = totals.fats,
                )
            }
        },
        snackbarHostState = snackbarHostState,
        modifier = modifier,
        state = formState,
    )
}

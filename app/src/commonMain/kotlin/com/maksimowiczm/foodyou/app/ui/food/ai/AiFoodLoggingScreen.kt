package com.maksimowiczm.foodyou.app.ui.food.ai

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.InputTransformation
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.maxLength
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.common.compose.extension.LaunchedCollectWithLifecycle
import com.maksimowiczm.foodyou.common.compose.utility.LocalDateFormatter
import com.maksimowiczm.foodyou.food.ai.domain.ParseMealError
import foodyou.app.generated.resources.*
import kotlin.math.roundToInt
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

@Composable
fun AiFoodLoggingScreen(
    onBack: () -> Unit,
    onLogged: () -> Unit,
    onConfigure: () -> Unit,
    date: LocalDate,
    mealId: Long,
    modifier: Modifier = Modifier,
) {
    val dateFormatter = LocalDateFormatter.current
    val viewModel: AiFoodLoggingViewModel =
        koinViewModel { parametersOf(mealId, date.toEpochDays().toLong()) }
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value

    val snackBarHostState = remember { SnackbarHostState() }
    val loggedMessage = stringResource(Res.string.neutral_ai_items_logged)

    LaunchedCollectWithLifecycle(viewModel.loggedEvents) {
        snackBarHostState.showSnackbar(loggedMessage)
        onLogged()
    }

    val eventMessages =
        mapOf(
            AiFoodLoggingEvent.ProductSaved to stringResource(Res.string.neutral_ai_product_saved),
            AiFoodLoggingEvent.ProductSaveFailed to
                stringResource(Res.string.error_ai_product_save_failed),
            AiFoodLoggingEvent.RecipeSaved to stringResource(Res.string.neutral_ai_recipe_saved),
            AiFoodLoggingEvent.RecipeSaveFailed to
                stringResource(Res.string.error_ai_recipe_save_failed),
        )
    LaunchedCollectWithLifecycle(viewModel.events) { event ->
        eventMessages[event]?.let { snackBarHostState.showSnackbar(it) }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(Res.string.headline_ai_food_logging)) },
                subtitle = { Text(dateFormatter.formatDate(date)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
            )
        },
        snackbarHost = { SnackbarHost(snackBarHostState) },
    ) { paddingValues ->
        Box(Modifier.fillMaxSize().padding(paddingValues)) {
            when (uiState) {
                is AiFoodLoggingUiState.Input ->
                    InputContent(
                        hasApiKey = uiState.hasApiKey,
                        onAnalyze = viewModel::parse,
                        onConfigure = onConfigure,
                    )

                AiFoodLoggingUiState.Loading,
                AiFoodLoggingUiState.Logging ->
                    CircularProgressIndicator(Modifier.align(Alignment.Center))

                is AiFoodLoggingUiState.Review ->
                    if (uiState.items.isEmpty()) {
                        EmptyContent(onRetry = viewModel::reset)
                    } else {
                        ReviewContent(
                            items = uiState.items,
                            suggestedRecipeName = viewModel.lastDescription,
                            onEdit = viewModel::editItem,
                            onRemove = viewModel::removeItem,
                            onToggleExpand = viewModel::toggleExpanded,
                            onSaveProduct = viewModel::saveAsProduct,
                            onSaveRecipe = viewModel::saveAsRecipe,
                            onSaveRecipeAndLog = viewModel::saveAsRecipeAndLog,
                            onConfirm = viewModel::confirm,
                        )
                    }

                is AiFoodLoggingUiState.Error ->
                    ErrorContent(
                        error = uiState.error,
                        onRetry = viewModel::reset,
                        onConfigure = onConfigure,
                    )
            }
        }
    }
}

@Composable
private fun InputContent(
    hasApiKey: Boolean,
    onAnalyze: (String) -> Unit,
    onConfigure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val textState = rememberTextFieldState()

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = stringResource(Res.string.description_ai_food_logging),
            style = MaterialTheme.typography.bodyMedium,
        )

        OutlinedTextField(
            state = textState,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text(stringResource(Res.string.hint_ai_meal_description)) },
            lineLimits = TextFieldLineLimits.MultiLine(3, 8),
        )

        if (!hasApiKey) {
            Text(
                text = stringResource(Res.string.description_ai_missing_api_key),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
            )
            OutlinedButton(onClick = onConfigure) {
                Text(stringResource(Res.string.action_add_api_key))
            }
        }

        Button(
            onClick = { onAnalyze(textState.text.toString()) },
            enabled = hasApiKey && textState.text.isNotBlank(),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(stringResource(Res.string.action_analyze))
        }
    }
}

/** Which action a confirmed recipe-name dialog should trigger. */
private enum class RecipeDialogAction {
    SaveAndLog,
    SaveOnly,
}

@Composable
private fun ReviewContent(
    items: List<EditableMealItem>,
    suggestedRecipeName: String,
    onEdit: (Long, EditableMealItem.() -> EditableMealItem) -> Unit,
    onRemove: (Long) -> Unit,
    onToggleExpand: (Long) -> Unit,
    onSaveProduct: (Long) -> Unit,
    onSaveRecipe: (String) -> Unit,
    onSaveRecipeAndLog: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var dialogAction by remember { mutableStateOf<RecipeDialogAction?>(null) }

    dialogAction?.let { action ->
        SaveRecipeDialog(
            suggestedName = suggestedRecipeName,
            onDismiss = { dialogAction = null },
            onConfirm = { name ->
                when (action) {
                    RecipeDialogAction.SaveAndLog -> onSaveRecipeAndLog(name)
                    RecipeDialogAction.SaveOnly -> onSaveRecipe(name)
                }
                dialogAction = null
            },
        )
    }

    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { item ->
                ReviewItemCard(
                    item = item,
                    onEdit = { onEdit(item.id, it) },
                    onRemove = { onRemove(item.id) },
                    onToggleExpand = { onToggleExpand(item.id) },
                    onSaveProduct = { onSaveProduct(item.id) },
                )
            }
        }

        ReviewActions(
            itemCount = items.size,
            onSaveAndLog = { dialogAction = RecipeDialogAction.SaveAndLog },
            onSaveRecipe = { dialogAction = RecipeDialogAction.SaveOnly },
            onLog = onConfirm,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        )
    }
}

@Composable
private fun ReviewActions(
    itemCount: Int,
    onSaveAndLog: () -> Unit,
    onSaveRecipe: () -> Unit,
    onLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Button(onClick = onSaveAndLog, modifier = Modifier.weight(1f)) {
            Text(stringResource(Res.string.action_save_recipe_and_log))
        }

        Box {
            IconButton(onClick = { menuExpanded = true }) {
                Icon(
                    imageVector = Icons.Filled.MoreVert,
                    contentDescription = stringResource(Res.string.action_more),
                )
            }
            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.action_save_as_recipe)) },
                    onClick = {
                        menuExpanded = false
                        onSaveRecipe()
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(Res.string.action_log_items, itemCount)) },
                    onClick = {
                        menuExpanded = false
                        onLog()
                    },
                )
            }
        }
    }
}

@Composable
private fun ReviewItemCard(
    item: EditableMealItem,
    onEdit: (EditableMealItem.() -> EditableMealItem) -> Unit,
    onRemove: () -> Unit,
    onToggleExpand: () -> Unit,
    onSaveProduct: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val nameState = rememberTextFieldState(item.name)
    val gramsState = rememberTextFieldState(item.grams.roundToInt().toString())

    // Propagate edits back to the view model.
    LaunchedEffect(nameState.text) {
        val text = nameState.text.toString()
        if (text != item.name) onEdit { copy(name = text) }
    }
    LaunchedEffect(gramsState.text) {
        val grams = gramsState.text.toString().toDoubleOrNull()
        if (grams != null && grams > 0 && grams != item.grams) onEdit { copy(grams = grams) }
    }

    ElevatedCard(modifier = modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    state = nameState,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(Res.string.product_name)) },
                )
                IconButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, contentDescription = stringResource(Res.string.action_delete))
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedTextField(
                    state = gramsState,
                    modifier = Modifier.weight(1f),
                    label = { Text(stringResource(Res.string.unit_gram_short)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    lineLimits = TextFieldLineLimits.SingleLine,
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Switch(checked = item.isLiquid, onCheckedChange = { checked -> onEdit { copy(isLiquid = checked) } })
                    Text(
                        text = stringResource(Res.string.action_treat_as_liquid),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }

            val factor = item.grams / 100.0
            val energy = item.nutrition.energy?.times(factor)?.roundToInt()
            if (energy != null) {
                Text(
                    text = "≈ $energy kcal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Collapsible per-100g macro editor.
            TextButton(onClick = onToggleExpand) {
                Icon(
                    imageVector = if (item.expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
                Text(
                    text = stringResource(Res.string.action_edit_nutrition),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            AnimatedVisibility(visible = item.expanded) {
                NutritionEditor(nutrition = item.nutrition, onEdit = onEdit)
            }

            val saved = item.savedProductId != null
            OutlinedButton(onClick = onSaveProduct, enabled = !saved) {
                if (saved) {
                    Icon(Icons.Filled.Check, contentDescription = null)
                    Text(
                        text = stringResource(Res.string.neutral_ai_saved),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                } else {
                    Text(stringResource(Res.string.action_save_as_product))
                }
            }
        }
    }
}

@Composable
private fun NutritionEditor(
    nutrition: EditableNutrition,
    onEdit: (EditableMealItem.() -> EditableMealItem) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = stringResource(Res.string.description_ai_nutrition_per_100),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        NutrientField(Res.string.unit_energy, nutrition.energy) { v ->
            onEdit { copy(nutrition = nutrition.copy(energy = v)) }
        }
        NutrientField(Res.string.nutriment_proteins, nutrition.proteins) { v ->
            onEdit { copy(nutrition = nutrition.copy(proteins = v)) }
        }
        NutrientField(Res.string.nutriment_carbohydrates, nutrition.carbohydrates) { v ->
            onEdit { copy(nutrition = nutrition.copy(carbohydrates = v)) }
        }
        NutrientField(Res.string.nutriment_fats, nutrition.fats) { v ->
            onEdit { copy(nutrition = nutrition.copy(fats = v)) }
        }
        NutrientField(Res.string.nutriment_fiber, nutrition.dietaryFiber) { v ->
            onEdit { copy(nutrition = nutrition.copy(dietaryFiber = v)) }
        }
        NutrientField(Res.string.nutriment_sugars, nutrition.sugars) { v ->
            onEdit { copy(nutrition = nutrition.copy(sugars = v)) }
        }
        NutrientField(Res.string.nutriment_saturated_fats, nutrition.saturatedFats) { v ->
            onEdit { copy(nutrition = nutrition.copy(saturatedFats = v)) }
        }
        NutrientField(Res.string.mineral_sodium, nutrition.sodium) { v ->
            onEdit { copy(nutrition = nutrition.copy(sodium = v)) }
        }
    }
}

@Composable
private fun NutrientField(
    label: StringResource,
    value: Double?,
    onValueChange: (Double?) -> Unit,
) {
    val state = rememberTextFieldState(value?.let { formatValue(it) } ?: "")

    LaunchedEffect(state.text) {
        val parsed = state.text.toString().toDoubleOrNull()
        if (parsed != value) onValueChange(parsed)
    }

    OutlinedTextField(
        state = state,
        modifier = Modifier.fillMaxWidth(),
        label = { Text(stringResource(label)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        lineLimits = TextFieldLineLimits.SingleLine,
    )
}

@Composable
private fun SaveRecipeDialog(
    suggestedName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    // Collapse whitespace/newlines from the meal description and cap to a sane length.
    val initial =
        suggestedName.replace(Regex("\\s+"), " ").trim().take(MAX_RECIPE_NAME_LENGTH)
    val nameState =
        rememberTextFieldState(
            initialText = initial,
            initialSelection = TextRange(0, initial.length),
        )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.headline_ai_save_recipe)) },
        text = {
            OutlinedTextField(
                state = nameState,
                label = { Text(stringResource(Res.string.product_name)) },
                lineLimits = TextFieldLineLimits.SingleLine,
                inputTransformation = InputTransformation.maxLength(MAX_RECIPE_NAME_LENGTH),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(nameState.text.toString()) },
                enabled = nameState.text.isNotBlank(),
            ) {
                Text(stringResource(Res.string.action_save))
            }
        },
        dismissButton = {
            TextButton(onDismiss) { Text(stringResource(Res.string.action_cancel)) }
        },
    )
}

@Composable
private fun EmptyContent(onRetry: () -> Unit, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(Res.string.description_ai_no_items),
            style = MaterialTheme.typography.bodyLarge,
        )
        OutlinedButton(onClick = onRetry) { Text(stringResource(Res.string.action_analyze)) }
    }
}

@Composable
private fun ErrorContent(
    error: ParseMealError,
    onRetry: () -> Unit,
    onConfigure: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = stringResource(error.messageRes()),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.error,
        )
        if (error is ParseMealError.MissingApiKey || error is ParseMealError.Unauthorized) {
            Button(onClick = onConfigure) { Text(stringResource(Res.string.action_add_api_key)) }
        }
        OutlinedButton(onClick = onRetry) { Text(stringResource(Res.string.action_analyze)) }
    }
}

private const val MAX_RECIPE_NAME_LENGTH = 60

private fun formatValue(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

private fun ParseMealError.messageRes() =
    when (this) {
        ParseMealError.EmptyInput -> Res.string.error_ai_unknown
        ParseMealError.MissingApiKey -> Res.string.error_ai_missing_api_key
        ParseMealError.Network -> Res.string.error_ai_network
        ParseMealError.RateLimited -> Res.string.error_ai_rate_limited
        ParseMealError.Unauthorized -> Res.string.error_ai_unauthorized
        ParseMealError.MalformedResponse -> Res.string.error_ai_malformed_response
        ParseMealError.Refused -> Res.string.error_ai_refused
        is ParseMealError.Unknown -> Res.string.error_ai_unknown
    }

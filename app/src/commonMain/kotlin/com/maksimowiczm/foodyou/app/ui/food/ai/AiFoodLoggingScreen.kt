package com.maksimowiczm.foodyou.app.ui.food.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
                            onEdit = viewModel::editItem,
                            onRemove = viewModel::removeItem,
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
            lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.MultiLine(3, 8),
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

@Composable
private fun ReviewContent(
    items: List<EditableMealItem>,
    onEdit: (Long, EditableMealItem.() -> EditableMealItem) -> Unit,
    onRemove: (Long) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(items, key = { it.id }) { item ->
                ReviewItemCard(item = item, onEdit = { onEdit(item.id, it) }, onRemove = { onRemove(item.id) })
            }
        }

        Button(
            onClick = onConfirm,
            modifier = Modifier.fillMaxWidth().padding(16.dp),
        ) {
            Text(stringResource(Res.string.action_log_items, items.size))
        }
    }
}

@Composable
private fun ReviewItemCard(
    item: EditableMealItem,
    onEdit: (EditableMealItem.() -> EditableMealItem) -> Unit,
    onRemove: () -> Unit,
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
                    keyboardOptions =
                        androidx.compose.foundation.text.KeyboardOptions(
                            keyboardType = KeyboardType.Number
                        ),
                    lineLimits = androidx.compose.foundation.text.input.TextFieldLineLimits.SingleLine,
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

            val facts = item.item.nutritionFactsPer100g
            val factor = item.grams / 100.0
            val energy = facts.energy.value?.times(factor)?.roundToInt()
            if (energy != null) {
                Text(
                    text = "≈ $energy kcal",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
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

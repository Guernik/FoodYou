package com.maksimowiczm.foodyou.app.ui.food.diary.quickadd

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipAnchorPosition
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.maksimowiczm.foodyou.app.ui.common.form.FormField
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalEnergyFormatter
import com.maksimowiczm.foodyou.app.ui.common.utility.LocalNutrientsOrder
import com.maksimowiczm.foodyou.common.compose.component.unorderedList
import com.maksimowiczm.foodyou.food.ai.domain.ParseMealError
import com.maksimowiczm.foodyou.settings.domain.entity.NutrientsOrder
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun QuickAddForm(
    state: QuickAddFormState,
    aiState: QuickAddAiState,
    onAnalyze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val energyFormatter = LocalEnergyFormatter.current

    Column(modifier = modifier) {
        OutlinedTextField(
            state = state.name.textFieldState,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.product_name)) },
            supportingText = { Text(stringResource(Res.string.neutral_required)) },
            isError = state.name.error != null,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
        )

        AiAnalysisSection(
            aiState = aiState,
            enabled = state.name.value.isNotBlank(),
            onAnalyze = onAnalyze,
            modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
        )

        LocalNutrientsOrder.current.forEach {
            when (it) {
                NutrientsOrder.Proteins ->
                    state.proteins.TextField(
                        label = stringResource(Res.string.nutriment_proteins),
                        modifier = Modifier.fillMaxWidth(),
                    )
                NutrientsOrder.Fats ->
                    state.fats.TextField(
                        label = stringResource(Res.string.nutriment_fats),
                        modifier = Modifier.fillMaxWidth(),
                    )
                NutrientsOrder.Carbohydrates ->
                    state.carbohydrates.TextField(
                        label = stringResource(Res.string.nutriment_carbohydrates),
                        modifier = Modifier.fillMaxWidth(),
                    )
                else -> Unit
            }
        }

        OutlinedTextField(
            state = state.energy.textFieldState,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(Res.string.unit_energy)) },
            supportingText = {
                val error = state.energy.error
                if (error != null) {
                    Text(error.stringResource())
                }
            },
            suffix = { Text(energyFormatter.suffix()) },
            trailingIcon = {
                TooltipBox(
                    positionProvider =
                        TooltipDefaults.rememberTooltipPositionProvider(
                            TooltipAnchorPosition.Above
                        ),
                    tooltip = {
                        PlainTooltip {
                            Text(
                                text =
                                    if (state.autoCalculateEnergy) {
                                        stringResource(Res.string.headline_auto_calculate_energy)
                                    } else {
                                        stringResource(Res.string.headline_manual_energy_input)
                                    },
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    },
                    state = rememberTooltipState(isPersistent = true),
                ) {
                    IconButton(
                        onClick = { state.autoCalculateEnergy = !state.autoCalculateEnergy }
                    ) {
                        if (state.autoCalculateEnergy) {
                            Icon(imageVector = Icons.Outlined.Calculate, contentDescription = null)
                        } else {
                            Icon(imageVector = Icons.Outlined.Keyboard, contentDescription = null)
                        }
                    }
                }
            },
            isError = state.energy.error != null,
            keyboardOptions =
                KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
        )

        Text(
            text = stringResource(Res.string.description_calories_are_calculated),
            style = MaterialTheme.typography.bodySmall,
        )
        Text(
            text =
                unorderedList(
                    stringResource(
                        Res.string.x_energy_unit_per_g,
                        stringResource(Res.string.nutriment_proteins),
                        energyFormatter.proteinsEnergyDensity,
                        energyFormatter.suffix(),
                    ),
                    stringResource(
                        Res.string.x_energy_unit_per_g,
                        stringResource(Res.string.nutriment_carbohydrates),
                        energyFormatter.carbohydratesEnergyDensity,
                        energyFormatter.suffix(),
                    ),
                    stringResource(
                        Res.string.x_energy_unit_per_g,
                        stringResource(Res.string.nutriment_fats),
                        energyFormatter.fatsEnergyDensity,
                        energyFormatter.suffix(),
                    ),
                ),
            style = MaterialTheme.typography.bodySmall,
        )

        Row(
            modifier =
                Modifier.fillMaxWidth()
                    .padding(top = 8.dp)
                    .clickable { state.saveAsProduct = !state.saveAsProduct }
                    .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Checkbox(checked = state.saveAsProduct, onCheckedChange = null)
            Spacer(Modifier.width(12.dp))
            Text(
                text = stringResource(Res.string.action_also_save_as_product),
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * The AI analysis action. Estimates the nutrition for the name the user typed, so it is disabled
 * until there is a name and an API key to use.
 */
@Composable
private fun AiAnalysisSection(
    aiState: QuickAddAiState,
    enabled: Boolean,
    onAnalyze: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier) {
        OutlinedButton(
            onClick = onAnalyze,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled && aiState.hasApiKey && !aiState.isLoading,
        ) {
            if (aiState.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(18.dp),
                    strokeWidth = 2.dp,
                    color = LocalContentColor.current,
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                )
            }
            Spacer(Modifier.width(8.dp))
            Text(stringResource(Res.string.action_ai_analysis))
        }

        if (!aiState.hasApiKey) {
            Text(
                text = stringResource(Res.string.description_ai_missing_api_key),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (aiState is QuickAddAiState.Error) {
            Text(
                text = stringResource(aiState.error.messageRes()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
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

@Composable
private fun FormField<Double?, QuickAddFormFieldError>.TextField(
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        state = textFieldState,
        modifier = modifier,
        keyboardOptions =
            KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
        suffix = { Text(stringResource(Res.string.unit_gram_short)) },
        supportingText = {
            val error = error
            if (error != null) {
                Text(error.stringResource())
            }
        },
        isError = error != null,
        label = { Text(label) },
    )
}

package com.maksimowiczm.foodyou.app.ui.database.externaldatabases

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.common.domain.userpreferences.UserPreferencesRepository
import com.maksimowiczm.foodyou.food.ai.domain.LlmApiKeyRepository
import com.maksimowiczm.foodyou.food.ai.domain.LlmSettings
import foodyou.app.generated.resources.*
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import org.koin.core.qualifier.named

@Composable
fun UpdateLlmSettingsDialog(
    onDismissRequest: () -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val settingsRepository: UserPreferencesRepository<LlmSettings> =
        koinInject(named(LlmSettings::class.qualifiedName!!))
    val apiKeyRepository: LlmApiKeyRepository = koinInject()

    val settings = settingsRepository.observe().collectAsStateWithLifecycle(null).value
    val hasKey = apiKeyRepository.hasKey().collectAsStateWithLifecycle(false).value
    val scope = rememberCoroutineScope()

    if (settings == null) {
        return
    }

    val baseUrlState = rememberTextFieldState(settings.baseUrl)
    val modelState = rememberTextFieldState(settings.model)
    // Never pre-fill the key; blank = leave unchanged.
    val apiKeyState = rememberTextFieldState("")

    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = {
            TextButton(
                onClick = {
                    scope.launch {
                        settingsRepository.update {
                            copy(
                                baseUrl =
                                    baseUrlState.text.toString().ifBlank { LlmSettings.DEFAULT_BASE_URL },
                                model = modelState.text.toString().ifBlank { LlmSettings.DEFAULT_MODEL },
                            )
                        }
                        val key = apiKeyState.text.toString()
                        if (key.isNotBlank()) {
                            apiKeyRepository.store(key)
                        }
                        onSave()
                    }
                }
            ) {
                Text(stringResource(Res.string.action_save))
            }
        },
        modifier = modifier,
        dismissButton = {
            TextButton(onDismissRequest) { Text(stringResource(Res.string.action_cancel)) }
        },
        title = { Text(stringResource(Res.string.headline_ai_food_logging)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(Res.string.description_ai_settings),
                    style = androidx.compose.material3.MaterialTheme.typography.bodySmall,
                )
                OutlinedTextField(
                    state = baseUrlState,
                    label = { Text(stringResource(Res.string.headline_ai_base_url)) },
                )
                OutlinedTextField(
                    state = modelState,
                    label = { Text(stringResource(Res.string.headline_ai_model)) },
                )
                OutlinedTextField(
                    state = apiKeyState,
                    label = { Text(stringResource(Res.string.headline_ai_api_key)) },
                    placeholder = {
                        if (hasKey) Text(stringResource(Res.string.headline_api_key))
                    },
                )
            }
        },
    )
}

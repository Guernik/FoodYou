package com.maksimowiczm.foodyou.app.ui.settings.ai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maksimowiczm.foodyou.app.ui.common.component.ArrowBackIconButton
import com.maksimowiczm.foodyou.food.ai.domain.LlmVendor
import com.maksimowiczm.foodyou.food.ai.domain.TestConnectionResult
import foodyou.app.generated.resources.*
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AiSettingsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val viewModel: AiSettingsViewModel = koinViewModel()
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    val hasKey = viewModel.hasKey.collectAsStateWithLifecycle().value
    val testState = viewModel.testState.collectAsStateWithLifecycle().value

    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    Scaffold(
        modifier = modifier,
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text(stringResource(Res.string.headline_ai_settings)) },
                navigationIcon = { ArrowBackIconButton(onBack) },
                scrollBehavior = scrollBehavior,
            )
        },
    ) { paddingValues ->
        if (uiState == null) {
            return@Scaffold
        }

        Column(
            modifier =
                Modifier.fillMaxSize()
                    .nestedScroll(scrollBehavior.nestedScrollConnection)
                    .verticalScroll(rememberScrollState())
                    .padding(paddingValues)
                    .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(Res.string.description_ai_settings),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            VendorDropdown(vendor = uiState.vendor, onSelect = viewModel::selectVendor)

            OutlinedTextField(
                value = uiState.baseUrl,
                onValueChange = viewModel::setBaseUrl,
                label = { Text(stringResource(Res.string.headline_ai_base_url)) },
                singleLine = true,
                readOnly = uiState.vendor != LlmVendor.Custom,
                modifier = Modifier.fillMaxWidth(),
            )

            ModelField(
                vendor = uiState.vendor,
                model = uiState.model,
                manual = uiState.manualModel,
                onModelChange = viewModel::setModel,
                onManualChange = viewModel::setManualModel,
            )

            ApiKeyField(
                apiKey = uiState.apiKey,
                hasStoredKey = hasKey,
                onApiKeyChange = viewModel::setApiKey,
                onClear = viewModel::clearKey,
            )

            TestRow(state = testState, onTest = viewModel::test)

            Button(onClick = { viewModel.save(onBack) }, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(Res.string.action_save))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun VendorDropdown(vendor: LlmVendor, onSelect: (LlmVendor) -> Unit) {
    var expanded by remember { mutableStateOf(false) }

    ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
        OutlinedTextField(
            value = stringResource(vendor.labelRes()),
            onValueChange = {},
            readOnly = true,
            label = { Text(stringResource(Res.string.headline_ai_vendor)) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
            modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            LlmVendor.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(stringResource(option.labelRes())) },
                    onClick = {
                        onSelect(option)
                        expanded = false
                    },
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelField(
    vendor: LlmVendor,
    model: String,
    manual: Boolean,
    onModelChange: (String) -> Unit,
    onManualChange: (Boolean) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (manual || vendor.models.isEmpty()) {
            OutlinedTextField(
                value = model,
                onValueChange = onModelChange,
                label = { Text(stringResource(Res.string.headline_ai_model)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            var expanded by remember { mutableStateOf(false) }
            ExposedDropdownMenuBox(expanded = expanded, onExpandedChange = { expanded = it }) {
                OutlinedTextField(
                    value = model,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(Res.string.headline_ai_model)) },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded) },
                    modifier = Modifier.fillMaxWidth().menuAnchor(MenuAnchorType.PrimaryNotEditable),
                )
                ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                    vendor.models.forEach { option ->
                        DropdownMenuItem(
                            text = { Text(option) },
                            onClick = {
                                onModelChange(option)
                                expanded = false
                            },
                        )
                    }
                }
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Switch(checked = manual, onCheckedChange = onManualChange)
            Text(
                text = stringResource(Res.string.action_enter_model_manually),
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(start = 8.dp),
            )
        }
    }
}

@Composable
private fun ApiKeyField(
    apiKey: String,
    hasStoredKey: Boolean,
    onApiKeyChange: (String) -> Unit,
    onClear: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        OutlinedTextField(
            value = apiKey,
            onValueChange = onApiKeyChange,
            label = { Text(stringResource(Res.string.headline_ai_api_key)) },
            singleLine = true,
            placeholder = {
                if (hasStoredKey && apiKey.isEmpty()) {
                    Text("••••••••")
                }
            },
            supportingText = {
                if (hasStoredKey) {
                    Text(stringResource(Res.string.description_ai_api_key_stored))
                }
            },
            modifier = Modifier.fillMaxWidth(),
        )
        if (hasStoredKey) {
            TextButton(onClick = onClear) {
                Text(stringResource(Res.string.action_clear_key))
            }
        }
    }
}

@Composable
private fun TestRow(state: TestState, onTest: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        OutlinedButton(onClick = onTest, enabled = state != TestState.Testing) {
            Text(stringResource(Res.string.action_test_connection))
        }

        when (state) {
            TestState.Idle -> Unit
            TestState.Testing -> CircularProgressIndicator(Modifier.padding(4.dp))
            is TestState.Result ->
                when (val result = state.result) {
                    TestConnectionResult.Success ->
                        StatusText(
                            success = true,
                            text = stringResource(Res.string.neutral_ai_test_ok),
                        )
                    else ->
                        StatusText(
                            success = false,
                            text = stringResource(result.messageRes()),
                        )
                }
        }
    }
}

@Composable
private fun StatusText(success: Boolean, text: String) {
    val color =
        if (success) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(
            imageVector = if (success) Icons.Filled.CheckCircle else Icons.Filled.Error,
            contentDescription = null,
            tint = color,
        )
        Text(text = text, color = color, modifier = Modifier.padding(start = 8.dp))
    }
}

private fun LlmVendor.labelRes(): StringResource =
    when (this) {
        LlmVendor.OpenAI -> Res.string.headline_ai_vendor_openai
        LlmVendor.OpenRouter -> Res.string.headline_ai_vendor_openrouter
        LlmVendor.Custom -> Res.string.headline_ai_vendor_custom
    }

private fun TestConnectionResult.messageRes(): StringResource =
    when (this) {
        TestConnectionResult.Success -> Res.string.neutral_ai_test_ok
        TestConnectionResult.Unauthorized -> Res.string.error_ai_unauthorized
        TestConnectionResult.Network -> Res.string.error_ai_network
        TestConnectionResult.RateLimited -> Res.string.error_ai_rate_limited
        is TestConnectionResult.Unknown -> Res.string.error_ai_unknown
    }

package com.mrredhood.astracode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AiProviderSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { AiProviderSettingsRepository(context) }
    val scope = rememberCoroutineScope()
    var selectedProviderName by remember { mutableStateOf(repository.selectedProviderId().name) }
    val selectedProvider = CloudAiProviderId.values().firstOrNull { it.name == selectedProviderName }
        ?: CloudAiProviderId.OPENROUTER
    val definition = requireNotNull(CloudAiProviderCatalog.find(selectedProvider))
    val savedConfig = remember(selectedProvider) { repository.loadConfiguration(selectedProvider) }
    var modelId by remember(selectedProvider) { mutableStateOf(savedConfig.modelId) }
    var endpointOverride by remember(selectedProvider) { mutableStateOf(savedConfig.baseUrlOverride) }
    var discoveredModels by remember(selectedProvider) { mutableStateOf<List<AiDiscoveredModel>>(emptyList()) }
    var modelSearchQuery by remember(selectedProvider) { mutableStateOf("") }
    // API key input is intentionally not saveable, so it is not persisted in saved-instance Bundle state.
    var apiKeyDraft by remember(selectedProvider) { mutableStateOf("") }
    var hasSavedKey by remember(selectedProvider) {
        mutableStateOf(runCatching { repository.hasApiKey(selectedProvider) }.getOrDefault(false))
    }
    var expanded by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isStatusError by remember { mutableStateOf(false) }
    var confirmRemoveKey by remember { mutableStateOf(false) }

    fun persistConfiguration(testConnection: Boolean) {
        val config = AiProviderConfiguration(
            providerId = selectedProvider,
            modelId = modelId.trim(),
            baseUrlOverride = endpointOverride.trim()
        )
        val keyDraft = apiKeyDraft
        scope.launch {
            isBusy = true
            statusMessage = null
            isStatusError = false
            try {
                withContext(Dispatchers.IO) {
                    repository.saveConfiguration(config)
                    if (keyDraft.isNotBlank()) repository.saveApiKey(selectedProvider, keyDraft)
                }
                hasSavedKey = withContext(Dispatchers.IO) { repository.hasApiKey(selectedProvider) }
                apiKeyDraft = ""
                statusMessage = "Settings saved. API keys are encrypted with Android Keystore."
                if (testConnection) {
                    statusMessage = "Testing connection with a short request…"
                    when (val result = performProviderConnectionTest(repository, config)) {
                        is AiProviderResult.Success -> {
                            statusMessage = "Connection successful. Model response: " + result.response.text.take(240)
                            isStatusError = false
                        }
                        is AiProviderResult.Failure -> {
                            statusMessage = "Connection test failed: " + result.failure.detail
                            isStatusError = true
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                statusMessage = "Could not save settings or test the connection. Check the endpoint, model ID and API key."
                isStatusError = true
            } finally {
                isBusy = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        OutlinedButton(onClick = onBack) { Text("Back to More") }
        Text("AI provider settings", style = MaterialTheme.typography.titleLarge)
        Text(
            "Choose a cloud provider, discover the models available to your API key, or enter a model ID manually.",
            style = MaterialTheme.typography.bodyMedium
        )

        Text("Provider", style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { expanded = true }, enabled = !isBusy) {
                Text(selectedProvider.displayName)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                CloudAiProviderCatalog.all.forEach { provider ->
                    DropdownMenuItem(
                        text = { Text(provider.id.displayName) },
                        onClick = {
                            selectedProviderName = provider.id.name
                            expanded = false
                            statusMessage = null
                            isStatusError = false
                            discoveredModels = emptyList()
                            modelSearchQuery = ""
                        }
                    )
                }
            }
        }
        Text(definition.description, style = MaterialTheme.typography.bodySmall)
        Text("API protocol: ${definition.apiProtocol.displayName}", style = MaterialTheme.typography.bodySmall)
        if (!definition.defaultBaseUrl.isNullOrBlank()) {
            Text("Default endpoint: ${definition.defaultBaseUrl}", style = MaterialTheme.typography.bodySmall)
        }

        OutlinedTextField(
            value = modelId,
            onValueChange = { modelId = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Model ID") },
            placeholder = { Text("Enter the exact model ID from your provider") },
            singleLine = true,
            enabled = !isBusy
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    val keyDraft = apiKeyDraft
                    val endpoint = endpointOverride.trim().takeIf { it.isNotEmpty() }
                    scope.launch {
                        isBusy = true
                        statusMessage = null
                        isStatusError = false
                        try {
                            if (keyDraft.isNotBlank()) {
                                withContext(Dispatchers.IO) {
                                    repository.saveApiKey(selectedProvider, keyDraft)
                                }
                                apiKeyDraft = ""
                                hasSavedKey = true
                            }
                            statusMessage = "Loading available models…"
                            when (val result = withContext(Dispatchers.IO) {
                                CloudAiModelDiscoveryService(repository).discover(selectedProvider, endpoint)
                            }) {
                                is AiModelDiscoveryResult.Success -> {
                                    discoveredModels = result.models
                                    modelSearchQuery = ""
                                    statusMessage = if (result.models.isEmpty()) {
                                        "The provider returned no selectable models. Check the provider account or enter a model ID manually."
                                    } else {
                                        "Loaded ${result.models.size} available models. Select one below."
                                    }
                                    isStatusError = result.models.isEmpty()
                                }
                                is AiModelDiscoveryResult.Failure -> {
                                    statusMessage = result.failure.detail
                                    isStatusError = true
                                }
                            }
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (error: Exception) {
                            statusMessage = "Model discovery failed. Check the endpoint and API key."
                            isStatusError = true
                        } finally {
                            isBusy = false
                        }
                    }
                },
                enabled = !isBusy
            ) {
                Text(if (isBusy) "Working…" else "Discover models")
            }
        }
        if (discoveredModels.isNotEmpty()) {
            OutlinedTextField(
                value = modelSearchQuery,
                onValueChange = { modelSearchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Filter discovered models") },
                placeholder = { Text("Search by name or model ID") },
                singleLine = true,
                enabled = !isBusy
            )
            val query = modelSearchQuery.trim()
            val filteredModels = discoveredModels.filter { model ->
                query.isEmpty() ||
                    model.id.contains(query, ignoreCase = true) ||
                    model.displayName.contains(query, ignoreCase = true)
            }
            Text(
                "Choose a model to fill the Model ID field. Showing up to 12 matches.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (filteredModels.isEmpty()) {
                Text("No discovered models match that search.", style = MaterialTheme.typography.bodySmall)
            } else {
                filteredModels.take(12).forEach { model ->
                    OutlinedButton(
                        onClick = {
                            modelId = model.id
                            statusMessage = "Selected ${model.displayName}."
                            isStatusError = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(model.displayName, style = MaterialTheme.typography.bodyMedium)
                            Text(model.id, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }
        }
        OutlinedTextField(
            value = endpointOverride,
            onValueChange = {
                endpointOverride = it
                discoveredModels = emptyList()
            },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("Custom HTTPS base URL (optional)") },
            placeholder = { Text(definition.defaultBaseUrl ?: "https://your-provider.example/v1") },
            supportingText = {
                Text(if (definition.defaultBaseUrl == null) "Required for this provider. HTTPS only." else "Leave blank to use the default endpoint.")
            },
            singleLine = true,
            enabled = !isBusy
        )
        OutlinedTextField(
            value = apiKeyDraft,
            onValueChange = { apiKeyDraft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API key (optional when already saved)") },
            placeholder = { Text(if (hasSavedKey) "Saved key present — enter a new value to replace it" else definition.credentialLabel) },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            enabled = !isBusy
        )
        Text(
            if (hasSavedKey) "A key is stored encrypted on this device. Its value is never displayed." else "No key is saved for this provider.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = { persistConfiguration(testConnection = false) }, enabled = !isBusy) {
                Text("Save settings")
            }
            Button(onClick = { persistConfiguration(testConnection = true) }, enabled = !isBusy) {
                Text(if (isBusy) "Working…" else "Save & test")
            }
        }
        if (hasSavedKey) {
            OutlinedButton(onClick = { confirmRemoveKey = true }, enabled = !isBusy) {
                Text("Remove saved API key")
            }
        }

        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceVariant
        ) {
            Text(
                "Connection testing sends a short prompt to the selected provider and may incur usage charges. It does not send workspace files. Never share API keys in chat or bug reports.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
        statusMessage?.let { message ->
            Text(
                message,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isStatusError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
        }
    }

    if (confirmRemoveKey) {
        AlertDialog(
            onDismissRequest = { confirmRemoveKey = false },
            title = { Text("Remove saved API key?") },
            text = { Text("AstraCode will delete the encrypted key for ${selectedProvider.displayName} from this device.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmRemoveKey = false
                        scope.launch {
                            isBusy = true
                            try {
                                withContext(Dispatchers.IO) { repository.removeApiKey(selectedProvider) }
                                hasSavedKey = false
                                apiKeyDraft = ""
                                statusMessage = "Saved API key removed."
                                isStatusError = false
                            } catch (cancelled: CancellationException) {
                                throw cancelled
                            } catch (error: Exception) {
                                statusMessage = "Could not remove the saved API key."
                                isStatusError = true
                            } finally {
                                isBusy = false
                            }
                        }
                    }
                ) { Text("Remove") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemoveKey = false }) { Text("Cancel") }
            }
        )
    }
}

private suspend fun performProviderConnectionTest(
    repository: AiProviderSettingsRepository,
    configuration: AiProviderConfiguration
): AiProviderResult = withContext(Dispatchers.IO) {
    val selectedModel = configuration.modelId.trim()
    val capabilities = mapOf(
        selectedModel to AiModelCapabilities(contextWindowTokens = 8192, maxOutputTokens = 128)
    )
    val endpoint = configuration.baseUrlOverride.trim().takeIf { it.isNotEmpty() }
    val provider = CloudAiProviderFactory(repository).create(
        id = configuration.providerId,
        baseUrlOverride = endpoint,
        modelCapabilities = capabilities
    )
    provider.generate(
        modelId = selectedModel,
        request = AiGenerationRequest(
            messages = listOf(AiChatMessage(AiMessageRole.USER, "Connection check: reply with only OK.")),
            maxOutputTokens = 16
        )
    )
}

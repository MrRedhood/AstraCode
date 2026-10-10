package com.mrredhood.astracode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
    // Keep an existing user's custom HTTPS override for compatibility; new setups use standard defaults.
    val savedConfig = remember(selectedProvider) { repository.loadConfiguration(selectedProvider) }
    // API key input is intentionally not saveable, so it is never placed in saved-instance Bundle state.
    var apiKeyDraft by remember(selectedProvider) { mutableStateOf("") }
    var hasSavedKey by remember(selectedProvider) {
        mutableStateOf(runCatching { repository.hasApiKey(selectedProvider) }.getOrDefault(false))
    }
    var expanded by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isStatusError by remember { mutableStateOf(false) }
    var confirmRemoveKey by remember { mutableStateOf(false) }

    fun saveAndConnect() {
        val keyDraft = apiKeyDraft
        scope.launch {
            isBusy = true
            statusMessage = null
            isStatusError = false
            try {
                if (keyDraft.isNotBlank()) {
                    withContext(Dispatchers.IO) { repository.saveApiKey(selectedProvider, keyDraft) }
                }
                hasSavedKey = withContext(Dispatchers.IO) { repository.hasApiKey(selectedProvider) }
                apiKeyDraft = ""
                if (!hasSavedKey) {
                    statusMessage = "Enter an API key for the selected provider to connect."
                    isStatusError = true
                    return@launch
                }
                statusMessage = "Finding an available chat model…"
                val discovery = withContext(Dispatchers.IO) {
                    CloudAiModelDiscoveryService(repository).discover(
                        selectedProvider,
                        savedConfig.baseUrlOverride.trim().takeIf { it.isNotEmpty() }
                    )
                }
                val models = when (discovery) {
                    is AiModelDiscoveryResult.Success -> discovery.models
                    is AiModelDiscoveryResult.Failure -> {
                        statusMessage = "Could not find a chat model: " + discovery.failure.detail
                        isStatusError = true
                        return@launch
                    }
                }
                val selectedModel = AiDefaultModelSelector.select(selectedProvider, models)
                if (selectedModel == null) {
                    statusMessage = "The provider returned no suitable chat model. Check that this API key can access text-generation models."
                    isStatusError = true
                    return@launch
                }
                val config = AiProviderConfiguration(
                    providerId = selectedProvider,
                    modelId = selectedModel.id,
                    baseUrlOverride = savedConfig.baseUrlOverride.trim()
                )
                withContext(Dispatchers.IO) { repository.saveConfiguration(config) }
                statusMessage = "Testing the connection…"
                when (val result = performProviderConnectionTest(repository, config)) {
                    is AiProviderResult.Success -> {
                        statusMessage = "Connected successfully. AstraCode selected ${selectedModel.displayName} automatically."
                        isStatusError = false
                    }
                    is AiProviderResult.Failure -> {
                        statusMessage = "Model selected automatically, but the connection test failed: " + result.failure.detail
                        isStatusError = true
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                statusMessage = "Could not connect. Check the API key and provider access, then try again."
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
            "Choose a cloud provider and enter its API key. AstraCode uses the provider's standard HTTPS endpoint and automatically selects an available chat model.",
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
                        },
                        enabled = !isBusy
                    )
                }
            }
        }
        Text(definition.description, style = MaterialTheme.typography.bodySmall)
        Text("API protocol: ${definition.apiProtocol.displayName}", style = MaterialTheme.typography.bodySmall)
        OutlinedTextField(
            value = apiKeyDraft,
            onValueChange = { apiKeyDraft = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text("API key") },
            placeholder = {
                Text(if (hasSavedKey) "Saved key is encrypted; enter a replacement only if needed" else definition.credentialLabel)
            },
            supportingText = {
                Text(if (hasSavedKey) "A key is stored encrypted on this device." else "No key is saved for this provider.")
            },
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            singleLine = true,
            enabled = !isBusy
        )
        Button(
            onClick = { saveAndConnect() },
            enabled = !isBusy,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (isBusy) "Connecting…" else "Save & connect")
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
                "AstraCode discovers a compatible chat model for you. Connecting sends a short test prompt and may incur provider charges; workspace files are not included. Saved API keys are encrypted with Android Keystore and excluded from backup. Never share API keys in chat or bug reports.",
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
    val capabilities = mapOf(selectedModel to AiModelCapabilities(contextWindowTokens = 8192, maxOutputTokens = 128))
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

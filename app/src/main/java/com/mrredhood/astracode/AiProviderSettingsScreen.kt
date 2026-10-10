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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class AiModelPriceFilter(val label: String) {
    ALL("All prices"), FREE("Free"), PAID("Paid"), UNKNOWN("Unknown price")
}

private enum class AiModelContextFilter(val label: String) {
    ALL("Any context size"),
    HIGH("High · 128K+ tokens"),
    STANDARD("Standard · 32K–128K"),
    LOW("Low · under 32K"),
    UNKNOWN("Unknown context")
}

@Composable
fun AiProviderSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { AiProviderSettingsRepository(context) }
    val scope = rememberCoroutineScope()
    var selectedProviderName by remember { mutableStateOf(repository.selectedProviderId().name) }
    val selectedProvider = CloudAiProviderId.values().firstOrNull { it.name == selectedProviderName }
        ?: CloudAiProviderId.OPENROUTER
    val definition = requireNotNull(CloudAiProviderCatalog.find(selectedProvider))
    // Preserve custom endpoints saved by older versions; new setup uses standard provider defaults.
    val savedConfig = remember(selectedProvider) { repository.loadConfiguration(selectedProvider) }
    // The key draft is deliberately not saveable, keeping secrets out of the saved-instance Bundle.
    var apiKeyDraft by remember(selectedProvider) { mutableStateOf("") }
    var hasSavedKey by remember(selectedProvider) {
        mutableStateOf(runCatching { repository.hasApiKey(selectedProvider) }.getOrDefault(false))
    }
    var selectedModelId by remember(selectedProvider) { mutableStateOf(savedConfig.modelId) }
    var discoveredModels by remember(selectedProvider) { mutableStateOf<List<AiDiscoveredModel>>(emptyList()) }
    var modelSearchQuery by remember(selectedProvider) { mutableStateOf("") }
    var priceFilter by remember(selectedProvider) { mutableStateOf(AiModelPriceFilter.ALL) }
    var contextFilter by remember(selectedProvider) { mutableStateOf(AiModelContextFilter.ALL) }
    var providerMenuExpanded by remember { mutableStateOf(false) }
    var priceMenuExpanded by remember { mutableStateOf(false) }
    var contextMenuExpanded by remember { mutableStateOf(false) }
    var isBusy by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isStatusError by remember { mutableStateOf(false) }
    var confirmRemoveKey by remember { mutableStateOf(false) }

    fun discoverModels() {
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
                    statusMessage = "Enter an API key before discovering models."
                    isStatusError = true
                    return@launch
                }
                statusMessage = "Discovering available models…"
                when (val result = withContext(Dispatchers.IO) {
                    CloudAiModelDiscoveryService(repository).discover(
                        selectedProvider,
                        savedConfig.baseUrlOverride.trim().takeIf { it.isNotEmpty() }
                    )
                }) {
                    is AiModelDiscoveryResult.Success -> {
                        discoveredModels = result.models
                        val savedChoice = result.models.firstOrNull { it.id == selectedModelId }
                        val suggested = savedChoice ?: AiDefaultModelSelector.select(selectedProvider, result.models)
                        selectedModelId = suggested?.id.orEmpty()
                        statusMessage = when {
                            result.models.isEmpty() -> "The provider returned no models. Check API access and try again."
                            suggested == null -> "Found ${result.models.size} models, but none looks like a supported chat model."
                            else -> "Found ${result.models.size} models. A suggestion is selected; tap any other model to choose it manually."
                        }
                        isStatusError = result.models.isEmpty() || suggested == null
                    }
                    is AiModelDiscoveryResult.Failure -> {
                        statusMessage = result.failure.detail
                        isStatusError = true
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                statusMessage = "Model discovery failed. Check the API key and provider access."
                isStatusError = true
            } finally {
                isBusy = false
            }
        }
    }

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

                var candidateModels = discoveredModels
                if (candidateModels.isEmpty()) {
                    statusMessage = "Finding an available chat model…"
                    candidateModels = when (val result = withContext(Dispatchers.IO) {
                        CloudAiModelDiscoveryService(repository).discover(
                            selectedProvider,
                            savedConfig.baseUrlOverride.trim().takeIf { it.isNotEmpty() }
                        )
                    }) {
                        is AiModelDiscoveryResult.Success -> result.models
                        is AiModelDiscoveryResult.Failure -> {
                            statusMessage = result.failure.detail
                            isStatusError = true
                            return@launch
                        }
                    }
                    discoveredModels = candidateModels
                }
                val chosenModel = candidateModels.firstOrNull { it.id == selectedModelId }
                    ?: selectedModelId.takeIf { it.isNotBlank() }?.let { AiDiscoveredModel(it, it) }
                    ?: AiDefaultModelSelector.select(selectedProvider, candidateModels)
                if (chosenModel == null) {
                    statusMessage = "No suitable chat model was found. Discover models and select one manually."
                    isStatusError = true
                    return@launch
                }
                selectedModelId = chosenModel.id
                val config = AiProviderConfiguration(
                    providerId = selectedProvider,
                    modelId = chosenModel.id,
                    baseUrlOverride = savedConfig.baseUrlOverride.trim()
                )
                withContext(Dispatchers.IO) { repository.saveConfiguration(config) }

                statusMessage = "Testing the selected model…"
                when (val result = performProviderConnectionTest(repository, config)) {
                    is AiProviderResult.Success -> {
                        statusMessage = "Connected. Selected model: ${chosenModel.displayName}."
                        isStatusError = false
                    }
                    is AiProviderResult.Failure -> {
                        statusMessage = "Model selected, but connection test failed: " + result.failure.detail
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

    val filteredModels = discoveredModels.filter { model ->
        val query = modelSearchQuery.trim()
        val matchesSearch = query.isEmpty() ||
            model.id.contains(query, ignoreCase = true) ||
            model.displayName.contains(query, ignoreCase = true)
        val matchesPrice = when (priceFilter) {
            AiModelPriceFilter.ALL -> true
            AiModelPriceFilter.FREE -> model.pricingTier == AiModelPricingTier.FREE
            AiModelPriceFilter.PAID -> model.pricingTier == AiModelPricingTier.PAID
            AiModelPriceFilter.UNKNOWN -> model.pricingTier == AiModelPricingTier.UNKNOWN
        }
        val matchesContext = when (contextFilter) {
            AiModelContextFilter.ALL -> true
            AiModelContextFilter.HIGH -> (model.contextWindowTokens ?: 0) >= HIGH_CONTEXT_TOKEN_THRESHOLD
            AiModelContextFilter.STANDARD -> model.contextWindowTokens?.let { it in STANDARD_CONTEXT_TOKEN_RANGE } == true
            AiModelContextFilter.LOW -> model.contextWindowTokens != null &&
                model.contextWindowTokens < LOW_CONTEXT_TOKEN_THRESHOLD
            AiModelContextFilter.UNKNOWN -> model.contextWindowTokens == null
        }
        matchesSearch && matchesPrice && matchesContext
    }.sortedWith(
        compareByDescending<AiDiscoveredModel> { it.contextWindowTokens ?: -1 }
            .thenBy { it.displayName.lowercase(Locale.ROOT) }
    )

    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        OutlinedButton(onClick = onBack) { Text("Back to More") }
        Text("AI provider settings", style = MaterialTheme.typography.titleLarge)
        Text(
            "Choose a provider and enter its API key. AstraCode can select a model automatically, or you can discover models and choose one using search and filters.",
            style = MaterialTheme.typography.bodyMedium
        )

        Text("Provider", style = MaterialTheme.typography.labelLarge)
        Box {
            OutlinedButton(onClick = { providerMenuExpanded = true }, enabled = !isBusy) {
                Text(selectedProvider.displayName)
            }
            DropdownMenu(expanded = providerMenuExpanded, onDismissRequest = { providerMenuExpanded = false }) {
                CloudAiProviderCatalog.all.forEach { provider ->
                    DropdownMenuItem(
                        text = { Text(provider.id.displayName) },
                        onClick = {
                            selectedProviderName = provider.id.name
                            providerMenuExpanded = false
                            statusMessage = null
                            isStatusError = false
                        },
                        enabled = !isBusy
                    )
                }
            }
        }
        Text(definition.description, style = MaterialTheme.typography.bodySmall)

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

        Button(onClick = { discoverModels() }, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
            Text(if (isBusy) "Discovering…" else "Discover models")
        }

        if (discoveredModels.isNotEmpty()) {
            Text("Choose a model", style = MaterialTheme.typography.titleMedium)
            OutlinedTextField(
                value = modelSearchQuery,
                onValueChange = { modelSearchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Search models") },
                placeholder = { Text("Search name or model ID") },
                singleLine = true,
                enabled = !isBusy
            )
            Box {
                OutlinedButton(onClick = { priceMenuExpanded = true }, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                    Text("Price filter: ${priceFilter.label}")
                }
                DropdownMenu(expanded = priceMenuExpanded, onDismissRequest = { priceMenuExpanded = false }) {
                    AiModelPriceFilter.values().forEach { filter ->
                        DropdownMenuItem(
                            text = { Text(filter.label) },
                            onClick = { priceFilter = filter; priceMenuExpanded = false },
                            enabled = !isBusy
                        )
                    }
                }
            }
            Box {
                OutlinedButton(onClick = { contextMenuExpanded = true }, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
                    Text("Token filter: ${contextFilter.label}")
                }
                DropdownMenu(expanded = contextMenuExpanded, onDismissRequest = { contextMenuExpanded = false }) {
                    AiModelContextFilter.values().forEach { filter ->
                        DropdownMenuItem(
                            text = { Text(filter.label) },
                            onClick = { contextFilter = filter; contextMenuExpanded = false },
                            enabled = !isBusy
                        )
                    }
                }
            }
            Text(
                "Price and context filters use provider metadata when available. Unknown means the provider did not supply that information.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                "${filteredModels.size} matching models" + if (filteredModels.size > MAX_VISIBLE_MODELS) " · showing first $MAX_VISIBLE_MODELS" else "",
                style = MaterialTheme.typography.labelMedium
            )
            if (filteredModels.isEmpty()) {
                Text("No models match these filters. Change a filter or search term.", style = MaterialTheme.typography.bodySmall)
            } else {
                filteredModels.take(MAX_VISIBLE_MODELS).forEach { model ->
                    OutlinedButton(
                        onClick = {
                            selectedModelId = model.id
                            statusMessage = "Selected ${model.displayName}. Save & connect to use this model."
                            isStatusError = false
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = !isBusy
                    ) {
                        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                            Text(
                                (if (selectedModelId == model.id) "✓  " else "") + model.displayName,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = if (selectedModelId == model.id) FontWeight.SemiBold else FontWeight.Normal
                            )
                            Text(model.id, style = MaterialTheme.typography.labelSmall)
                            Text(
                                "Context: ${model.contextWindowTokens?.let(::formatTokenCount) ?: "not supplied"} · Price: ${model.pricingTier.label()}",
                                style = MaterialTheme.typography.bodySmall
                            )
                            if (model.inputPriceUsdPerMillionTokens != null && model.outputPriceUsdPerMillionTokens != null) {
                                Text(
                                    String.format(
                                        Locale.US,
                                        "Approx. USD %.4f input / USD %.4f output per 1M tokens",
                                        model.inputPriceUsdPerMillionTokens,
                                        model.outputPriceUsdPerMillionTokens
                                    ),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }

        Button(onClick = { saveAndConnect() }, enabled = !isBusy, modifier = Modifier.fillMaxWidth()) {
            Text(if (isBusy) "Connecting…" else "Save & connect")
        }
        if (hasSavedKey) {
            OutlinedButton(onClick = { confirmRemoveKey = true }, enabled = !isBusy) {
                Text("Remove saved API key")
            }
        }
        Surface(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceVariant) {
            Text(
                "AstraCode keeps API keys encrypted with Android Keystore and excludes them from backup. Save & connect sends a short test prompt and may incur provider charges; workspace files are not included. Price and context filters depend on provider metadata, so some entries may show Unknown.",
                modifier = Modifier.padding(16.dp),
                style = MaterialTheme.typography.bodySmall
            )
        }
        statusMessage?.let { message ->
            Text(message, style = MaterialTheme.typography.bodyMedium, color = if (isStatusError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
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
            dismissButton = { TextButton(onClick = { confirmRemoveKey = false }) { Text("Cancel") } }
        )
    }
}

private const val MAX_VISIBLE_MODELS = 30
private const val HIGH_CONTEXT_TOKEN_THRESHOLD = 128_000
private const val LOW_CONTEXT_TOKEN_THRESHOLD = 32_000
private val STANDARD_CONTEXT_TOKEN_RANGE = 32_000 until 128_000

private fun AiModelPricingTier.label(): String = when (this) {
    AiModelPricingTier.FREE -> "Free"
    AiModelPricingTier.PAID -> "Paid"
    AiModelPricingTier.UNKNOWN -> "Unknown"
}

private fun formatTokenCount(tokens: Int): String = when {
    tokens >= 1_000_000 -> String.format(Locale.US, "%.1fM", tokens / 1_000_000.0)
    tokens >= 1_000 -> String.format(Locale.US, "%.0fK", tokens / 1_000.0)
    else -> tokens.toString()
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

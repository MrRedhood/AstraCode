package com.mrredhood.astracode

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun AiChatScreen(
    onOpenAiSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { AiProviderSettingsRepository(context) }
    val configuredProvider = remember(repository) { repository.selectedProviderId() }
    val configuration = remember(configuredProvider) { repository.loadConfiguration(configuredProvider) }
    val hasKey = remember(configuredProvider) {
        runCatching { repository.hasApiKey(configuredProvider) }.getOrDefault(false)
    }
    val isConfigured = configuration.modelId.isNotBlank() && hasKey
    val messages = remember { mutableStateListOf<AiChatMessage>() }
    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    var prompt by remember { mutableStateOf("") }
    var isSending by remember { mutableStateOf(false) }
    var activeRequest by remember { mutableStateOf<Job?>(null) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var statusIsError by remember { mutableStateOf(false) }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) listState.animateScrollToItem(messages.lastIndex)
    }

    fun sendMessage() {
        val userText = prompt.trim()
        if (userText.isEmpty() || isSending) return
        if (!isConfigured) {
            statusMessage = "Set a provider, model ID and API key in AI & Models before chatting."
            statusIsError = true
            onOpenAiSettings()
            return
        }

        // Bound retained history for lower-memory Android devices and provider context limits.
        while (messages.size >= MAX_HISTORY_MESSAGES - 1) messages.removeAt(0)
        messages.add(AiChatMessage(AiMessageRole.USER, userText))
        prompt = ""
        statusMessage = "Sending to ${configuration.providerId.displayName}…"
        statusIsError = false

        val history = listOf(
            AiChatMessage(
                AiMessageRole.SYSTEM,
                "You are AstraCode, a cloud-based coding assistant. Give practical, accurate answers. " +
                    "Do not claim that files were changed, commands were run, or tests passed unless " +
                    "the application provides direct evidence of those actions."
            )
        ) + messages.toList().takeLast(MAX_HISTORY_MESSAGES - 1)

        val job = scope.launch(start = CoroutineStart.LAZY) {
            isSending = true
            try {
                val result = withContext(Dispatchers.IO) {
                    val capabilities = mapOf(
                        configuration.modelId to AiModelCapabilities(
                            contextWindowTokens = CLIENT_CONTEXT_WINDOW_TOKENS,
                            maxOutputTokens = CLIENT_MAX_OUTPUT_TOKENS
                        )
                    )
                    val provider = CloudAiProviderFactory(repository).create(
                        id = configuration.providerId,
                        baseUrlOverride = configuration.baseUrlOverride.trim().takeIf { it.isNotEmpty() },
                        modelCapabilities = capabilities
                    )
                    provider.generate(
                        modelId = configuration.modelId,
                        request = AiGenerationRequest(
                            messages = history,
                            maxOutputTokens = CHAT_MAX_OUTPUT_TOKENS
                        )
                    )
                }
                when (result) {
                    is AiProviderResult.Success -> {
                        val answer = result.response.text.take(MAX_RESPONSE_CHARS)
                        val visibleAnswer = if (result.response.text.length > MAX_RESPONSE_CHARS) {
                            "$answer\n\n[Response clipped to keep the mobile chat responsive.]"
                        } else {
                            answer
                        }
                        messages.add(AiChatMessage(AiMessageRole.ASSISTANT, visibleAnswer))
                        statusMessage = "Answered by ${configuration.providerId.displayName} · ${result.response.modelId}"
                        statusIsError = false
                    }
                    is AiProviderResult.Failure -> {
                        statusMessage = result.failure.detail
                        statusIsError = true
                    }
                }
            } catch (cancelled: CancellationException) {
                statusMessage = "Generation cancelled."
                statusIsError = false
                throw cancelled
            } catch (error: Exception) {
                statusMessage = "The request could not be completed. Check AI & Models and try again."
                statusIsError = true
            } finally {
                isSending = false
                activeRequest = null
            }
        }
        activeRequest = job
        job.start()
    }

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    if (isConfigured) configuration.modelId else "Cloud AI not configured",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    if (isConfigured) configuration.providerId.displayName else "Configure a provider to start",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            OutlinedButton(onClick = onOpenAiSettings, enabled = !isSending) {
                Text("AI settings")
            }
            if (messages.isNotEmpty() && !isSending) {
                OutlinedButton(onClick = {
                    messages.clear()
                    statusMessage = null
                }) { Text("Clear") }
            }
        }

        if (!isConfigured) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceVariant
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Connect a cloud model", style = MaterialTheme.typography.titleSmall)
                    Text(
                        "Choose a provider, discover or enter a model ID, save its API key, and run Save & test. Chat sends only the conversation text you type; workspace files are not attached automatically.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Button(onClick = onOpenAiSettings) { Text("Configure AI provider") }
                }
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            state = listState,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (messages.isEmpty()) {
                item {
                    Text(
                        "Ask a coding question to begin. Requests go to the provider and model configured in More → AI & Models.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            itemsIndexed(messages.toList(), key = { index, message -> "$index-${message.role}" }) { _, message ->
                ChatMessageBubble(message)
            }
        }

        statusMessage?.let { status ->
            Text(
                status,
                style = MaterialTheme.typography.bodySmall,
                color = if (statusIsError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedTextField(
                value = prompt,
                onValueChange = { if (it.length <= MAX_PROMPT_CHARS) prompt = it },
                modifier = Modifier.weight(1f),
                label = { Text("Message AstraCode") },
                placeholder = { Text("Ask about code…") },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                minLines = 1,
                maxLines = 4,
                enabled = isConfigured && !isSending
            )
            if (isSending) {
                OutlinedButton(onClick = { activeRequest?.cancel() }) {
                    Text("Cancel")
                }
            } else {
                Button(onClick = ::sendMessage, enabled = isConfigured && prompt.isNotBlank()) {
                    Text("Send")
                }
            }
        }
    }
}

@Composable
private fun ChatMessageBubble(message: AiChatMessage) {
    val isUser = message.role == AiMessageRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(modifier = Modifier.fillMaxWidth(0.94f)) {
            Column(
                modifier = Modifier.padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    if (isUser) "You" else "AstraCode AI",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.SemiBold
                )
                Text(message.content, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

private const val MAX_HISTORY_MESSAGES = 60
private const val MAX_PROMPT_CHARS = 20_000
private const val MAX_RESPONSE_CHARS = 60_000
private const val CLIENT_CONTEXT_WINDOW_TOKENS = 32_768
private const val CLIENT_MAX_OUTPUT_TOKENS = 2_048
private const val CHAT_MAX_OUTPUT_TOKENS = 512

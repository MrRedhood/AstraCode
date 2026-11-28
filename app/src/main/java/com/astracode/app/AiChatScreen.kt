@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package com.astracode.app

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubbleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Movie
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astracode.connectors.AiCapabilitySupport
import com.astracode.connectors.AiConversationMessage
import com.astracode.connectors.AiConversationRole
import com.astracode.connectors.AiConversationStore
import com.astracode.connectors.AiConversationThread
import com.astracode.connectors.AiRequestCancelledException
import com.astracode.connectors.AiModelCapabilities
import com.astracode.connectors.AiProvider
import com.astracode.connectors.AiSecretRedactor
import com.astracode.connectors.AiStreamEvent
import com.astracode.connectors.AiTokenUsage
import com.astracode.connectors.WorkspaceAiAssistant
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Date
import java.util.Locale
import java.util.Calendar

@Composable
internal fun AiChatScreen(
    activeWorkspaceId: String,
    initialPrompt: String = "",
    assistant: WorkspaceAiAssistant,
    conversationStore: AiConversationStore,
    preferences: android.content.SharedPreferences,
    onOpenWorkspacePlanner: () -> Unit,
    onClose: () -> Unit = {},
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val listState = rememberLazyListState()

    DisposableEffect(assistant) {
        onDispose { assistant.cancelActiveRequests() }
    }

    var selectedProvider by remember(activeWorkspaceId) {
        mutableStateOf(AiProvider.fromId(preferences.getString("provider", "openai")))
    }
    var selectedModel by remember(activeWorkspaceId) {
        mutableStateOf(preferences.getString("model_" + selectedProvider.id, "").orEmpty())
    }
    var customBaseUrl by remember(activeWorkspaceId) {
        mutableStateOf(preferences.getString("base_url_" + selectedProvider.id, "").orEmpty())
    }
    var keyRevision by remember { mutableStateOf(0) }
    var apiKeyInput by remember { mutableStateOf("") }
    var showKeyDialog by remember { mutableStateOf(false) }
    var hasApiKey by remember(selectedProvider, keyRevision) {
        mutableStateOf(runCatching { assistant.hasApiKey(selectedProvider) }.getOrDefault(false))
    }
    val connectedProviders = remember(assistant, keyRevision) {
        AiProvider.entries.filter { provider ->
            runCatching { assistant.hasApiKey(provider) }.getOrDefault(false)
        }.toSet()
    }
    var showModelSheet by remember { mutableStateOf(false) }
    var modelSearch by remember { mutableStateOf("") }
    var modelCatalog by remember { mutableStateOf(emptyList<AiModelCapabilities>()) }
    var loadingModels by remember { mutableStateOf(false) }
    var modelError by remember { mutableStateOf<String?>(null) }
    var showAttachmentTray by remember { mutableStateOf(false) }
    var showPromptMenu by remember { mutableStateOf(false) }
    var showRenameDialog by remember { mutableStateOf(false) }
    var renameValue by remember { mutableStateOf("") }
    var renameTarget by remember { mutableStateOf<AiConversationThread?>(null) }
    var deleteTarget by remember { mutableStateOf<AiConversationThread?>(null) }

    var currentThread by remember(activeWorkspaceId) { mutableStateOf<AiConversationThread?>(null) }
    var messages by remember(activeWorkspaceId) { mutableStateOf(emptyList<AiConversationMessage>()) }
    var history by remember(activeWorkspaceId) { mutableStateOf(emptyList<AiConversationThread>()) }
    var historySearch by remember(activeWorkspaceId) { mutableStateOf("") }
    var historyLoading by remember { mutableStateOf(false) }
    var draft by remember(activeWorkspaceId) { mutableStateOf(initialPrompt) }
    var chatError by remember { mutableStateOf<String?>(null) }
    var isSending by remember { mutableStateOf(false) }
    var streamedText by remember { mutableStateOf("") }
    var activityStatus by remember { mutableStateOf("") }
    var activityEvents by remember { mutableStateOf(emptyList<String>()) }
    var usage by remember { mutableStateOf<AiTokenUsage?>(null) }
    var attachmentNotice by remember {
        mutableStateOf("Text documents up to 24 KiB can be attached. Image, video and audio input are not available in the current provider request bridge.")
    }

    fun persistProvider(provider: AiProvider) {
        selectedProvider = provider
        selectedModel = preferences.getString("model_" + provider.id, "").orEmpty()
        customBaseUrl = preferences.getString("base_url_" + provider.id, "").orEmpty()
        preferences.edit().putString("provider", provider.id).apply()
        modelCatalog = emptyList()
        modelError = null
        keyRevision++
    }

    suspend fun openThread(thread: AiConversationThread) {
        val loaded = withContext(Dispatchers.IO) { conversationStore.loadMessages(thread.id) }
        currentThread = thread
        messages = loaded
        streamedText = ""
        chatError = null
        preferences.edit().putString("active_ai_thread_" + activeWorkspaceId, thread.id).apply()
        drawerState.close()
    }

    fun refreshHistory(query: String = historySearch) {
        historyLoading = true
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { conversationStore.listThreads(activeWorkspaceId, query) }
            }
            if (query == historySearch) {
                result.onSuccess { history = it }
                    .onFailure { chatError = it.message ?: "Conversation history could not be loaded." }
                historyLoading = false
            }
        }
    }

    fun createNewConversation() {
        currentThread = null
        messages = emptyList()
        draft = ""
        streamedText = ""
        usage = null
        chatError = null
        activityStatus = ""
        activityEvents = emptyList()
        preferences.edit().remove("active_ai_thread_" + activeWorkspaceId).apply()
        scope.launch { drawerState.close() }
    }

    fun selectModel(model: AiModelCapabilities) {
        if (selectedProvider != model.provider) {
            customBaseUrl = preferences.getString("base_url_" + model.provider.id, "").orEmpty()
            keyRevision++
        }
        selectedProvider = model.provider
        selectedModel = model.modelId
        preferences.edit()
            .putString("provider", model.provider.id)
            .putString("model_" + model.provider.id, model.modelId)
            .apply()
        showModelSheet = false
    }

    fun loadModels(provider: AiProvider) {
        if (!runCatching { assistant.hasApiKey(provider) }.getOrDefault(false)) {
            modelError = "Add an API key for " + provider.displayName + " before loading its model catalogue."
            showKeyDialog = true
            return
        }
        loadingModels = true
        modelError = null
        scope.launch {
            val result = runCatching {
                withContext(Dispatchers.IO) { assistant.fetchModelCatalog(provider, customBaseUrl) }
            }
            if (selectedProvider == provider) {
                result.onSuccess { modelCatalog = it }
                    .onFailure { modelError = it.message ?: "Could not load models from this provider." }
                loadingModels = false
            }
        }
    }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri != null) {
            scope.launch {
                val result = runCatching { withContext(Dispatchers.IO) { readTextDocument(context, uri) } }
                result.onSuccess { attachment ->
                    draft = (draft.trimEnd() + "\n\n[Attached text: " + attachment.first + "]\n" + attachment.second).trimStart()
                    chatError = null
                }.onFailure { chatError = it.message ?: "The selected document could not be read." }
            }
        }
    }

    LaunchedEffect(activeWorkspaceId) {
        val rememberedId = preferences.getString("active_ai_thread_" + activeWorkspaceId, null)
        if (!rememberedId.isNullOrBlank()) {
            val existing = withContext(Dispatchers.IO) {
                conversationStore.getThread(rememberedId)?.takeIf { it.workspaceId == activeWorkspaceId }
            }
            if (existing != null) openThread(existing)
        }
    }

    LaunchedEffect(historySearch, activeWorkspaceId, drawerState.currentValue) {
        if (drawerState.isOpen) {
            delay(180)
            refreshHistory(historySearch)
        }
    }

    LaunchedEffect(showModelSheet, selectedProvider, keyRevision, loadingModels) {
        if (
            showModelSheet &&
            !loadingModels &&
            modelCatalog.none { it.provider == selectedProvider } &&
            hasApiKey
        ) {
            loadModels(selectedProvider)
        }
    }

    LaunchedEffect(messages.size, streamedText.length / 128, isSending) {
        val count = messages.size + if (streamedText.isNotBlank() || isSending) 1 else 0
        if (count > 0) listState.animateScrollToItem(count - 1)
    }

    suspend fun sendCurrentMessage() {
        val raw = draft.trim()
        if (raw.isBlank() || isSending) return
        if (raw.length > 30_000) {
            chatError = "Keep a message and its attachments under 30,000 characters."
            return
        }
        if (!hasApiKey) {
            chatError = "Connect " + selectedProvider.displayName + " before sending a message."
            showKeyDialog = true
            return
        }
        if (selectedModel.isBlank()) {
            chatError = "Choose a model from the selected provider before sending."
            showModelSheet = true
            return
        }
        val safePrompt = AiSecretRedactor.redact(raw).content
        val provider = selectedProvider
        val model = selectedModel
        val baseUrl = customBaseUrl
        val workspaceId = activeWorkspaceId
        isSending = true
        chatError = null
        streamedText = ""
        activityStatus = "Preparing request…"
        activityEvents = emptyList()
        usage = null
        draft = ""
        val startedAt = System.currentTimeMillis()

        try {
            val thread = currentThread ?: withContext(Dispatchers.IO) {
                conversationStore.createThread(workspaceId, safePrompt.take(80))
            }
            currentThread = thread
            preferences.edit().putString("active_ai_thread_" + workspaceId, thread.id).apply()
            val historyContext = withContext(Dispatchers.IO) { conversationStore.buildHistoryContext(thread.id) }
            val userMessage = withContext(Dispatchers.IO) {
                conversationStore.appendMessage(
                    threadId = thread.id,
                    role = AiConversationRole.USER,
                    content = safePrompt,
                    providerId = provider.id,
                    modelId = model,
                    estimatedInputTokens = safePrompt.toByteArray(Charsets.UTF_8).size.toLong() / 4L,
                )
            }
            messages = messages + userMessage

            val channel = Channel<AiStreamEvent>(Channel.UNLIMITED)
            assistant.beginOperation()
            val request = scope.async(Dispatchers.IO) {
                try {
                    assistant.sendChatMessage(
                        provider = provider,
                        model = model,
                        customBaseUrl = baseUrl,
                        historyContext = historyContext,
                        userMessage = safePrompt,
                    ) { event -> channel.trySend(event) }
                } finally {
                    channel.close()
                }
            }
            val collector = scope.launch {
                for (event in channel) {
                    when (event) {
                        is AiStreamEvent.TextDelta -> streamedText += event.text
                        is AiStreamEvent.Status -> activityStatus = event.message
                        is AiStreamEvent.ToolStarted -> activityEvents = activityEvents + (event.name + " · running")
                        is AiStreamEvent.ToolFinished -> activityEvents = activityEvents + (
                            event.name + if (event.succeeded) " · completed" else " · failed"
                        )
                        is AiStreamEvent.Usage -> usage = event.usage
                    }
                }
            }
            val response = request.await()
            collector.join()
            val assistantMessage = withContext(Dispatchers.IO) {
                conversationStore.appendMessage(
                    threadId = thread.id,
                    role = AiConversationRole.ASSISTANT,
                    content = response,
                    providerId = provider.id,
                    modelId = model,
                    durationMillis = (System.currentTimeMillis() - startedAt).coerceAtLeast(0L),
                    estimatedInputTokens = safePrompt.toByteArray(Charsets.UTF_8).size.toLong() / 4L,
                )
            }
            messages = messages + assistantMessage
            currentThread = withContext(Dispatchers.IO) { conversationStore.getThread(thread.id) } ?: thread
            streamedText = ""
            activityStatus = "Response complete"
            refreshHistory()
        } catch (cancelled: AiRequestCancelledException) {
            chatError = cancelled.message ?: "The AI request was cancelled."
            activityStatus = "Request cancelled"
        } catch (error: Exception) {
            chatError = error.message ?: "The AI request did not complete."
            if (streamedText.isBlank()) activityStatus = "Request failed"
        } finally {
            isSending = false
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = true,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.fillMaxHeight().fillMaxWidth(0.88f),
                drawerContainerColor = MaterialTheme.colorScheme.surface,
                drawerContentColor = MaterialTheme.colorScheme.onSurface,
                windowInsets = WindowInsets(0, 0, 0, 0),
            ) {
                HistoryDrawer(
                    threads = history,
                    currentThreadId = currentThread?.id,
                    search = historySearch,
                    loading = historyLoading,
                    onSearch = { historySearch = it },
                    onNew = { createNewConversation() },
                    onClose = { scope.launch { drawerState.close() } },
                    onOpen = { thread -> scope.launch { openThread(thread) } },
                    onRename = { thread ->
                        renameTarget = thread
                        renameValue = thread.title
                        showRenameDialog = true
                    },
                    onDelete = { deleteTarget = it },
                    onWorkspacePlan = onOpenWorkspacePlanner,
                )
            }
        },
    ) {
        Column(
            modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).statusBarsPadding().navigationBarsPadding().imePadding(),
        ) {
            ChatTopBar(
                providerName = selectedProvider.displayName,
                modelName = modelCatalog.firstOrNull {
                    it.provider == selectedProvider && it.modelId == selectedModel
                }?.displayName ?: selectedModel.ifBlank { "Choose model" },
                onMenu = {
                    scope.launch {
                        historySearch = ""
                        refreshHistory("")
                        drawerState.open()
                    }
                },
                onSelectModel = { showModelSheet = true },
                onSettings = onOpenWorkspacePlanner,
                onProviderSettings = { showKeyDialog = true },
                onClose = onClose,
            )
            Divider(color = MaterialTheme.colorScheme.outlineVariant, thickness = 0.7.dp)
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(7.dp),
                    ) {
                        Box(Modifier.size(6.dp).clip(CircleShape).background(if (isSending) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline))
                        Text(
                            currentThread?.title ?: "New conversation",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
            LazyColumn(
                state = listState,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                items(messages, key = { it.id }) { message ->
                    ChatMessageItem(
                        message = message,
                        providerName = message.providerId?.let { AiProvider.fromId(it).displayName } ?: "AstraCode",
                        onCopy = {
                            clipboard.setText(AnnotatedString(message.content))
                            chatError = "Message copied."
                        },
                        onShare = { shareText(context, message.content) },
                        onEdit = {
                            val thread = currentThread ?: return@ChatMessageItem
                            scope.launch {
                                withContext(Dispatchers.IO) { conversationStore.removeMessagesFrom(thread.id, message.id) }
                                messages = withContext(Dispatchers.IO) { conversationStore.loadMessages(thread.id) }
                                currentThread = withContext(Dispatchers.IO) { conversationStore.getThread(thread.id) }
                                draft = message.content
                                refreshHistory()
                                chatError = "Edit the message and send it again. Later messages were removed."
                            }
                        },
                        onDelete = {
                            val thread = currentThread ?: return@ChatMessageItem
                            scope.launch {
                                runCatching {
                                    withContext(Dispatchers.IO) { conversationStore.removeMessagesFrom(thread.id, message.id) }
                                }.onSuccess {
                                    messages = withContext(Dispatchers.IO) { conversationStore.loadMessages(thread.id) }
                                    currentThread = withContext(Dispatchers.IO) { conversationStore.getThread(thread.id) }
                                    refreshHistory()
                                    chatError = "This message and later turns were deleted."
                                }.onFailure { chatError = it.message ?: "Message could not be deleted." }
                            }
                        },
                    )
                }
                if (isSending && streamedText.isBlank()) {
                    item(key = "thinking-indicator") {
                        ThinkingRow(status = activityStatus.ifBlank { "Thinking…" })
                    }
                }
                if (streamedText.isNotBlank()) {
                    item(key = "streaming-response") {
                        StreamingMessage(providerName = selectedProvider.displayName, modelName = selectedModel, text = streamedText)
                    }
                }
                if (activityEvents.isNotEmpty() || activityStatus.isNotBlank() || usage != null) {
                    item(key = "activity-metrics") { ActivityTray(activityStatus, activityEvents, usage) }
                }
                if (chatError != null) {
                    item(key = "chat-status") {
                        Text(chatError.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp))
                    }
                }
            }

            if (showAttachmentTray) {
                AttachmentTray(
                    notice = attachmentNotice,
                    onClose = { showAttachmentTray = false },
                    onDocuments = {
                        showAttachmentTray = false
                        documentPicker.launch(arrayOf("text/*", "application/json", "application/xml", "application/yaml", "application/x-yaml"))
                    },
                    onImages = { attachmentNotice = "Image input is not supported by the current provider request bridge." },
                    onVideo = { attachmentNotice = "Video input is not supported by the current provider request bridge." },
                    onAudio = { attachmentNotice = "Audio input is not supported by the current provider request bridge." },
                    onFiles = {
                        showAttachmentTray = false
                        documentPicker.launch(arrayOf("text/*", "application/json", "application/xml"))
                    },
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(horizontal = 12.dp, vertical = 5.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                QuickChip(label = "Media & docs", icon = Icons.Filled.Add) { showAttachmentTray = !showAttachmentTray }
                QuickChip(label = "@ Mention tools/files") { draft = (draft.trimEnd() + " @").trimStart() }
                QuickChip(label = "/ Commands") { draft = (draft.trimEnd() + " /").trimStart() }
            }
            ChatComposer(
                value = draft,
                enabled = !isSending,
                onValueChange = { draft = it },
                onAttach = { showAttachmentTray = !showAttachmentTray },
                onSend = { scope.launch { sendCurrentMessage() } },
                onStop = { assistant.cancelActiveRequests() },
                sending = isSending,
            )
            ChatBottomNavigation(
                onChat = { scope.launch { drawerState.close() } },
                onModels = { showModelSheet = true },
                onPrompts = { showPromptMenu = true },
                onHistory = { scope.launch { historySearch = ""; refreshHistory(""); drawerState.open() } },
            )
        }
    }

    if (showPromptMenu) {
        AlertDialog(
            onDismissRequest = { showPromptMenu = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            title = { Text("Prompt library", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(
                        "Explain this code clearly.",
                        "Review this code for bugs and security issues.",
                        "Find the root cause and suggest a minimal fix.",
                        "Generate unit tests for this code.",
                        "Explain this build error and provide exact repair steps.",
                    ).forEach { template ->
                        Surface(onClick = { draft = template; showPromptMenu = false }, color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp)) {
                            Text(template, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.fillMaxWidth().padding(12.dp))
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showPromptMenu = false }) { Text("Close") } },
        )
    }

    if (showModelSheet) {
        ModelPickerSheet(
            selectedProvider = selectedProvider,
            selectedModel = selectedModel,
            search = modelSearch,
            models = modelCatalog,
            connectedProviders = connectedProviders,
            loading = loadingModels,
            error = modelError,
            onDismiss = { showModelSheet = false },
            onSearch = { modelSearch = it },
            onSelectProvider = { provider ->
                if (provider != selectedProvider) {
                    persistProvider(provider)
                    if (provider !in connectedProviders) {
                        modelError = "Connect " + provider.displayName + " to load its model list."
                        showKeyDialog = true
                    }
                }
            },
            onSelect = ::selectModel,
            onRefresh = { loadModels(selectedProvider) },
            onManualModel = { id ->
                selectedModel = id
                preferences.edit().putString("model_" + selectedProvider.id, id).apply()
                showModelSheet = false
            },
            onConfigureKey = { showKeyDialog = true },
        )
    }

    if (showKeyDialog) {
        AlertDialog(
            onDismissRequest = { showKeyDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            title = { Text("Provider connection", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(selectedProvider.displayName, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (selectedProvider.customEndpoint) {
                        OutlinedTextField(
                            value = customBaseUrl,
                            onValueChange = { customBaseUrl = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            label = { Text("HTTPS base URL") },
                            colors = darkChatFieldColors(),
                        )
                    }
                    OutlinedTextField(
                        value = apiKeyInput,
                        onValueChange = { apiKeyInput = it },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        label = { Text(if (hasApiKey) "Replace API key (optional)" else selectedProvider.keyLabel) },
                        visualTransformation = PasswordVisualTransformation(),
                        colors = darkChatFieldColors(),
                    )
                    Text(
                        if (hasApiKey) "An API key is stored for this provider." else "No API key is stored for this provider.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val provider = selectedProvider
                            val key = apiKeyInput
                            val url = customBaseUrl
                            val result = runCatching {
                                withContext(Dispatchers.IO) {
                                    if (key.isNotBlank()) assistant.saveApiKey(provider, key)
                                    if (provider.customEndpoint && url.isNotBlank()) {
                                        require(url.startsWith("https://", ignoreCase = true)) { "Custom API endpoints must use HTTPS." }
                                    }
                                    if (url.isNotBlank()) preferences.edit().putString("base_url_" + provider.id, url).apply()
                                    assistant.hasApiKey(provider)
                                }
                            }
                            result.onSuccess {
                                hasApiKey = it
                                apiKeyInput = ""
                                keyRevision++
                                chatError = "Provider settings saved."
                                showKeyDialog = false
                                showModelSheet = true
                            }.onFailure { chatError = it.message ?: "Provider settings could not be saved." }
                        }
                    },
                    enabled = apiKeyInput.isNotBlank() || (selectedProvider.customEndpoint && customBaseUrl.isNotBlank()),
                ) { Text("Save") }
            },
            dismissButton = {
                Row {
                    if (hasApiKey) {
                        TextButton(onClick = {
                            scope.launch {
                                runCatching { withContext(Dispatchers.IO) { assistant.removeApiKey(selectedProvider) } }
                                    .onSuccess {
                                        hasApiKey = false
                                        keyRevision++
                                        modelCatalog = emptyList()
                                    }
                                    .onFailure { chatError = it.message ?: "API key could not be removed." }
                            }
                        }) { Text("Remove key") }
                    }
                    TextButton(onClick = { showKeyDialog = false }) { Text("Cancel") }
                }
            },
        )
    }

    if (showRenameDialog && renameTarget != null) {
        AlertDialog(
            onDismissRequest = { showRenameDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            title = { Text("Rename conversation", color = MaterialTheme.colorScheme.onSurface) },
            text = {
                OutlinedTextField(
                    value = renameValue,
                    onValueChange = { renameValue = it.take(80) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = darkChatFieldColors(),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val target = renameTarget ?: return@TextButton
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { conversationStore.renameThread(target.id, renameValue) } }
                            .onSuccess {
                                currentThread = withContext(Dispatchers.IO) { conversationStore.getThread(target.id) }
                                refreshHistory("")
                                showRenameDialog = false
                            }.onFailure { chatError = it.message ?: "Conversation could not be renamed." }
                    }
                }, enabled = renameValue.isNotBlank()) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showRenameDialog = false }) { Text("Cancel") } },
        )
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            title = { Text("Delete conversation?", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("This permanently deletes the conversation and its stored messages.", color = MaterialTheme.colorScheme.onSurfaceVariant) },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        runCatching { withContext(Dispatchers.IO) { conversationStore.deleteThread(target.id) } }
                            .onSuccess {
                                if (currentThread?.id == target.id) createNewConversation()
                                refreshHistory("")
                                deleteTarget = null
                            }.onFailure { chatError = it.message ?: "Conversation could not be deleted." }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.onSurface) }
            },
            dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun ChatTopBar(
    providerName: String,
    modelName: String,
    onMenu: () -> Unit,
    onSelectModel: () -> Unit,
    onSettings: () -> Unit,
    onProviderSettings: () -> Unit,
    onClose: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(62.dp).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        IconButton(onClick = onMenu, modifier = Modifier.size(36.dp)) {
            Icon(Icons.Filled.Menu, contentDescription = "Open conversation history", tint = MaterialTheme.colorScheme.onSurface)
        }
        Column(
            modifier = Modifier.width(82.dp),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
                Text(
                    "Chat Stream",
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
            Text(
                providerName,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Surface(
            onClick = onSelectModel,
            color = MaterialTheme.colorScheme.surfaceContainerLow,
            shape = RoundedCornerShape(13.dp),
            modifier = Modifier.weight(1f).heightIn(min = 42.dp),
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Icon(
                    Icons.Filled.Psychology,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(17.dp),
                )
                Text(
                    modelName,
                    color = MaterialTheme.colorScheme.onSurface,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                Icon(
                    Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(16.dp),
                )
            }
        }
        IconButton(onClick = onSettings, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.Tune, contentDescription = "AI settings", tint = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = onProviderSettings, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Filled.Person, contentDescription = "Provider connection", tint = MaterialTheme.colorScheme.onSurface)
        }
        IconButton(onClick = onClose, modifier = Modifier.size(34.dp).testTag("chat-close-button")) {
            Icon(Icons.Filled.Close, contentDescription = "Close AI chat", tint = MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun HistoryDrawer(
    threads: List<AiConversationThread>,
    currentThreadId: String?,
    search: String,
    loading: Boolean,
    onSearch: (String) -> Unit,
    onNew: () -> Unit,
    onClose: () -> Unit,
    onOpen: (AiConversationThread) -> Unit,
    onRename: (AiConversationThread) -> Unit,
    onDelete: (AiConversationThread) -> Unit,
    onWorkspacePlan: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surface).statusBarsPadding().navigationBarsPadding()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(60.dp).padding(horizontal = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Box(Modifier.size(34.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(18.dp))
                }
            }
            Spacer(Modifier.width(9.dp))
            Text("Conversations", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            Button(
                onClick = onNew,
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.onSurface, contentColor = MaterialTheme.colorScheme.background),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 4.dp),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(4.dp))
                Text("New", fontSize = 12.sp)
            }
            IconButton(onClick = onClose, modifier = Modifier.size(38.dp)) {
                Icon(Icons.Filled.Close, contentDescription = "Close history", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Divider(color = MaterialTheme.colorScheme.outlineVariant)
        OutlinedTextField(
            value = search,
            onValueChange = onSearch,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 12.dp),
            singleLine = true,
            placeholder = { Text("Search chats & code…", color = MaterialTheme.colorScheme.outline) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant) },
            colors = darkChatFieldColors(),
        )
        if (loading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally).size(20.dp), color = MaterialTheme.colorScheme.onSurface, strokeWidth = 2.dp)
        }
        if (!loading && threads.isEmpty()) {
            Text("No conversations in this workspace yet.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(18.dp))
        }
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            val grouped = threads.groupBy { historyGroup(it.updatedAtEpochMillis) }
            val order = listOf("Today", "Yesterday", "Previous 7 Days", "Earlier")
            order.forEach { group ->
                val values = grouped[group].orEmpty()
                if (values.isNotEmpty()) {
                    item(key = "group-" + group) {
                        Text(group.uppercase(Locale.getDefault()), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp, modifier = Modifier.padding(start = 10.dp, top = 8.dp, bottom = 4.dp))
                    }
                    items(values, key = { it.id }) { thread ->
                        var menuExpanded by remember(thread.id) { mutableStateOf(false) }
                        Row(
                            modifier = Modifier.fillMaxWidth()
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (thread.id == currentThreadId) MaterialTheme.colorScheme.surfaceContainerHigh else Color.Transparent)
                                .combinedClickable(onClick = { onOpen(thread) }, onLongClick = { menuExpanded = true })
                                .padding(start = 10.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            if (thread.id == currentThreadId) {
                                Box(Modifier.size(7.dp).clip(CircleShape).background(MaterialTheme.colorScheme.onSurface))
                            } else {
                                Icon(Icons.Filled.ChatBubbleOutline, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                            }
                            Spacer(Modifier.width(10.dp))
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(thread.title, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(thread.messageCount.toString() + " messages · " + formatTime(thread.updatedAtEpochMillis), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                            Box {
                                IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(34.dp)) {
                                    Icon(Icons.Filled.MoreVert, contentDescription = "Conversation options", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                                }
                                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                                    DropdownMenuItem(text = { Text("Rename") }, onClick = { menuExpanded = false; onRename(thread) })
                                    DropdownMenuItem(text = { Text("Delete") }, onClick = { menuExpanded = false; onDelete(thread) })
                                }
                            }
                        }
                    }
                }
            }
        }
        Divider(color = MaterialTheme.colorScheme.outlineVariant)
        Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Tune, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(8.dp))
                Text("Provider settings are available from the model selector.", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            }
            Surface(onClick = onWorkspacePlan, color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Code, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(17.dp))
                    Spacer(Modifier.width(9.dp))
                    Text("Workspace change plan", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}

@Composable
private fun ChatMessageItem(
    message: AiConversationMessage,
    providerName: String,
    onCopy: () -> Unit,
    onShare: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember(message.id) { mutableStateOf(false) }
    val isUser = message.role == AiConversationRole.USER
    if (isUser) {
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("You  ·  " + formatTime(message.createdAtEpochMillis), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            Box {
                Surface(
                    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 4.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh,
                    modifier = Modifier.fillMaxWidth(0.86f).combinedClickable(onClick = {}, onLongClick = { menuExpanded = true }),
                ) {
                    Text(message.content, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 14.dp, vertical = 11.dp))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(text = { Text("Edit") }, leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) }, onClick = { menuExpanded = false; onEdit() })
                    DropdownMenuItem(text = { Text("Copy") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) }, onClick = { menuExpanded = false; onCopy() })
                    DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) }, onClick = { menuExpanded = false; onShare() })
                    DropdownMenuItem(text = { Text("Delete") }, leadingIcon = { Icon(Icons.Filled.DeleteOutline, contentDescription = null) }, onClick = { menuExpanded = false; onDelete() })
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                message.estimatedInputTokens?.let { Text("~" + it + " tokens", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.labelSmall) }
                Icon(Icons.Filled.Check, contentDescription = "Saved", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(14.dp))
            }
        }
    } else {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Box(Modifier.size(27.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                    }
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(providerName + " · AstraCode", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    Text(message.modelId.orEmpty(), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(34.dp)) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "Message options", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                        DropdownMenuItem(text = { Text("Copy message") }, leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) }, onClick = { menuExpanded = false; onCopy() })
                        DropdownMenuItem(text = { Text("Share") }, leadingIcon = { Icon(Icons.Filled.Share, contentDescription = null) }, onClick = { menuExpanded = false; onShare() })
                    }
                }
            }
            MessageBody(message.content)
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                message.durationMillis?.let { Text(String.format(Locale.getDefault(), "%.1f s", it / 1000.0), color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.labelSmall) }
                message.estimatedInputTokens?.let { Text("~" + it + " input tokens", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.labelSmall) }
            }
        }
    }
}

@Composable
private fun MessageBody(content: String) {
    val codeBlock = Regex("\\x60\\x60\\x60([A-Za-z0-9_+.-]*)\\s*\\n([\\s\\S]*?)\\x60\\x60\\x60")
    val matches = codeBlock.findAll(content).toList()
    if (matches.isEmpty()) {
        Text(content, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
        return
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        var cursor = 0
        matches.forEach { match ->
            if (match.range.first > cursor) {
                Text(content.substring(cursor, match.range.first).trim(), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
            }
            CodeMessageCard(language = match.groupValues[1].ifBlank { "code" }, code = match.groupValues[2].trimEnd())
            cursor = match.range.last + 1
        }
        if (cursor < content.length) Text(content.substring(cursor).trim(), color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CodeMessageCard(language: String, code: String) {
    val clipboard = LocalClipboardManager.current
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(15.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceContainerLow).padding(horizontal = 12.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Code, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(15.dp))
                Spacer(Modifier.width(7.dp))
                Text(language, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = { clipboard.setText(AnnotatedString(code)) }, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy code", tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(15.dp))
                }
            }
            Text(code, color = MaterialTheme.colorScheme.onSurface, fontFamily = FontFamily.Monospace, fontSize = 12.sp, lineHeight = 18.sp, modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()).padding(13.dp))
        }
    }
}

@Composable
private fun StreamingMessage(providerName: String, modelName: String, text: String) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(7.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                Box(Modifier.size(27.dp), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Psychology, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(16.dp))
                }
            }
            Text(providerName + " · " + modelName, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
        MessageBody(text)
    }
}

@Composable
private fun ThinkingRow(status: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(9.dp)) {
        CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(17.dp), strokeWidth = 2.dp)
        Text(status, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun ActivityTray(status: String, events: List<String>, usage: AiTokenUsage?) {
    Row(modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        if (status.isNotBlank()) ActivityPill(status, Icons.Filled.Psychology)
        events.takeLast(5).forEach { ActivityPill(it, Icons.Filled.CheckCircle) }
        usage?.totalTokens?.let { ActivityPill(it.toString() + " total tokens", Icons.Filled.Check) }
        usage?.estimatedCostUsd?.let { ActivityPill(String.format(Locale.US, "$%.5f", it), Icons.Filled.Tune) }
    }
}

@Composable
private fun ActivityPill(text: String, icon: ImageVector) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = CircleShape) {
        Row(modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(13.dp))
            Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, maxLines = 1)
        }
    }
}

@Composable
private fun AttachmentTray(
    notice: String,
    onClose: () -> Unit,
    onDocuments: () -> Unit,
    onImages: () -> Unit,
    onVideo: () -> Unit,
    onAudio: () -> Unit,
    onFiles: () -> Unit,
) {
    Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("CONTEXTUAL ATTACHMENTS", color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelSmall, modifier = Modifier.weight(1f))
                IconButton(onClick = onClose, modifier = Modifier.size(30.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close attachments", tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(17.dp))
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                AttachmentOption("Doc", Icons.Filled.Description, onDocuments, Modifier.weight(1f))
                AttachmentOption("Image", Icons.Filled.Image, onImages, Modifier.weight(1f), enabled = false)
                AttachmentOption("Video", Icons.Filled.Movie, onVideo, Modifier.weight(1f), enabled = false)
                AttachmentOption("Audio", Icons.Filled.Mic, onAudio, Modifier.weight(1f), enabled = false)
                AttachmentOption("Files", Icons.Filled.FolderOpen, onFiles, Modifier.weight(1f))
            }
            Text(notice, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun AttachmentOption(label: String, icon: ImageVector, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Surface(onClick = onClick, enabled = enabled, color = MaterialTheme.colorScheme.surfaceContainerHigh, contentColor = if (enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.outline, shape = RoundedCornerShape(12.dp), modifier = modifier.height(64.dp)) {
        Column(modifier = Modifier.fillMaxSize().padding(5.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(19.dp))
            Spacer(Modifier.height(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, fontSize = 10.sp)
        }
    }
}

@Composable
private fun QuickChip(label: String, icon: ImageVector? = null, onClick: () -> Unit) {
    Surface(onClick = onClick, color = MaterialTheme.colorScheme.surfaceContainerLow, shape = CircleShape) {
        Row(modifier = Modifier.padding(horizontal = 11.dp, vertical = 7.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            if (icon != null) Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(14.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ChatComposer(
    value: String,
    enabled: Boolean,
    onValueChange: (String) -> Unit,
    onAttach: () -> Unit,
    onSend: () -> Unit,
    onStop: () -> Unit,
    sending: Boolean,
) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(24.dp), border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 5.dp)) {
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = 7.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
            IconButton(onClick = onAttach, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Filled.Add, contentDescription = "Attach media and documents", tint = MaterialTheme.colorScheme.onSurface)
            }
            Box(modifier = Modifier.weight(1f).heightIn(min = 40.dp, max = 108.dp), contentAlignment = Alignment.CenterStart) {
                if (value.isBlank()) Text("Ask anything, @ files/tools, / commands…", color = MaterialTheme.colorScheme.outline, style = MaterialTheme.typography.bodySmall, maxLines = 2)
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = MaterialTheme.colorScheme.onSurface),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.onSurface),
                    maxLines = 4,
                )
            }
            if (sending) {
                IconButton(onClick = onStop, modifier = Modifier.size(42.dp)) {
                    Icon(Icons.Filled.Stop, contentDescription = "Stop response", tint = MaterialTheme.colorScheme.onSurface)
                }
            } else {
                Surface(onClick = onSend, enabled = value.isNotBlank(), shape = CircleShape, color = if (value.isNotBlank()) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh, modifier = Modifier.size(42.dp)) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Send message", tint = if (value.isNotBlank()) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.outline)
                    }
                }
            }
        }
    }
}

@Composable
private fun ChatBottomNavigation(onChat: () -> Unit, onModels: () -> Unit, onPrompts: () -> Unit, onHistory: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(horizontal = 8.dp, vertical = 3.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
        BottomNavItem("Chat", Icons.Filled.ChatBubbleOutline, true, onChat)
        BottomNavItem("Models", Icons.Filled.Psychology, false, onModels)
        BottomNavItem("Prompts", Icons.Filled.AutoAwesome, false, onPrompts)
        BottomNavItem("History", Icons.Filled.FolderOpen, false, onHistory)
    }
}

@Composable
private fun BottomNavItem(label: String, icon: ImageVector, selected: Boolean, onClick: () -> Unit) {
    Surface(onClick = onClick, color = Color.Transparent, shape = RoundedCornerShape(12.dp), modifier = Modifier.height(52.dp).width(76.dp)) {
        Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Icon(icon, contentDescription = null, tint = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(21.dp))
            Spacer(Modifier.height(2.dp))
            Text(label, color = if (selected) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
        }
    }
}

@Composable
private fun ModelPickerSheet(
    selectedProvider: AiProvider,
    selectedModel: String,
    search: String,
    models: List<AiModelCapabilities>,
    connectedProviders: Set<AiProvider>,
    loading: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSearch: (String) -> Unit,
    onSelectProvider: (AiProvider) -> Unit,
    onSelect: (AiModelCapabilities) -> Unit,
    onRefresh: () -> Unit,
    onManualModel: (String) -> Unit,
    onConfigureKey: () -> Unit,
) {
    var manualModel by remember(selectedProvider) { mutableStateOf(selectedModel) }
    var showManualModel by remember(selectedProvider) { mutableStateOf(false) }
    val providerConnected = selectedProvider in connectedProviders
    val filteredModels = models.filter { model ->
        model.provider == selectedProvider &&
            (search.isBlank() ||
                model.displayName.contains(search, true) ||
                model.modelId.contains(search, true) ||
                model.capabilitySummary().contains(search, true))
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 720.dp).padding(horizontal = 15.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceContainerHigh) {
                    Box(Modifier.size(36.dp), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Psychology, contentDescription = null, modifier = Modifier.size(19.dp))
                    }
                }
                Spacer(Modifier.width(9.dp))
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("AI model", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    Text("Choose a provider, then choose its model", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                }
                IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close model selector")
                }
            }

            Text("PROVIDER", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                AiProvider.entries.forEach { provider ->
                    val connected = provider in connectedProviders
                    FilterChip(
                        selected = selectedProvider == provider,
                        onClick = { onSelectProvider(provider) },
                        modifier = Modifier.testTag("ai-provider-${provider.id}"),
                        label = { Text(provider.displayName + if (connected) " ✓" else "") },
                    )
                }
            }

            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(13.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 9.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box(
                        Modifier.size(8.dp).clip(CircleShape)
                            .background(if (providerConnected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(
                            if (providerConnected) "Connected" else "Not connected",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Text(
                            selectedProvider.displayName,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                    TextButton(onClick = onConfigureKey) {
                        Text(if (providerConnected) "Manage" else "Connect")
                    }
                }
            }

            OutlinedTextField(
                value = search,
                onValueChange = onSearch,
                modifier = Modifier.fillMaxWidth().testTag("ai-model-search"),
                singleLine = true,
                placeholder = { Text("Search this provider's models") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                colors = darkChatFieldColors(),
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text("MODELS", color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall)
                    Text(
                        if (selectedModel.isBlank()) "No model selected" else "Current: " + selectedModel,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (loading) CircularProgressIndicator(modifier = Modifier.size(17.dp), strokeWidth = 2.dp)
                TextButton(onClick = onRefresh, enabled = providerConnected && !loading) {
                    Text(if (models.any { it.provider == selectedProvider }) "Refresh" else "Load models")
                }
            }

            when {
                !providerConnected -> {
                    Text(
                        "Connect this provider to load its model list. Your key is stored in the app's protected credential store.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                filteredModels.isEmpty() && !loading -> {
                    Text(
                        if (models.any { it.provider == selectedProvider }) {
                            "No models match this search. Try another name or refresh the list."
                        } else {
                            "No model list loaded yet. Tap Load models, or enter a model ID below."
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            if (error != null) {
                Text(error, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            LazyColumn(
                modifier = Modifier.fillMaxWidth().heightIn(max = 280.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                items(filteredModels, key = { it.provider.id + ":" + it.modelId }) { model ->
                    val selected = model.modelId == selectedModel && model.provider == selectedProvider
                    Surface(
                        onClick = { onSelect(model) },
                        color = if (selected) MaterialTheme.colorScheme.surfaceContainerHigh else MaterialTheme.colorScheme.surface,
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(
                            if (selected) 1.dp else 0.5.dp,
                            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(model.displayName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                                Text(
                                    model.modelId,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                                    model.contextWindowTokens?.let { ModelBadge(formatCount(it) + " context") }
                                    when (model.supportsStreaming) {
                                        AiCapabilitySupport.SUPPORTED -> ModelBadge("Streaming")
                                        AiCapabilitySupport.UNSUPPORTED -> ModelBadge("No streaming")
                                        else -> Unit
                                    }
                                    if (model.supportsToolCalling == AiCapabilitySupport.SUPPORTED) ModelBadge("Tools")
                                }
                            }
                            if (selected) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = "Selected model", tint = MaterialTheme.colorScheme.primary)
                            } else {
                                Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }

            TextButton(
                onClick = { showManualModel = !showManualModel },
                modifier = Modifier.fillMaxWidth().testTag("ai-manual-model-toggle"),
            ) {
                Text(if (showManualModel) "Hide custom model ID" else "Use a custom model ID")
            }
            if (showManualModel) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = manualModel,
                        onValueChange = { manualModel = it.take(256) },
                        modifier = Modifier.weight(1f).testTag("ai-manual-model-input"),
                        singleLine = true,
                        label = { Text("Model ID") },
                        placeholder = { Text("Paste the exact model ID") },
                        colors = darkChatFieldColors(),
                    )
                    Button(
                        onClick = { onManualModel(manualModel.trim()) },
                        enabled = manualModel.isNotBlank(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.onSurface,
                            contentColor = MaterialTheme.colorScheme.background,
                        ),
                    ) { Text("Use") }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ModelBadge(text: String) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, shape = CircleShape) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp))
    }
}

private fun historyGroup(epochMillis: Long): String {
    val now = Calendar.getInstance()
    val target = Calendar.getInstance().apply { timeInMillis = epochMillis }
    val sameDay = now.get(Calendar.YEAR) == target.get(Calendar.YEAR) && now.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)
    if (sameDay) return "Today"
    val yesterday = (now.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (yesterday.get(Calendar.YEAR) == target.get(Calendar.YEAR) && yesterday.get(Calendar.DAY_OF_YEAR) == target.get(Calendar.DAY_OF_YEAR)) return "Yesterday"
    val days = ((now.timeInMillis - target.timeInMillis) / 86_400_000L).toInt()
    return if (days in 2..7) "Previous 7 Days" else "Earlier"
}

private fun formatTime(epochMillis: Long): String = java.text.SimpleDateFormat("h:mm a", Locale.getDefault()).format(Date(epochMillis))

private fun formatCount(value: Long): String =
    if (value >= 1_000_000) String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    else if (value >= 1_000) String.format(Locale.US, "%.0fk", value / 1_000.0)
    else value.toString()

private fun readTextDocument(context: Context, uri: Uri): Pair<String, String> {
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
        ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
        ?: "attached-document.txt"
    val stream = context.contentResolver.openInputStream(uri) ?: throw IllegalArgumentException("Android could not open the selected document")
    val bytes = stream.use { input ->
        val output = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(8_192)
        var total = 0
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            total += count
            require(total <= 24 * 1024) { "Text attachments are limited to 24 KiB." }
            output.write(buffer, 0, count)
        }
        output.toByteArray()
    }
    val content = bytes.toString(Charsets.UTF_8)
    require('\u0000' !in content) { "The selected file does not appear to be a text document." }
    val safeContent = AiSecretRedactor.redact(content).content
    require(safeContent.isNotBlank()) { "The selected document is empty." }
    return name to safeContent
}

private fun shareText(context: Context, text: String) {
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    context.startActivity(Intent.createChooser(intent, "Share AstraCode message"))
}

@Composable
private fun darkChatFieldColors() = OutlinedTextFieldDefaults.colors(
    focusedTextColor = MaterialTheme.colorScheme.onSurface,
    unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
    cursorColor = MaterialTheme.colorScheme.onSurface,
    focusedBorderColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant,
    focusedLabelColor = MaterialTheme.colorScheme.onSurface,
    unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
    focusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
    unfocusedLeadingIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
)

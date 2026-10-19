package com.astracode.app

import android.content.Context
import android.os.Bundle
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.astracode.filesystem.FileEntry
import com.astracode.filesystem.FileEntryType
import com.astracode.filesystem.FileManagementSystem
import com.astracode.filesystem.WorkspaceStorage
import com.astracode.filesystem.WorkspaceStorageType
import com.astracode.filesystem.Utf8RepairDecoder
import com.astracode.app.storage.SafWorkspaceStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF0B110F)
private val Panel = Color(0xFF141C19)
private val PanelRaised = Color(0xFF1B2621)
private val Mint = Color(0xFFA6F2C7)
private val PrimaryText = Color(0xFFEAF4ED)
private val MutedText = Color(0xFF9AA99F)
private val Border = Color(0xFF2C3A32)
private val Danger = Color(0xFFFFB4AB)

private val AstraColorScheme = darkColorScheme(
    primary = Mint,
    onPrimary = Ink,
    secondary = Color(0xFFC8B7FF),
    background = Ink,
    onBackground = PrimaryText,
    surface = Panel,
    onSurface = PrimaryText,
    surfaceVariant = PanelRaised,
    onSurfaceVariant = MutedText,
    outline = Border,
    error = Danger,
)

private const val PRIVATE_WORKSPACE_ID = "private"
private const val SAVED_WORKSPACES_KEY = "saved_workspace_uris"
private const val ACTIVE_WORKSPACE_KEY = "active_workspace_id"
private const val SAVED_DOCUMENTS_KEY = "saved_document_uris"
private const val ACTIVE_DOCUMENT_KEY = "active_document_uri"
private const val MAX_STANDALONE_DOCUMENT_BYTES = 8L * 1024L * 1024L

private data class WorkspaceBookmark(
    val uri: Uri,
    val name: String,
) {
    val id: String get() = uri.toString()
}

private data class DocumentBookmark(
    val uri: Uri,
    val name: String,
    val writable: Boolean,
) {
    val id: String get() = uri.toString()
}

private data class StandaloneDocumentContent(
    val text: String,
    val wasRepaired: Boolean,
)

class MainActivity : ComponentActivity() {
    private var activeStorage: WorkspaceStorage? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val preferences = getSharedPreferences("astracode_workspace", MODE_PRIVATE)
        val savedUris = loadSavedWorkspaceUris(preferences)
        val legacyActiveUri = preferences.getString("tree_uri", null)
        val initialBookmarks = savedUris.map(::createWorkspaceBookmark)
        val savedDocumentUris = loadSavedDocumentUris(preferences)
        val initialDocumentBookmarks = savedDocumentUris.map(::createDocumentBookmark)
        val initialActiveDocument = preferences.getString(ACTIVE_DOCUMENT_KEY, null)
            ?.let { Uri.parse(it) }
            ?.takeIf { candidate -> savedDocumentUris.contains(candidate) }
            ?.let(::createDocumentBookmark)
        val requestedActiveId = preferences.getString(
            ACTIVE_WORKSPACE_KEY,
            legacyActiveUri ?: PRIVATE_WORKSPACE_ID,
        ) ?: PRIVATE_WORKSPACE_ID

        var restoreNotice: String? = null
        var resolvedActiveId = PRIVATE_WORKSPACE_ID
        val initialStorage: WorkspaceStorage = if (
            requestedActiveId != PRIVATE_WORKSPACE_ID &&
            savedUris.any { it.toString() == requestedActiveId }
        ) {
            runCatching {
                SafWorkspaceStorage(applicationContext, Uri.parse(requestedActiveId))
            }.onSuccess {
                resolvedActiveId = requestedActiveId
            }.onFailure { error ->
                restoreNotice = "Could not restore the saved folder: " +
                    (error.message ?: "permission denied") +
                    ". Open Saved workspaces to try again."
            }.getOrNull() ?: createPrivateWorkspace()
        } else {
            createPrivateWorkspace()
        }
        activeStorage = initialStorage

        // Migrate the former single-folder preference into the saved-folder list.
        preferences.edit()
            .putString(SAVED_WORKSPACES_KEY, encodeWorkspaceUris(savedUris))
            .putString(ACTIVE_WORKSPACE_KEY, resolvedActiveId)
            .remove("tree_uri")
            .apply()

        setContent {
            var storage by remember { mutableStateOf(initialStorage) }
            var activeWorkspaceId by remember { mutableStateOf(resolvedActiveId) }
            var savedWorkspaceFolders by remember { mutableStateOf(initialBookmarks) }
            var savedDocuments by remember { mutableStateOf(initialDocumentBookmarks) }
            var activeDocument by remember { mutableStateOf(initialActiveDocument) }
            var workspaceNotice by remember { mutableStateOf(restoreNotice) }

            fun selectDocument(bookmark: DocumentBookmark) {
                activeDocument = bookmark
                preferences.edit().putString(ACTIVE_DOCUMENT_KEY, bookmark.uri.toString()).apply()
            }

            fun closeActiveDocument() {
                activeDocument = null
                preferences.edit().remove(ACTIVE_DOCUMENT_KEY).apply()
            }

            fun removeSavedDocument(bookmark: DocumentBookmark) {
                val remaining = savedDocuments.filterNot { it.id == bookmark.id }
                savedDocuments = remaining
                preferences.edit()
                    .putString(SAVED_DOCUMENTS_KEY, encodeDocumentUris(remaining.map { it.uri }))
                    .apply()
                if (activeDocument?.id == bookmark.id) closeActiveDocument()
                releasePersistedDocumentPermission(bookmark.uri)
                workspaceNotice = "Removed " + bookmark.name +
                    " from AstraCode. The original file was not deleted."
            }

            fun savePickedDocument(uri: Uri) {
                val alreadySaved = savedDocuments.any { it.uri == uri }
                val alreadyGranted = contentResolver.persistedUriPermissions.any { it.uri == uri }
                runCatching {
                    persistDocumentPermission(uri)
                    val bookmark = createDocumentBookmark(uri)
                    ensureSupportedTextDocument(uri, bookmark.name)
                    bookmark
                }.onSuccess { bookmark ->
                    val updated = if (alreadySaved) {
                        savedDocuments.map { if (it.uri == uri) bookmark else it }
                    } else {
                        savedDocuments + bookmark
                    }
                    savedDocuments = updated
                    activeDocument = bookmark
                    preferences.edit()
                        .putString(SAVED_DOCUMENTS_KEY, encodeDocumentUris(updated.map { it.uri }))
                        .putString(ACTIVE_DOCUMENT_KEY, bookmark.uri.toString())
                        .apply()
                    workspaceNotice = "Opened and saved " + bookmark.name
                }.onFailure { error ->
                    if (!alreadySaved && !alreadyGranted) releasePersistedDocumentPermission(uri)
                    workspaceNotice = "Could not open file: " +
                        (error.message ?: "permission denied")
                }
            }

            fun activatePrivateWorkspace() {
                if (activeWorkspaceId == PRIVATE_WORKSPACE_ID) return
                val previous = storage
                val next = createPrivateWorkspace()
                storage = next
                activeStorage = next
                activeWorkspaceId = PRIVATE_WORKSPACE_ID
                preferences.edit().putString(ACTIVE_WORKSPACE_KEY, PRIVATE_WORKSPACE_ID).apply()
                previous.close()
                workspaceNotice = "Using the private workspace."
                // Other saved folders intentionally keep their Android URI grants.
            }

            fun activateSavedWorkspace(bookmark: WorkspaceBookmark) {
                if (activeWorkspaceId == bookmark.id) return
                runCatching {
                    SafWorkspaceStorage(applicationContext, bookmark.uri)
                }.onSuccess { nextStorage ->
                    val previous = storage
                    storage = nextStorage
                    activeStorage = nextStorage
                    activeWorkspaceId = bookmark.id
                    preferences.edit().putString(ACTIVE_WORKSPACE_KEY, bookmark.id).apply()
                    previous.close()
                    workspaceNotice = "Opened " + nextStorage.displayName
                }.onFailure { error ->
                    workspaceNotice = "Could not open saved folder: " +
                        (error.message ?: "permission denied") +
                        ". You can add the folder again to restore access."
                }
            }

            fun removeSavedWorkspace(bookmark: WorkspaceBookmark) {
                val remaining = savedWorkspaceFolders.filterNot { it.id == bookmark.id }
                val wasActive = activeWorkspaceId == bookmark.id
                if (wasActive) {
                    val previous = storage
                    val next = createPrivateWorkspace()
                    storage = next
                    activeStorage = next
                    activeWorkspaceId = PRIVATE_WORKSPACE_ID
                    preferences.edit().putString(ACTIVE_WORKSPACE_KEY, PRIVATE_WORKSPACE_ID).apply()
                    previous.close()
                }
                savedWorkspaceFolders = remaining
                preferences.edit()
                    .putString(SAVED_WORKSPACES_KEY, encodeWorkspaceUris(remaining.map { it.uri }))
                    .apply()
                releasePersistedTreePermission(bookmark.uri)
                workspaceNotice = "Removed " + bookmark.name + " from AstraCode. Its files were not deleted."
            }

            val chooseFolder = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocumentTree(),
            ) { uri ->
                if (uri != null) {
                    val alreadySaved = savedWorkspaceFolders.any { it.uri == uri }
                    val alreadyGranted = contentResolver.persistedUriPermissions.any { it.uri == uri }
                    runCatching {
                        contentResolver.takePersistableUriPermission(
                            uri,
                            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                        )
                        SafWorkspaceStorage(applicationContext, uri)
                    }.onSuccess { nextStorage ->
                        val bookmark = WorkspaceBookmark(uri, nextStorage.displayName)
                        val updatedBookmarks = if (alreadySaved) {
                            savedWorkspaceFolders.map { if (it.uri == uri) bookmark else it }
                        } else {
                            savedWorkspaceFolders + bookmark
                        }
                        val previous = storage
                        storage = nextStorage
                        activeStorage = nextStorage
                        activeWorkspaceId = bookmark.id
                        savedWorkspaceFolders = updatedBookmarks
                        preferences.edit()
                            .putString(SAVED_WORKSPACES_KEY, encodeWorkspaceUris(updatedBookmarks.map { it.uri }))
                            .putString(ACTIVE_WORKSPACE_KEY, bookmark.id)
                            .apply()
                        previous.close()
                        workspaceNotice = if (alreadySaved) {
                            "Opened saved folder " + nextStorage.displayName
                        } else {
                            "Added and opened " + nextStorage.displayName
                        }
                    }.onFailure { error ->
                        if (!alreadySaved && !alreadyGranted) releasePersistedTreePermission(uri)
                        workspaceNotice = "Could not add folder: " +
                            (error.message ?: "permission denied")
                    }
                }
            }

            val chooseDocument = rememberLauncherForActivityResult(
                contract = ActivityResultContracts.OpenDocument(),
            ) { uri ->
                if (uri != null) savePickedDocument(uri)
            }

            MaterialTheme(colorScheme = AstraColorScheme) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    FileManagementScreen(
                        storage = storage,
                        activeWorkspaceId = activeWorkspaceId,
                        savedWorkspaceFolders = savedWorkspaceFolders,
                        savedDocuments = savedDocuments,
                        selectedDocument = activeDocument,
                        externalMessage = workspaceNotice,
                        onAddWorkspace = { chooseFolder.launch(null) },
                        onOpenStandaloneFile = { chooseDocument.launch(arrayOf("*/*")) },
                        onOpenSavedDocument = { bookmark -> selectDocument(bookmark) },
                        onRemoveSavedDocument = { bookmark -> removeSavedDocument(bookmark) },
                        onCloseStandaloneDocument = { closeActiveDocument() },
                        onSelectPrivateWorkspace = { activatePrivateWorkspace() },
                        onSelectSavedWorkspace = { bookmark -> activateSavedWorkspace(bookmark) },
                        onRemoveSavedWorkspace = { bookmark -> removeSavedWorkspace(bookmark) },
                    )
                }
            }
        }
    }

    private fun loadSavedWorkspaceUris(preferences: android.content.SharedPreferences): List<Uri> {
        val storedList = preferences.getString(SAVED_WORKSPACES_KEY, null)
        val rawUris = if (storedList != null) {
            runCatching {
                val json = org.json.JSONArray(storedList)
                List(json.length()) { index -> Uri.parse(json.getString(index)) }
            }.getOrDefault(emptyList())
        } else {
            preferences.getString("tree_uri", null)
                ?.takeIf { it.isNotBlank() }
                ?.let { listOf(Uri.parse(it)) }
                ?: emptyList()
        }
        return rawUris.distinctBy { it.toString() }
    }

    private fun encodeWorkspaceUris(uris: List<Uri>): String =
        org.json.JSONArray().apply {
            uris.distinctBy { it.toString() }.forEach { put(it.toString()) }
        }.toString()

    private fun loadSavedDocumentUris(preferences: android.content.SharedPreferences): List<Uri> {
        val storedList = preferences.getString(SAVED_DOCUMENTS_KEY, null) ?: return emptyList()
        return runCatching {
            val json = org.json.JSONArray(storedList)
            List(json.length()) { index -> Uri.parse(json.getString(index)) }
        }.getOrDefault(emptyList()).distinctBy { it.toString() }
    }

    private fun encodeDocumentUris(uris: List<Uri>): String =
        org.json.JSONArray().apply {
            uris.distinctBy { it.toString() }.forEach { put(it.toString()) }
        }.toString()

    private fun createDocumentBookmark(uri: Uri): DocumentBookmark {
        val displayName = runCatching {
            contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        val name = displayName
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
                ?.takeIf { it.isNotBlank() }
            ?: "External file"
        val grant = contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri }
        return DocumentBookmark(uri, name, writable = grant?.isWritePermission == true)
    }

    private fun ensureSupportedTextDocument(uri: Uri, fileName: String) {
        val mimeType = contentResolver.getType(uri).orEmpty().lowercase()
        val extension = fileName.substringAfterLast('.', "").lowercase()
        val textExtensions = setOf(
            "kt", "kts", "java", "xml", "json", "md", "txt", "gradle", "properties",
            "html", "htm", "css", "js", "jsx", "ts", "tsx", "py", "sh", "bash",
            "c", "h", "cc", "cpp", "hpp", "yml", "yaml", "toml", "ini", "cfg",
            "conf", "sql", "rs", "go", "rb", "php", "dart", "swift", "log", "csv",
            "env", "gitignore", "makefile",
        )
        val supportedMime = mimeType.startsWith("text/") ||
            mimeType in setOf(
                "application/json",
                "application/xml",
                "application/javascript",
                "application/x-sh",
                "application/yaml",
                "application/x-yaml",
            )
        if (!supportedMime && extension !in textExtensions) {
            throw java.io.IOException("AstraCode's editor currently supports text and source-code files only.")
        }
    }

    private fun persistDocumentPermission(uri: Uri) {
        try {
            contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        } catch (_: Exception) {
            // A provider may expose a read-only document. Keep its read grant if available.
            contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun releasePersistedDocumentPermission(uri: Uri) {
        val grant = contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri } ?: return
        var flags = 0
        if (grant.isReadPermission) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (grant.isWritePermission) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (flags != 0) {
            runCatching { contentResolver.releasePersistableUriPermission(uri, flags) }
        }
    }

    private fun createWorkspaceBookmark(uri: Uri): WorkspaceBookmark {
        val providerName = runCatching {
            androidx.documentfile.provider.DocumentFile.fromTreeUri(applicationContext, uri)?.name
        }.getOrNull()
        val readableName = providerName
            ?.takeIf { it.isNotBlank() }
            ?: uri.lastPathSegment?.substringAfterLast(':')?.substringAfterLast('/')
                ?.takeIf { it.isNotBlank() }
            ?: "External folder"
        return WorkspaceBookmark(uri, readableName)
    }

    private fun releasePersistedTreePermission(uri: Uri) {
        val grant = contentResolver.persistedUriPermissions.firstOrNull { it.uri == uri } ?: return
        var flags = 0
        if (grant.isReadPermission) flags = flags or Intent.FLAG_GRANT_READ_URI_PERMISSION
        if (grant.isWritePermission) flags = flags or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        if (flags != 0) {
            runCatching { contentResolver.releasePersistableUriPermission(uri, flags) }
        }
    }

    private fun createPrivateWorkspace(): FileManagementSystem =
        FileManagementSystem(filesDir.toPath().resolve("workspace"))

    override fun onDestroy() {
        activeStorage?.close()
        activeStorage = null
        super.onDestroy()
    }
}

private fun readStandaloneDocument(context: Context, uri: Uri): StandaloneDocumentContent {
    val decoder = Utf8RepairDecoder()
    val text = StringBuilder()
    val buffer = ByteArray(16 * 1024)
    var bytesRead = 0L
    val input = context.contentResolver.openInputStream(uri)
        ?: throw java.io.IOException("The document provider could not open this file for reading.")
    input.use { stream ->
        while (true) {
            val count = stream.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            bytesRead += count
            if (bytesRead > MAX_STANDALONE_DOCUMENT_BYTES) {
                throw java.io.IOException("Files larger than 8 MB are not supported yet.")
            }
            text.append(decoder.decode(buffer, 0, count))
        }
        text.append(decoder.decode(ByteArray(0), endOfInput = true))
    }
    return StandaloneDocumentContent(
        text = text.toString(),
        wasRepaired = decoder.repairedSpans.isNotEmpty(),
    )
}

private fun writeStandaloneDocument(context: Context, uri: Uri, text: String): String {
    val (persistedText, _) = replaceUnpairedSurrogates(text)
    val bytes = persistedText.toByteArray(Charsets.UTF_8)
    if (bytes.size.toLong() > MAX_STANDALONE_DOCUMENT_BYTES) {
        throw java.io.IOException("Files larger than 8 MB are not supported yet.")
    }
    val output = context.contentResolver.openOutputStream(uri, "wt")
        ?: throw java.io.IOException("The document provider could not open this file for writing.")
    output.use { stream ->
        stream.write(bytes)
        stream.flush()
    }
    return persistedText
}

private fun replaceUnpairedSurrogates(text: String): Pair<String, Int> {
    var replacements = 0
    val output = StringBuilder(text.length)
    var index = 0
    while (index < text.length) {
        val current = text[index]
        when {
            Character.isHighSurrogate(current) -> {
                if (index + 1 < text.length && Character.isLowSurrogate(text[index + 1])) {
                    output.append(current).append(text[index + 1])
                    index += 2
                } else {
                    output.append('\uFFFD')
                    replacements++
                    index++
                }
            }
            Character.isLowSurrogate(current) -> {
                output.append('\uFFFD')
                replacements++
                index++
            }
            else -> {
                output.append(current)
                index++
            }
        }
    }
    return output.toString() to replacements
}

@Composable
private fun FileManagementScreen(
    storage: WorkspaceStorage,
    activeWorkspaceId: String,
    savedWorkspaceFolders: List<WorkspaceBookmark>,
    savedDocuments: List<DocumentBookmark>,
    selectedDocument: DocumentBookmark?,
    externalMessage: String?,
    onAddWorkspace: () -> Unit,
    onOpenStandaloneFile: () -> Unit,
    onOpenSavedDocument: (DocumentBookmark) -> Unit,
    onRemoveSavedDocument: (DocumentBookmark) -> Unit,
    onCloseStandaloneDocument: () -> Unit,
    onSelectPrivateWorkspace: () -> Unit,
    onSelectSavedWorkspace: (WorkspaceBookmark) -> Unit,
    onRemoveSavedWorkspace: (WorkspaceBookmark) -> Unit,
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    var currentDirectory by remember { mutableStateOf("") }
    var openedFile by remember { mutableStateOf<String?>(null) }
    var editorText by remember { mutableStateOf("") }
    var savedText by remember { mutableStateOf("") }
    var entries by remember { mutableStateOf<List<FileEntry>>(emptyList()) }
    var folderCounts by remember { mutableStateOf<Map<String, Int?>>(emptyMap()) }
    var refreshKey by remember { mutableStateOf(0) }
    var newFileName by remember { mutableStateOf("") }
    var newFolderName by remember { mutableStateOf("") }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var showNewFolderDialog by remember { mutableStateOf(false) }
    var pendingDeletePath by remember { mutableStateOf<String?>(null) }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showWorkspaceManager by remember { mutableStateOf(false) }
    var showSavedDocuments by remember { mutableStateOf(false) }
    var standaloneDocumentLoading by remember(selectedDocument?.id) {
        mutableStateOf(selectedDocument != null)
    }

    fun notify(message: String) {
        scope.launch { snackbarHostState.showSnackbar(message) }
    }

    fun requestNavigation(action: () -> Unit) {
        if ((openedFile != null || selectedDocument != null) && editorText != savedText) {
            pendingNavigation = action
            showDiscardDialog = true
        } else {
            action()
        }
    }

    fun openFile(path: String) {
        scope.launch {
            val result = runCatchingCancellable {
                withContext(Dispatchers.IO) { storage.readText(path) }
            }
            result.onSuccess { snapshot ->
                openedFile = snapshot.path
                currentDirectory = parentPath(snapshot.path)
                editorText = snapshot.text
                savedText = snapshot.text
                statusMessage = if (snapshot.wasRepaired) {
                    "Invalid UTF-8 was repaired while opening this file."
                } else {
                    null
                }
                refreshKey++
            }.onFailure { error ->
                notify("Could not open file: " + (error.message ?: "unknown error"))
            }
        }
    }

    fun saveFile() {
        val content = editorText
        val document = selectedDocument
        if (document != null) {
            scope.launch {
                val result = runCatchingCancellable {
                    withContext(Dispatchers.IO) {
                        writeStandaloneDocument(context, document.uri, content)
                    }
                }
                result.onSuccess { persistedText ->
                    editorText = persistedText
                    savedText = persistedText
                    notify("Saved " + document.name)
                }.onFailure { error ->
                    notify("Could not save file: " + (error.message ?: "unknown error"))
                }
            }
            return
        }
        val path = openedFile ?: return
        scope.launch {
            val result = runCatchingCancellable {
                withContext(Dispatchers.IO) { storage.writeText(path, content) }
            }
            result.onSuccess { saved ->
                editorText = saved.persistedText
                savedText = saved.persistedText
                refreshKey++
                notify(
                    if (saved.replacedUnpairedSurrogateCount > 0) {
                        "Saved with invalid characters repaired."
                    } else {
                        "Saved " + fileName(path)
                    },
                )
            }.onFailure { error ->
                notify("Could not save file: " + (error.message ?: "unknown error"))
            }
        }
    }

    LaunchedEffect(storage, currentDirectory, refreshKey) {
        try {
            val listedEntries = withContext(Dispatchers.IO) {
                storage.listDirectory(currentDirectory)
            }
            entries = listedEntries
            folderCounts = emptyMap()

            // Count only direct children for each visible folder. Each folder gets its own
            // count when opened; we do not recursively scan the entire project on redraw.
            folderCounts = withContext(Dispatchers.IO) {
                listedEntries.asSequence()
                    .filter { it.type == FileEntryType.DIRECTORY }
                    .associate { folder ->
                        val count = try {
                            storage.listDirectory(folder.path).size
                        } catch (cancelled: CancellationException) {
                            throw cancelled
                        } catch (_: Exception) {
                            // Some external providers may expose a folder but not allow it to be listed.
                            null
                        }
                        folder.path to count
                    }
            }
        } catch (cancelled: CancellationException) {
            // A folder change or a disposed screen cancels this load by design.
            // Cancellation is control flow, not a folder-loading failure.
            throw cancelled
        } catch (error: Exception) {
            entries = emptyList()
            folderCounts = emptyMap()
            snackbarHostState.showSnackbar(
                "Could not load folder: " + (error.message ?: "unknown error"),
            )
        }
    }

    LaunchedEffect(storage, selectedDocument) {
        currentDirectory = ""
        openedFile = null
        editorText = ""
        savedText = ""
        entries = emptyList()
        folderCounts = emptyMap()
        pendingNavigation = null
        showDiscardDialog = false
        standaloneDocumentLoading = selectedDocument != null
        refreshKey++
        if (selectedDocument != null) {
            val result = runCatchingCancellable {
                withContext(Dispatchers.IO) {
                    readStandaloneDocument(context, selectedDocument.uri)
                }
            }
            result.onSuccess { snapshot ->
                editorText = snapshot.text
                savedText = snapshot.text
                standaloneDocumentLoading = false
                statusMessage = if (snapshot.wasRepaired) {
                    "Invalid UTF-8 was repaired while opening this file."
                } else {
                    null
                }
            }.onFailure { error ->
                standaloneDocumentLoading = false
                notify("Could not open file: " + (error.message ?: "unknown error"))
                onCloseStandaloneDocument()
            }
        }
    }

    LaunchedEffect(externalMessage) {
        val message = externalMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
    }

    LaunchedEffect(statusMessage) {
        val message = statusMessage ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(message)
        statusMessage = null
    }

    Scaffold(
        containerColor = Ink,
        contentWindowInsets = WindowInsets.systemBars,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(Ink),
        ) {
            AppTopBar(
                openedFile = selectedDocument?.name ?: openedFile,
                dirty = !standaloneDocumentLoading &&
                    (openedFile != null || selectedDocument != null) && editorText != savedText,
                canSave = !standaloneDocumentLoading && selectedDocument?.writable != false,
                onBack = {
                    requestNavigation {
                        if (selectedDocument != null) {
                            onCloseStandaloneDocument()
                        } else if (openedFile != null) {
                            openedFile = null
                            editorText = ""
                            savedText = ""
                        } else if (currentDirectory.isNotEmpty()) {
                            currentDirectory = parentPath(currentDirectory)
                            refreshKey++
                        }
                    }
                },
                onSave = ::saveFile,
            )
            Divider(color = Border, thickness = 1.dp)

            if (openedFile != null || selectedDocument != null) {
                if (selectedDocument != null && standaloneDocumentLoading) {
                    StandaloneDocumentLoadingPanel(selectedDocument.name)
                } else {
                    EditorPanel(
                        path = selectedDocument?.name ?: openedFile.orEmpty(),
                        text = editorText,
                        readOnly = selectedDocument?.writable == false,
                        onTextChange = { editorText = it },
                        onSave = ::saveFile,
                    )
                }
            } else {
                WorkspacePanel(
                    currentDirectory = currentDirectory,
                    rootName = storage.displayName,
                    storageType = storage.storageType,
                    entries = entries,
                    folderCounts = folderCounts,
                    savedWorkspaceFolders = savedWorkspaceFolders,
                    activeWorkspaceId = activeWorkspaceId,
                    onAddWorkspace = { requestNavigation(onAddWorkspace) },
                    onOpenStandaloneFile = { requestNavigation(onOpenStandaloneFile) },
                    onManageSavedDocuments = { showSavedDocuments = true },
                    onManageWorkspaces = { showWorkspaceManager = true },
                    onOpenEntry = { entry ->
                        when (entry.type) {
                            FileEntryType.DIRECTORY -> requestNavigation {
                                currentDirectory = entry.path
                                refreshKey++
                            }
                            FileEntryType.FILE -> requestNavigation { openFile(entry.path) }
                            FileEntryType.SYMBOLIC_LINK -> notify("Symbolic links are not opened from the workspace.")
                            FileEntryType.OTHER -> notify("This file type is not supported yet.")
                        }
                    },
                    onNewFile = {
                        newFileName = ""
                        showNewFileDialog = true
                    },
                    onNewFolder = {
                        newFolderName = ""
                        showNewFolderDialog = true
                    },
                    onDelete = { path -> pendingDeletePath = path },
                    onGoUp = {
                        requestNavigation {
                            currentDirectory = parentPath(currentDirectory)
                            refreshKey++
                        }
                    },
                )
            }
        }
    }

    if (showSavedDocuments) {
        SavedDocumentsDialog(
            savedDocuments = savedDocuments,
            onDismiss = { showSavedDocuments = false },
            onChooseFile = {
                showSavedDocuments = false
                requestNavigation(onOpenStandaloneFile)
            },
            onSelectDocument = { bookmark ->
                showSavedDocuments = false
                onOpenSavedDocument(bookmark)
            },
            onRemoveDocument = onRemoveSavedDocument,
        )
    }

    if (showWorkspaceManager) {
        WorkspaceManagerDialog(
            savedFolders = savedWorkspaceFolders,
            activeWorkspaceId = activeWorkspaceId,
            onDismiss = { showWorkspaceManager = false },
            onAddFolder = {
                showWorkspaceManager = false
                onAddWorkspace()
            },
            onSelectPrivate = {
                showWorkspaceManager = false
                onSelectPrivateWorkspace()
            },
            onSelectFolder = { bookmark ->
                showWorkspaceManager = false
                onSelectSavedWorkspace(bookmark)
            },
            onRemoveFolder = onRemoveSavedWorkspace,
        )
    }

    if (showNewFileDialog) {
        NameEntryDialog(
            title = "Create file",
            label = "File name",
            value = newFileName,
            testTag = "new-file-name-input",
            confirmLabel = "Create file",
            confirmTestTag = "confirm-create-file",
            onValueChange = { newFileName = it },
            onDismiss = { showNewFileDialog = false },
            onConfirm = {
                val name = newFileName.trim()
                if (!isSimpleName(name)) {
                    notify("Enter a file name without folders or path segments.")
                } else {
                    val path = joinPath(currentDirectory, name)
                    scope.launch {
                        val result = runCatchingCancellable {
                            withContext(Dispatchers.IO) { storage.writeText(path, "") }
                        }
                        result.onSuccess {
                            showNewFileDialog = false
                            openedFile = path
                            editorText = ""
                            savedText = ""
                            refreshKey++
                        }.onFailure { error ->
                            notify("Could not create file: " + (error.message ?: "unknown error"))
                        }
                    }
                }
            },
        )
    }

    if (showNewFolderDialog) {
        NameEntryDialog(
            title = "Create folder",
            label = "Folder name",
            value = newFolderName,
            testTag = "new-folder-name-input",
            confirmLabel = "Create folder",
            confirmTestTag = "confirm-create-folder",
            onValueChange = { newFolderName = it },
            onDismiss = { showNewFolderDialog = false },
            onConfirm = {
                val name = newFolderName.trim()
                if (!isSimpleName(name)) {
                    notify("Enter a folder name without path segments.")
                } else {
                    val path = joinPath(currentDirectory, name)
                    scope.launch {
                        val result = runCatchingCancellable {
                            withContext(Dispatchers.IO) { storage.createDirectory(path) }
                        }
                        result.onSuccess {
                            showNewFolderDialog = false
                            refreshKey++
                            notify("Created folder " + name)
                        }.onFailure { error ->
                            notify("Could not create folder: " + (error.message ?: "unknown error"))
                        }
                    }
                }
            },
        )
    }

    pendingDeletePath?.let { path ->
        AlertDialog(
            onDismissRequest = { pendingDeletePath = null },
            title = { Text("Delete " + fileName(path) + "?") },
            text = { Text("This removes the item from the workspace. Non-empty folders cannot be deleted here.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val result = runCatchingCancellable {
                                withContext(Dispatchers.IO) { storage.delete(path) }
                            }
                            result.onSuccess {
                                pendingDeletePath = null
                                refreshKey++
                                notify("Deleted " + fileName(path))
                            }.onFailure { error ->
                                pendingDeletePath = null
                                notify("Could not delete item: " + (error.message ?: "unknown error"))
                            }
                        }
                    },
                ) { Text("Delete", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePath = null }) { Text("Cancel") }
            },
        )
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = {
                showDiscardDialog = false
                pendingNavigation = null
            },
            title = { Text("Discard unsaved changes?") },
            text = { Text("Your latest edits have not been saved.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        val action = pendingNavigation
                        showDiscardDialog = false
                        pendingNavigation = null
                        action?.invoke()
                    },
                ) { Text("Discard changes") }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        showDiscardDialog = false
                        pendingNavigation = null
                    },
                ) { Text("Keep editing") }
            },
        )
    }
}

@Composable
private fun AppTopBar(
    openedFile: String?,
    dirty: Boolean,
    canSave: Boolean = true,
    onBack: () -> Unit,
    onSave: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(76.dp)
            .padding(horizontal = 18.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (openedFile != null) {
            IconButton(onClick = onBack, modifier = Modifier.testTag("editor-back")) {
                Icon(Icons.Filled.ArrowBack, contentDescription = "Back")
            }
        } else {
            Box(
                modifier = Modifier
                    .size(46.dp)
                    .background(Mint, RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Code, contentDescription = null, tint = Ink, modifier = Modifier.size(29.dp))
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                if (openedFile == null) "AstraCode" else fileName(openedFile),
                color = PrimaryText,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                if (openedFile == null) "ANDROID DEVELOPMENT WORKSPACE"
                else if (dirty) "UNSAVED CHANGES" else "FILE EDITOR",
                color = if (dirty) Mint else MutedText,
                fontSize = 10.sp,
                letterSpacing = 1.1.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        if (openedFile != null) {
            Button(
                onClick = onSave,
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                enabled = dirty && canSave,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("save-file-button"),
            ) {
                Icon(Icons.Filled.Save, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Save")
            }
        }
    }
}

@Composable
private fun WorkspacePanel(
    currentDirectory: String,
    rootName: String,
    storageType: WorkspaceStorageType,
    entries: List<FileEntry>,
    folderCounts: Map<String, Int?>,
    savedWorkspaceFolders: List<WorkspaceBookmark>,
    activeWorkspaceId: String,
    onAddWorkspace: () -> Unit,
    onOpenStandaloneFile: () -> Unit,
    onManageSavedDocuments: () -> Unit,
    onManageWorkspaces: () -> Unit,
    onOpenEntry: (FileEntry) -> Unit,
    onNewFile: () -> Unit,
    onNewFolder: () -> Unit,
    onDelete: (String) -> Unit,
    onGoUp: () -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 22.dp, end = 16.dp, top = 22.dp, bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    "WORKSPACE",
                    color = Mint,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.6.sp,
                )
                Spacer(Modifier.height(5.dp))
                Text(
                    if (currentDirectory.isEmpty()) rootName else currentDirectory.substringAfterLast('/'),
                    color = PrimaryText,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (storageType == WorkspaceStorageType.DOCUMENT_TREE) {
                        savedWorkspaceFolders.size.toString() + " saved external " +
                            if (savedWorkspaceFolders.size == 1) "folder" else "folders"
                    } else {
                        "Private app workspace"
                    },
                    color = MutedText,
                    fontSize = 12.sp,
                )
            }
            IconButton(
                onClick = onAddWorkspace,
                modifier = Modifier.testTag("add-workspace-button"),
            ) {
                Icon(Icons.Filled.FolderOpen, contentDescription = "Add workspace folder", tint = Mint)
            }
            IconButton(
                onClick = onManageWorkspaces,
                modifier = Modifier.testTag("manage-workspaces-button"),
            ) {
                Icon(Icons.Filled.Folder, contentDescription = "Manage workspaces", tint = MutedText)
            }
            IconButton(
                onClick = onNewFolder,
                modifier = Modifier.testTag("new-folder-button"),
            ) {
                Icon(Icons.Filled.CreateNewFolder, contentDescription = "New folder", tint = Mint)
            }
            Button(
                onClick = onNewFile,
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.testTag("new-file-button"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("New file")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onOpenStandaloneFile,
                modifier = Modifier.weight(1f).testTag("open-standalone-file-button"),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Open file")
            }
            OutlinedButton(
                onClick = onManageSavedDocuments,
                modifier = Modifier.weight(1f).testTag("manage-saved-documents-button"),
                shape = RoundedCornerShape(12.dp),
            ) {
                Icon(Icons.Filled.Folder, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Saved files")
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp)
                .padding(bottom = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = MutedText, modifier = Modifier.size(17.dp))
            Spacer(Modifier.width(8.dp))
            Text(
                if (currentDirectory.isEmpty()) rootName else rootName + " / " + currentDirectory,
                color = MutedText,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (currentDirectory.isNotEmpty()) {
                TextButton(onClick = onGoUp) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Up")
                }
            }
        }

        Divider(color = Border, thickness = 1.dp)
        Text(
            entries.size.toString() + if (entries.size == 1) " ITEM" else " ITEMS",
            color = MutedText,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.4.sp,
            modifier = Modifier.padding(start = 22.dp, top = 18.dp, bottom = 10.dp),
        )
        if (entries.isEmpty()) {
            EmptyWorkspace(onCreateFile = onNewFile)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                items(entries, key = { it.path }) { entry ->
                    FileEntryRow(
                        entry = entry,
                        folderCount = folderCounts[entry.path],
                        folderCountLoaded = folderCounts.containsKey(entry.path),
                        onClick = { onOpenEntry(entry) },
                        onDelete = { onDelete(entry.path) },
                    )
                }
            }
        }
    }
}

@Composable
private fun WorkspaceManagerDialog(
    savedFolders: List<WorkspaceBookmark>,
    activeWorkspaceId: String,
    onDismiss: () -> Unit,
    onAddFolder: () -> Unit,
    onSelectPrivate: () -> Unit,
    onSelectFolder: (WorkspaceBookmark) -> Unit,
    onRemoveFolder: (WorkspaceBookmark) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saved workspaces") },
        text = {
            Column {
                Text(
                    "Folders stay here until you remove them. Removing a folder only removes its access from AstraCode; it does not delete its files.",
                    color = MutedText,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onSelectPrivate)
                        .padding(vertical = 10.dp, horizontal = 4.dp)
                        .testTag("private-workspace-bookmark"),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Filled.Folder, contentDescription = null, tint = Mint, modifier = Modifier.size(25.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Private workspace", color = PrimaryText, fontWeight = FontWeight.SemiBold)
                        Text("Stored inside AstraCode", color = MutedText, fontSize = 12.sp)
                    }
                    if (activeWorkspaceId == PRIVATE_WORKSPACE_ID) {
                        Text("Current", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
                Divider(color = Border)
                if (savedFolders.isEmpty()) {
                    Text(
                        "No external folders saved yet. Tap Add folder to keep one here.",
                        color = MutedText,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 18.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(270.dp),
                    ) {
                        items(savedFolders, key = { it.id }) { bookmark ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onSelectFolder(bookmark) }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.FolderOpen,
                                        contentDescription = null,
                                        tint = Mint,
                                        modifier = Modifier.size(25.dp),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            bookmark.name,
                                            color = PrimaryText,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text("External folder", color = MutedText, fontSize = 12.sp)
                                    }
                                }
                                if (activeWorkspaceId == bookmark.id) {
                                    Text("Current", color = Mint, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                                IconButton(
                                    onClick = { onRemoveFolder(bookmark) },
                                    modifier = Modifier.testTag("remove-workspace-" + bookmark.id),
                                ) {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = "Remove " + bookmark.name,
                                        tint = Danger,
                                    )
                                }
                            }
                            Divider(color = Border)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAddFolder,
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                modifier = Modifier.testTag("add-workspace-from-manager"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Add folder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun SavedDocumentsDialog(
    savedDocuments: List<DocumentBookmark>,
    onDismiss: () -> Unit,
    onChooseFile: () -> Unit,
    onSelectDocument: (DocumentBookmark) -> Unit,
    onRemoveDocument: (DocumentBookmark) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Saved files") },
        text = {
            Column {
                Text(
                    "Files stay here until you remove them. Removing a bookmark only removes AstraCode's access; it does not delete the original file.",
                    color = MutedText,
                    fontSize = 13.sp,
                    lineHeight = 19.sp,
                )
                Spacer(Modifier.height(12.dp))
                if (savedDocuments.isEmpty()) {
                    Text(
                        "No files saved yet. Choose a file to add one.",
                        color = MutedText,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(vertical = 18.dp),
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp),
                    ) {
                        items(savedDocuments, key = { it.id }) { bookmark ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { onSelectDocument(bookmark) }
                                        .padding(vertical = 8.dp, horizontal = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                ) {
                                    Icon(
                                        Icons.Filled.Description,
                                        contentDescription = null,
                                        tint = Color(0xFFB9C6FF),
                                        modifier = Modifier.size(25.dp),
                                    )
                                    Spacer(Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            bookmark.name,
                                            color = PrimaryText,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                        )
                                        Text(
                                            if (bookmark.writable) "Saved file" else "Read-only access",
                                            color = MutedText,
                                            fontSize = 12.sp,
                                        )
                                    }
                                }
                                IconButton(
                                    onClick = { onRemoveDocument(bookmark) },
                                    modifier = Modifier.testTag("remove-document-" + bookmark.uri.hashCode()),
                                ) {
                                    Icon(
                                        Icons.Filled.DeleteOutline,
                                        contentDescription = "Remove " + bookmark.name,
                                        tint = Danger,
                                    )
                                }
                            }
                            Divider(color = Border)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onChooseFile,
                colors = ButtonDefaults.buttonColors(containerColor = Mint, contentColor = Ink),
                modifier = Modifier.testTag("choose-standalone-file-button"),
            ) {
                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(17.dp))
                Spacer(Modifier.width(6.dp))
                Text("Choose file")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Done") }
        },
    )
}

@Composable
private fun EmptyWorkspace(onCreateFile: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 44.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(92.dp)
                .background(PanelRaised, RoundedCornerShape(28.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.FolderOpen, contentDescription = null, tint = Mint, modifier = Modifier.size(43.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text("A clean workspace.", color = PrimaryText, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Text(
            "Create a file or folder. Your project starts here.",
            color = MutedText,
            fontSize = 14.sp,
            lineHeight = 21.sp,
        )
        Spacer(Modifier.height(22.dp))
        OutlinedButton(
            onClick = onCreateFile,
            shape = RoundedCornerShape(14.dp),
        ) {
            Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("Create your first file")
        }
    }
}

@Composable
private fun FileEntryRow(
    entry: FileEntry,
    folderCount: Int?,
    folderCountLoaded: Boolean,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val isDirectory = entry.type == FileEntryType.DIRECTORY
    val isSupported = isDirectory || entry.type == FileEntryType.FILE
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = isSupported, onClick = onClick)
            .testTag("entry-" + entry.name),
        shape = RoundedCornerShape(15.dp),
        colors = CardDefaults.cardColors(containerColor = Panel),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border.copy(alpha = 0.65f)),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 13.dp, end = 4.dp, top = 9.dp, bottom = 9.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(if (isDirectory) PanelRaised else Ink, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    if (isDirectory) Icons.Filled.Folder else Icons.Filled.Description,
                    contentDescription = null,
                    tint = if (isDirectory) Mint else Color(0xFFB9C6FF),
                    modifier = Modifier.size(22.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    entry.name,
                    color = PrimaryText,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                val detail = when {
                    isDirectory -> when {
                        !folderCountLoaded -> "Counting items…"
                        folderCount == null -> "Count unavailable"
                        else -> {
                            val countLabel = folderCount.toString() +
                                if (folderCount == 1) " item" else " items"
                            if (entry.name.startsWith(".")) {
                                "Hidden folder · " + countLabel
                            } else {
                                countLabel
                            }
                        }
                    }
                    entry.type == FileEntryType.FILE && entry.name.startsWith(".") ->
                        "Hidden file · " + humanSize(entry.sizeBytes)
                    entry.type == FileEntryType.FILE -> humanSize(entry.sizeBytes)
                    else -> "Unsupported item"
                }
                Text(
                    detail,
                    color = MutedText,
                    fontSize = 12.sp,
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier.size(40.dp),
            ) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete " + entry.name, tint = MutedText)
            }
        }
    }
}

@Composable
private fun StandaloneDocumentLoadingPanel(name: String) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(color = Mint)
            Spacer(Modifier.height(16.dp))
            Text(
                "Opening " + name,
                color = PrimaryText,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun EditorPanel(
    path: String,
    text: String,
    readOnly: Boolean = false,
    onTextChange: (String) -> Unit,
    onSave: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Text(
            path,
            color = MutedText,
            fontSize = 12.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(12.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("EDITOR", color = Mint, fontSize = 11.sp, letterSpacing = 1.4.sp, fontWeight = FontWeight.Bold)
            Text(if (readOnly) "READ ONLY" else "UTF-8", color = MutedText, fontSize = 11.sp)
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = text,
            onValueChange = onTextChange,
            readOnly = readOnly,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .testTag("file-editor-input"),
            label = { Text("File contents") },
            placeholder = { Text("Start writing…") },
            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.None,
                keyboardType = KeyboardType.Text,
                autoCorrectEnabled = false,
            ),
            shape = RoundedCornerShape(16.dp),
            colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                focusedTextColor = PrimaryText,
                unfocusedTextColor = PrimaryText,
                focusedBorderColor = Mint,
                unfocusedBorderColor = Border,
                cursorColor = Mint,
                focusedLabelColor = Mint,
                unfocusedLabelColor = MutedText,
            ),
            singleLine = false,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "UTF-8 text • save guarantees depend on the selected storage provider.",
            color = MutedText,
            fontSize = 11.sp,
        )
    }
}

@Composable
private fun NameEntryDialog(
    title: String,
    label: String,
    value: String,
    testTag: String,
    confirmLabel: String,
    confirmTestTag: String,
    onValueChange: (String) -> Unit,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = value,
                onValueChange = onValueChange,
                label = { Text(label) },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(testTag),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    keyboardType = KeyboardType.Ascii,
                    autoCorrectEnabled = false,
                ),
            )
        },
        confirmButton = {
            TextButton(
                onClick = onConfirm,
                modifier = Modifier.testTag(confirmTestTag),
            ) { Text(confirmLabel) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        },
    )
}

private suspend fun <T> runCatchingCancellable(block: suspend () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        // Never turn coroutine cancellation into a user-visible storage error.
        throw cancelled
    } catch (error: Exception) {
        Result.failure(error)
    }

private fun isSimpleName(value: String): Boolean =
    value.isNotBlank() &&
        value != "." &&
        value != ".." &&
        !value.contains('/') &&
        !value.contains('\\') &&
        value.none { it == '\u0000' }

private fun joinPath(directory: String, name: String): String =
    if (directory.isBlank()) name else "$directory/$name"

private fun parentPath(path: String): String {
    val separator = path.lastIndexOf('/')
    return if (separator < 0) "" else path.substring(0, separator)
}

private fun fileName(path: String): String =
    path.substringAfterLast('/').ifEmpty { "workspace" }

private fun humanSize(sizeBytes: Long): String =
    when {
        sizeBytes < 1024 -> "$sizeBytes B"
        sizeBytes < 1024 * 1024 -> String.format("%.1f KB", sizeBytes / 1024.0)
        else -> String.format("%.1f MB", sizeBytes / (1024.0 * 1024.0))
    }

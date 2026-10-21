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
import androidx.compose.material.icons.filled.Archive
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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
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
import com.astracode.app.storage.WorkspaceArchive
import com.astracode.app.storage.WorkspaceSafety
import com.astracode.app.storage.WorkspaceSyncLink
import com.astracode.app.storage.loadWorkspaceSyncLinks
import com.astracode.app.storage.mapBetweenWorkspacePaths
import com.astracode.app.storage.propagateWorkspaceChange
import com.astracode.app.storage.saveWorkspaceSyncLinks
import com.astracode.app.storage.syncWorkspaceEntry
import com.astracode.ui.components.AstraCodeBottomNav
import com.astracode.ui.components.CreateZipModal
import com.astracode.ui.components.QuickInspectorSheet
import com.astracode.ui.components.Screen
import com.astracode.ui.models.AtelierDuplicateGroup
import com.astracode.ui.models.AtelierFileItem
import com.astracode.ui.models.AtelierPreview
import com.astracode.ui.models.AtelierSearchResult
import com.astracode.ui.models.AtelierStorageHealth
import com.astracode.ui.models.AtelierTrashItem
import com.astracode.ui.screens.FilesScreen
import com.astracode.ui.screens.SafetyScreen
import com.astracode.ui.screens.SearchScreen
import com.astracode.ui.screens.TrashScreen
import com.astracode.ui.theme.AstraCodeTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val Ink = Color(0xFF101311)
private val Panel = Color(0xFF1C211D)
private val PanelRaised = Color(0xFF272D27)
private val Mint = Color(0xFFC5A46D)
private val PrimaryText = Color(0xFFE8E3D8)
private val MutedText = Color(0xFFA7ADA4)
private val Sage = Color(0xFF87948A)
private val Border = Color(0xFF343B34)
private val Danger = Color(0xFFD9857C)
private val Warning = Color(0xFFE3B567)

private val AstraColorScheme = darkColorScheme(
    primary = Mint,
    onPrimary = Ink,
    primaryContainer = Color(0xFF3B3020),
    onPrimaryContainer = PrimaryText,
    secondary = Sage,
    onSecondary = Ink,
    secondaryContainer = Color(0xFF283229),
    onSecondaryContainer = PrimaryText,
    tertiary = Warning,
    onTertiary = Ink,
    background = Ink,
    onBackground = PrimaryText,
    surface = Ink,
    onSurface = PrimaryText,
    surfaceVariant = Panel,
    onSurfaceVariant = MutedText,
    outline = Border,
    outlineVariant = Border,
    error = Danger,
    errorContainer = Color(0xFF35201D),
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
                val isStillSynced = loadWorkspaceSyncLinks(applicationContext)
                    .any { it.externalTreeUri == bookmark.id }
                if (!isStillSynced) releasePersistedTreePermission(bookmark.uri)
                workspaceNotice = if (isStillSynced) {
                    "Removed " + bookmark.name + " from the workspace list. Sync links keep access to its original files."
                } else {
                    "Removed " + bookmark.name + " from AstraCode. Its files were not deleted."
                }
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

            AstraCodeTheme {
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

private fun formatAtelierTimestamp(millis: Long): String =
    if (millis <= 0L) "Time not recorded"
    else java.time.Instant.ofEpochMilli(millis).toString().replace('T', ' ').take(16)

private fun FileEntry.toAtelierFileItem(childCount: Int?): AtelierFileItem {
    val isDirectory = type == FileEntryType.DIRECTORY
    val kind = when (type) {
        FileEntryType.DIRECTORY -> "Folder"
        FileEntryType.FILE -> name.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.uppercase() ?: "File"
        FileEntryType.SYMBOLIC_LINK -> "Symbolic link"
        FileEntryType.OTHER -> "Other"
    }
    return AtelierFileItem(
        path = path,
        name = name,
        kind = kind,
        isDirectory = isDirectory,
        sizeBytes = sizeBytes,
        modifiedLabel = formatAtelierTimestamp(modifiedAt.toEpochMilli()),
        childCount = childCount,
        createdLabel = createdAt?.let { formatAtelierTimestamp(it.toEpochMilli()) } ?: "Not recorded by provider",
        formatLabel = kind,
    )
}

private fun com.astracode.app.storage.WorkspaceSearchHit.toAtelierSearchResult(): AtelierSearchResult {
    val kind = when (type) {
        FileEntryType.DIRECTORY -> "Folder"
        FileEntryType.FILE -> name.substringAfterLast('.', "").takeIf { it.isNotBlank() }?.uppercase() ?: "File"
        FileEntryType.SYMBOLIC_LINK -> "Symbolic link"
        FileEntryType.OTHER -> "Other"
    }
    return AtelierSearchResult(
        path = path,
        name = name,
        kind = kind,
        sizeBytes = sizeBytes,
        modifiedLabel = formatAtelierTimestamp(modifiedAtMillis),
    )
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
    var operationEntry by remember { mutableStateOf<FileEntry?>(null) }
    var operationKind by remember { mutableStateOf<String?>(null) }
    var operationInput by remember { mutableStateOf("") }
    var syncEntry by remember { mutableStateOf<FileEntry?>(null) }
    var syncExternalUri by remember { mutableStateOf("") }
    var syncLinks by remember(context) { mutableStateOf(loadWorkspaceSyncLinks(context)) }
    var selectionMode by remember { mutableStateOf(false) }
    var selectedPaths by remember { mutableStateOf(emptySet<String>()) }
    var bulkActionKind by remember { mutableStateOf<String?>(null) }
    var bulkInput by remember { mutableStateOf("") }
    var pendingNavigation by remember { mutableStateOf<(() -> Unit)?>(null) }
    var showDiscardDialog by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var showWorkspaceManager by remember { mutableStateOf(false) }
    var showSavedDocuments by remember { mutableStateOf(false) }
    var showWorkspaceSafety by remember { mutableStateOf(false) }
    var activeMainScreen by remember { mutableStateOf<Screen>(Screen.Files) }
    var inspectorEntry by remember { mutableStateOf<FileEntry?>(null) }

    var searchResults by remember(storage) { mutableStateOf(emptyList<AtelierSearchResult>()) }
    var searchBusy by remember(storage) { mutableStateOf(false) }
    var searchMessage by remember(storage) { mutableStateOf<String?>(null) }
    var searchPreview by remember(storage) { mutableStateOf<AtelierPreview?>(null) }
    var duplicateGroups by remember(storage) { mutableStateOf(emptyList<AtelierDuplicateGroup>()) }
    var duplicatesScanned by remember(storage) { mutableStateOf(false) }
    var skippedDuplicateFiles by remember(storage) { mutableStateOf(0) }

    var healthReport by remember(storage) { mutableStateOf<AtelierStorageHealth?>(null) }
    var healthLoading by remember(storage) { mutableStateOf(false) }
    var healthMessage by remember(storage) { mutableStateOf<String?>(null) }

    var trashScreenItems by remember(storage) { mutableStateOf(emptyList<AtelierTrashItem>()) }
    var trashScreenLoading by remember(storage) { mutableStateOf(false) }
    var trashScreenMessage by remember(storage) { mutableStateOf<String?>(null) }
    var bulkActionInProgress by remember { mutableStateOf(false) }
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


    suspend fun propagateChangedPath(
        path: String,
        deleted: Boolean = false,
        movedTo: String? = null,
    ): List<WorkspaceSyncLink> {
        val currentLinks = syncLinks
        return withContext(Dispatchers.IO) {
            propagateWorkspaceChange(
                context = context,
                activeWorkspaceId = activeWorkspaceId,
                activeStorage = storage,
                links = currentLinks,
                changedPath = path,
                deleted = deleted,
                movedTo = movedTo,
            )
        }
    }

    fun showOperationDialog(entry: FileEntry, kind: String) {
        operationEntry = entry
        operationKind = kind
        operationInput = when (kind) {
            "rename" -> entry.name
            "copy" -> "copy-" + entry.name
            "extract" -> joinPath(parentPath(entry.path), entry.name.replace(Regex("(?i)\\.zip$"), ""))
            else -> entry.path
        }
    }

    fun startBulkAction(kind: String) {
        // Selection is limited to visible children in this folder, so no storage traversal is needed here.
        val paths = selectedPaths.toList()
        if (paths.isEmpty()) {
            notify("Select at least one file or folder.")
            return
        }
        val input = bulkInput.trim()
        val archiveName = if (kind == "zip") {
            when {
                input.isBlank() -> {
                    notify("Enter a name for the ZIP file.")
                    return
                }
                input.endsWith(".zip", ignoreCase = true) -> input
                else -> "$input.zip"
            }
        } else ""
        if (kind == "zip" && !isSimpleName(archiveName)) {
            notify("Enter a ZIP file name without folder paths.")
            return
        }
        if (kind in setOf("copy", "move") && !isSafeWorkspaceRelativePath(input, allowEmpty = true)) {
            notify("Enter a valid destination folder path.")
            return
        }

        scope.launch {
            bulkActionInProgress = true
            var completed = 0
            try {
                when (kind) {
                    "zip" -> {
                        val outputPath = joinPath(currentDirectory, archiveName)
                        withContext(Dispatchers.IO) {
                            WorkspaceArchive.moveToZip(storage, paths, outputPath, currentDirectory)
                        }
                        completed = 1
                        paths.sortedByDescending { it.count { ch -> ch == '/' } }.forEach { path ->
                            syncLinks = propagateChangedPath(path, deleted = true)
                        }
                        syncLinks = propagateChangedPath(outputPath)
                        notify("Moved ${paths.size} item(s) into $archiveName")
                    }
                    "delete" -> {
                        for (path in paths.sortedByDescending { it.count { ch -> ch == '/' } }) {
                            withContext(Dispatchers.IO) {
                                WorkspaceSafety.moveToTrash(storage, path)
                            }
                            completed++
                        }
                        notify(
                            "Moved $completed item(s) to Trash. Open File safety to restore them; " +
                                "linked copies have not been changed.",
                        )
                    }
                    "copy", "move" -> {
                        val destinationFolder = input
                        val targetPaths = withContext(Dispatchers.IO) {
                            if (destinationFolder.isNotEmpty()) {
                                val folder = findWorkspaceEntry(storage, destinationFolder)
                                    ?: throw IllegalArgumentException("Destination folder does not exist: $destinationFolder")
                                if (folder.type != FileEntryType.DIRECTORY) {
                                    throw IllegalArgumentException("Destination is not a folder: $destinationFolder")
                                }
                            } else {
                                storage.listDirectory("")
                            }
                            paths.map { sourcePath ->
                                val source = findWorkspaceEntry(storage, sourcePath)
                                    ?: throw IllegalArgumentException("Selected item no longer exists: $sourcePath")
                                val destinationPath = joinPath(destinationFolder, fileName(sourcePath))
                                if (destinationPath == sourcePath) {
                                    throw IllegalArgumentException("The destination is the same as the original path.")
                                }
                                if (source.type == FileEntryType.DIRECTORY &&
                                    isSameOrChildPath(destinationPath, sourcePath)) {
                                    throw IllegalArgumentException("A folder cannot be copied or moved into itself.")
                                }
                                if (storage.exists(destinationPath)) {
                                    throw IllegalArgumentException("Destination already exists: $destinationPath")
                                }
                                destinationPath
                            }
                        }
                        paths.zip(targetPaths).forEach { (sourcePath, destinationPath) ->
                            withContext(Dispatchers.IO) {
                                if (kind == "move") {
                                    storage.move(sourcePath, destinationPath, replaceExisting = false)
                                } else {
                                    syncWorkspaceEntry(
                                        storage,
                                        sourcePath,
                                        storage,
                                        destinationPath,
                                        overwriteExisting = false,
                                    )
                                }
                            }
                            completed++
                            syncLinks = if (kind == "move") {
                                propagateChangedPath(sourcePath, movedTo = destinationPath)
                            } else {
                                propagateChangedPath(destinationPath)
                            }
                        }
                        notify((if (kind == "move") "Moved " else "Copied ") + completed + " item(s)")
                    }
                    else -> throw IllegalArgumentException("Unknown bulk action.")
                }
            } catch (error: Exception) {
                syncLinks = loadWorkspaceSyncLinks(context)
                val partial = if (completed > 0) "$completed item(s) completed. " else ""
                notify(partial + "Could not complete the action: " + (error.message ?: "unknown error"))
            } finally {
                bulkActionInProgress = false
                bulkActionKind = null
                selectionMode = false
                selectedPaths = emptySet()
                refreshKey++
            }
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
                scope.launch {
                    val synced = runCatchingCancellable { propagateChangedPath(path) }
                    synced.onSuccess { updatedLinks -> syncLinks = updatedLinks }
                        .onFailure { error ->
                            notify("Saved locally, but a synced copy could not be updated: " +
                                (error.message ?: "unknown error"))
                        }
                }
            }.onFailure { error ->
                notify("Could not save file: " + (error.message ?: "unknown error"))
            }
        }
    }

    fun openWorkspaceEntry(path: String) {
        val selected = runCatching { findWorkspaceEntry(storage, path) }.getOrNull()
        if (selected == null) {
            notify("This item is no longer available.")
            refreshKey++
            return
        }
        requestNavigation {
            activeMainScreen = Screen.Files
            when (selected.type) {
                FileEntryType.DIRECTORY -> {
                    currentDirectory = selected.path
                    refreshKey++
                }
                FileEntryType.FILE -> {
                    if (selected.name.endsWith(".zip", ignoreCase = true)) {
                        showOperationDialog(selected, "extract")
                    } else {
                        openFile(selected.path)
                    }
                }
                FileEntryType.SYMBOLIC_LINK -> notify("Symbolic links are not opened from the workspace.")
                FileEntryType.OTHER -> notify("This file type is not supported yet.")
            }
        }
    }

    fun prepareWorkspaceSync(entry: FileEntry) {
        val linked = if (activeWorkspaceId == PRIVATE_WORKSPACE_ID) {
            syncLinks.filter {
                entry.path == it.privatePath || entry.path.startsWith(it.privatePath + "/")
            }.maxByOrNull { it.privatePath.length }
        } else {
            syncLinks.filter {
                it.externalTreeUri == activeWorkspaceId &&
                    (entry.path == it.externalPath || entry.path.startsWith(it.externalPath + "/"))
            }.maxByOrNull { it.externalPath.length }
        }
        syncExternalUri = linked?.externalTreeUri
            ?: if (activeWorkspaceId != PRIVATE_WORKSPACE_ID) activeWorkspaceId
            else savedWorkspaceFolders.firstOrNull()?.id.orEmpty()
        syncEntry = entry
    }

    fun refreshTrashScreen() {
        val requestedStorage = storage
        trashScreenLoading = true
        trashScreenMessage = null
        scope.launch {
            val result = runCatchingCancellable {
                withContext(Dispatchers.IO) { WorkspaceSafety.listTrash(requestedStorage) }
            }
            if (storage === requestedStorage) {
                result.onSuccess { records ->
                    trashScreenItems = records.map { record ->
                        AtelierTrashItem(
                            id = record.id,
                            originalPath = record.originalPath,
                            deletedAtMillis = record.deletedAtMillis,
                            deletedLabel = formatAtelierTimestamp(record.deletedAtMillis),
                        )
                    }
                    trashScreenMessage = if (records.isEmpty()) "Trash is empty." else null
                }.onFailure { error ->
                    trashScreenMessage = "Could not load Trash: " + (error.message ?: "storage error")
                }
                trashScreenLoading = false
            }
        }
    }

    fun refreshHealthScreen() {
        val requestedStorage = storage
        healthLoading = true
        healthMessage = null
        scope.launch {
            val result = runCatchingCancellable {
                withContext(Dispatchers.IO) { WorkspaceSafety.healthReport(requestedStorage) }
            }
            if (storage === requestedStorage) {
                result.onSuccess { report ->
                    healthReport = AtelierStorageHealth(
                        totalBytes = report.totalBytes,
                        fileCount = report.fileCount,
                        directoryCount = report.directoryCount,
                        largestFiles = report.largestFiles,
                        storageLabel = requestedStorage.displayName,
                    )
                }.onFailure { error ->
                    healthMessage = "Storage scan failed: " + (error.message ?: "storage error")
                }
                healthLoading = false
            }
        }
    }

    LaunchedEffect(storage, currentDirectory, refreshKey) {
        try {
            val listedEntries = withContext(Dispatchers.IO) {
                storage.listDirectory(currentDirectory)
                    .filterNot { WorkspaceSafety.isReservedPath(it.path) }
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
                            storage.listDirectory(folder.path).count { !WorkspaceSafety.isReservedPath(it.path) }
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
        activeMainScreen = Screen.Files
        currentDirectory = ""
        openedFile = null
        editorText = ""
        savedText = ""
        entries = emptyList()
        folderCounts = emptyMap()
        selectionMode = false
        selectedPaths = emptySet()
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
                when (activeMainScreen) {
                    Screen.Files -> FilesScreen(
                        modifier = Modifier.weight(1f),
                        rootName = storage.displayName,
                        currentDirectory = currentDirectory,
                        storageLabel = if (storage.storageType == WorkspaceStorageType.DOCUMENT_TREE) {
                            "External storage · provider-managed"
                        } else {
                            "Private app workspace"
                        },
                        items = entries.map { it.toAtelierFileItem(folderCounts[it.path]) },
                        selectedPaths = selectedPaths,
                        selectionMode = selectionMode,
                        savedWorkspaceCount = savedWorkspaceFolders.size,
                        onAddWorkspace = { requestNavigation(onAddWorkspace) },
                        onManageWorkspaces = { showWorkspaceManager = true },
                        onNewFile = {
                            newFileName = ""
                            showNewFileDialog = true
                        },
                        onNewFolder = {
                            newFolderName = ""
                            showNewFolderDialog = true
                        },
                        onOpenStandaloneFile = { requestNavigation(onOpenStandaloneFile) },
                        onManageSavedDocuments = { showSavedDocuments = true },
                        onOpenSafetyTools = {
                            showWorkspaceSafety = true
                        },
                        onSelectionModeChanged = { enabled ->
                            selectionMode = enabled
                            if (!enabled) selectedPaths = emptySet()
                        },
                        onToggleSelection = { path ->
                            selectedPaths = if (path in selectedPaths) selectedPaths - path else selectedPaths + path
                        },
                        onBulkAction = { kind ->
                            bulkActionKind = kind
                            bulkInput = when (kind) {
                                "zip" -> "archive.zip"
                                "copy", "move" -> parentPath(currentDirectory)
                                else -> ""
                            }
                        },
                        onOpenEntry = ::openWorkspaceEntry,
                        onInspectEntry = { path ->
                            inspectorEntry = runCatching { findWorkspaceEntry(storage, path) }.getOrNull()
                        },
                        onGoUp = {
                            requestNavigation {
                                selectionMode = false
                                selectedPaths = emptySet()
                                currentDirectory = parentPath(currentDirectory)
                                refreshKey++
                            }
                        },
                    )

                    Screen.Search -> SearchScreen(
                        modifier = Modifier.weight(1f),
                        hits = searchResults,
                        busy = searchBusy,
                        statusMessage = searchMessage,
                        duplicates = duplicateGroups,
                        duplicatesScanned = duplicatesScanned,
                        skippedDuplicateFiles = skippedDuplicateFiles,
                        preview = searchPreview,
                        onSearch = { query, extension, minimumBytes, maximumBytes, includeHidden ->
                            val requestedStorage = storage
                            searchBusy = true
                            searchMessage = "Searching workspace…"
                            searchPreview = null
                            scope.launch {
                                val result = runCatchingCancellable {
                                    withContext(Dispatchers.IO) {
                                        WorkspaceSafety.search(
                                            requestedStorage,
                                            com.astracode.app.storage.WorkspaceSearchOptions(
                                                query = query,
                                                extension = extension.trim().takeIf { it.isNotEmpty() },
                                                minSizeBytes = minimumBytes.toLongOrNull(),
                                                maxSizeBytes = maximumBytes.toLongOrNull(),
                                                includeHidden = includeHidden,
                                            ),
                                        )
                                    }
                                }
                                if (storage === requestedStorage) {
                                    result.onSuccess { hits ->
                                        searchResults = hits.map { it.toAtelierSearchResult() }
                                        searchMessage = if (hits.isEmpty()) "No matching workspace items." else "Found ${hits.size} matching item(s)."
                                    }.onFailure { error ->
                                        searchResults = emptyList()
                                        searchMessage = "Search failed: " + (error.message ?: "storage error")
                                    }
                                    searchBusy = false
                                }
                            }
                        },
                        onFindDuplicates = {
                            val requestedStorage = storage
                            searchBusy = true
                            searchMessage = "Scanning file contents for exact duplicates…"
                            scope.launch {
                                val result = runCatchingCancellable {
                                    withContext(Dispatchers.IO) { WorkspaceSafety.findDuplicates(requestedStorage) }
                                }
                                if (storage === requestedStorage) {
                                    result.onSuccess { scan ->
                                        duplicateGroups = scan.groups.map {
                                            AtelierDuplicateGroup(it.sha256, it.sizeBytes, it.paths)
                                        }
                                        skippedDuplicateFiles = scan.skippedFiles
                                        duplicatesScanned = true
                                        searchMessage = if (scan.groups.isEmpty()) {
                                            "No exact duplicate groups found. Files skipped: ${scan.skippedFiles}."
                                        } else {
                                            "Found ${scan.groups.size} duplicate group(s). Nothing was deleted."
                                        }
                                    }.onFailure { error ->
                                        searchMessage = "Duplicate scan failed: " + (error.message ?: "storage error")
                                    }
                                    searchBusy = false
                                }
                            }
                        },
                        onOpenPath = ::openWorkspaceEntry,
                        onPreviewPath = { path ->
                            val requestedStorage = storage
                            searchBusy = true
                            searchMessage = "Reading a bounded, read-only preview…"
                            scope.launch {
                                val result = runCatchingCancellable {
                                    withContext(Dispatchers.IO) { WorkspaceSafety.preview(requestedStorage, path) }
                                }
                                if (storage === requestedStorage) {
                                    result.onSuccess { item ->
                                        searchPreview = AtelierPreview(
                                            path = item.path,
                                            sizeBytes = item.sizeBytes,
                                            message = item.message,
                                            text = item.text,
                                            imageBytes = item.imageBytes,
                                        )
                                        searchMessage = "Preview is read-only. The original file was not changed."
                                    }.onFailure { error ->
                                        searchMessage = "Preview failed: " + (error.message ?: "storage error")
                                    }
                                    searchBusy = false
                                }
                            }
                        },
                        onClosePreview = { searchPreview = null },
                    )

                    Screen.Safety -> SafetyScreen(
                        modifier = Modifier.weight(1f),
                        health = healthReport,
                        isLoading = healthLoading,
                        message = healthMessage,
                        onRefresh = ::refreshHealthScreen,
                        onOpenAdvancedTools = { showWorkspaceSafety = true },
                    )

                    Screen.Trash -> TrashScreen(
                        modifier = Modifier.weight(1f),
                        items = trashScreenItems,
                        isLoading = trashScreenLoading,
                        message = trashScreenMessage,
                        onRefresh = ::refreshTrashScreen,
                        onRestore = { item, alternateDestination ->
                            val requestedStorage = storage
                            trashScreenLoading = true
                            trashScreenMessage = "Restoring item…"
                            scope.launch {
                                val result = runCatchingCancellable {
                                    withContext(Dispatchers.IO) {
                                        WorkspaceSafety.restoreFromTrash(
                                            requestedStorage,
                                            item.id,
                                            alternateDestination,
                                        )
                                    }
                                }
                                if (storage === requestedStorage) {
                                    result.onSuccess { restoredPath ->
                                        trashScreenItems = trashScreenItems.filterNot { it.id == item.id }
                                        trashScreenMessage = "Restored " + restoredPath
                                        refreshKey++
                                        scope.launch {
                                            val syncResult = runCatchingCancellable { propagateChangedPath(restoredPath) }
                                            syncResult.onSuccess { updatedLinks ->
                                                syncLinks = updatedLinks
                                            }.onFailure { error ->
                                                notify(
                                                    "Restored locally, but the linked copy could not be updated: " +
                                                        (error.message ?: "storage error"),
                                                )
                                            }
                                        }
                                    }.onFailure { error ->
                                        trashScreenMessage = "Restore failed: " + (error.message ?: "storage error") +
                                            ". The item remains in Trash."
                                    }
                                    trashScreenLoading = false
                                }
                            }
                        },
                    )
                }

                AstraCodeBottomNav(
                    currentScreen = activeMainScreen,
                    onTabSelected = { screen ->
                        activeMainScreen = screen
                        selectionMode = false
                        selectedPaths = emptySet()
                        when (screen) {
                            Screen.Safety -> refreshHealthScreen()
                            Screen.Trash -> refreshTrashScreen()
                            else -> Unit
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
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

    if (showWorkspaceSafety && openedFile == null && selectedDocument == null) {
        WorkspaceSafetyDialog(
            storage = storage,
            currentDirectory = currentDirectory,
            onDismiss = { showWorkspaceSafety = false },
            onStorageChanged = { refreshKey++ },
            onRestored = { restoredPath ->
                scope.launch {
                    val syncResult = runCatchingCancellable { propagateChangedPath(restoredPath) }
                    syncResult.onSuccess { updatedLinks ->
                        syncLinks = updatedLinks
                    }.onFailure { error ->
                        notify(
                            "Restored locally, but the linked copy could not be updated: " +
                                (error.message ?: "unknown error"),
                        )
                    }
                }
            },
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
                            scope.launch {
                                val synced = runCatchingCancellable { propagateChangedPath(path) }
                                synced.onSuccess { updatedLinks -> syncLinks = updatedLinks }
                                    .onFailure { error ->
                                        notify("Created the file, but its synced copy could not be updated: " +
                                            (error.message ?: "unknown error"))
                                    }
                            }
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
                            scope.launch {
                                val synced = runCatchingCancellable { propagateChangedPath(path) }
                                synced.onSuccess { updatedLinks -> syncLinks = updatedLinks }
                                    .onFailure { error ->
                                        notify("Created the folder, but its synced copy could not be updated: " +
                                            (error.message ?: "unknown error"))
                                    }
                            }
                        }.onFailure { error ->
                            notify("Could not create folder: " + (error.message ?: "unknown error"))
                        }
                    }
                }
            },
        )
    }


    syncEntry?.let { entry ->
        val externalUri = if (activeWorkspaceId == PRIVATE_WORKSPACE_ID) syncExternalUri else activeWorkspaceId
        AlertDialog(
            onDismissRequest = { syncEntry = null },
            title = { Text("Sync " + entry.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (activeWorkspaceId == PRIVATE_WORKSPACE_ID) {
                        Text("Choose an external folder, then choose which side should replace the other.")
                        if (savedWorkspaceFolders.isEmpty()) {
                            Text("Add an external folder before syncing.")
                            TextButton(
                                onClick = {
                                    syncEntry = null
                                    requestNavigation(onAddWorkspace)
                                },
                                modifier = Modifier.fillMaxWidth().testTag("sync-add-external-folder"),
                            ) { Text("Add external folder") }
                        } else {
                            savedWorkspaceFolders.forEach { bookmark ->
                                TextButton(
                                    onClick = { syncExternalUri = bookmark.id },
                                    modifier = Modifier.fillMaxWidth().testTag("sync-target-" + bookmark.name),
                                ) {
                                    Text((if (externalUri == bookmark.id) "✓ " else "") + bookmark.name)
                                }
                            }
                        }
                    } else {
                        Text("External folder: " + storage.displayName)
                    }
                    Button(
                        onClick = {
                            val selectedEntry = syncEntry ?: return@Button
                            if (externalUri.isBlank()) {
                                notify("Choose or add an external folder first.")
                                return@Button
                            }
                            val usePrivateActive = activeWorkspaceId == PRIVATE_WORKSPACE_ID
                            val matching = if (usePrivateActive) {
                                syncLinks.filter { it.externalTreeUri == externalUri }
                                    .filter { selectedEntry.path == it.privatePath ||
                                        selectedEntry.path.startsWith(it.privatePath + "/") }
                                    .maxByOrNull { it.privatePath.length }
                            } else {
                                syncLinks.filter { it.externalTreeUri == externalUri }
                                    .filter { selectedEntry.path == it.externalPath ||
                                        selectedEntry.path.startsWith(it.externalPath + "/") }
                                    .maxByOrNull { it.externalPath.length }
                            }
                            val privatePath = if (usePrivateActive) selectedEntry.path else {
                                matching?.let {
                                    mapBetweenWorkspacePaths(it.externalPath, it.privatePath, selectedEntry.path)
                                } ?: selectedEntry.path
                            }
                            val externalPath = if (usePrivateActive) {
                                matching?.let {
                                    mapBetweenWorkspacePaths(it.privatePath, it.externalPath, selectedEntry.path)
                                } ?: selectedEntry.path
                            } else selectedEntry.path
                            val linksSnapshot = syncLinks
                            scope.launch {
                                val result = runCatchingCancellable {
                                    withContext(Dispatchers.IO) {
                                        val temporaryPrivate = !usePrivateActive
                                        val temporaryExternal = usePrivateActive
                                        val privateStorage: WorkspaceStorage = if (usePrivateActive) storage
                                            else FileManagementSystem(context.filesDir.toPath().resolve("workspace"))
                                        val externalStorage: WorkspaceStorage = if (!usePrivateActive) storage
                                            else SafWorkspaceStorage(context, Uri.parse(externalUri))
                                        try {
                                            syncWorkspaceEntry(
                                                sourceStorage = privateStorage,
                                                sourcePath = privatePath,
                                                targetStorage = externalStorage,
                                                targetPath = externalPath,
                                                overwriteExisting = true,
                                            )
                                            val existingParent = linksSnapshot.any {
                                                it.externalTreeUri == externalUri &&
                                                    (privatePath == it.privatePath || privatePath.startsWith(it.privatePath + "/")) &&
                                                    (externalPath == it.externalPath || externalPath.startsWith(it.externalPath + "/"))
                                            }
                                            val updatedLinks = if (existingParent) {
                                                linksSnapshot
                                            } else {
                                                val rest = linksSnapshot.filterNot {
                                                    it.externalTreeUri == externalUri &&
                                                        (it.privatePath == privatePath ||
                                                            it.privatePath.startsWith(privatePath + "/") ||
                                                            it.externalPath == externalPath ||
                                                            it.externalPath.startsWith(externalPath + "/"))
                                                }
                                                rest + WorkspaceSyncLink(privatePath, externalUri, externalPath)
                                            }
                                            saveWorkspaceSyncLinks(context, updatedLinks)
                                            updatedLinks
                                        } finally {
                                            if (temporaryPrivate) privateStorage.close()
                                            if (temporaryExternal) externalStorage.close()
                                        }
                                    }
                                }
                                result.onSuccess { updatedLinks ->
                                    syncLinks = updatedLinks
                                    syncEntry = null
                                    refreshKey++
                                    notify("Synced app copy to external storage.")
                                }.onFailure { error ->
                                    notify("Sync failed: " + (error.message ?: "unknown error"))
                                }
                            }
                        },
                        enabled = externalUri.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("sync-app-to-external"),
                    ) { Text("App → external storage") }
                    Button(
                        onClick = {
                            // The same pair of copies is used; the direction is shown explicitly.
                            val selectedEntry = syncEntry ?: return@Button
                            if (externalUri.isBlank()) {
                                notify("Choose or add an external folder first.")
                                return@Button
                            }
                            val usePrivateActive = activeWorkspaceId == PRIVATE_WORKSPACE_ID
                            val matching = if (usePrivateActive) {
                                syncLinks.filter { it.externalTreeUri == externalUri }
                                    .filter { selectedEntry.path == it.privatePath ||
                                        selectedEntry.path.startsWith(it.privatePath + "/") }
                                    .maxByOrNull { it.privatePath.length }
                            } else {
                                syncLinks.filter { it.externalTreeUri == externalUri }
                                    .filter { selectedEntry.path == it.externalPath ||
                                        selectedEntry.path.startsWith(it.externalPath + "/") }
                                    .maxByOrNull { it.externalPath.length }
                            }
                            val privatePath = if (usePrivateActive) selectedEntry.path else {
                                matching?.let { mapBetweenWorkspacePaths(it.externalPath, it.privatePath, selectedEntry.path) }
                                    ?: selectedEntry.path
                            }
                            val externalPath = if (usePrivateActive) {
                                matching?.let { mapBetweenWorkspacePaths(it.privatePath, it.externalPath, selectedEntry.path) }
                                    ?: selectedEntry.path
                            } else selectedEntry.path
                            val linksSnapshot = syncLinks
                            scope.launch {
                                val result = runCatchingCancellable {
                                    withContext(Dispatchers.IO) {
                                        val temporaryPrivate = !usePrivateActive
                                        val temporaryExternal = usePrivateActive
                                        val privateStorage: WorkspaceStorage = if (usePrivateActive) storage
                                            else FileManagementSystem(context.filesDir.toPath().resolve("workspace"))
                                        val externalStorage: WorkspaceStorage = if (!usePrivateActive) storage
                                            else SafWorkspaceStorage(context, Uri.parse(externalUri))
                                        try {
                                            syncWorkspaceEntry(
                                                sourceStorage = externalStorage,
                                                sourcePath = externalPath,
                                                targetStorage = privateStorage,
                                                targetPath = privatePath,
                                                overwriteExisting = true,
                                            )
                                            val existingParent = linksSnapshot.any {
                                                it.externalTreeUri == externalUri &&
                                                    (privatePath == it.privatePath || privatePath.startsWith(it.privatePath + "/")) &&
                                                    (externalPath == it.externalPath || externalPath.startsWith(it.externalPath + "/"))
                                            }
                                            val updatedLinks = if (existingParent) linksSnapshot else {
                                                val rest = linksSnapshot.filterNot {
                                                    it.externalTreeUri == externalUri &&
                                                        (it.privatePath == privatePath ||
                                                            it.privatePath.startsWith(privatePath + "/") ||
                                                            it.externalPath == externalPath ||
                                                            it.externalPath.startsWith(externalPath + "/"))
                                                }
                                                rest + WorkspaceSyncLink(privatePath, externalUri, externalPath)
                                            }
                                            saveWorkspaceSyncLinks(context, updatedLinks)
                                            updatedLinks
                                        } finally {
                                            if (temporaryPrivate) privateStorage.close()
                                            if (temporaryExternal) externalStorage.close()
                                        }
                                    }
                                }
                                result.onSuccess { updatedLinks ->
                                    syncLinks = updatedLinks
                                    syncEntry = null
                                    refreshKey++
                                    notify("Synced external storage to app copy.")
                                }.onFailure { error ->
                                    notify("Sync failed: " + (error.message ?: "unknown error"))
                                }
                            }
                        },
                        enabled = externalUri.isNotBlank(),
                        modifier = Modifier.fillMaxWidth().testTag("sync-external-to-app"),
                    ) { Text("External storage → app") }
                }
            },
            confirmButton = { TextButton(onClick = { syncEntry = null }) { Text("Cancel") } },
        )
    }


    bulkActionKind?.let { kind ->
        if (kind == "zip") {
            CreateZipModal(
                selectedCount = selectedPaths.size,
                fileName = bulkInput,
                onFileNameChange = { bulkInput = it },
                isBusy = bulkActionInProgress,
                onDismiss = { if (!bulkActionInProgress) bulkActionKind = null },
                onCreate = { startBulkAction(kind) },
            )
        } else {
            val selected = selectedPaths.size
            AlertDialog(
                onDismissRequest = { if (!bulkActionInProgress) bulkActionKind = null },
                title = {
                    Text(when (kind) {
                        "copy" -> "Copy $selected item(s)"
                        "move" -> "Move $selected item(s)"
                        else -> "Move $selected item(s) to Trash?"
                    })
                },
                text = {
                    Column {
                        Text(when (kind) {
                            "copy" -> "Copy all selected items into a destination folder that already exists."
                            "move" -> "Move all selected items into a destination folder that already exists. Synced copies are moved too."
                            else -> "Move selected items and their contents to Trash. Linked copies are not changed by this bulk action."
                        })
                        if (kind != "delete") {
                            OutlinedTextField(
                                value = bulkInput,
                                onValueChange = { bulkInput = it },
                                label = { Text("Destination folder path") },
                                modifier = Modifier.fillMaxWidth().testTag("workspace-bulk-input"),
                                enabled = !bulkActionInProgress,
                            )
                        }
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = { startBulkAction(kind) },
                        enabled = !bulkActionInProgress,
                        modifier = Modifier.testTag("confirm-workspace-bulk-action"),
                    ) {
                        Text(when (kind) {
                            "copy" -> "Copy"
                            "move" -> "Move"
                            else -> "Move to Trash"
                        })
                    }
                },
                dismissButton = {
                    TextButton(onClick = { bulkActionKind = null }, enabled = !bulkActionInProgress) { Text("Cancel") }
                },
            )
        }
    }

    operationEntry?.let { entry ->
        val kind = operationKind.orEmpty()
        AlertDialog(
            onDismissRequest = { operationEntry = null; operationKind = null },
            title = {
                Text(when (kind) {
                    "rename" -> "Rename " + entry.name
                    "copy" -> "Copy " + entry.name
                    "extract" -> "Extract " + entry.name
                    else -> "Move " + entry.name
                })
            },
            text = {
                Column {
                    Text(
                        when (kind) {
                            "rename" -> "Enter a new name for this item."
                            "copy" -> "Enter a name for the copy in this folder."
                            "extract" -> "ZIP files cannot be edited directly. Extract the contents first, then open the extracted files to edit them. Choose a destination folder; existing folders will not be overwritten."
                            else -> "Enter a destination path relative to the workspace root."
                        },
                        modifier = if (kind == "extract") Modifier.testTag("workspace-extract-explanation") else Modifier,
                    )
                    OutlinedTextField(
                        value = operationInput,
                        onValueChange = { operationInput = it },
                        label = { Text(if (kind == "move" || kind == "extract") "Destination path" else "Name") },
                        modifier = Modifier.fillMaxWidth().testTag("workspace-operation-input"),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val raw = operationInput.trim()
                        if (kind != "move" && kind != "extract" && !isSimpleName(raw)) {
                            notify("Enter a valid name without folders or path segments.")
                            return@TextButton
                        }
                        if ((kind == "move" || kind == "extract") &&
                            !isSafeWorkspaceRelativePath(raw, allowEmpty = false)) {
                            notify("Enter a valid workspace-relative destination path.")
                            return@TextButton
                        }
                        val destinationPath = if (kind == "move" || kind == "extract") raw
                            else joinPath(parentPath(entry.path), raw)
                        if (destinationPath.isBlank() || destinationPath.startsWith("/") ||
                            destinationPath.contains('\\') ||
                            destinationPath.split('/').any { it.isBlank() || it == "." || it == ".." }) {
                            notify("Enter a valid workspace-relative destination path.")
                            return@TextButton
                        }
                        if (destinationPath == entry.path) {
                            notify("The destination is the same as the original path.")
                            return@TextButton
                        }
                        if (entry.type == FileEntryType.DIRECTORY &&
                            destinationPath.startsWith(entry.path + "/")) {
                            notify("A folder cannot be copied or moved into itself.")
                            return@TextButton
                        }
                        val linksSnapshot = syncLinks
                        scope.launch {
                            val operation = runCatchingCancellable {
                                withContext(Dispatchers.IO) {
                                    when (kind) {
                                        "copy" -> syncWorkspaceEntry(
                                            storage, entry.path, storage, destinationPath, overwriteExisting = false,
                                        )
                                        "extract" -> WorkspaceArchive.extractZip(storage, entry.path, destinationPath)
                                        else -> storage.move(entry.path, destinationPath, replaceExisting = false)
                                    }
                                }
                            }
                            operation.onSuccess {
                                operationEntry = null
                                operationKind = null
                                refreshKey++
                                if (kind == "rename" || kind == "move") {
                                    val synced = runCatchingCancellable {
                                        withContext(Dispatchers.IO) {
                                            propagateWorkspaceChange(
                                                context, activeWorkspaceId, storage, linksSnapshot,
                                                entry.path, deleted = false, movedTo = destinationPath,
                                            )
                                        }
                                    }
                                    synced.onSuccess { updatedLinks ->
                                        syncLinks = updatedLinks
                                        notify((if (kind == "rename") "Renamed " else "Moved ") + entry.name)
                                    }.onFailure { error ->
                                        notify("The item was moved, but the synced copy could not be updated: " +
                                            (error.message ?: "unknown error"))
                                    }
                                } else {
                                    val synced = runCatchingCancellable {
                                        withContext(Dispatchers.IO) {
                                            propagateWorkspaceChange(
                                                context, activeWorkspaceId, storage, linksSnapshot,
                                                destinationPath,
                                            )
                                        }
                                    }
                                    synced.onSuccess { updatedLinks ->
                                        syncLinks = updatedLinks
                                        notify((if (kind == "extract") "Extracted " else "Copied ") + entry.name)
                                    }.onFailure { error ->
                                        notify("The item was created locally, but the synced copy could not be updated: " +
                                            (error.message ?: "unknown error"))
                                    }
                                }
                            }.onFailure { error ->
                                notify("Could not " + kind + " item: " + (error.message ?: "unknown error"))
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm-workspace-operation"),
                ) { Text(when (kind) { "rename" -> "Rename"; "copy" -> "Copy"; "extract" -> "Extract"; else -> "Move" }) }
            },
            dismissButton = {
                TextButton(onClick = { operationEntry = null; operationKind = null }) { Text("Cancel") }
            },
        )
    }

    pendingDeletePath?.let { path ->
        AlertDialog(
            onDismissRequest = { pendingDeletePath = null },
            title = { Text("Delete " + fileName(path) + "?") },
            text = { Text("This moves the item and its contents to Trash in this workspace. A linked copy is left unchanged.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            val result = runCatchingCancellable {
                                withContext(Dispatchers.IO) { WorkspaceSafety.moveToTrash(storage, path) }
                            }
                            result.onSuccess { trashRecord ->
                                pendingDeletePath = null
                                refreshKey++
                                scope.launch {
                                    val snackbarResult = snackbarHostState.showSnackbar(
                                        message = "Moved " + fileName(path) +
                                            " to Trash. The linked copy has not been changed.",
                                        actionLabel = "Undo",
                                        withDismissAction = true,
                                        duration = SnackbarDuration.Long,
                                    )
                                    if (snackbarResult == SnackbarResult.ActionPerformed) {
                                        val restored = runCatchingCancellable {
                                            withContext(Dispatchers.IO) {
                                                WorkspaceSafety.restoreFromTrash(storage, trashRecord.id)
                                            }
                                        }
                                        restored.onSuccess { restoredPath ->
                                            refreshKey++
                                            val syncResult = runCatchingCancellable {
                                                propagateChangedPath(restoredPath)
                                            }
                                            syncResult.onSuccess { updatedLinks ->
                                                syncLinks = updatedLinks
                                                notify("Restored " + fileName(restoredPath))
                                            }.onFailure { error ->
                                                notify(
                                                    "Restored locally, but the linked copy could not be updated: " +
                                                        (error.message ?: "unknown error"),
                                                )
                                            }
                                        }.onFailure { error ->
                                            notify("Could not undo Trash move: " + (error.message ?: "unknown error"))
                                        }
                                    }
                                }
                            }.onFailure { error ->
                                pendingDeletePath = null
                                notify("Could not move item to Trash: " + (error.message ?: "unknown error"))
                            }
                        }
                    },
                    modifier = Modifier.testTag("confirm-workspace-delete"),
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

    inspectorEntry?.let { entry ->
        QuickInspectorSheet(
            item = entry.toAtelierFileItem(folderCounts[entry.path]),
            onDismiss = { inspectorEntry = null },
            onOpen = {
                inspectorEntry = null
                openWorkspaceEntry(entry.path)
            },
            onCreateZip = {
                inspectorEntry = null
                selectedPaths = setOf(entry.path)
                selectionMode = true
                bulkActionKind = "zip"
                bulkInput = entry.name.removeSuffix(".zip").removeSuffix(".ZIP") + "-archive.zip"
            },
            onRename = {
                inspectorEntry = null
                showOperationDialog(entry, "rename")
            },
            onCopy = {
                inspectorEntry = null
                showOperationDialog(entry, "copy")
            },
            onMove = {
                inspectorEntry = null
                showOperationDialog(entry, "move")
            },
            onMoveToTrash = {
                inspectorEntry = null
                pendingDeletePath = entry.path
            },
            onSync = {
                inspectorEntry = null
                prepareWorkspaceSync(entry)
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
    selectionMode: Boolean,
    selectedPaths: Set<String>,
    onSelectionModeChanged: (Boolean) -> Unit,
    onToggleSelection: (String) -> Unit,
    onBulkAction: (String) -> Unit,
    onAddWorkspace: () -> Unit,
    onOpenStandaloneFile: () -> Unit,
    onManageSavedDocuments: () -> Unit,
    onOpenSafetyTools: () -> Unit,
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
            OutlinedButton(
                onClick = onOpenSafetyTools,
                modifier = Modifier.weight(1f).testTag("workspace-safety-tools"),
                shape = RoundedCornerShape(12.dp),
            ) {
                Text("File safety")
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
            TextButton(
                onClick = { onSelectionModeChanged(!selectionMode) },
                modifier = Modifier.testTag("workspace-toggle-selection"),
            ) { Text(if (selectionMode) "Cancel" else "Select") }
            if (currentDirectory.isNotEmpty()) {
                TextButton(onClick = onGoUp, enabled = !selectionMode) {
                    Icon(Icons.Filled.ArrowBack, contentDescription = null, modifier = Modifier.size(15.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Up")
                }
            }
        }

        if (selectionMode) {
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${selectedPaths.size} selected", color = MutedText, modifier = Modifier.weight(1f))
                    TextButton(
                        onClick = { onBulkAction("zip") },
                        enabled = selectedPaths.isNotEmpty(),
                        modifier = Modifier.testTag("workspace-bulk-zip"),
                    ) { Text("Move into ZIP") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(
                        onClick = { onBulkAction("copy") },
                        enabled = selectedPaths.isNotEmpty(),
                        modifier = Modifier.testTag("workspace-bulk-copy"),
                    ) { Text("Copy") }
                    TextButton(
                        onClick = { onBulkAction("move") },
                        enabled = selectedPaths.isNotEmpty(),
                        modifier = Modifier.testTag("workspace-bulk-move"),
                    ) { Text("Move") }
                    TextButton(
                        onClick = { onBulkAction("delete") },
                        enabled = selectedPaths.isNotEmpty(),
                        modifier = Modifier.testTag("workspace-bulk-delete"),
                    ) { Text("Delete") }
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
                        selectionMode = selectionMode,
                        selected = entry.path in selectedPaths,
                        onToggleSelection = { onToggleSelection(entry.path) },
                        onClick = { if (selectionMode) onToggleSelection(entry.path) else onOpenEntry(entry) },
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
    selectionMode: Boolean,
    selected: Boolean,
    onToggleSelection: () -> Unit,
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
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggleSelection() },
                    enabled = isSupported,
                    modifier = Modifier.testTag("workspace-select-" + entry.name),
                )
            }
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(if (isDirectory) PanelRaised else Ink, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = when {
                        isDirectory -> Icons.Filled.Folder
                        entry.type == FileEntryType.FILE && entry.name.endsWith(".zip", ignoreCase = true) -> Icons.Filled.Archive
                        else -> Icons.Filled.Description
                    },
                    contentDescription = if (
                        entry.type == FileEntryType.FILE && entry.name.endsWith(".zip", ignoreCase = true)
                    ) "ZIP archive" else null,
                    tint = when {
                        isDirectory -> Mint
                        entry.type == FileEntryType.FILE && entry.name.endsWith(".zip", ignoreCase = true) -> Color(0xFFFFC857)
                        else -> Color(0xFFB9C6FF)
                    },
                    modifier = Modifier.size(22.dp).testTag("entry-icon-${entry.name}"),
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
            if (!selectionMode) {
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(40.dp),
                ) {
                    Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete " + entry.name, tint = MutedText)
                }
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

private fun isSafeWorkspaceRelativePath(value: String, allowEmpty: Boolean): Boolean {
    if (value.isEmpty()) return allowEmpty
    if (value.startsWith("/") || value.contains('\\') || value.indexOf('\u0000') >= 0) return false
    return value.split('/').none { it.isBlank() || it == "." || it == ".." }
}

private fun isSameOrChildPath(path: String, root: String): Boolean =
    path == root || path.startsWith(root + "/")

private fun findWorkspaceEntry(storage: WorkspaceStorage, path: String): FileEntry? {
    if (!isSafeWorkspaceRelativePath(path, allowEmpty = false)) {
        throw IllegalArgumentException("Invalid workspace-relative path.")
    }
    if (!storage.exists(path)) return null
    val parts = path.split('/')
    var parent = ""
    var found: FileEntry? = null
    parts.forEachIndexed { index, segment ->
        val entry = storage.listDirectory(parent).firstOrNull { it.name == segment } ?: return null
        if (index < parts.lastIndex && entry.type != FileEntryType.DIRECTORY) {
            throw IllegalArgumentException("Not a folder: $parent")
        }
        found = entry
        parent = joinPath(parent, segment)
    }
    return found
}

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

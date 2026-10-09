package com.mrredhood.astracode

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

private data class WorkspaceBreadcrumb(val documentId: String, val name: String)

@Composable
internal fun WorkspaceScreen() {
    val context = LocalContext.current
    val repository = remember(context) { WorkspaceRepository(context.applicationContext) }
    var treeUriString by rememberSaveable { mutableStateOf(repository.savedTreeUri()?.toString()) }
    var errorMessage by rememberSaveable { mutableStateOf<String?>(null) }
    var refreshKey by rememberSaveable { mutableIntStateOf(0) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var openedFileId by rememberSaveable { mutableStateOf<String?>(null) }
    var openedFileName by rememberSaveable { mutableStateOf<String?>(null) }
    var openedFileMime by rememberSaveable { mutableStateOf<String?>(null) }
    var previewText by rememberSaveable { mutableStateOf<String?>(null) }
    var previewLoading by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<WorkspaceEntry>>(emptyList()) }
    var listingLoading by remember { mutableStateOf(false) }
    val directoryStack = remember(treeUriString) { mutableStateListOf<WorkspaceBreadcrumb>() }
    val treeUri = remember(treeUriString) { treeUriString?.let(Uri::parse) }

    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            var readPermissionPersisted = false
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
                readPermissionPersisted = true
            } catch (_: SecurityException) {
                errorMessage = "Android could not preserve access to that folder. Choose it again and grant access."
            }
            if (readPermissionPersisted) {
                // Read-only providers remain valid; write is retained when the provider grants it.
                runCatching {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                    )
                }
                repository.saveTreeUri(uri)
                treeUriString = uri.toString()
                openedFileId = null
                openedFileName = null
                openedFileMime = null
                previewText = null
                errorMessage = null
                refreshKey++
            }
        }
    }

    LaunchedEffect(treeUriString) {
        directoryStack.clear()
        openedFileId = null
        openedFileName = null
        openedFileMime = null
        previewText = null
        if (treeUri != null) {
            try {
                directoryStack.add(WorkspaceBreadcrumb(repository.rootDocumentId(treeUri), "Workspace"))
            } catch (_: Exception) {
                errorMessage = "The saved workspace address is invalid. Choose the folder again."
            }
        }
    }

    val currentDirectory = directoryStack.lastOrNull()
    LaunchedEffect(treeUriString, currentDirectory?.documentId, refreshKey, openedFileId) {
        val activeUri = treeUri
        val parent = currentDirectory
        if (activeUri == null || parent == null || openedFileId != null) return@LaunchedEffect
        listingLoading = true
        errorMessage = null
        try {
            entries = withContext(Dispatchers.IO) {
                repository.listChildren(activeUri, parent.documentId)
            }
        } catch (_: SecurityException) {
            entries = emptyList()
            errorMessage = "Workspace access has expired or was revoked. Choose the folder again."
        } catch (exception: Exception) {
            entries = emptyList()
            errorMessage = "Could not read this folder. ${exception.message ?: "Check access and try again."}"
        } finally {
            listingLoading = false
        }
    }

    LaunchedEffect(treeUriString, openedFileId, openedFileName, openedFileMime) {
        val activeUri = treeUri
        val documentId = openedFileId
        if (activeUri == null || documentId == null) {
            previewText = null
            previewLoading = false
            return@LaunchedEffect
        }
        val fileName = openedFileName.orEmpty()
        val mimeType = openedFileMime.orEmpty()
        if (!WorkspaceFilePolicy.supportsTextPreview(fileName, mimeType)) {
            previewText = "Preview is not available for this file type. The file has not been changed."
            previewLoading = false
            return@LaunchedEffect
        }
        previewLoading = true
        previewText = null
        try {
            val result = withContext(Dispatchers.IO) {
                repository.readTextPreview(activeUri, documentId)
            }
            previewText = if (result.truncated) {
                result.text + "\n\n[Preview truncated at 256 KiB. The original file is unchanged.]"
            } else {
                result.text
            }
        } catch (_: SecurityException) {
            previewText = "Access to this file was denied. Choose the workspace folder again if its permission has expired."
        } catch (exception: Exception) {
            previewText = "Could not preview this file. ${exception.message ?: "The file may no longer be available."}"
        } finally {
            previewLoading = false
        }
    }

    BackHandler(enabled = openedFileId != null || directoryStack.size > 1) {
        if (openedFileId != null) {
            openedFileId = null
            openedFileName = null
            openedFileMime = null
            previewText = null
        } else if (directoryStack.size > 1) {
            directoryStack.removeAt(directoryStack.lastIndex)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (treeUri == null) {
            Text("No workspace selected", style = MaterialTheme.typography.titleMedium)
            Text(
                "Choose a project folder with Android's system picker. AstraCode keeps the granted folder permission so the workspace can be reopened later.",
                style = MaterialTheme.typography.bodyMedium
            )
            Button(onClick = { folderPicker.launch(null) }, modifier = Modifier.fillMaxWidth()) {
                Text("Choose project folder")
            }
        } else if (openedFileId != null) {
            OutlinedButton(
                onClick = {
                    openedFileId = null
                    openedFileName = null
                    openedFileMime = null
                    previewText = null
                }
            ) { Text("Back to files") }
            Text(openedFileName ?: "File preview", style = MaterialTheme.typography.titleLarge)
            Text(
                "Read-only preview · Files larger than 256 KiB are truncated for safety.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (previewLoading) {
                CircularProgressIndicator()
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceVariant
                ) {
                    Text(
                        previewText.orEmpty(),
                        modifier = Modifier.padding(14.dp),
                        style = MaterialTheme.typography.bodySmall,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(onClick = { folderPicker.launch(treeUri) }, modifier = Modifier.weight(1f)) {
                    Text("Change folder")
                }
                OutlinedButton(onClick = { refreshKey++ }, modifier = Modifier.weight(1f)) {
                    Text("Refresh")
                }
            }
            Text(
                directoryStack.joinToString(" / ") { it.name },
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Filter this folder") },
                singleLine = true
            )
            if (listingLoading) {
                CircularProgressIndicator()
            } else if (errorMessage != null) {
                WorkspaceMessage(errorMessage ?: "Could not load workspace", actionLabel = "Choose folder") {
                    folderPicker.launch(treeUri)
                }
            } else {
                val visibleEntries = entries.filter { it.displayName.contains(searchQuery.trim(), ignoreCase = true) }
                if (visibleEntries.isEmpty()) {
                    WorkspaceMessage(
                        if (entries.isEmpty()) "This folder is empty." else "No files match your filter.",
                        actionLabel = if (entries.isEmpty()) null else "Clear filter"
                    ) {
                        searchQuery = ""
                    }
                } else {
                    Text("${visibleEntries.size} item(s)", style = MaterialTheme.typography.labelMedium)
                    visibleEntries.forEach { entry ->
                        Card(
                            onClick = {
                                if (entry.isDirectory) {
                                    directoryStack.add(WorkspaceBreadcrumb(entry.documentId, entry.displayName))
                                    searchQuery = ""
                                } else {
                                    openedFileId = entry.documentId
                                    openedFileName = entry.displayName
                                    openedFileMime = entry.mimeType
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(14.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                AstraIcon(
                                    if (entry.isDirectory) "more" else "code",
                                    size = 24.dp,
                                    description = if (entry.isDirectory) "Folder" else "File"
                                )
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(3.dp)
                                ) {
                                    Text(entry.displayName, style = MaterialTheme.typography.titleSmall)
                                    Text(
                                        if (entry.isDirectory) "Folder" else formatSize(entry.sizeBytes),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    if (entry.isDirectory) "Open" else "Preview",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
            if (errorMessage != null && !listingLoading) {
                Text(errorMessage.orEmpty(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error)
            }
            OutlinedButton(
                onClick = {
                    repository.clearTreeUri()
                    treeUriString = null
                    entries = emptyList()
                }
            ) { Text("Forget workspace") }
        }
    }
}

@Composable
private fun WorkspaceMessage(
    message: String,
    actionLabel: String?,
    onAction: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceVariant
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(message, style = MaterialTheme.typography.bodyMedium)
            if (actionLabel != null) OutlinedButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

private fun formatSize(bytes: Long): String {
    if (bytes < 0) return "File"
    if (bytes < 1024) return "$bytes B"
    if (bytes < 1024 * 1024) return String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0)
    return String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0))
}

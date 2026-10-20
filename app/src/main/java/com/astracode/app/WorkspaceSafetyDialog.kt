package com.astracode.app

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Divider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.astracode.app.storage.WorkspacePreviewResult
import com.astracode.app.storage.WorkspaceRenameResult
import com.astracode.app.storage.WorkspaceSafety
import com.astracode.app.storage.WorkspaceSearchHit
import com.astracode.app.storage.WorkspaceSearchOptions
import com.astracode.app.storage.WorkspaceTrashRecord
import com.astracode.filesystem.WorkspaceStorage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale

@Composable
internal fun WorkspaceSafetyDialog(
    storage: WorkspaceStorage,
    currentDirectory: String,
    onDismiss: () -> Unit,
    onStorageChanged: () -> Unit,
    onRestored: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()
    var query by remember(storage) { mutableStateOf("") }
    var extension by remember(storage) { mutableStateOf("") }
    var minimumBytes by remember(storage) { mutableStateOf("") }
    var maximumBytes by remember(storage) { mutableStateOf("") }
    var includeHidden by remember(storage) { mutableStateOf(false) }
    var renameDraft by remember(storage) { mutableStateOf("") }
    var restoreDestination by remember(storage) { mutableStateOf("") }
    var resultsText by remember(storage) { mutableStateOf("") }
    var searchHits by remember(storage) { mutableStateOf(emptyList<WorkspaceSearchHit>()) }
    var trashItems by remember(storage) { mutableStateOf(emptyList<WorkspaceTrashRecord>()) }
    var pendingRenamePreview by remember(storage) { mutableStateOf<Map<String, String>?>(null) }
    var previewResult by remember(storage) { mutableStateOf<WorkspacePreviewResult?>(null) }
    var busy by remember(storage) { mutableStateOf(false) }

    val previewBitmap = remember(previewResult?.imageBytes) {
        previewResult?.imageBytes?.let { bytes ->
            runCatching { BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.asImageBitmap() }.getOrNull()
        }
    }

    fun refreshTrash() {
        scope.launch {
            runCatching { withContext(Dispatchers.IO) { WorkspaceSafety.listTrash(storage) } }
                .onSuccess { trashItems = it }
                .onFailure { error ->
                    resultsText = "Could not load Trash: " + (error.message ?: "storage error")
                }
        }
    }

    LaunchedEffect(storage) { refreshTrash() }

    AlertDialog(
        onDismissRequest = {
            if (pendingRenamePreview != null) {
                pendingRenamePreview = null
                resultsText = "Rename cancelled. No files were changed."
            } else {
                onDismiss()
            }
        },
        title = { Text(if (pendingRenamePreview != null) "Review batch rename" else "File safety tools") },
        text = {
            val renameReview = pendingRenamePreview
            Column(
                modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (renameReview != null) {
                    Text("Review every proposed change. Nothing is renamed until you confirm.")
                    renameReview.forEach { (old, new) -> Text(old + "  →  " + new) }
                } else {
                    Text("Search, preview, restore, and safely rename workspace items.")
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search text or path") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("workspace-safety-search-input"),
                    )
                    OutlinedTextField(
                        value = extension,
                        onValueChange = { extension = it },
                        label = { Text("Extension (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("workspace-safety-extension"),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedTextField(
                            value = minimumBytes,
                            onValueChange = { minimumBytes = it.filter(Char::isDigit) },
                            label = { Text("Min bytes") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("workspace-safety-min-bytes"),
                        )
                        OutlinedTextField(
                            value = maximumBytes,
                            onValueChange = { maximumBytes = it.filter(Char::isDigit) },
                            label = { Text("Max bytes") },
                            singleLine = true,
                            modifier = Modifier.weight(1f).testTag("workspace-safety-max-bytes"),
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = includeHidden,
                            onCheckedChange = { includeHidden = it },
                            modifier = Modifier.testTag("workspace-safety-include-hidden"),
                        )
                        Text("Include hidden items")
                    }
                    Button(
                        onClick = {
                            scope.launch {
                                busy = true
                                previewResult = null
                                val result = runCatching {
                                    withContext(Dispatchers.IO) {
                                        WorkspaceSafety.search(
                                            storage,
                                            WorkspaceSearchOptions(
                                                query = query,
                                                extension = extension.trim().takeIf { it.isNotEmpty() },
                                                minSizeBytes = minimumBytes.toLongOrNull(),
                                                maxSizeBytes = maximumBytes.toLongOrNull(),
                                                includeHidden = includeHidden,
                                            ),
                                        )
                                    }
                                }
                                result.onSuccess { hits ->
                                    searchHits = hits
                                    resultsText = if (hits.isEmpty()) "No matching files."
                                    else "Found " + hits.size + " file(s):\n" +
                                        hits.joinToString("\n") { it.path + " · " + it.sizeBytes + " bytes" }
                                }.onFailure { error ->
                                    searchHits = emptyList()
                                    resultsText = "Search failed: " + (error.message ?: "storage error")
                                }
                                busy = false
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().testTag("workspace-safety-search"),
                    ) { Text("Search") }

                    if (searchHits.isNotEmpty()) {
                        searchHits.forEachIndexed { index, hit ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(hit.path, modifier = Modifier.weight(1f))
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            busy = true
                                            runCatching {
                                                withContext(Dispatchers.IO) { WorkspaceSafety.preview(storage, hit.path) }
                                            }.onSuccess {
                                                previewResult = it
                                                resultsText = "Read-only preview: " + it.path + ". The original was not changed."
                                            }.onFailure { error ->
                                                resultsText = "Preview failed: " + (error.message ?: "storage error")
                                            }
                                            busy = false
                                        }
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.testTag("workspace-preview-result-" + index),
                                ) { Text("Preview") }
                            }
                        }
                    }

                    previewResult?.let { preview ->
                        Divider()
                        Text("Preview · " + preview.path, modifier = Modifier.testTag("workspace-safety-preview"))
                        Text(preview.message)
                        if (previewBitmap != null) {
                            Image(
                                bitmap = previewBitmap,
                                contentDescription = "Read-only image preview",
                                modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp),
                            )
                        } else if (preview.text != null) {
                            Text(
                                preview.text.take(20_000),
                                modifier = Modifier.fillMaxWidth().testTag("workspace-safety-preview-text"),
                            )
                        }
                        TextButton(
                            onClick = { previewResult = null },
                            modifier = Modifier.testTag("workspace-safety-preview-close"),
                        ) { Text("Close preview") }
                    }

                    if (resultsText.isNotBlank()) {
                        Text(resultsText, modifier = Modifier.fillMaxWidth().testTag("workspace-safety-results"))
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    runCatching { withContext(Dispatchers.IO) { WorkspaceSafety.findDuplicates(storage) } }
                                        .onSuccess { scan ->
                                            resultsText = if (scan.groups.isEmpty()) {
                                                "No exact duplicates found. Files skipped: " + scan.skippedFiles + "."
                                            } else {
                                                "Exact duplicate groups:\n" +
                                                    scan.groups.joinToString("\n\n") { group ->
                                                        "SHA-256 " + group.sha256 + "\n" + group.sizeBytes +
                                                            " bytes\n" + group.paths.joinToString("\n")
                                                    } + "\nFiles skipped: " + scan.skippedFiles + "."
                                            }
                                        }.onFailure { error ->
                                            resultsText = "Duplicate scan failed: " + (error.message ?: "storage error")
                                        }
                                    busy = false
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f).testTag("workspace-safety-duplicates"),
                        ) { Text("Find duplicates") }
                        Button(
                            onClick = {
                                scope.launch {
                                    busy = true
                                    runCatching { withContext(Dispatchers.IO) { WorkspaceSafety.healthReport(storage) } }
                                        .onSuccess { report ->
                                            resultsText = "Workspace size: " + report.totalBytes + " bytes\n" +
                                                "Files: " + report.fileCount + "\nFolders: " + report.directoryCount + "\n" +
                                                "Largest files:\n" + report.largestFiles.joinToString("\n") {
                                                    it.first + " · " + it.second + " bytes"
                                                }
                                        }.onFailure { error ->
                                            resultsText = "Storage scan failed: " + (error.message ?: "storage error")
                                        }
                                    busy = false
                                }
                            },
                            enabled = !busy,
                            modifier = Modifier.weight(1f).testTag("workspace-safety-health"),
                        ) { Text("Storage health") }
                    }

                    Divider()
                    Text("Batch rename for the current folder. Enter one old-name=new-name mapping per line.")
                    OutlinedTextField(
                        value = renameDraft,
                        onValueChange = { renameDraft = it },
                        label = { Text("Rename mappings") },
                        modifier = Modifier.fillMaxWidth().testTag("workspace-batch-rename-input"),
                    )
                    Button(
                        onClick = {
                            val lines = renameDraft.lines().filter { it.isNotBlank() }
                            val pairs = lines.mapNotNull { line ->
                                val separator = line.indexOf('=')
                                if (separator <= 0 || separator == line.lastIndex) null
                                else line.substring(0, separator).trim() to line.substring(separator + 1).trim()
                            }
                            val proposed = linkedMapOf<String, String>()
                            pairs.forEach { (old, new) -> proposed[old] = new }
                            val currentNames = runCatching {
                                storage.listDirectory(currentDirectory).map { it.name }.toSet()
                            }.getOrElse { emptySet() }
                            val duplicateTargets = proposed.values.map { it.lowercase(Locale.ROOT) }.distinct().size != proposed.size
                            val targetConflict = proposed.values.any { target ->
                                currentNames.any { it.equals(target, ignoreCase = true) } &&
                                    proposed.keys.none { it.equals(target, ignoreCase = true) }
                            }
                            when {
                                lines.isEmpty() || pairs.size != lines.size || proposed.size != pairs.size ->
                                    resultsText = "Each rename must be a unique old-name=new-name line."
                                duplicateTargets -> resultsText = "Two items cannot be renamed to the same destination."
                                targetConflict -> resultsText = "A destination name already exists. Resolve the conflict before continuing."
                                else -> {
                                    pendingRenamePreview = proposed
                                    resultsText = "Review the proposed changes below. No files have been renamed yet."
                                }
                            }
                        },
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth().testTag("workspace-batch-rename"),
                    ) { Text("Preview rename") }

                    Divider()
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Trash · " + trashItems.size + " item(s)", modifier = Modifier.weight(1f))
                        TextButton(
                            onClick = { refreshTrash() },
                            enabled = !busy,
                            modifier = Modifier.testTag("workspace-refresh-trash"),
                        ) { Text("Refresh") }
                    }
                    OutlinedTextField(
                        value = restoreDestination,
                        onValueChange = { restoreDestination = it },
                        label = { Text("Alternate restore path (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("workspace-restore-destination"),
                    )
                    if (trashItems.isEmpty()) {
                        Text("Trash is empty.")
                    } else {
                        trashItems.forEach { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(item.originalPath, modifier = Modifier.weight(1f))
                                TextButton(
                                    onClick = {
                                        scope.launch {
                                            busy = true
                                            val destination = restoreDestination.trim().takeIf { it.isNotEmpty() }
                                            runCatching {
                                                withContext(Dispatchers.IO) {
                                                    WorkspaceSafety.restoreFromTrash(storage, item.id, destination)
                                                }
                                            }.onSuccess { restoredPath ->
                                                trashItems = trashItems.filterNot { it.id == item.id }
                                                onRestored(restoredPath)
                                                onStorageChanged()
                                                resultsText = "Restored " + restoredPath
                                                restoreDestination = ""
                                            }.onFailure { error ->
                                                resultsText = "Restore failed: " + (error.message ?: "storage error") +
                                                    ". The item remains in Trash."
                                            }
                                            busy = false
                                        }
                                    },
                                    enabled = !busy,
                                    modifier = Modifier.testTag("workspace-trash-restore-" + item.id),
                                ) { Text("Restore") }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (pendingRenamePreview != null) {
                TextButton(
                    onClick = {
                        val proposed = pendingRenamePreview.orEmpty().toMap()
                        scope.launch {
                            busy = true
                            runCatching {
                                withContext(Dispatchers.IO) {
                                    WorkspaceSafety.batchRename(storage, currentDirectory, proposed)
                                }
                            }.onSuccess { renamed: List<WorkspaceRenameResult> ->
                                pendingRenamePreview = null
                                renameDraft = ""
                                searchHits = emptyList()
                                onStorageChanged()
                                resultsText = "Renamed " + renamed.size + " item(s):\n" +
                                    renamed.joinToString("\n") { it.from + " → " + it.to }
                            }.onFailure { error ->
                                resultsText = "Batch rename failed: " + (error.message ?: "storage error")
                            }
                            busy = false
                        }
                    },
                    enabled = !busy,
                    modifier = Modifier.testTag("workspace-batch-rename-confirm"),
                ) { Text("Confirm rename") }
            } else {
                TextButton(onClick = onDismiss, modifier = Modifier.testTag("workspace-safety-close")) { Text("Close") }
            }
        },
        dismissButton = {
            if (pendingRenamePreview != null) {
                TextButton(
                    onClick = {
                        pendingRenamePreview = null
                        resultsText = "Rename cancelled. No files were changed."
                    },
                    enabled = !busy,
                    modifier = Modifier.testTag("workspace-batch-rename-cancel"),
                ) { Text("Cancel") }
            }
        },
    )
}

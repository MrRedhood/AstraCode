package com.astracode.app

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.astracode.filesystem.FileEntry
import com.astracode.filesystem.FileEntryType
import com.astracode.filesystem.WorkspaceStorage
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private data class WorkspaceTreeVisibleEntry(
    val entry: FileEntry,
    val depth: Int,
    val ancestorHasNext: List<Boolean>,
)

@Composable
internal fun WorkspaceTreeDrawer(
    storage: WorkspaceStorage,
    currentDirectory: String,
    openedFile: String?,
    onNavigateToDirectory: (String) -> Unit,
    onSelectEntry: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    var childrenByDirectory by remember(storage) {
        mutableStateOf(emptyMap<String, List<FileEntry>>())
    }
    var expandedDirectories by remember(storage) { mutableStateOf(setOf("")) }
    var loadingDirectories by remember(storage) { mutableStateOf(emptySet<String>()) }
    var loadErrors by remember(storage) { mutableStateOf(emptyMap<String, String>()) }
    val scope = rememberCoroutineScope()

    fun requestChildren(directory: String) {
        if (directory in childrenByDirectory || directory in loadingDirectories) return
        loadingDirectories = loadingDirectories + directory
        scope.launch {
            try {
                val entries = withContext(Dispatchers.IO) {
                    storage.listDirectory(directory).sortedWith(
                        compareBy<FileEntry>(
                            { it.type != FileEntryType.DIRECTORY },
                            { it.name.lowercase() },
                        ),
                    )
                }
                childrenByDirectory = childrenByDirectory + (directory to entries)
                loadErrors = loadErrors - directory
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                loadErrors = loadErrors + (
                    directory to (error.message ?: "Could not load this folder.")
                )
            } finally {
                loadingDirectories = loadingDirectories - directory
            }
        }
    }

    fun toggleDirectory(path: String) {
        if (path in expandedDirectories) {
            expandedDirectories = expandedDirectories - path
        } else {
            expandedDirectories = expandedDirectories + path
            requestChildren(path)
        }
    }

    LaunchedEffect(storage) {
        requestChildren("")
    }

    val visibleEntries = remember(childrenByDirectory, expandedDirectories) {
        buildList {
            fun appendChildren(
                directory: String,
                depth: Int,
                ancestorHasNext: List<Boolean>,
                ancestry: Set<String>,
            ) {
                val entries = childrenByDirectory[directory].orEmpty()
                entries.forEachIndexed { index, entry ->
                    add(WorkspaceTreeVisibleEntry(entry, depth, ancestorHasNext))
                    if (
                        entry.type == FileEntryType.DIRECTORY &&
                        entry.path in expandedDirectories &&
                        entry.path !in ancestry
                    ) {
                        appendChildren(
                            directory = entry.path,
                            depth = depth + 1,
                            ancestorHasNext = ancestorHasNext + (index < entries.lastIndex),
                            ancestry = ancestry + entry.path,
                        )
                    }
                }
            }
            if ("" in expandedDirectories) {
                appendChildren("", depth = 1, ancestorHasNext = listOf(true), ancestry = setOf(""))
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.48f)),
        ) {
            Row(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 360.dp)
                        .fillMaxWidth(0.88f)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "Workspace",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.testTag("workspace-tree-title"),
                            )
                            Text(
                                storage.displayName.ifBlank { "Workspace root" },
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("workspace-tree-close"),
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Close workspace tree")
                        }
                    }
                    Divider()
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth().testTag("workspace-tree-list"),
                    ) {
                        item(key = "workspace-tree-root") {
                            WorkspaceTreeRow(
                                path = "",
                                name = "Workspace",
                                isDirectory = true,
                                depth = 0,
                                ancestorHasNext = emptyList(),
                                expanded = "" in expandedDirectories,
                                selected = currentDirectory.isBlank() && openedFile == null,
                                isRoot = true,
                                onToggleExpansion = { toggleDirectory("") },
                                onSelect = { onNavigateToDirectory("") },
                            )
                        }
                        items(visibleEntries, key = { it.entry.path }) { visible ->
                            val entry = visible.entry
                            val isDirectory = entry.type == FileEntryType.DIRECTORY
                            WorkspaceTreeRow(
                                path = entry.path,
                                name = entry.name,
                                isDirectory = isDirectory,
                                depth = visible.depth,
                                ancestorHasNext = visible.ancestorHasNext,
                                expanded = entry.path in expandedDirectories,
                                selected = entry.path == currentDirectory || entry.path == openedFile,
                                onToggleExpansion = { toggleDirectory(entry.path) },
                                onSelect = {
                                    if (isDirectory) onNavigateToDirectory(entry.path)
                                    else onSelectEntry(entry.path)
                                },
                            )
                        }
                    }
                    loadErrors.values.firstOrNull()?.let { errorMessage ->
                        Text(
                            errorMessage,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("workspace-tree-error"),
                        )
                    }
                    if (loadingDirectories.isNotEmpty()) {
                        Text(
                            "Loading folders…",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.testTag("workspace-tree-loading"),
                        )
                    }
                }
                Spacer(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(onClick = onDismiss),
                )
            }
        }
    }
}

@Composable
private fun WorkspaceTreeRow(
    path: String,
    name: String,
    isDirectory: Boolean,
    depth: Int,
    ancestorHasNext: List<Boolean>,
    expanded: Boolean,
    selected: Boolean,
    isRoot: Boolean = false,
    onToggleExpansion: () -> Unit,
    onSelect: () -> Unit,
) {
    val connectorColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(
                if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.14f)
                else Color.Transparent,
            )
            .clickable(onClick = onSelect)
            .testTag(
                if (isRoot) "workspace-tree-root"
                else "workspace-tree-${if (isDirectory) "folder" else "file"}-${path.replace("/", "-")}",
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (depth > 0) {
            Canvas(
                modifier = Modifier
                    .width((depth * 16).dp)
                    .height(42.dp),
            ) {
                val step = 16.dp.toPx()
                val centerY = size.height / 2f
                val stroke = 1.dp.toPx()
                ancestorHasNext.forEachIndexed { index, hasNext ->
                    drawLine(
                        color = connectorColor,
                        start = Offset((index + 0.5f) * step, 0f),
                        end = Offset(
                            (index + 0.5f) * step,
                            if (hasNext) size.height else centerY,
                        ),
                        strokeWidth = stroke,
                    )
                }
                drawLine(
                    color = connectorColor,
                    start = Offset((depth - 0.5f) * step, centerY),
                    end = Offset(depth * step, centerY),
                    strokeWidth = stroke,
                )
            }
        }
        if (isDirectory) {
            IconButton(
                onClick = onToggleExpansion,
                modifier = Modifier
                    .size(32.dp)
                    .testTag(
                        "workspace-tree-expand-${if (isRoot) "root" else path.replace("/", "-")}",
                    ),
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandMore else Icons.Filled.ChevronRight,
                    contentDescription = if (expanded) "Collapse $name" else "Expand $name",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            Spacer(Modifier.width(32.dp))
        }
        Icon(
            imageVector = when {
                !isDirectory -> Icons.Filled.Description
                expanded -> Icons.Filled.FolderOpen
                else -> Icons.Filled.Folder
            },
            contentDescription = null,
            tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp),
        )
        Text(
            name,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 9.dp, end = 6.dp).weight(1f),
        )
    }
}

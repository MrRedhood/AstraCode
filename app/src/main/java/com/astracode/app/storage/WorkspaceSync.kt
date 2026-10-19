package com.astracode.app.storage

import android.content.Context
import com.astracode.filesystem.FileEntry
import com.astracode.filesystem.FileEntryType
import com.astracode.filesystem.WorkspaceStorage
import java.io.IOException
import org.json.JSONArray
import org.json.JSONObject

internal data class WorkspaceSyncLink(
    val privatePath: String,
    val externalTreeUri: String,
    val externalPath: String,
)

private const val WORKSPACE_SYNC_LINKS_KEY = "workspace_sync_links"

internal fun loadWorkspaceSyncLinks(context: Context): List<WorkspaceSyncLink> {
    val raw = context.getSharedPreferences("astracode_workspace", Context.MODE_PRIVATE)
        .getString(WORKSPACE_SYNC_LINKS_KEY, null) ?: return emptyList()
    return runCatching {
        val array = JSONArray(raw)
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val local = item.optString("privatePath")
            val uri = item.optString("externalTreeUri")
            val remote = item.optString("externalPath")
            if (local.isBlank() || uri.isBlank() || remote.isBlank()) null
            else WorkspaceSyncLink(local, uri, remote)
        }
    }.getOrDefault(emptyList())
}

internal fun saveWorkspaceSyncLinks(context: Context, links: List<WorkspaceSyncLink>) {
    val array = JSONArray()
    links.distinctBy { Triple(it.privatePath, it.externalTreeUri, it.externalPath) }
        .forEach { link ->
            array.put(JSONObject()
                .put("privatePath", link.privatePath)
                .put("externalTreeUri", link.externalTreeUri)
                .put("externalPath", link.externalPath))
        }
    context.getSharedPreferences("astracode_workspace", Context.MODE_PRIVATE)
        .edit().putString(WORKSPACE_SYNC_LINKS_KEY, array.toString()).apply()
}

internal fun matchingPrivateSyncLink(links: List<WorkspaceSyncLink>, privatePath: String): WorkspaceSyncLink? =
    links.asSequence()
        .filter { privatePath == it.privatePath || privatePath.startsWith(it.privatePath + "/") }
        .maxByOrNull { it.privatePath.length }

internal fun matchingExternalSyncLink(
    links: List<WorkspaceSyncLink>,
    treeUri: String,
    externalPath: String,
): WorkspaceSyncLink? = links.asSequence()
    .filter {
        it.externalTreeUri == treeUri &&
            (externalPath == it.externalPath || externalPath.startsWith(it.externalPath + "/"))
    }
    .maxByOrNull { it.externalPath.length }

internal fun mappedChildPath(linkRoot: String, childPath: String): String {
    val suffix = childPath.removePrefix(linkRoot).trimStart('/')
    return if (suffix.isEmpty()) linkRoot else "$linkRoot/$suffix"
}

/**
 * Copies one file or mirrors a folder between any two workspace adapters.
 *
 * With overwriteExisting=true, the destination becomes an exact snapshot of the source
 * subtree: changed files are replaced and destination-only children are removed.
 * With overwriteExisting=false, the operation refuses to replace an existing item.
 */
internal fun syncWorkspaceEntry(
    sourceStorage: WorkspaceStorage,
    sourcePath: String,
    targetStorage: WorkspaceStorage,
    targetPath: String,
    overwriteExisting: Boolean = true,
) {
    val source = findEntry(sourceStorage, sourcePath)
        ?: throw IOException("The sync source no longer exists: $sourcePath")
    if (source.type == FileEntryType.SYMBOLIC_LINK || source.type == FileEntryType.OTHER) {
        throw IOException("This item type cannot be synced safely: $sourcePath")
    }
    if (sourceStorage === targetStorage && sourcePath == targetPath) {
        throw IOException("Source and destination are the same item.")
    }
    val target = findEntry(targetStorage, targetPath)
    if (target != null && !overwriteExisting) {
        throw IOException("The destination already exists: $targetPath")
    }
    mirrorNode(sourceStorage, source, targetStorage, targetPath, target, overwriteExisting, 0)
}

private fun mirrorNode(
    sourceStorage: WorkspaceStorage,
    sourceEntry: FileEntry,
    targetStorage: WorkspaceStorage,
    targetPath: String,
    targetEntry: FileEntry?,
    overwriteExisting: Boolean,
    depth: Int,
) {
    if (depth > 128) throw IOException("The folder tree is too deep to sync safely.")
    when (sourceEntry.type) {
        FileEntryType.DIRECTORY -> {
            if (targetEntry != null && targetEntry.type != FileEntryType.DIRECTORY) {
                if (!overwriteExisting) throw IOException("The destination has a different item type: $targetPath")
                targetStorage.delete(targetPath, recursive = true)
            }
            if (targetEntry?.type != FileEntryType.DIRECTORY) {
                targetStorage.createDirectory(targetPath)
            }

            val sourceChildren = sourceStorage.listDirectory(sourceEntry.path)
            val targetChildren = targetStorage.listDirectory(targetPath)
            val sourceNames = sourceChildren.map { it.name }.toSet()
            if (overwriteExisting) {
                targetChildren.filterNot { it.name in sourceNames }.forEach { extra ->
                    targetStorage.delete(extra.path, recursive = true)
                }
            }

            sourceChildren.forEach { child ->
                if (child.type == FileEntryType.SYMBOLIC_LINK || child.type == FileEntryType.OTHER) {
                    throw IOException("This item type cannot be synced safely: " + child.path)
                }
                val childTargetPath = joinWorkspacePath(targetPath, child.name)
                val childTarget = findEntry(targetStorage, childTargetPath)
                if (childTarget != null && !overwriteExisting) {
                    throw IOException("The destination already exists: $childTargetPath")
                }
                mirrorNode(
                    sourceStorage = sourceStorage,
                    sourceEntry = child,
                    targetStorage = targetStorage,
                    targetPath = childTargetPath,
                    targetEntry = childTarget,
                    overwriteExisting = overwriteExisting,
                    depth = depth + 1,
                )
            }
        }
        FileEntryType.FILE -> {
            if (targetEntry != null && targetEntry.type != FileEntryType.FILE) {
                if (!overwriteExisting) throw IOException("The destination has a different item type: $targetPath")
                targetStorage.delete(targetPath, recursive = true)
            }
            val bytes = sourceStorage.readBytes(sourceEntry.path)
            targetStorage.writeBytes(targetPath, bytes)
        }
        FileEntryType.SYMBOLIC_LINK, FileEntryType.OTHER ->
            throw IOException("This item type cannot be synced safely: " + sourceEntry.path)
    }
}

private fun findEntry(storage: WorkspaceStorage, path: String): FileEntry? {
    if (path.isBlank() || path.startsWith("/") || path.contains('\\') ||
        path.split('/').any { it.isBlank() || it == "." || it == ".." }
    ) {
        throw SecurityException("Only valid workspace-relative paths can be synced.")
    }
    if (!storage.exists(path)) return null
    val parent = path.substringBeforeLast('/', "")
    val name = path.substringAfterLast('/')
    return storage.listDirectory(parent).firstOrNull { it.name == name }
        ?: throw IOException("The storage provider reported an item but did not list it: $path")
}

private fun joinWorkspacePath(parent: String, child: String): String =
    if (parent.isEmpty()) child else "$parent/$child"

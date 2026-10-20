package com.astracode.app.storage

import com.astracode.filesystem.FileEntry
import com.astracode.filesystem.FileEntryType
import com.astracode.filesystem.WorkspaceStorage
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Base64
import java.util.Locale
import java.util.UUID
import java.util.concurrent.CancellationException

internal data class WorkspaceTrashRecord(val id: String, val originalPath: String, val deletedAtMillis: Long)
internal data class WorkspacePreviewResult(val path: String, val sizeBytes: Long, val text: String? = null, val imageBytes: ByteArray? = null, val supported: Boolean, val message: String)
internal data class WorkspaceSearchOptions(val query: String = "", val extension: String? = null, val minSizeBytes: Long? = null, val maxSizeBytes: Long? = null, val modifiedAfterMillis: Long? = null, val includeHidden: Boolean = false, val maxResults: Int = 5000) {
    init {
        require(minSizeBytes == null || minSizeBytes >= 0)
        require(maxSizeBytes == null || maxSizeBytes >= 0)
        require(minSizeBytes == null || maxSizeBytes == null || minSizeBytes <= maxSizeBytes)
        require(maxResults in 1..20_000)
    }
}
internal data class WorkspaceSearchHit(val path: String, val name: String, val type: FileEntryType, val sizeBytes: Long, val modifiedAtMillis: Long)
internal data class WorkspaceDuplicateGroup(val sha256: String, val sizeBytes: Long, val paths: List<String>)
internal data class WorkspaceDuplicateScan(val groups: List<WorkspaceDuplicateGroup>, val skippedFiles: Int)
internal data class WorkspaceHealthReport(val totalBytes: Long, val fileCount: Int, val directoryCount: Int, val largestFiles: List<Pair<String, Long>>)
internal data class WorkspaceRenameResult(val from: String, val to: String)

/** Provider-aware file safety services. Journal entries record paths and outcomes, never contents. */
internal object WorkspaceSafety {
    const val TRASH_DIRECTORY = ".astracode-trash"
    const val OPERATIONS_DIRECTORY = ".astracode-operations"
    const val DEFAULT_RETENTION_MILLIS = 30L * 24 * 60 * 60 * 1000
    const val DEFAULT_PREVIEW_LIMIT_BYTES = 4L * 1024 * 1024
    private const val MAX_DEPTH = 128
    private const val MAX_ITEMS = 20_000
    private val reservedRoots = setOf(TRASH_DIRECTORY, OPERATIONS_DIRECTORY)
    private val lockMonitor = Object()
    private val activeLocks = mutableSetOf<String>()
    private val textExtensions = setOf("txt", "json", "xml", "kt", "kts", "java", "c", "cc", "cpp", "h", "hpp", "sh", "py", "js", "mjs", "ts", "tsx", "jsx", "html", "css", "yaml", "yml", "toml", "properties", "gradle", "csv", "ini", "conf", "sql", "rs", "go", "swift")
    private val imageExtensions = setOf("png", "jpg", "jpeg", "webp", "gif", "bmp")

    fun isReservedPath(path: String): Boolean = path.substringBefore('/').let { it in reservedRoots }

    fun validatePath(path: String, allowEmpty: Boolean = false): String {
        if (path.isEmpty() && allowEmpty) return path
        if (path.isBlank() || path.startsWith("/") || path.contains('\\') || path.indexOf('\u0000') >= 0) throw SecurityException("Only workspace-relative paths are allowed.")
        val parts = path.split('/')
        if (parts.any { it.isBlank() || it == "." || it == ".." } || parts.size > MAX_DEPTH) throw SecurityException("Invalid or excessively deep workspace path.")
        if (isReservedPath(path)) throw SecurityException("This path is reserved for workspace recovery data.")
        return parts.joinToString("/")
    }

    private fun entry(storage: WorkspaceStorage, path: String): FileEntry? {
        val normalized = validatePath(path)
        if (!storage.exists(normalized)) return null
        val parts = normalized.split('/')
        var parent = ""
        var found: FileEntry? = null
        parts.forEachIndexed { index, part ->
            val child = storage.listDirectory(parent).firstOrNull { it.name == part } ?: return null
            if (index < parts.lastIndex && child.type != FileEntryType.DIRECTORY) throw IOException("Not a folder: $parent")
            found = child
            parent = join(parent, part)
        }
        return found
    }

    private fun ensureDirectory(storage: WorkspaceStorage, path: String) {
        if (path.isEmpty()) { storage.listDirectory(""); return }
        if (storage.exists(path)) {
            if (entry(storage, path)?.type != FileEntryType.DIRECTORY) throw IOException("Not a folder: $path")
            return
        }
        val parent = parent(path)
        if (parent.isNotEmpty()) ensureDirectory(storage, parent)
        storage.createDirectory(path)
    }

    private fun writeMetadata(storage: WorkspaceStorage, path: String, record: WorkspaceTrashRecord) {
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(record.originalPath.toByteArray(Charsets.UTF_8))
        storage.writeText(path, "id=${record.id}\npath=$encoded\ndeletedAtMillis=${record.deletedAtMillis}\n")
    }

    private fun parseMetadata(storage: WorkspaceStorage, path: String): WorkspaceTrashRecord? = runCatching {
        val values = storage.readText(path).text.lineSequence().mapNotNull { line ->
            val index = line.indexOf('=')
            if (index <= 0) null else line.substring(0, index) to line.substring(index + 1)
        }.toMap()
        val id = values["id"] ?: return@runCatching null
        val encodedPath = values["path"] ?: return@runCatching null
        val original = String(Base64.getUrlDecoder().decode(encodedPath), Charsets.UTF_8)
        val timestamp = values["deletedAtMillis"]?.toLongOrNull() ?: return@runCatching null
        if (!id.matches(Regex("[0-9]+-[0-9a-fA-F-]{36}"))) return@runCatching null
        WorkspaceTrashRecord(id, original, timestamp)
    }.getOrNull()

    fun moveToTrash(storage: WorkspaceStorage, path: String, nowMillis: Long = System.currentTimeMillis()): WorkspaceTrashRecord =
        withPathLocks(storage, listOf(path)) {
            val normalized = validatePath(path)
            val item = entry(storage, normalized) ?: throw IOException("Selected item no longer exists: $normalized")
            if (item.type != FileEntryType.FILE && item.type != FileEntryType.DIRECTORY) throw IOException("This item type cannot be moved to Trash.")
            ensureDirectory(storage, "$TRASH_DIRECTORY/items")
            val id = "$nowMillis-${UUID.randomUUID()}"
            val recordDir = "$TRASH_DIRECTORY/items/$id"
            storage.createDirectory(recordDir)
            val metadataPath = "$recordDir/item.properties"
            val payloadPath = "$recordDir/payload"
            try {
                writeMetadata(storage, metadataPath, WorkspaceTrashRecord(id, normalized, nowMillis))
                storage.move(normalized, payloadPath, replaceExisting = false)
                recordOperation(storage, "delete-to-trash", listOf(normalized), "success")
                WorkspaceTrashRecord(id, normalized, nowMillis)
            } catch (error: Exception) {
                if (!storage.exists(payloadPath)) runCatching { storage.delete(recordDir, recursive = true) }
                recordOperation(storage, "delete-to-trash", listOf(normalized), "failure")
                throw error
            }
        }

    fun listTrash(storage: WorkspaceStorage): List<WorkspaceTrashRecord> {
        val itemsPath = "$TRASH_DIRECTORY/items"
        if (!storage.exists(itemsPath)) return emptyList()
        return storage.listDirectory(itemsPath)
            .filter { it.type == FileEntryType.DIRECTORY && it.name.matches(Regex("[0-9]+-[0-9a-fA-F-]{36}")) }
            .mapNotNull { folder ->
                val record = parseMetadata(storage, "$itemsPath/${folder.name}/item.properties") ?: return@mapNotNull null
                if (record.id != folder.name || !storage.exists("$itemsPath/${folder.name}/payload")) null else record
            }.sortedByDescending { it.deletedAtMillis }
    }

    fun restoreFromTrash(storage: WorkspaceStorage, id: String, destinationPath: String? = null): String {
        if (!id.matches(Regex("[0-9]+-[0-9a-fA-F-]{36}"))) throw SecurityException("Invalid Trash record.")
        val recordDir = "$TRASH_DIRECTORY/items/$id"
        val record = parseMetadata(storage, "$recordDir/item.properties") ?: throw IOException("Trash record is damaged.")
        if (record.id != id || !storage.exists("$recordDir/payload")) throw IOException("Trash item is incomplete.")
        val destination = validatePath(destinationPath ?: record.originalPath)
        return withPathLocks(storage, listOf(destination)) {
            if (storage.exists(destination)) throw IOException("Cannot restore because the destination already exists: $destination")
            val parentPath = parent(destination)
            if (parentPath.isNotEmpty()) ensureDirectory(storage, parentPath)
            try { storage.move("$recordDir/payload", destination, replaceExisting = false) }
            catch (error: Exception) { throw IOException("Could not restore the item. It remains in Trash.", error) }
            runCatching { storage.delete(recordDir, recursive = true) }
            recordOperation(storage, "restore-from-trash", listOf(destination), "success")
            destination
        }
    }

    fun cleanupExpiredTrash(storage: WorkspaceStorage, nowMillis: Long = System.currentTimeMillis(), retentionMillis: Long = DEFAULT_RETENTION_MILLIS): Int {
        require(retentionMillis >= 0)
        var removed = 0
        listTrash(storage).filter { it.deletedAtMillis <= nowMillis - retentionMillis }.forEach { record ->
            runCatching { storage.delete("$TRASH_DIRECTORY/items/${record.id}", recursive = true) }.onSuccess { removed++ }
        }
        return removed
    }

    fun recordOperation(storage: WorkspaceStorage, operation: String, paths: List<String>, status: String) {
        if (!operation.matches(Regex("[a-z0-9-]{1,40}")) || status !in setOf("success", "failure", "cancelled")) return
        runCatching {
            ensureDirectory(storage, "$OPERATIONS_DIRECTORY/journal")
            val id = "${System.currentTimeMillis()}-${UUID.randomUUID()}"
            val encoded = paths.joinToString("\n") { Base64.getUrlEncoder().withoutPadding().encodeToString(it.toByteArray(Charsets.UTF_8)) }
            storage.writeText("$OPERATIONS_DIRECTORY/journal/$id.entry", "operation=$operation\nstatus=$status\ntime=${System.currentTimeMillis()}\npaths=$encoded\n")
        }
    }

    fun cleanupExpiredJournal(storage: WorkspaceStorage, nowMillis: Long = System.currentTimeMillis(), retentionMillis: Long = DEFAULT_RETENTION_MILLIS): Int {
        require(retentionMillis >= 0)
        val path = "$OPERATIONS_DIRECTORY/journal"
        if (!storage.exists(path)) return 0
        var removed = 0
        storage.listDirectory(path).filter { it.type == FileEntryType.FILE && it.name.matches(Regex("[0-9]+-[0-9a-fA-F-]{36}\\.entry")) }.forEach { item ->
            val modified = item.modifiedAt.toEpochMilli()
            if (modified > 0 && modified <= nowMillis - retentionMillis) runCatching { storage.delete("$path/${item.name}") }.onSuccess { removed++ }
        }
        return removed
    }

    /** Verify a sibling temporary document before replacement and attempt rollback if the provider rejects it. */
    fun writeVerified(storage: WorkspaceStorage, path: String, bytes: ByteArray, cancelled: () -> Boolean = { false }) =
        withPathLocks(storage, listOf(path)) {
            val normalized = validatePath(path)
            if (cancelled()) throw CancellationException("Write cancelled before starting.")
            val parentPath = parent(normalized)
            if (parentPath.isNotEmpty()) ensureDirectory(storage, parentPath)
            val temporary = join(parentPath, ".astracode-tmp-${UUID.randomUUID()}")
            val backup = join(parentPath, ".astracode-backup-${UUID.randomUUID()}")
            try {
                storage.writeBytes(temporary, bytes)
                if (cancelled()) throw CancellationException("Write cancelled before publication.")
                if (!storage.readBytes(temporary).contentEquals(bytes)) throw IOException("Temporary file failed integrity verification.")
                if (storage.exists(normalized)) {
                    storage.move(normalized, backup, replaceExisting = false)
                    try { storage.move(temporary, normalized, replaceExisting = false) }
                    catch (error: Exception) {
                        try {
                            if (!storage.exists(normalized) && storage.exists(backup)) storage.move(backup, normalized, replaceExisting = false)
                        } catch (restore: Exception) {
                            error.addSuppressed(restore)
                            throw IOException("Write failed; the previous file remains in hidden backup $backup.", error)
                        }
                        throw error
                    }
                    runCatching { if (storage.exists(backup)) storage.delete(backup, recursive = true) }
                } else {
                    if (storage.exists(normalized)) throw IOException("Destination appeared during write.")
                    storage.move(temporary, normalized, replaceExisting = false)
                }
                recordOperation(storage, "write-verified", listOf(normalized), "success")
            } catch (error: Exception) {
                runCatching { if (storage.exists(temporary)) storage.delete(temporary, recursive = true) }
                recordOperation(storage, "write-verified", listOf(normalized), if (error is CancellationException) "cancelled" else "failure")
                throw error
            }
        }

    fun copyVerified(storage: WorkspaceStorage, sourcePath: String, destinationPath: String, cancelled: () -> Boolean = { false }, progress: (Long, Long) -> Unit = { _, _ -> }, replaceExisting: Boolean = false) =
        withPathLocks(storage, listOf(sourcePath, destinationPath)) {
            val source = validatePath(sourcePath)
            val destination = validatePath(destinationPath)
            if (source == destination) throw IOException("Source and destination are the same item.")
            if (entry(storage, source)?.type != FileEntryType.FILE) throw IOException("Copy source is not a regular file.")
            if (entry(storage, destination)?.type == FileEntryType.DIRECTORY) throw IOException("Copy destination is a folder.")
            if (storage.exists(destination) && !replaceExisting) throw IOException("Destination already exists: $destination")
            if (cancelled()) throw CancellationException("Copy cancelled before reading the source.")
            val bytes = storage.readBytes(source)
            val expected = sha256(bytes)
            progress(0L, bytes.size.toLong())
            if (cancelled()) throw CancellationException("Copy cancelled before publication.")
            if (replaceExisting && storage.exists(destination)) {
                val parentPath = parent(destination)
                val temp = join(parentPath, ".astracode-copy-${UUID.randomUUID()}")
                val backup = join(parentPath, ".astracode-backup-${UUID.randomUUID()}")
                storage.writeBytes(temp, bytes)
                if (sha256(storage.readBytes(temp)) != expected) { runCatching { storage.delete(temp) }; throw IOException("SHA-256 verification failed; destination was not changed.") }
                try {
                    storage.move(destination, backup, false)
                    try { storage.move(temp, destination, false) }
                    catch (error: Exception) {
                        runCatching { storage.move(backup, destination, false) }.onFailure(error::addSuppressed)
                        throw error
                    }
                    runCatching { storage.delete(backup) }
                } catch (error: Exception) {
                    runCatching { if (storage.exists(temp)) storage.delete(temp) }
                    throw error
                }
            } else {
                writeVerified(storage, destination, bytes, cancelled)
            }
            if (sha256(storage.readBytes(destination)) != expected) throw IOException("SHA-256 verification failed after copy.")
            progress(bytes.size.toLong(), bytes.size.toLong())
            recordOperation(storage, "copy-verified", listOf(source, destination), "success")
        }

    fun preview(storage: WorkspaceStorage, path: String, maxBytes: Long = DEFAULT_PREVIEW_LIMIT_BYTES): WorkspacePreviewResult {
        require(maxBytes > 0)
        val normalized = validatePath(path)
        val item = entry(storage, normalized) ?: throw IOException("File no longer exists: $normalized")
        if (item.type != FileEntryType.FILE) throw IOException("Preview supports regular files only.")
        if (item.sizeBytes > maxBytes) return WorkspacePreviewResult(normalized, item.sizeBytes, supported = false, message = "File exceeds the read-only preview size limit.")
        val extension = item.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val bytes = try { storage.readBytes(normalized) } catch (error: Exception) {
            return WorkspacePreviewResult(normalized, item.sizeBytes, supported = false, message = "Could not read preview: ${error.message ?: "storage error"}")
        }
        if (bytes.size > maxBytes) return WorkspacePreviewResult(normalized, bytes.size.toLong(), supported = false, message = "File exceeds the read-only preview size limit.")
        if (extension in imageExtensions) return WorkspacePreviewResult(normalized, bytes.size.toLong(), imageBytes = bytes, supported = true, message = "Read-only image preview; the original was not changed or executed.")
        if (extension !in textExtensions) return WorkspacePreviewResult(normalized, bytes.size.toLong(), supported = false, message = "This file type is not available in preview. The original was not changed or executed.")
        if (bytes.any { it == 0.toByte() }) return WorkspacePreviewResult(normalized, bytes.size.toLong(), supported = false, message = "Binary content is not shown as text.")
        val decoded = try {
            StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            return WorkspacePreviewResult(normalized, bytes.size.toLong(), supported = false, message = "The file is not valid UTF-8 text; the original was not changed.")
        }
        return WorkspacePreviewResult(normalized, bytes.size.toLong(), text = decoded, supported = true, message = "Read-only preview; file contents were not executed.")
    }

    fun search(storage: WorkspaceStorage, options: WorkspaceSearchOptions = WorkspaceSearchOptions()): List<WorkspaceSearchHit> {
        val query = options.query.trim().lowercase(Locale.ROOT)
        val extension = options.extension?.trim()?.removePrefix(".")?.lowercase(Locale.ROOT)?.takeIf { it.isNotEmpty() }
        val hits = mutableListOf<WorkspaceSearchHit>()
        val pending = ArrayDeque<Pair<String, Int>>()
        pending.add("" to 0)
        var visited = 0
        while (pending.isNotEmpty() && hits.size < options.maxResults) {
            val (directory, depth) = pending.removeFirst()
            if (depth > MAX_DEPTH) continue
            storage.listDirectory(directory).forEach { child ->
                val path = join(directory, child.name)
                if (isReservedPath(path)) return@forEach
                if (++visited > MAX_ITEMS) throw IOException("Search stopped at the workspace item limit.")
                if (!options.includeHidden && path.split('/').any { it.startsWith(".") }) return@forEach
                if (child.type == FileEntryType.DIRECTORY) {
                    if (depth < MAX_DEPTH) pending.add(path to depth + 1)
                } else if (child.type == FileEntryType.FILE) {
                    val modified = child.modifiedAt.toEpochMilli().coerceAtLeast(0)
                    if (query.isNotEmpty() && !path.lowercase(Locale.ROOT).contains(query)) return@forEach
                    if (extension != null && child.name.substringAfterLast('.', "").lowercase(Locale.ROOT) != extension) return@forEach
                    if (options.minSizeBytes != null && child.sizeBytes < options.minSizeBytes) return@forEach
                    if (options.maxSizeBytes != null && child.sizeBytes > options.maxSizeBytes) return@forEach
                    if (options.modifiedAfterMillis != null && modified > 0 && modified < options.modifiedAfterMillis) return@forEach
                    hits += WorkspaceSearchHit(path, child.name, child.type, child.sizeBytes, modified)
                }
            }
        }
        return hits.sortedWith(compareBy<WorkspaceSearchHit> { it.path.lowercase(Locale.ROOT) }.thenBy { it.path })
    }

    fun findDuplicates(storage: WorkspaceStorage, maxFiles: Int = MAX_ITEMS): WorkspaceDuplicateScan {
        require(maxFiles in 1..100_000)
        val files = search(storage, WorkspaceSearchOptions(includeHidden = true, maxResults = minOf(maxFiles, MAX_ITEMS)))
            .filter { it.type == FileEntryType.FILE && !isReservedPath(it.path) }
        val groups = mutableListOf<WorkspaceDuplicateGroup>()
        var skipped = 0
        files.groupBy { it.sizeBytes }.values.filter { it.size > 1 }.forEach { candidates ->
            val byHash = mutableMapOf<String, MutableList<String>>()
            candidates.forEach { file ->
                try {
                    val hash = sha256(storage.readBytes(file.path))
                    byHash.getOrPut(hash) { mutableListOf() }.add(file.path)
                } catch (_: Exception) { skipped++ }
            }
            byHash.filterValues { it.size > 1 }.forEach { (hash, paths) ->
                groups += WorkspaceDuplicateGroup(hash, candidates.first { it.path in paths }.sizeBytes, paths.sorted())
            }
        }
        return WorkspaceDuplicateScan(groups.sortedWith(compareByDescending<WorkspaceDuplicateGroup> { it.sizeBytes }.thenBy { it.paths.first() }), skipped)
    }

    fun batchRename(storage: WorkspaceStorage, directory: String, proposedNames: Map<String, String>): List<WorkspaceRenameResult> {
        if (proposedNames.isEmpty()) throw IOException("Enter at least one rename.")
        val dir = validatePath(directory, allowEmpty = true)
        val current = storage.listDirectory(dir).associateBy { it.name }
        val oldNames = proposedNames.keys.toSet()
        val targets = proposedNames.values.toList()
        if (targets.map { it.lowercase(Locale.ROOT) }.distinct().size != targets.size) throw IOException("Two items cannot be renamed to the same name.")
        proposedNames.forEach { (old, new) ->
            if (!simpleName(old) || !simpleName(new)) throw SecurityException("Enter valid names without path separators or reserved prefixes.")
            if (old !in current) throw IOException("Rename source does not exist in this folder: $old")
            if (new in current && new !in oldNames) throw IOException("Rename destination already exists: $new")
        }
        val locks = oldNames.map { join(dir, it) } + targets.map { join(dir, it) }
        return withPathLocks(storage, locks) {
            val staged = linkedMapOf<String, String>()
            val completed = mutableListOf<Pair<String, String>>()
            try {
                proposedNames.keys.forEach { old ->
                    val temp = join(dir, ".astracode-rename-${UUID.randomUUID()}")
                    storage.move(join(dir, old), temp, false)
                    staged[old] = temp
                }
                proposedNames.forEach { (old, new) ->
                    val dest = join(dir, new)
                    if (storage.exists(dest)) throw IOException("Rename destination appeared during operation: $new")
                    storage.move(staged.getValue(old), dest, false)
                    completed += old to new
                }
            } catch (error: Exception) {
                completed.asReversed().forEach { (old, new) ->
                    runCatching {
                        val target = join(dir, new); val original = join(dir, old)
                        if (storage.exists(target) && !storage.exists(original)) storage.move(target, original, false)
                    }.onFailure(error::addSuppressed)
                }
                staged.forEach { (old, temp) ->
                    runCatching {
                        val original = join(dir, old)
                        if (storage.exists(temp) && !storage.exists(original)) storage.move(temp, original, false)
                    }.onFailure(error::addSuppressed)
                }
                recordOperation(storage, "batch-rename", proposedNames.keys.map { join(dir, it) }, "failure")
                throw error
            }
            recordOperation(storage, "batch-rename", proposedNames.keys.map { join(dir, it) }, "success")
            proposedNames.map { (old, new) -> WorkspaceRenameResult(join(dir, old), join(dir, new)) }
        }
    }

    fun healthReport(storage: WorkspaceStorage, largestCount: Int = 10): WorkspaceHealthReport {
        require(largestCount in 1..100)
        val entries = search(storage, WorkspaceSearchOptions(includeHidden = true, maxResults = MAX_ITEMS))
        val files = entries.filter { it.type == FileEntryType.FILE && !isReservedPath(it.path) }
        val dirs = entries.filter { it.type == FileEntryType.DIRECTORY && !isReservedPath(it.path) }
        return WorkspaceHealthReport(files.sumOf { it.sizeBytes.coerceAtLeast(0) }, files.size, dirs.size,
            files.sortedByDescending { it.sizeBytes }.take(largestCount).map { it.path to it.sizeBytes })
    }

    fun <T> withPathLocks(storage: WorkspaceStorage, paths: List<String>, action: () -> T): T {
        require(paths.isNotEmpty())
        val rootKey = "${storage.storageType}:${storage.displayName}:${System.identityHashCode(storage)}"
        val requested = paths.map { "$rootKey::${validatePath(it, allowEmpty = true)}" }.toSet()
        synchronized(lockMonitor) {
            while (activeLocks.any { held -> requested.any { overlaps(it, held) } }) {
                try { lockMonitor.wait() } catch (_: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw IOException("Interrupted while waiting for a file operation lock.")
                }
            }
            activeLocks.addAll(requested)
        }
        try { return action() }
        finally { synchronized(lockMonitor) { activeLocks.removeAll(requested); lockMonitor.notifyAll() } }
    }

    private fun overlaps(first: String, second: String): Boolean {
        val fi = first.indexOf("::"); val si = second.indexOf("::")
        if (fi < 0 || si < 0 || first.substring(0, fi) != second.substring(0, si)) return false
        val a = first.substring(fi + 2); val b = second.substring(si + 2)
        return a == b || a.isEmpty() || b.isEmpty() || a.startsWith("$b/") || b.startsWith("$a/")
    }
    private fun sha256(bytes: ByteArray): String = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    private fun simpleName(value: String): Boolean = value.isNotBlank() && value != "." && value != ".." && value.length <= 255 &&
        !value.startsWith(".astracode-") && value !in reservedRoots && !value.contains('/') && !value.contains('\\') && value.indexOf('\u0000') < 0
    private fun join(parent: String, child: String): String = if (parent.isEmpty()) child else "$parent/$child"
    private fun parent(path: String): String = path.substringBeforeLast('/', "")
}

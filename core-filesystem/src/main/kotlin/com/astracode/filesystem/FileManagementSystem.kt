package com.astracode.filesystem

import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.nio.file.attribute.FileTime
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Local workspace file operations for AstraCode.
 *
 * All public paths are workspace-relative. Symbolic links are deliberately not
 * followed, preventing a project entry from redirecting reads or writes outside
 * the selected workspace. Text is decoded as UTF-8 with WHATWG-compatible repair.
 * Writes use a sibling temp file and an atomic move; there is no unsafe fallback.
 */
class FileManagementSystem(
    rootDirectory: Path,
    private val options: FileSystemOptions = FileSystemOptions(),
) : WorkspaceStorage {
    private val root: Path
    private val textCache = LruBufferCache<String, CachedText>(options.cacheCapacity)
    private val listeners = CopyOnWriteArrayList<(FileChangeEvent) -> Unit>()
    @Volatile private var closed = false

    init {
        Files.createDirectories(rootDirectory.toAbsolutePath().normalize())
        val normalized = rootDirectory.toAbsolutePath().normalize()
        if (Files.isSymbolicLink(normalized)) {
            throw SecurityException("Workspace root must not be a symbolic link")
        }
        root = normalized.toRealPath()
    }

    val rootDirectory: Path get() = root
    override val displayName: String get() = root.fileName?.toString() ?: "Workspace"
    override val storageType: WorkspaceStorageType get() = WorkspaceStorageType.PRIVATE

    override fun addChangeListener(listener: (FileChangeEvent) -> Unit): AutoCloseable {
        ensureOpen()
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }

    override fun exists(relativePath: String): Boolean {
        ensureOpen()
        return Files.exists(resolve(relativePath), LinkOption.NOFOLLOW_LINKS)
    }

    override fun listDirectory(relativePath: String): List<FileEntry> {
        ensureOpen()
        val directory = resolve(relativePath)
        requireDirectory(directory, relativePath)
        return Files.newDirectoryStream(directory).use { children ->
            children.map { child -> describeEntry(child) }
                .sortedWith(compareBy<FileEntry> { it.type != FileEntryType.DIRECTORY }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name })
        }
    }

    override fun scanTree(maxDepth: Int): List<FileEntry> {
        ensureOpen()
        require(maxDepth >= 0) { "maxDepth must be zero or greater" }
        val output = mutableListOf<FileEntry>()
        fun visit(directory: Path, depth: Int) {
            if (depth >= maxDepth) return
            for (entry in listDirectory(toWorkspacePath(directory))) {
                output += entry
                if (entry.type == FileEntryType.DIRECTORY) {
                    visit(resolve(entry.path), depth + 1)
                }
            }
        }
        visit(root, 0)
        return output
    }

    override fun createDirectory(relativePath: String): FileEntry {
        ensureOpen()
        val directory = resolve(relativePath)
        require(directory != root) { "Workspace root already exists" }
        createSafeParents(directory.parent)
        if (Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("Path already exists: $relativePath")
        }
        Files.createDirectory(directory)
        checkContainedAndNoSymlinks(directory)
        invalidateCache(relativePath)
        publish(FileChangeEvent(FileChangeType.DIRECTORY_CREATED, normalizeRelative(relativePath)))
        return describeEntry(directory)
    }

    override fun readText(relativePath: String): TextFileSnapshot =
        readText(relativePath, options.defaultReadBufferSize)

    fun readText(
        relativePath: String,
        readBufferSize: Int,
    ): TextFileSnapshot {
        ensureOpen()
        require(readBufferSize > 0) { "readBufferSize must be greater than zero" }
        val target = resolve(relativePath)
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("Not a regular file: $relativePath")
        }
        checkContainedAndNoSymlinks(target)
        val path = normalizeRelative(relativePath)

        repeat(options.concurrentReadRetries + 1) { attempt ->
            val before = fingerprint(target)
            if (before.size > options.maxFileBytes) {
                throw FileTooLargeException(path, before.size, options.maxFileBytes)
            }
            textCache[path]?.let { cached ->
                if (cached.fingerprint == before) return cached.snapshot
                textCache.remove(path)
            }

            val decoder = Utf8RepairDecoder()
            val text = StringBuilder(minOf(before.size, 64L * 1024L).toInt())
            val window = ByteArray(readBufferSize)
            var bytesRead = 0L
            Files.newInputStream(target, StandardOpenOption.READ).use { input ->
                while (true) {
                    val count = input.read(window)
                    if (count < 0) break
                    if (count == 0) continue
                    bytesRead += count
                    if (bytesRead > options.maxFileBytes) {
                        throw FileTooLargeException(path, bytesRead, options.maxFileBytes)
                    }
                    text.append(decoder.decode(window, 0, count))
                }
                text.append(decoder.decode(ByteArray(0), endOfInput = true))
            }

            val after = fingerprint(target)
            if (before == after && bytesRead == after.size) {
                val snapshot = TextFileSnapshot(
                    path = path,
                    text = text.toString(),
                    sourceByteLength = bytesRead,
                    modifiedAt = after.lastModified.toInstant(),
                    hadLeadingBom = decoder.hadLeadingBom,
                    repairedSpans = decoder.repairedSpans,
                )
                textCache.put(path, CachedText(after, snapshot))
                return snapshot
            }
            textCache.remove(path)
            if (attempt == options.concurrentReadRetries) {
                throw ConcurrentFileModificationException(path)
            }
        }
        throw ConcurrentFileModificationException(path)
    }

    /**
     * Save canonical UTF-8 text via temp-file + ATOMIC_MOVE. Malformed UTF-16
     * code units from callers are normalized to U+FFFD before encoding.
     */
    override fun writeText(relativePath: String, text: String): TextFileWriteResult {
        ensureOpen()
        val normalizedPath = normalizeRelative(relativePath)
        val target = resolve(normalizedPath)
        require(target != root) { "A file path is required, not the workspace root" }
        checkContainedAndNoSymlinks(target)
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
        ) {
            throw IOException("Cannot write text to a non-regular file: $normalizedPath")
        }

        val (persistedText, replacedCount) = replaceUnpairedSurrogates(text)
        val bytes = persistedText.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size.toLong() > options.maxFileBytes) {
            throw FileTooLargeException(normalizedPath, bytes.size.toLong(), options.maxFileBytes)
        }

        createSafeParents(target.parent)
        checkContainedAndNoSymlinks(target)
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
        ) {
            throw IOException("Cannot write text to a non-regular file: $normalizedPath")
        }
        val existed = Files.exists(target, LinkOption.NOFOLLOW_LINKS)
        val parent = target.parent ?: throw IOException("Target has no parent: $normalizedPath")
        val temporary = Files.createTempFile(parent, ".astracode-", ".tmp")
        try {
            FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            checkContainedAndNoSymlinks(target)
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (unsupported: AtomicMoveNotSupportedException) {
                throw AtomicFileOperationNotSupportedException(normalizedPath, unsupported)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }

        checkContainedAndNoSymlinks(target)
        textCache.remove(normalizedPath)
        publish(FileChangeEvent(if (existed) FileChangeType.MODIFIED else FileChangeType.CREATED, normalizedPath))
        return TextFileWriteResult(normalizedPath, bytes.size.toLong(), persistedText, replacedCount)
    }

    override fun readBytes(relativePath: String): ByteArray {
        ensureOpen()
        val path = normalizeRelative(relativePath)
        val target = resolve(path)
        if (!Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("Not a regular file: $path")
        }
        checkContainedAndNoSymlinks(target)
        val size = Files.size(target)
        if (size > options.maxFileBytes) {
            throw FileTooLargeException(path, size, options.maxFileBytes)
        }
        val bytes = Files.readAllBytes(target)
        if (bytes.size.toLong() > options.maxFileBytes) {
            throw FileTooLargeException(path, bytes.size.toLong(), options.maxFileBytes)
        }
        return bytes
    }

    override fun writeBytes(relativePath: String, bytes: ByteArray) {
        ensureOpen()
        val path = normalizeRelative(relativePath)
        val target = resolve(path)
        require(target != root) { "A file path is required, not the workspace root" }
        if (bytes.size.toLong() > options.maxFileBytes) {
            throw FileTooLargeException(path, bytes.size.toLong(), options.maxFileBytes)
        }
        checkContainedAndNoSymlinks(target)
        if (Files.exists(target, LinkOption.NOFOLLOW_LINKS) &&
            !Files.isRegularFile(target, LinkOption.NOFOLLOW_LINKS)
        ) {
            throw IOException("Cannot write bytes to a non-regular file: $path")
        }
        createSafeParents(target.parent)
        checkContainedAndNoSymlinks(target)
        val existed = Files.exists(target, LinkOption.NOFOLLOW_LINKS)
        val parent = target.parent ?: throw IOException("Target has no parent: $path")
        val temporary = Files.createTempFile(parent, ".astracode-copy-", ".tmp")
        try {
            FileChannel.open(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { channel ->
                val buffer = ByteBuffer.wrap(bytes)
                while (buffer.hasRemaining()) channel.write(buffer)
                channel.force(true)
            }
            checkContainedAndNoSymlinks(target)
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
            } catch (unsupported: AtomicMoveNotSupportedException) {
                throw AtomicFileOperationNotSupportedException(path, unsupported)
            }
        } finally {
            Files.deleteIfExists(temporary)
        }
        checkContainedAndNoSymlinks(target)
        textCache.remove(path)
        publish(FileChangeEvent(if (existed) FileChangeType.MODIFIED else FileChangeType.CREATED, path))
    }

    override fun move(sourcePath: String, targetPath: String, replaceExisting: Boolean): FileEntry {
        ensureOpen()
        val sourceName = normalizeRelative(sourcePath)
        val targetName = normalizeRelative(targetPath)
        val source = resolve(sourceName)
        val target = resolve(targetName)
        require(source != root && target != root) { "Workspace root cannot be moved or replaced" }
        if (!Files.exists(source, LinkOption.NOFOLLOW_LINKS)) throw IOException("Source does not exist: $sourceName")
        checkContainedAndNoSymlinks(source)
        createSafeParents(target.parent)
        checkContainedAndNoSymlinks(target)
        if (!replaceExisting && Files.exists(target, LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("Target already exists: $targetName")
        }
        val optionsForMove = if (replaceExisting) {
            arrayOf(StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        } else {
            arrayOf(StandardCopyOption.ATOMIC_MOVE)
        }
        try {
            Files.move(source, target, *optionsForMove)
        } catch (unsupported: AtomicMoveNotSupportedException) {
            throw AtomicFileOperationNotSupportedException(targetName, unsupported)
        }
        invalidateCache(sourceName)
        invalidateCache(targetName)
        publish(FileChangeEvent(FileChangeType.MOVED, targetName, sourceName))
        return describeEntry(target)
    }

    override fun delete(relativePath: String, recursive: Boolean) {
        ensureOpen()
        val normalizedPath = normalizeRelative(relativePath)
        val target = resolve(normalizedPath)
        require(target != root) { "Workspace root cannot be deleted" }
        if (!Files.exists(target, LinkOption.NOFOLLOW_LINKS)) return
        checkContainedAndNoSymlinks(target)
        if (Files.isDirectory(target, LinkOption.NOFOLLOW_LINKS) && recursive) {
            Files.walkFileTree(target, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, error: IOException?): FileVisitResult {
                    if (error != null) throw error
                    Files.delete(dir)
                    return FileVisitResult.CONTINUE
                }
            })
        } else {
            Files.delete(target)
        }
        invalidateCache(normalizedPath)
        publish(FileChangeEvent(FileChangeType.DELETED, normalizedPath))
    }

    override fun close() {
        if (closed) return
        closed = true
        textCache.clear()
        listeners.clear()
    }

    private fun resolve(relativePath: String): Path {
        ensureOpen()
        val candidatePath = try {
            Paths.get(relativePath.ifEmpty { "." })
        } catch (error: Exception) {
            throw IllegalArgumentException("Invalid workspace-relative path", error)
        }
        if (candidatePath.isAbsolute) throw SecurityException("Absolute paths are not allowed")
        if (relativePath.indexOf('\u0000') >= 0 || relativePath.contains('\\')) {
            throw SecurityException("Invalid path character in workspace path")
        }
        val candidate = root.resolve(candidatePath).normalize()
        if (!candidate.startsWith(root)) throw SecurityException("Path escapes the workspace: $relativePath")
        checkContainedAndNoSymlinks(candidate)
        return candidate
    }

    private fun checkContainedAndNoSymlinks(path: Path) {
        if (!path.toAbsolutePath().normalize().startsWith(root)) {
            throw SecurityException("Path escapes the workspace: $path")
        }
        var current = root
        val relative = root.relativize(path.toAbsolutePath().normalize())
        for (segment in relative) {
            current = current.resolve(segment)
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current)) throw SecurityException("Symbolic links are not allowed in workspace paths: $current")
                val actual = current.toRealPath()
                if (!actual.startsWith(root)) throw SecurityException("Resolved path escapes the workspace: $current")
            }
        }
    }

    private fun createSafeParents(parent: Path?) {
        if (parent == null) throw IOException("Target has no parent")
        if (!parent.toAbsolutePath().normalize().startsWith(root)) {
            throw SecurityException("Parent directory escapes the workspace: $parent")
        }
        var current = root
        val relative = root.relativize(parent.toAbsolutePath().normalize())
        for (segment in relative) {
            current = current.resolve(segment)
            if (Files.exists(current, LinkOption.NOFOLLOW_LINKS)) {
                if (Files.isSymbolicLink(current)) throw SecurityException("Symbolic links are not allowed: $current")
                if (!Files.isDirectory(current, LinkOption.NOFOLLOW_LINKS)) throw IOException("Not a directory: $current")
                if (!current.toRealPath().startsWith(root)) throw SecurityException("Directory escapes the workspace: $current")
            } else {
                Files.createDirectory(current)
                publish(FileChangeEvent(FileChangeType.DIRECTORY_CREATED, toWorkspacePath(current)))
            }
        }
    }

    private fun requireDirectory(path: Path, displayPath: String) {
        if (!Files.isDirectory(path, LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("Not a directory: ${displayPath.ifEmpty { "." }}")
        }
    }

    private fun describeEntry(path: Path): FileEntry {
        val attrs = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        val type = when {
            attrs.isSymbolicLink -> FileEntryType.SYMBOLIC_LINK
            attrs.isDirectory -> FileEntryType.DIRECTORY
            attrs.isRegularFile -> FileEntryType.FILE
            else -> FileEntryType.OTHER
        }
        return FileEntry(
            path = toWorkspacePath(path),
            name = path.fileName?.toString() ?: "",
            type = type,
            sizeBytes = if (type == FileEntryType.FILE) attrs.size() else 0L,
            modifiedAt = attrs.lastModifiedTime().toInstant(),
            readable = type != FileEntryType.SYMBOLIC_LINK && Files.isReadable(path),
            writable = type != FileEntryType.SYMBOLIC_LINK && Files.isWritable(path),
        )
    }

    private fun toWorkspacePath(path: Path): String =
        root.relativize(path.toAbsolutePath().normalize()).toString().replace(path.fileSystem.separator, "/")

    private fun normalizeRelative(path: String): String {
        val resolved = resolve(path)
        if (resolved == root) throw IllegalArgumentException("A non-root path is required")
        return toWorkspacePath(resolved)
    }

    private fun fingerprint(path: Path): Fingerprint {
        checkContainedAndNoSymlinks(path)
        val attrs = Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
        return Fingerprint(attrs.size(), attrs.lastModifiedTime(), attrs.fileKey()?.toString())
    }

    private fun invalidateCache(path: String) {
        val normalized = path.trimEnd('/')
        textCache.removeIf { cachedPath ->
            cachedPath == normalized || cachedPath.startsWith("$normalized/")
        }
    }

    private fun publish(event: FileChangeEvent) {
        for (listener in listeners) {
            try {
                listener(event)
            } catch (_: Exception) {
                // A broken UI observer must not roll back a successful disk operation.
            }
        }
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

    private fun ensureOpen() {
        check(!closed) { "FileManagementSystem has been closed" }
    }

    private data class Fingerprint(val size: Long, val lastModified: FileTime, val fileKey: String?)
    private data class CachedText(val fingerprint: Fingerprint, val snapshot: TextFileSnapshot)
}

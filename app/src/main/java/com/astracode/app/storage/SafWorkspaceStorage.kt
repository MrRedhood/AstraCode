package com.astracode.app.storage

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.webkit.MimeTypeMap
import androidx.documentfile.provider.DocumentFile
import com.astracode.filesystem.FileChangeEvent
import com.astracode.filesystem.FileChangeType
import com.astracode.filesystem.FileEntry
import com.astracode.filesystem.FileEntryType
import com.astracode.filesystem.FileSystemOptions
import com.astracode.filesystem.FileTooLargeException
import com.astracode.filesystem.TextFileSnapshot
import com.astracode.filesystem.TextFileWriteResult
import com.astracode.filesystem.Utf8RepairDecoder
import com.astracode.filesystem.WorkspaceStorage
import com.astracode.filesystem.WorkspaceStorageType
import java.io.IOException
import java.time.Instant
import java.util.concurrent.CopyOnWriteArrayList

/**
 * Workspace adapter for a tree selected with ACTION_OPEN_DOCUMENT_TREE.
 *
 * Reads/writes use ContentResolver and the document provider's URIs. Providers
 * do not promise atomic replacement, so this adapter does not claim that saving
 * is atomic (unlike the private-filesystem implementation).
 */
class SafWorkspaceStorage(
    context: Context,
    treeUri: Uri,
    private val options: FileSystemOptions = FileSystemOptions(),
) : WorkspaceStorage {
    private val appContext = context.applicationContext
    private val resolver: ContentResolver = appContext.contentResolver
    private val root: DocumentFile =
        DocumentFile.fromTreeUri(appContext, treeUri)
            ?: throw IOException("The selected URI is not an accessible document tree.")
    private val listeners = CopyOnWriteArrayList<(FileChangeEvent) -> Unit>()

    @Volatile
    private var closed = false

    init {
        if (!root.exists() || !root.isDirectory) {
            throw IOException("The selected workspace folder no longer exists.")
        }
        if (!root.canRead()) {
            throw SecurityException("AstraCode no longer has permission to read this folder. Select it again.")
        }
    }

    override val displayName: String
        get() = root.name?.takeIf { it.isNotBlank() } ?: "External folder"

    override val storageType: WorkspaceStorageType
        get() = WorkspaceStorageType.DOCUMENT_TREE

    override fun addChangeListener(listener: (FileChangeEvent) -> Unit): AutoCloseable {
        ensureOpen()
        listeners += listener
        return AutoCloseable { listeners -= listener }
    }

    override fun exists(relativePath: String): Boolean {
        ensureOpen()
        return find(relativePath) != null
    }

    override fun listDirectory(relativePath: String): List<FileEntry> {
        ensureOpen()
        val directory = resolve(relativePath)
        if (!directory.isDirectory) {
            throw IOException("Not a directory: " + relativePath.ifEmpty { displayName })
        }
        if (!directory.canRead()) throw SecurityException("Cannot read folder: $relativePath")

        return directory.listFiles()
            .map { child ->
                val childName = child.name ?: child.uri.lastPathSegment ?: "unnamed"
                describe(child, join(relativePath, childName))
            }
            .sortedWith(
                compareBy<FileEntry> { it.type != FileEntryType.DIRECTORY }
                    .thenBy(String.CASE_INSENSITIVE_ORDER) { it.name },
            )
    }

    override fun createDirectory(relativePath: String): FileEntry {
        ensureOpen()
        val parts = segments(relativePath)
        if (parts.isEmpty()) throw IllegalArgumentException("A subfolder name is required.")
        val parent = resolveParent(parts, createParents = true)
        val name = parts.last()
        if (parent.findFile(name) != null) throw IOException("Path already exists: $relativePath")
        val created = parent.createDirectory(name)
            ?: throw IOException("The selected storage provider could not create folder '$name'.")
        val canonicalPath = parts.joinToString("/")
        publish(FileChangeEvent(FileChangeType.DIRECTORY_CREATED, canonicalPath))
        return describe(created, canonicalPath)
    }

    override fun readText(relativePath: String): TextFileSnapshot {
        ensureOpen()
        val path = normalizedPath(relativePath)
        val document = resolve(path)
        if (document.isDirectory || !document.isFile || document.isVirtual) {
            throw IOException("Not a readable text file: $path")
        }
        if (!document.canRead()) throw SecurityException("Cannot read file: $path")

        val reportedSize = document.length().coerceAtLeast(0L)
        if (reportedSize > options.maxFileBytes) {
            throw FileTooLargeException(path, reportedSize, options.maxFileBytes)
        }

        val decoder = Utf8RepairDecoder()
        val text = StringBuilder(minOf(reportedSize, 64L * 1024L).toInt())
        val window = ByteArray(options.defaultReadBufferSize)
        var bytesRead = 0L
        val input = resolver.openInputStream(document.uri)
            ?: throw IOException("The storage provider could not open '$path' for reading.")
        input.use { stream ->
            while (true) {
                val count = stream.read(window)
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

        return TextFileSnapshot(
            path = path,
            text = text.toString(),
            sourceByteLength = bytesRead,
            modifiedAt = Instant.ofEpochMilli(document.lastModified().coerceAtLeast(0L)),
            hadLeadingBom = decoder.hadLeadingBom,
            repairedSpans = decoder.repairedSpans,
        )
    }

    override fun writeText(relativePath: String, text: String): TextFileWriteResult {
        ensureOpen()
        val parts = segments(relativePath)
        if (parts.isEmpty()) throw IllegalArgumentException("A file name is required.")
        val path = parts.joinToString("/")
        val parent = resolveParent(parts, createParents = true)
        var target = parent.findFile(parts.last())
        val existed = target != null

        if (target?.isDirectory == true || target?.isVirtual == true) {
            throw IOException("Cannot write text to a directory or virtual document: $path")
        }
        if (target != null && !target.canWrite()) {
            throw SecurityException("The storage provider does not allow writing '$path'.")
        }
        if (target == null) {
            target = parent.createFile(mimeTypeFor(parts.last()), parts.last())
                ?: throw IOException("The storage provider could not create '$path'.")
        }

        val (persistedText, replacedCount) = replaceUnpairedSurrogates(text)
        val bytes = persistedText.toByteArray(Charsets.UTF_8)
        if (bytes.size.toLong() > options.maxFileBytes) {
            if (!existed) target.delete()
            throw FileTooLargeException(path, bytes.size.toLong(), options.maxFileBytes)
        }

        try {
            val output = resolver.openOutputStream(target.uri, "wt")
                ?: throw IOException("The storage provider could not open '$path' for writing.")
            output.use { stream ->
                stream.write(bytes)
                stream.flush()
            }
        } catch (error: Exception) {
            if (!existed) target.delete()
            throw IOException("Could not save '$path' through the selected storage provider.", error)
        }

        publish(FileChangeEvent(if (existed) FileChangeType.MODIFIED else FileChangeType.CREATED, path))
        return TextFileWriteResult(
            path = path,
            bytesWritten = bytes.size.toLong(),
            persistedText = persistedText,
            replacedUnpairedSurrogateCount = replacedCount,
        )
    }

    override fun move(
        sourcePath: String,
        targetPath: String,
        replaceExisting: Boolean,
    ): FileEntry {
        ensureOpen()
        val sourceName = normalizedPath(sourcePath)
        val targetParts = segments(targetPath)
        if (targetParts.isEmpty()) throw IllegalArgumentException("A target file name is required.")
        val source = resolve(sourceName)
        val targetParent = resolveParent(targetParts, createParents = false)
        val targetName = targetParts.last()
        val sourceParent = source.parentFile
            ?: throw IOException("The workspace root cannot be moved.")

        if (sourceParent.uri != targetParent.uri) {
            throw IOException("This storage provider adapter currently supports rename within the same folder only.")
        }
        val target = targetParent.findFile(targetName)
        if (target != null && target.uri != source.uri) {
            if (!replaceExisting) throw IOException("Target already exists: $targetPath")
            if (!target.delete()) throw IOException("Could not replace target: $targetPath")
        }
        if (!source.renameTo(targetName)) {
            throw IOException("The storage provider does not support renaming '$sourceName'.")
        }
        val renamed = targetParent.findFile(targetName)
            ?: throw IOException("The renamed document could not be found.")
        publish(FileChangeEvent(FileChangeType.MOVED, targetParts.joinToString("/"), sourceName))
        return describe(renamed, targetParts.joinToString("/"))
    }

    override fun delete(relativePath: String, recursive: Boolean) {
        ensureOpen()
        val path = normalizedPath(relativePath)
        val target = resolve(path)
        if (target.uri == root.uri) throw IllegalArgumentException("The selected workspace root cannot be deleted.")

        if (target.isDirectory) {
            val children = target.listFiles()
            if (!recursive && children.isNotEmpty()) {
                throw java.nio.file.DirectoryNotEmptyException(path)
            }
            if (recursive) children.forEach(::deleteDocumentRecursively)
        }
        if (!target.delete()) throw IOException("The storage provider could not delete '$path'.")
        publish(FileChangeEvent(FileChangeType.DELETED, path))
    }

    override fun close() {
        if (closed) return
        closed = true
        listeners.clear()
    }

    private fun resolve(relativePath: String): DocumentFile {
        val parts = segments(relativePath)
        var current = root
        for (part in parts) {
            if (!current.isDirectory) throw IOException("Not a directory while resolving '$relativePath'.")
            current = current.findFile(part)
                ?: throw java.nio.file.NoSuchFileException(relativePath)
        }
        return current
    }

    private fun find(relativePath: String): DocumentFile? {
        val parts = segments(relativePath)
        var current = root
        for (part in parts) {
            if (!current.isDirectory) return null
            current = current.findFile(part) ?: return null
        }
        return current
    }

    private fun resolveParent(parts: List<String>, createParents: Boolean): DocumentFile {
        var current = root
        val pathPrefix = mutableListOf<String>()
        for (part in parts.dropLast(1)) {
            var child = current.findFile(part)
            if (child == null && createParents) {
                child = current.createDirectory(part)
                    ?: throw IOException("The storage provider could not create parent folder '$part'.")
                pathPrefix += part
                publish(FileChangeEvent(FileChangeType.DIRECTORY_CREATED, pathPrefix.joinToString("/")))
            } else {
                pathPrefix += part
            }
            child ?: throw java.nio.file.NoSuchFileException(parts.joinToString("/"))
            if (!child.isDirectory) throw IOException("Not a folder: $part")
            current = child
        }
        if (!current.canWrite()) throw SecurityException("The selected folder is not writable.")
        return current
    }

    private fun segments(relativePath: String): List<String> {
        ensureOpen()
        if (relativePath.isEmpty()) return emptyList()
        if (relativePath.startsWith("/") || relativePath.contains('\\') || relativePath.indexOf('\u0000') >= 0) {
            throw SecurityException("Only workspace-relative paths are allowed.")
        }
        val parts = relativePath.split('/')
        if (parts.any { it.isEmpty() || it == "." || it == ".." }) {
            throw SecurityException("Invalid workspace-relative path.")
        }
        return parts
    }

    private fun normalizedPath(relativePath: String): String {
        val parts = segments(relativePath)
        if (parts.isEmpty()) throw IllegalArgumentException("A non-root path is required.")
        return parts.joinToString("/")
    }

    private fun describe(document: DocumentFile, relativePath: String): FileEntry {
        val type = when {
            document.isDirectory -> FileEntryType.DIRECTORY
            document.isFile && !document.isVirtual -> FileEntryType.FILE
            else -> FileEntryType.OTHER
        }
        return FileEntry(
            path = relativePath,
            name = document.name ?: relativePath.substringAfterLast('/'),
            type = type,
            sizeBytes = if (type == FileEntryType.FILE) document.length().coerceAtLeast(0L) else 0L,
            modifiedAt = Instant.ofEpochMilli(document.lastModified().coerceAtLeast(0L)),
            readable = document.canRead(),
            writable = document.canWrite(),
        )
    }

    private fun deleteDocumentRecursively(document: DocumentFile) {
        if (document.isDirectory) document.listFiles().forEach(::deleteDocumentRecursively)
        if (!document.delete()) {
            throw IOException("The storage provider could not delete '" + (document.name ?: document.uri) + "'.")
        }
    }

    private fun mimeTypeFor(name: String): String {
        val extension = name.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension) ?: "text/plain"
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

    private fun publish(event: FileChangeEvent) {
        listeners.forEach { listener ->
            try {
                listener(event)
            } catch (_: Exception) {
                // Observers cannot undo an already completed provider operation.
            }
        }
    }

    private fun ensureOpen() {
        check(!closed) { "Workspace storage has been closed." }
    }

    private fun join(parent: String, child: String): String =
        if (parent.isEmpty()) child else "$parent/$child"
}

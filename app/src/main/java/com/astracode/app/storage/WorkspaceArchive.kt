package com.astracode.app.storage

import com.astracode.filesystem.FileEntry
import com.astracode.filesystem.FileEntryType
import com.astracode.filesystem.WorkspaceStorage
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import java.util.UUID

/**
 * ZIP handling over WorkspaceStorage, so private workspaces and Android document
 * trees share one implementation. Contents are copied as raw bytes, not decoded
 * as text, so DOCX, source code, images, and other formats are preserved.
 *
 * WorkspaceStorage exposes bounded byte-array I/O, so each ZIP is capped at 32 MiB.
 * Extraction has an additional total expanded-data limit to reduce ZIP-bomb risk.
 */
internal object WorkspaceArchive {
    private const val MAX_ARCHIVE_BYTES = 32 * 1024 * 1024
    private const val MAX_ENTRY_BYTES = 32 * 1024 * 1024
    private const val MAX_EXTRACTED_BYTES = 256L * 1024L * 1024L
    private const val MAX_ENTRIES = 10_000
    private const val MAX_PATH_DEPTH = 128
    private const val MAX_PATH_LENGTH = 4096

    fun createZip(
        storage: WorkspaceStorage,
        sourcePaths: List<String>,
        archivePath: String,
        archiveBasePath: String = "",
    ): String {
        val outputPath = validatePath(archivePath)
        if (sourcePaths.isEmpty()) throw IOException("Select at least one item to archive.")
        if (storage.exists(outputPath)) throw IOException("Destination already exists: $outputPath")
        requireDirectory(storage, parentPath(outputPath))

        val basePath = if (archiveBasePath.isBlank()) "" else validatePath(archiveBasePath)
        requireDirectory(storage, basePath)
        val sourceItems = normalizeSources(storage, sourcePaths)
        if (basePath.isNotEmpty() && sourceItems.any { !isSameOrChild(it.path, basePath) || it.path == basePath }) {
            throw IOException("All selected items must be inside the current folder.")
        }
        if (sourceItems.any { item ->
                outputPath == item.path ||
                    (item.type == FileEntryType.DIRECTORY && isSameOrChild(outputPath, item.path))
            }) {
            throw IOException("The archive cannot be created inside one of its selected folders.")
        }

        val output = BoundedByteArrayOutputStream(MAX_ARCHIVE_BYTES)
        ZipOutputStream(output).use { zip ->
            val seen = HashSet<String>()
            var entryCount = 0

            fun addItem(path: String, relativePath: String) {
                val item = findWorkspaceEntry(storage, path)
                    ?: throw IOException("Selected item no longer exists: $path")
                if (relativePath.isBlank() || relativePath.length > MAX_PATH_LENGTH ||
                    relativePath.split('/').size > MAX_PATH_DEPTH || relativePath == "." || relativePath.startsWith("/") ||
                    relativePath.split('/').any { it.isBlank() || it == "." || it == ".." } ||
                    WorkspaceSafety.isReservedPath(relativePath)) {
                    throw IOException("Invalid archive entry path.")
                }
                if (item.type == FileEntryType.SYMBOLIC_LINK || item.type == FileEntryType.OTHER) {
                    throw IOException("This item type cannot be archived safely: $path")
                }
                if (!seen.add(relativePath)) throw IOException("Duplicate archive entry: $relativePath")

                when (item.type) {
                    FileEntryType.DIRECTORY -> {
                        zip.putNextEntry(ZipEntry("$relativePath/"))
                        zip.closeEntry()
                        entryCount++
                        if (entryCount > MAX_ENTRIES) throw IOException("The archive has too many entries.")
                        storage.listDirectory(path).sortedBy { it.name }.forEach { child ->
                            addItem(child.path, "$relativePath/${child.name}")
                        }
                    }
                    FileEntryType.FILE -> {
                        val bytes = storage.readBytes(path)
                        if (bytes.size > MAX_ENTRY_BYTES) throw IOException("File exceeds the ZIP size limit: $path")
                        zip.putNextEntry(ZipEntry(relativePath))
                        zip.write(bytes)
                        zip.closeEntry()
                        entryCount++
                        if (entryCount > MAX_ENTRIES) throw IOException("The archive has too many entries.")
                    }
                    else -> throw IOException("This item type cannot be archived safely: $path")
                }
            }

            sourceItems.forEach { item ->
                val relativePath = relativeToBase(item.path, basePath)
                addItem(item.path, relativePath)
            }
        }

        val temporaryPath = joinPath(parentPath(outputPath), ".astracode-tmp-${UUID.randomUUID()}")
        try {
            storage.writeBytes(temporaryPath, output.toByteArray())
            storage.move(temporaryPath, outputPath, replaceExisting = false)
        } catch (error: Exception) {
            runCatching {
                if (storage.exists(temporaryPath)) storage.delete(temporaryPath, recursive = false)
            }
            throw error
        }
        return outputPath
    }

    /**
     * Creates the ZIP first, then removes the selected source items. If archiving fails,
     * originals remain untouched. If a provider refuses deletion afterward, the completed
     * ZIP is retained and the caller receives a clear partial-move error.
     */
    fun moveToZip(
        storage: WorkspaceStorage,
        sourcePaths: List<String>,
        archivePath: String,
        archiveBasePath: String = "",
    ): String {
        val sources = normalizeSources(storage, sourcePaths)
        val outputPath = createZip(storage, sources.map { it.path }, archivePath, archiveBasePath)
        sources.forEach { item ->
            if (!storage.exists(item.path)) return@forEach
            try {
                storage.delete(item.path, recursive = item.type == FileEntryType.DIRECTORY)
            } catch (error: Exception) {
                throw IOException(
                    "Created $outputPath, but could not remove the original item ${item.path}. The ZIP was kept; check for remaining originals.",
                    error,
                )
            }
        }
        return outputPath
    }

    fun extractZip(
        storage: WorkspaceStorage,
        archivePath: String,
        destinationPath: String,
    ): List<String> {
        val sourcePath = validatePath(archivePath)
        val destination = validatePath(destinationPath)
        val archiveEntry = findWorkspaceEntry(storage, sourcePath)
            ?: throw IOException("The ZIP archive does not exist: $sourcePath")
        if (archiveEntry.type != FileEntryType.FILE) throw IOException("The ZIP archive must be a regular file.")

        val archiveBytes = storage.readBytes(sourcePath)
        if (archiveBytes.size > MAX_ARCHIVE_BYTES) throw IOException("The ZIP archive exceeds the 32 MiB limit.")
        if (storage.exists(destination)) throw IOException("Destination already exists: $destination")
        requireDirectory(storage, parentPath(destination))

        val createdPaths = ArrayList<String>()
        val seen = HashSet<String>()
        var expandedBytes = 0L
        var entryCount = 0
        var destinationCreated = false
        try {
            storage.createDirectory(destination)
            destinationCreated = true
            ZipInputStream(ByteArrayInputStream(archiveBytes)).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    entryCount++
                    if (entryCount > MAX_ENTRIES) throw IOException("The ZIP has too many entries.")
                    val relative = validateArchiveEntry(entry.name, entry.isDirectory)
                    if (relative.split('/').size > MAX_PATH_DEPTH) throw IOException("The ZIP path is too deep.")
                    if (!seen.add(relative)) throw IOException("The ZIP contains a duplicate path: $relative")

                    val outputPath = joinPath(destination, relative)
                    if (entry.isDirectory) {
                        ensureDirectory(storage, outputPath)
                    } else {
                        ensureDirectory(storage, parentPath(outputPath))
                        if (storage.exists(outputPath)) throw IOException("The ZIP contains a conflicting path: $relative")
                        val entryBytes = BoundedByteArrayOutputStream(MAX_ENTRY_BYTES)
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = zip.read(buffer)
                            if (read < 0) break
                            if (read == 0) continue
                            expandedBytes += read
                            if (expandedBytes > MAX_EXTRACTED_BYTES) {
                                throw IOException("The ZIP expands beyond the 256 MiB safety limit.")
                            }
                            entryBytes.write(buffer, 0, read)
                        }
                        storage.writeBytes(outputPath, entryBytes.toByteArray())
                        createdPaths += outputPath
                    }
                    zip.closeEntry()
                }
            }
        } catch (error: Exception) {
            if (destinationCreated) {
                runCatching {
                    if (storage.exists(destination)) storage.delete(destination, recursive = true)
                }.onFailure { rollbackError -> error.addSuppressed(rollbackError) }
            }
            throw error
        }
        return createdPaths
    }

    private fun normalizeSources(storage: WorkspaceStorage, paths: List<String>): List<FileEntry> {
        val items = paths.distinct().map { path ->
            findWorkspaceEntry(storage, validatePath(path))
                ?: throw IOException("Selected item no longer exists: $path")
        }
        items.forEach { item ->
            if (item.type != FileEntryType.FILE && item.type != FileEntryType.DIRECTORY) {
                throw IOException("This item type cannot be archived safely: ${item.path}")
            }
        }
        val sorted = items.sortedBy { it.path.count { ch -> ch == '/' } }
        val retained = mutableListOf<FileEntry>()
        sorted.forEach { item ->
            if (retained.none { parent ->
                    item.path == parent.path ||
                        (parent.type == FileEntryType.DIRECTORY && isSameOrChild(item.path, parent.path))
                }) retained += item
        }
        return retained
    }

    private fun findWorkspaceEntry(storage: WorkspaceStorage, path: String): FileEntry? {
        val relative = validatePath(path)
        if (!storage.exists(relative)) return null
        val parts = relative.split('/')
        var parent = ""
        var found: FileEntry? = null
        parts.forEachIndexed { index, segment ->
            val entry = storage.listDirectory(parent).firstOrNull { it.name == segment } ?: return null
            if (index < parts.lastIndex && entry.type != FileEntryType.DIRECTORY) {
                throw IOException("Not a folder: $parent")
            }
            found = entry
            parent = joinPath(parent, segment)
        }
        return found
    }

    private fun requireDirectory(storage: WorkspaceStorage, path: String) {
        if (path.isBlank()) {
            storage.listDirectory("")
            return
        }
        val item = findWorkspaceEntry(storage, path)
            ?: throw IOException("Folder does not exist: $path")
        if (item.type != FileEntryType.DIRECTORY) throw IOException("Not a folder: $path")
    }

    private fun ensureDirectory(storage: WorkspaceStorage, path: String) {
        if (path.isBlank()) {
            storage.listDirectory("")
            return
        }
        val item = findWorkspaceEntry(storage, path)
        if (item != null) {
            if (item.type != FileEntryType.DIRECTORY) throw IOException("Not a folder: $path")
            return
        }
        ensureDirectory(storage, parentPath(path))
        storage.createDirectory(path)
    }

    private fun validatePath(rawPath: String): String {
        if (rawPath.isBlank() || rawPath.length > MAX_PATH_LENGTH || rawPath.startsWith("/") || rawPath.contains('\\') ||
            rawPath.indexOf('\u0000') >= 0) {
            throw SecurityException("Only workspace-relative paths are allowed.")
        }
        val segments = rawPath.split('/')
        if (segments.size > MAX_PATH_DEPTH ||
            segments.any { it.isBlank() || it == "." || it == ".." } ||
            WorkspaceSafety.isReservedPath(rawPath)) {
            throw SecurityException("Invalid, reserved, or excessively deep workspace path.")
        }
        return segments.joinToString("/")
    }

    private fun validateArchiveEntry(rawName: String, directory: Boolean): String {
        if (rawName.isBlank() || rawName.length > MAX_PATH_LENGTH || rawName.startsWith("/") || rawName.contains('\\') ||
            rawName.indexOf('\u0000') >= 0 || Regex("^[A-Za-z]:").containsMatchIn(rawName)) {
            throw SecurityException("The ZIP contains an unsafe path.")
        }
        val relative = if (directory) rawName.removeSuffix("/") else rawName
        val segments = relative.split('/')
        if (segments.size > MAX_PATH_DEPTH ||
            segments.any { it.isBlank() || it == "." || it == ".." } ||
            WorkspaceSafety.isReservedPath(relative)) {
            throw SecurityException("The ZIP contains an unsafe or reserved path.")
        }
        return segments.joinToString("/")
    }

    private fun relativeToBase(path: String, base: String): String {
        if (base.isBlank()) return path
        if (!isSameOrChild(path, base) || path == base) {
            throw IOException("Selected item is not inside the current folder: $path")
        }
        return path.removePrefix(base + "/")
    }

    private fun isSameOrChild(path: String, root: String): Boolean =
        path == root || path.startsWith(root + "/")

    private fun joinPath(parent: String, child: String): String =
        if (parent.isBlank()) child else "$parent/$child"

    private fun parentPath(path: String): String = path.substringBeforeLast('/', "")

    private fun fileName(path: String): String = path.substringAfterLast('/')

    private class BoundedByteArrayOutputStream(private val maxBytes: Int) : ByteArrayOutputStream() {
        private fun ensureCapacityFor(additional: Int) {
            if (additional < 0 || count > maxBytes - additional) {
                throw IOException("Archive exceeds the 32 MiB size limit.")
            }
        }

        override fun write(value: Int) {
            ensureCapacityFor(1)
            super.write(value)
        }

        override fun write(buffer: ByteArray, offset: Int, length: Int) {
            ensureCapacityFor(length)
            super.write(buffer, offset, length)
        }
    }
}

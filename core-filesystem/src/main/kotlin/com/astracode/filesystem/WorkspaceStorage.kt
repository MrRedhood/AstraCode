package com.astracode.filesystem

/**
 * Storage-neutral contract consumed by the editor and file-browser UI.
 *
 * Paths are relative to the selected workspace root, never absolute host paths.
 * Implementations may use a private filesystem directory or a user-granted
 * Android document tree. Providers may offer different guarantees; callers
 * should rely only on the behavior promised by each implementation.
 */
enum class WorkspaceStorageType { PRIVATE, DOCUMENT_TREE }

interface WorkspaceStorage : AutoCloseable {
    val displayName: String
    val storageType: WorkspaceStorageType

    fun addChangeListener(listener: (FileChangeEvent) -> Unit): AutoCloseable
    fun exists(relativePath: String): Boolean
    fun listDirectory(relativePath: String = ""): List<FileEntry>
    fun createDirectory(relativePath: String): FileEntry
    fun readText(relativePath: String): TextFileSnapshot
    fun writeText(relativePath: String, text: String): TextFileWriteResult
    fun move(sourcePath: String, targetPath: String, replaceExisting: Boolean = false): FileEntry
    fun delete(relativePath: String, recursive: Boolean = false)

    fun scanTree(maxDepth: Int = Int.MAX_VALUE): List<FileEntry> {
        require(maxDepth >= 0) { "maxDepth must be zero or greater" }
        val output = mutableListOf<FileEntry>()
        fun visit(directory: String, depth: Int) {
            if (depth >= maxDepth) return
            for (entry in listDirectory(directory)) {
                output += entry
                if (entry.type == FileEntryType.DIRECTORY) visit(entry.path, depth + 1)
            }
        }
        visit("", 0)
        return output
    }

    override fun close()
}

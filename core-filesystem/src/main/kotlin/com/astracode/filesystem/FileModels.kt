package com.astracode.filesystem

import java.time.Instant

enum class FileEntryType { FILE, DIRECTORY, SYMBOLIC_LINK, OTHER }

data class FileEntry(
    /** Workspace-relative path using forward slashes. */
    val path: String,
    val name: String,
    val type: FileEntryType,
    val sizeBytes: Long,
    val modifiedAt: Instant,
    val readable: Boolean,
    val writable: Boolean,
    /** Creation timestamp when the active storage provider can report it. */
    val createdAt: Instant? = null,
)

data class TextFileSnapshot(
    val path: String,
    val text: String,
    val sourceByteLength: Long,
    val modifiedAt: Instant,
    val hadLeadingBom: Boolean,
    val repairedSpans: List<RepairedSpan>,
    /** True when the diagnostic span list reached its configured safety limit. */
    val repairedSpansTruncated: Boolean = false,
) {
    val wasRepaired: Boolean get() = repairedSpans.isNotEmpty() || repairedSpansTruncated
}

data class TextFileWriteResult(
    val path: String,
    val bytesWritten: Long,
    /** Exact text represented by the saved UTF-8 bytes. */
    val persistedText: String,
    val replacedUnpairedSurrogateCount: Int,
)

enum class FileChangeType { CREATED, MODIFIED, DELETED, MOVED, DIRECTORY_CREATED }

data class FileChangeEvent(
    val type: FileChangeType,
    val path: String,
    val previousPath: String? = null,
)

data class FileSystemOptions(
    val maxFileBytes: Long = 32L * 1024L * 1024L,
    val cacheCapacity: Int = 32,
    val defaultReadBufferSize: Int = 8 * 1024,
    /** Number of extra reads allowed if a file changes during a read. */
    val concurrentReadRetries: Int = 2,
    /** Approximate UTF-16 text cache budget; zero disables text caching. */
    val maxCachedTextBytes: Long = 8L * 1024L * 1024L,
    /** Maximum malformed UTF-8 source spans retained for editor diagnostics. */
    val maxRepairSpans: Int = 100_000,
) {
    init {
        require(maxFileBytes > 0) { "maxFileBytes must be greater than zero" }
        require(cacheCapacity >= 0) { "cacheCapacity must be zero or greater" }
        require(defaultReadBufferSize > 0) { "defaultReadBufferSize must be greater than zero" }
        require(concurrentReadRetries >= 0) { "concurrentReadRetries must be zero or greater" }
        require(maxCachedTextBytes >= 0L) { "maxCachedTextBytes must be zero or greater" }
        require(maxRepairSpans >= 0) { "maxRepairSpans must be zero or greater" }
    }
}

class FileTooLargeException(path: String, size: Long, limit: Long) :
    IllegalArgumentException("File '$path' is $size bytes; configured limit is $limit bytes")

class ConcurrentFileModificationException(path: String) :
    IllegalStateException("File '$path' kept changing while it was being read")

class AtomicFileOperationNotSupportedException(path: String, cause: Throwable) :
    IllegalStateException("Atomic file operation is not supported for '$path'; existing data was not intentionally replaced", cause)

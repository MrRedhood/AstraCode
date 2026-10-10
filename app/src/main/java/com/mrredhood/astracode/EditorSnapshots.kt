package com.mrredhood.astracode

import android.content.Context
import android.util.AtomicFile
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.UUID

internal data class EditorSnapshotSummary(
    val id: String,
    val fileName: String,
    val createdAtMillis: Long,
    val contentBytes: Int,
)

internal data class EditorSnapshotRecord(
    val id: String,
    val fileName: String,
    val createdAtMillis: Long,
    val content: String,
)

internal enum class EditorSnapshotCreateResult { Created, TooLarge, Failed }

internal object EditorSnapshotCodec {
    const val MAX_CONTENT_BYTES = 2 * 1024 * 1024
    private const val MAGIC = 0x4153534E // ASSN
    private const val VERSION = 1
    private val idPattern = Regex("[0-9]{1,20}-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")

    fun isValidId(id: String): Boolean = idPattern.matches(id)

    fun encode(record: EditorSnapshotRecord): ByteArray? {
        if (!isValidId(record.id) || record.fileName.isBlank() || record.fileName.length > 120) return null
        val payload = record.content.toByteArray(Charsets.UTF_8)
        if (payload.size > MAX_CONTENT_BYTES) return null
        return ByteArrayOutputStream(payload.size + 256).also { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeUTF(record.id)
                output.writeUTF(record.fileName)
                output.writeLong(record.createdAtMillis)
                output.writeInt(payload.size)
                output.write(payload)
            }
        }.toByteArray()
    }

    fun decode(bytes: ByteArray): EditorSnapshotRecord? {
        if (bytes.size > MAX_CONTENT_BYTES + 512) return null
        return try {
            DataInputStream(ByteArrayInputStream(bytes)).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION) return null
                val id = input.readUTF()
                val fileName = input.readUTF()
                val timestamp = input.readLong()
                val length = input.readInt()
                if (!isValidId(id) || fileName.isBlank() || fileName.length > 120) return null
                if (length < 0 || length > MAX_CONTENT_BYTES || length != input.available()) return null
                val payload = ByteArray(length)
                input.readFully(payload)
                val decoder = Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                EditorSnapshotRecord(id, fileName, timestamp, decoder.decode(ByteBuffer.wrap(payload)).toString())
            }
        } catch (_: Exception) {
            null
        }
    }
}

/** Local snapshots never mutate workspace documents; only explicit Save writes to SAF. */
internal class EditorSnapshotStore(context: Context) {
    companion object {
        const val MAX_SNAPSHOTS_PER_FILE = 10
        private const val EXTENSION = ".snap"
    }

    private val root = File(context.noBackupFilesDir, "editor-snapshots")

    @Synchronized
    fun create(treeUri: String, documentId: String, fileName: String, content: String): EditorSnapshotCreateResult {
        val now = System.currentTimeMillis()
        val id = "$now-${UUID.randomUUID()}"
        val record = EditorSnapshotRecord(id, fileName, now, content)
        val encoded = EditorSnapshotCodec.encode(record) ?: return EditorSnapshotCreateResult.TooLarge
        val directory = snapshotDirectory(treeUri, documentId, true) ?: return EditorSnapshotCreateResult.Failed
        val atomic = AtomicFile(File(directory, "$id$EXTENSION"))
        var stream: FileOutputStream? = null
        return try {
            stream = atomic.startWrite()
            stream.write(encoded)
            stream.fd.sync()
            atomic.finishWrite(stream)
            stream = null
            prune(treeUri, documentId)
            EditorSnapshotCreateResult.Created
        } catch (_: Exception) {
            val failedStream = stream
            if (failedStream != null) runCatching { atomic.failWrite(failedStream) }
            EditorSnapshotCreateResult.Failed
        }
    }

    @Synchronized
    fun list(treeUri: String, documentId: String): List<EditorSnapshotSummary> {
        val directory = snapshotDirectory(treeUri, documentId, false) ?: return emptyList()
        return directory.listFiles().orEmpty()
            .filter { it.isFile && it.name.endsWith(EXTENSION) }
            .mapNotNull { file ->
                val record = readRecord(file)
                if (record == null || file.name != record.id + EXTENSION) {
                    runCatching { AtomicFile(file).delete() }
                    null
                } else {
                    EditorSnapshotSummary(record.id, record.fileName, record.createdAtMillis, record.content.toByteArray(Charsets.UTF_8).size)
                }
            }
            .sortedByDescending { it.createdAtMillis }
    }

    @Synchronized
    fun read(treeUri: String, documentId: String, snapshotId: String): EditorSnapshotRecord? {
        if (!EditorSnapshotCodec.isValidId(snapshotId)) return null
        val directory = snapshotDirectory(treeUri, documentId, false) ?: return null
        val file = File(directory, "$snapshotId$EXTENSION")
        if (!file.isFile) return null
        val record = readRecord(file)
        if (record == null || record.id != snapshotId) {
            runCatching { AtomicFile(file).delete() }
            return null
        }
        return record
    }

    @Synchronized
    fun delete(treeUri: String, documentId: String, snapshotId: String): Boolean {
        if (!EditorSnapshotCodec.isValidId(snapshotId)) return false
        val directory = snapshotDirectory(treeUri, documentId, false) ?: return false
        val file = File(directory, "$snapshotId$EXTENSION")
        if (!file.isFile) return false
        return runCatching { AtomicFile(file).delete(); true }.getOrDefault(false)
    }

    @Synchronized
    fun deleteAll(treeUri: String, documentId: String) {
        val directory = snapshotDirectory(treeUri, documentId, false) ?: return
        directory.listFiles().orEmpty().filter { it.isFile && it.name.endsWith(EXTENSION) }
            .forEach { runCatching { AtomicFile(it).delete() } }
        directory.delete()
    }

    private fun readRecord(file: File): EditorSnapshotRecord? = try {
        AtomicFile(file).openRead().use { input -> EditorSnapshotCodec.decode(input.readBytes()) }
    } catch (_: Exception) {
        null
    }

    private fun prune(treeUri: String, documentId: String) {
        list(treeUri, documentId).drop(MAX_SNAPSHOTS_PER_FILE).forEach { delete(treeUri, documentId, it.id) }
    }

    private fun snapshotDirectory(treeUri: String, documentId: String, create: Boolean): File? {
        val key = EditorDraftRecordCodec.fingerprint("$treeUri\n$documentId")
        val directory = File(root, key)
        if (create) {
            if (!directory.isDirectory && !directory.mkdirs()) return null
        } else if (!directory.isDirectory) return null
        return directory
    }
}

internal data class EditorTextDiffResult(
    val lines: List<String>,
    val approximate: Boolean = false,
    val omittedLineCount: Int = 0,
)

internal data class EditorSnapshotDiffView(
    val currentFileName: String,
    val snapshot: EditorSnapshotSummary,
    val result: EditorTextDiffResult,
)

/** Bounded line diff: exact LCS for small changes, compact summary for large inputs. */
internal object EditorTextDiff {
    private const val MAX_MATRIX_CELLS = 40_000L
    private const val MAX_VISIBLE_LINES = 240
    private const val SUMMARY_SIDE_LIMIT = 100

    fun compare(before: String, after: String): EditorTextDiffResult {
        if (before == after) return EditorTextDiffResult(listOf("No differences from this snapshot."))
        val oldCount = countLines(before)
        val newCount = countLines(after)
        val cells = (oldCount + 1L) * (newCount + 1L)
        if (cells > MAX_MATRIX_CELLS) return summaryDiff(before, after, oldCount, newCount)

        val oldLines = before.split('\n').map { it.removeSuffix("\r") }
        val newLines = after.split('\n').map { it.removeSuffix("\r") }
        val table = Array(oldLines.size + 1) { IntArray(newLines.size + 1) }
        for (i in oldLines.lastIndex downTo 0) {
            for (j in newLines.lastIndex downTo 0) {
                table[i][j] = if (oldLines[i] == newLines[j]) table[i + 1][j + 1] + 1
                else maxOf(table[i + 1][j], table[i][j + 1])
            }
        }
        val output = mutableListOf<String>()
        var omitted = 0
        fun append(line: String) {
            if (output.size < MAX_VISIBLE_LINES - 1) output.add(line) else omitted++
        }
        var i = 0
        var j = 0
        while (i < oldLines.size && j < newLines.size) {
            if (oldLines[i] == newLines[j]) { append("  " + oldLines[i]); i++; j++ }
            else if (table[i + 1][j] >= table[i][j + 1]) { append("- " + oldLines[i]); i++ }
            else { append("+ " + newLines[j]); j++ }
        }
        while (i < oldLines.size) append("- " + oldLines[i++])
        while (j < newLines.size) append("+ " + newLines[j++])
        if (omitted > 0) output.add("… additional diff line(s) omitted …")
        return EditorTextDiffResult(output, omittedLineCount = omitted)
    }

    private fun summaryDiff(before: String, after: String, oldCount: Int, newCount: Int): EditorTextDiffResult {
        var prefix = 0
        val oldForward = ForwardLineCursor(before)
        val newForward = ForwardLineCursor(after)
        val sharedLimit = minOf(oldCount, newCount)
        while (prefix < sharedLimit) {
            if (!oldForward.advance() || !newForward.advance()) break
            if (!oldForward.sameLine(newForward)) break
            prefix++
        }

        var suffix = 0
        val oldBackward = BackwardLineCursor(before)
        val newBackward = BackwardLineCursor(after)
        val suffixLimit = minOf(oldCount - prefix, newCount - prefix)
        while (suffix < suffixLimit) {
            if (!oldBackward.advance() || !newBackward.advance()) break
            if (!oldBackward.sameLine(newBackward)) break
            suffix++
        }

        val oldChangedCount = (oldCount - prefix - suffix).coerceAtLeast(0)
        val newChangedCount = (newCount - prefix - suffix).coerceAtLeast(0)
        val lines = mutableListOf(
            "Large file diff summary",
            "Old: " + oldCount + " lines; current: " + newCount + " lines",
            "Common prefix: " + prefix + " line(s); common suffix: " + suffix + " line(s)",
        )
        val oldSample = sampleChangedLines(before, prefix, oldChangedCount)
        oldSample.forEach { lines.add("- " + it) }
        val oldOmitted = (oldChangedCount - oldSample.size).coerceAtLeast(0)
        if (oldOmitted > 0) lines.add("… " + oldOmitted + " removed line(s) omitted …")
        val newSample = sampleChangedLines(after, prefix, newChangedCount)
        newSample.forEach { lines.add("+ " + it) }
        val newOmitted = (newChangedCount - newSample.size).coerceAtLeast(0)
        if (newOmitted > 0) lines.add("… " + newOmitted + " added line(s) omitted …")
        if (suffix > 0) lines.add("  … " + suffix + " shared trailing line(s) …")
        return EditorTextDiffResult(lines.take(MAX_VISIBLE_LINES), approximate = true, omittedLineCount = oldOmitted + newOmitted)
    }

    private fun sampleChangedLines(text: String, skip: Int, changedCount: Int): List<String> {
        if (changedCount <= 0) return emptyList()
        val cursor = ForwardLineCursor(text)
        var skipped = 0
        while (skipped < skip && cursor.advance()) skipped++
        val output = ArrayList<String>(minOf(changedCount, SUMMARY_SIDE_LIMIT))
        var sampled = 0
        while (sampled < minOf(changedCount, SUMMARY_SIDE_LIMIT) && cursor.advance()) {
            output += cursor.currentLine()
            sampled++
        }
        return output
    }

    private fun countLines(text: String): Int {
        var count = 1
        for (char in text) if (char == '\n') count++
        return count
    }

    private abstract class LineCursor(protected val text: String) {
        var lineStart: Int = 0
            protected set
        var lineEnd: Int = 0
            protected set

        fun sameLine(other: LineCursor): Boolean {
            val leftLength = lineEnd - lineStart
            val rightLength = other.lineEnd - other.lineStart
            return leftLength == rightLength &&
                text.regionMatches(lineStart, other.text, other.lineStart, leftLength, ignoreCase = false)
        }

        fun currentLine(): String = text.substring(lineStart, lineEnd)

        protected fun trimCarriageReturn() {
            if (lineEnd > lineStart && text[lineEnd - 1] == '\r') lineEnd--
        }

        abstract fun advance(): Boolean
    }

    private class ForwardLineCursor(text: String) : LineCursor(text) {
        private var nextStart = 0
        private var finished = false

        override fun advance(): Boolean {
            if (finished) return false
            val newline = text.indexOf('\n', nextStart)
            if (newline < 0) {
                lineStart = nextStart
                lineEnd = text.length
                finished = true
            } else {
                lineStart = nextStart
                lineEnd = newline
                nextStart = newline + 1
            }
            trimCarriageReturn()
            return true
        }
    }

    private class BackwardLineCursor(text: String) : LineCursor(text) {
        private var nextEnd = text.length
        private var finished = false

        override fun advance(): Boolean {
            if (finished) return false
            val newline = text.lastIndexOf('\n', nextEnd - 1)
            lineStart = newline + 1
            lineEnd = nextEnd
            if (newline < 0) finished = true else nextEnd = newline
            trimCarriageReturn()
            return true
        }
    }
}

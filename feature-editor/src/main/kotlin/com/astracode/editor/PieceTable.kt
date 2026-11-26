package com.astracode.editor

import java.nio.charset.StandardCharsets
import java.util.ArrayDeque

/**
 * Piece Table whose only document coordinate is a UTF-8 byte offset.
 *
 * Inserts and deletes must begin and end on UTF-8 code-point boundaries. The
 * table stores raw UTF-8 bytes; Strings are created only at API boundaries.
 */
class PieceTable(
    initialText: String = "",
    private val historyLimit: Int = 256,
) {
    private enum class Source { ORIGINAL, ADD }

    private data class Piece(val source: Source, val start: Int, val length: Int)

    private data class Edit(
        val offset: Int,
        val removed: ByteArray,
        val inserted: ByteArray,
    )

    private class AppendBuffer(initialCapacity: Int = 1024) {
        private var storage = ByteArray(initialCapacity.coerceAtLeast(1))
        var size: Int = 0
            private set

        fun append(bytes: ByteArray): Int {
            val start = size
            val required = size.toLong() + bytes.size.toLong()
            require(required <= Int.MAX_VALUE.toLong()) { "Document is too large" }
            ensureCapacity(required.toInt())
            bytes.copyInto(storage, start)
            size = required.toInt()
            return start
        }

        operator fun get(index: Int): Byte = storage[index]

        fun copyInto(sourceStart: Int, destination: ByteArray, destinationStart: Int, length: Int) {
            storage.copyInto(destination, destinationStart, sourceStart, sourceStart + length)
        }

        fun reset(bytes: ByteArray) {
            storage = ByteArray(maxOf(1024, bytes.size))
            bytes.copyInto(storage)
            size = bytes.size
        }

        private fun ensureCapacity(required: Int) {
            if (required <= storage.size) return
            var capacity = storage.size
            while (capacity < required) {
                val nextCapacity = (capacity.toLong() * 2L)
                    .coerceAtMost(Int.MAX_VALUE.toLong())
                require(nextCapacity > capacity.toLong()) { "Document is too large" }
                capacity = nextCapacity.toInt()
            }
            storage = storage.copyOf(capacity)
        }
    }

    private var original = initialText.toByteArray(StandardCharsets.UTF_8)
    private val additions = AppendBuffer()
    private val pieces = mutableListOf<Piece>()
    private val undoHistory = ArrayDeque<Edit>()
    private val redoHistory = ArrayDeque<Edit>()
    private var documentLength = original.size

    init {
        require(historyLimit >= 0) { "historyLimit must not be negative" }
        if (original.isNotEmpty()) pieces += Piece(Source.ORIGINAL, 0, original.size)
    }

    /** Number of UTF-8 bytes in the document. All edit offsets use this unit. */
    val length: Int get() = documentLength
    val byteLength: Int get() = documentLength
    val isEmpty: Boolean get() = documentLength == 0
    val canUndo: Boolean get() = undoHistory.isNotEmpty()
    val canRedo: Boolean get() = redoHistory.isNotEmpty()
    val pieceCount: Int get() = pieces.size

    /** Returns one raw UTF-8 byte at [byteOffset]. */
    operator fun get(byteOffset: Int): Byte {
        require(byteOffset in 0 until documentLength) { "Byte offset is outside the document" }
        var logicalOffset = 0
        for (piece in pieces) {
            val pieceEnd = logicalOffset + piece.length
            if (byteOffset < pieceEnd) {
                return byteAt(piece.source, piece.start + byteOffset - logicalOffset)
            }
            logicalOffset = pieceEnd
        }
        throw IndexOutOfBoundsException("Byte offset is outside the document")
    }

    fun insert(byteOffset: Int, text: String) {
        replace(byteOffset, byteOffset, text)
    }

    fun delete(startByteOffset: Int, endByteOffset: Int): String =
        replace(startByteOffset, endByteOffset, "")

    /** Replaces [startByteOffset, endByteOffset) and returns removed UTF-8 text. */
    fun replace(startByteOffset: Int, endByteOffset: Int, replacement: String): String {
        validateRange(startByteOffset, endByteOffset)
        val replacementBytes = replacement.toByteArray(StandardCharsets.UTF_8)
        val removedBytes = sliceBytesUnchecked(startByteOffset, endByteOffset)
        if (removedBytes.contentEquals(replacementBytes)) return decode(removedBytes)

        applyEdit(startByteOffset, endByteOffset, replacementBytes)
        if (historyLimit > 0) {
            if (undoHistory.size >= historyLimit) undoHistory.removeFirst()
            undoHistory.addLast(Edit(startByteOffset, removedBytes, replacementBytes))
        }
        redoHistory.clear()
        compactIfNeeded()
        return decode(removedBytes)
    }

    fun substring(startByteOffset: Int, endByteOffset: Int): String {
        validateRange(startByteOffset, endByteOffset)
        return decode(sliceBytesUnchecked(startByteOffset, endByteOffset))
    }

    fun sliceBytes(startByteOffset: Int, endByteOffset: Int): ByteArray {
        validateRange(startByteOffset, endByteOffset)
        return sliceBytesUnchecked(startByteOffset, endByteOffset)
    }

    fun lineCount(): Int {
        if (documentLength == 0) return 1
        var lines = 1
        forEachByte { value -> if (value == NEWLINE_BYTE) lines++ }
        return lines
    }

    /** Returns the zero-based line containing this UTF-8 byte offset. */
    fun lineOfOffset(byteOffset: Int): Int {
        require(byteOffset in 0..documentLength) { "Byte offset is outside the document" }
        require(isBoundary(byteOffset)) { "Byte offset splits a UTF-8 character" }
        var line = 0
        for (index in 0 until byteOffset) {
            if (get(index) == NEWLINE_BYTE) line++
        }
        return line
    }

    fun undo(): Boolean {
        if (undoHistory.isEmpty()) return false
        val edit = undoHistory.removeLast()
        applyEdit(edit.offset, edit.offset + edit.inserted.size, edit.removed)
        redoHistory.addLast(edit)
        compactIfNeeded()
        return true
    }

    fun redo(): Boolean {
        if (redoHistory.isEmpty()) return false
        val edit = redoHistory.removeLast()
        applyEdit(edit.offset, edit.offset + edit.removed.size, edit.inserted)
        undoHistory.addLast(edit)
        compactIfNeeded()
        return true
    }

    /**
     * Applies a Compose/String snapshot as a minimal UTF-8 byte edit.
     * The common prefix/suffix is moved to complete code-point boundaries.
     */
    fun replaceDocumentText(nextText: String) {
        val currentBytes = toByteArray()
        val nextBytes = nextText.toByteArray(StandardCharsets.UTF_8)
        if (currentBytes.contentEquals(nextBytes)) return

        var prefix = 0
        val commonLimit = minOf(currentBytes.size, nextBytes.size)
        while (prefix < commonLimit && currentBytes[prefix] == nextBytes[prefix]) prefix++
        while (prefix > 0 && (!isBoundary(currentBytes, prefix) || !isBoundary(nextBytes, prefix))) {
            prefix--
        }

        var suffix = 0
        while (
            suffix < currentBytes.size - prefix &&
            suffix < nextBytes.size - prefix &&
            currentBytes[currentBytes.size - 1 - suffix] == nextBytes[nextBytes.size - 1 - suffix]
        ) suffix++
        while (
            suffix > 0 &&
            (!isBoundary(currentBytes, currentBytes.size - suffix) ||
                !isBoundary(nextBytes, nextBytes.size - suffix))
        ) suffix--

        val replacement = decode(nextBytes.copyOfRange(prefix, nextBytes.size - suffix))
        replace(prefix, currentBytes.size - suffix, replacement)
    }

    /** Compacts all pieces into one original UTF-8 buffer and clears history. */
    fun compact() {
        compactInternal(clearHistory = true)
    }

    /**
     * Rebuilds the piece storage without changing logical document contents.
     * Edit history remains valid because every stored edit uses document byte
     * offsets and the document snapshot itself has not changed.
     */
    private fun compactInternal(clearHistory: Boolean) {
        val snapshot = toByteArray()
        original = snapshot
        additions.reset(byteArrayOf())
        pieces.clear()
        if (snapshot.isNotEmpty()) pieces += Piece(Source.ORIGINAL, 0, snapshot.size)
        documentLength = snapshot.size
        if (clearHistory) {
            undoHistory.clear()
            redoHistory.clear()
        }
    }

    fun toByteArray(): ByteArray = sliceBytesUnchecked(0, documentLength)

    override fun toString(): String = decode(toByteArray())

    private fun applyEdit(startByteOffset: Int, endByteOffset: Int, replacement: ByteArray) {
        val startIndex = splitAt(startByteOffset)
        val endIndex = splitAt(endByteOffset)
        pieces.subList(startIndex, endIndex).clear()

        if (replacement.isNotEmpty()) {
            val addStart = additions.append(replacement)
            pieces.add(startIndex, Piece(Source.ADD, addStart, replacement.size))
        }
        documentLength += replacement.size - (endByteOffset - startByteOffset)
        coalesceAdjacentPieces()
    }

    private fun splitAt(byteOffset: Int): Int {
        if (byteOffset == 0) return 0
        if (byteOffset == documentLength) return pieces.size

        var logicalOffset = 0
        for (index in pieces.indices) {
            val piece = pieces[index]
            val pieceEnd = logicalOffset + piece.length
            when {
                byteOffset == logicalOffset -> return index
                byteOffset == pieceEnd -> return index + 1
                byteOffset in (logicalOffset + 1) until pieceEnd -> {
                    val leftLength = byteOffset - logicalOffset
                    pieces[index] = piece.copy(length = leftLength)
                    pieces.add(
                        index + 1,
                        piece.copy(
                            start = piece.start + leftLength,
                            length = piece.length - leftLength,
                        ),
                    )
                    return index + 1
                }
            }
            logicalOffset = pieceEnd
        }
        return pieces.size
    }

    private fun coalesceAdjacentPieces() {
        var index = 1
        while (index < pieces.size) {
            val previous = pieces[index - 1]
            val current = pieces[index]
            if (
                previous.source == current.source &&
                previous.start + previous.length == current.start
            ) {
                pieces[index - 1] = previous.copy(length = previous.length + current.length)
                pieces.removeAt(index)
            } else {
                index++
            }
        }
    }

    private fun compactIfNeeded() {
        val retainedAddBytes = additions.size.toLong()
        val threshold = maxOf(256L * 1024L, documentLength.toLong() * 4L)
        if (retainedAddBytes > threshold) compactInternal(clearHistory = false)
    }

    private fun byteAt(source: Source, index: Int): Byte =
        if (source == Source.ORIGINAL) original[index] else additions[index]

    private fun copyBytes(
        source: Source,
        sourceStart: Int,
        destination: ByteArray,
        destinationStart: Int,
        length: Int,
    ) {
        if (source == Source.ORIGINAL) {
            original.copyInto(destination, destinationStart, sourceStart, sourceStart + length)
        } else {
            additions.copyInto(sourceStart, destination, destinationStart, length)
        }
    }

    private fun sliceBytesUnchecked(start: Int, end: Int): ByteArray {
        if (start == end) return byteArrayOf()
        val result = ByteArray(end - start)
        var logicalOffset = 0
        var destinationOffset = 0
        for (piece in pieces) {
            val pieceEnd = logicalOffset + piece.length
            val overlapStart = maxOf(start, logicalOffset)
            val overlapEnd = minOf(end, pieceEnd)
            if (overlapStart < overlapEnd) {
                val count = overlapEnd - overlapStart
                copyBytes(
                    piece.source,
                    piece.start + overlapStart - logicalOffset,
                    result,
                    destinationOffset,
                    count,
                )
                destinationOffset += count
            }
            if (pieceEnd >= end) break
            logicalOffset = pieceEnd
        }
        return result
    }

    private inline fun forEachByte(action: (Byte) -> Unit) {
        for (piece in pieces) {
            for (index in 0 until piece.length) {
                action(byteAt(piece.source, piece.start + index))
            }
        }
    }

    private fun validateRange(start: Int, end: Int) {
        require(start >= 0 && end >= start && end <= documentLength) {
            "Invalid UTF-8 byte range"
        }
        require(isBoundary(start) && isBoundary(end)) {
            "Edit offsets must be at UTF-8 character boundaries"
        }
    }

    private fun isBoundary(offset: Int): Boolean =
        offset == documentLength || !isContinuation(get(offset))

    private fun isBoundary(bytes: ByteArray, offset: Int): Boolean =
        offset == bytes.size || !isContinuation(bytes[offset])

    private fun isContinuation(value: Byte): Boolean = (value.toInt() and 0xC0) == 0x80

    private fun decode(bytes: ByteArray): String = String(bytes, StandardCharsets.UTF_8)

    private companion object {
        val NEWLINE_BYTE: Byte = 0x0A
    }
}

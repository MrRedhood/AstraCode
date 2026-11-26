package com.astracode.editor

import java.util.ArrayDeque

/**
 * A UTF-16-offset Piece Table.
 *
 * Original text is immutable until compaction. New text is appended to an add
 * buffer; edits only split, remove, and insert piece descriptors. The complete
 * document is materialized only when a caller explicitly asks for a snapshot.
 */
class PieceTable(
    initialText: String = "",
    private val historyLimit: Int = 256,
) {
    private enum class Source { ORIGINAL, ADD }

    private data class Piece(
        val source: Source,
        val start: Int,
        val length: Int,
    )

    private data class Edit(
        val offset: Int,
        val removed: String,
        val inserted: String,
    )

    private var original = initialText
    private val additions = StringBuilder()
    private val pieces = mutableListOf<Piece>()
    private val undoHistory = ArrayDeque<Edit>()
    private val redoHistory = ArrayDeque<Edit>()
    private var documentLength = initialText.length

    init {
        require(historyLimit >= 0) { "historyLimit must not be negative" }
        if (initialText.isNotEmpty()) {
            pieces += Piece(Source.ORIGINAL, 0, initialText.length)
        }
    }

    val length: Int get() = documentLength
    val isEmpty: Boolean get() = documentLength == 0
    val canUndo: Boolean get() = undoHistory.isNotEmpty()
    val canRedo: Boolean get() = redoHistory.isNotEmpty()
    val pieceCount: Int get() = pieces.size

    operator fun get(index: Int): Char {
        require(index in 0 until documentLength) { "Index is outside the document" }
        var logicalOffset = 0
        for (piece in pieces) {
            val pieceEnd = logicalOffset + piece.length
            if (index < pieceEnd) {
                val bufferOffset = piece.start + (index - logicalOffset)
                return buffer(piece.source)[bufferOffset]
            }
            logicalOffset = pieceEnd
        }
        throw IndexOutOfBoundsException("Index is outside the document")
    }

    fun insert(offset: Int, text: String) {
        replace(offset, offset, text)
    }

    fun delete(startOffset: Int, endOffset: Int): String =
        replace(startOffset, endOffset, "")

    /**
     * Replaces [startOffset, endOffset) with [replacement].
     * Returns the removed text. Offsets use Kotlin String/Compose UTF-16 units.
     */
    fun replace(startOffset: Int, endOffset: Int, replacement: String): String {
        validateRange(startOffset, endOffset)
        val removed = substring(startOffset, endOffset)
        if (removed == replacement) return removed

        applyEdit(startOffset, endOffset, replacement)
        if (historyLimit > 0) {
            if (undoHistory.size >= historyLimit) undoHistory.removeFirst()
            undoHistory.addLast(Edit(startOffset, removed, replacement))
        }
        redoHistory.clear()
        compactIfNeeded()
        return removed
    }

    fun substring(startOffset: Int, endOffset: Int): String {
        validateRange(startOffset, endOffset)
        if (startOffset == endOffset) return ""
        val result = StringBuilder(endOffset - startOffset)
        var logicalOffset = 0
        for (piece in pieces) {
            val pieceEnd = logicalOffset + piece.length
            val overlapStart = maxOf(startOffset, logicalOffset)
            val overlapEnd = minOf(endOffset, pieceEnd)
            if (overlapStart < overlapEnd) {
                val sourceStart = piece.start + overlapStart - logicalOffset
                val sourceEnd = sourceStart + overlapEnd - overlapStart
                result.append(buffer(piece.source), sourceStart, sourceEnd)
            }
            if (pieceEnd >= endOffset) break
            logicalOffset = pieceEnd
        }
        return result.toString()
    }

    fun lineCount(): Int {
        if (documentLength == 0) return 1
        var lines = 1
        for (piece in pieces) {
            val source = buffer(piece.source)
            val end = piece.start + piece.length
            for (index in piece.start until end) {
                if (source[index] == '\n') lines++
            }
        }
        return lines
    }

    /** Returns a zero-based line number for a UTF-16 document offset. */
    fun lineOfOffset(offset: Int): Int {
        require(offset in 0..documentLength) { "Offset is outside the document" }
        var line = 0
        var consumed = 0
        for (piece in pieces) {
            if (consumed >= offset) break
            val count = minOf(piece.length, offset - consumed)
            val source = buffer(piece.source)
            val end = piece.start + count
            for (index in piece.start until end) {
                if (source[index] == '\n') line++
            }
            consumed += count
        }
        return line
    }

    fun undo(): Boolean {
        if (undoHistory.isEmpty()) return false
        val edit = undoHistory.removeLast()
        applyEdit(edit.offset, edit.offset + edit.inserted.length, edit.removed)
        redoHistory.addLast(edit)
        compactIfNeeded()
        return true
    }

    fun redo(): Boolean {
        if (redoHistory.isEmpty()) return false
        val edit = redoHistory.removeLast()
        applyEdit(edit.offset, edit.offset + edit.removed.length, edit.inserted)
        undoHistory.addLast(edit)
        compactIfNeeded()
        return true
    }

    /**
     * Applies a complete text-field snapshot using only the changed middle range.
     * This lets an existing Compose editor feed incremental edits into this buffer.
     */
    fun replaceDocumentText(nextText: String) {
        val current = toString()
        if (current == nextText) return

        var prefix = 0
        val commonLimit = minOf(current.length, nextText.length)
        while (prefix < commonLimit && current[prefix] == nextText[prefix]) {
            prefix++
        }

        var suffix = 0
        while (
            suffix < current.length - prefix &&
            suffix < nextText.length - prefix &&
            current[current.length - 1 - suffix] == nextText[nextText.length - 1 - suffix]
        ) {
            suffix++
        }

        replace(prefix, current.length - suffix, nextText.substring(prefix, nextText.length - suffix))
    }

    /** Materializes a compact original buffer and drops history to bound memory. */
    fun compact() {
        val snapshot = toString()
        original = snapshot
        additions.setLength(0)
        pieces.clear()
        if (snapshot.isNotEmpty()) pieces += Piece(Source.ORIGINAL, 0, snapshot.length)
        documentLength = snapshot.length
        undoHistory.clear()
        redoHistory.clear()
    }

    override fun toString(): String {
        if (documentLength == 0) return ""
        val result = StringBuilder(documentLength)
        for (piece in pieces) {
            result.append(buffer(piece.source), piece.start, piece.start + piece.length)
        }
        return result.toString()
    }

    private fun applyEdit(startOffset: Int, endOffset: Int, replacement: String) {
        val startIndex = splitAt(startOffset)
        val endIndex = splitAt(endOffset)
        pieces.subList(startIndex, endIndex).clear()

        if (replacement.isNotEmpty()) {
            val addStart = additions.length
            additions.append(replacement)
            pieces.add(startIndex, Piece(Source.ADD, addStart, replacement.length))
        }

        documentLength += replacement.length - (endOffset - startOffset)
        coalesceAdjacentPieces()
    }

    /**
     * Splits a piece at a logical offset and returns the index immediately after
     * the split. Existing piece order is preserved.
     */
    private fun splitAt(offset: Int): Int {
        if (offset == 0) return 0
        if (offset == documentLength) return pieces.size

        var logicalOffset = 0
        for (index in pieces.indices) {
            val piece = pieces[index]
            val pieceEnd = logicalOffset + piece.length
            when {
                offset == logicalOffset -> return index
                offset == pieceEnd -> return index + 1
                offset in (logicalOffset + 1) until pieceEnd -> {
                    val leftLength = offset - logicalOffset
                    val left = piece.copy(length = leftLength)
                    val right = piece.copy(
                        start = piece.start + leftLength,
                        length = piece.length - leftLength,
                    )
                    pieces[index] = left
                    pieces.add(index + 1, right)
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
        val retainedAddText = additions.length.toLong()
        val compactThreshold = maxOf(256L * 1024L, documentLength.toLong() * 4L)
        if (retainedAddText > compactThreshold) compact()
    }

    private fun buffer(source: Source): CharSequence =
        if (source == Source.ORIGINAL) original else additions

    private fun validateRange(startOffset: Int, endOffset: Int) {
        require(startOffset >= 0 && endOffset >= startOffset && endOffset <= documentLength) {
            "Invalid document range"
        }
    }
}

package com.astracode.editor

/**
 * Position encodings defined by LSP 3.17.
 * The editor itself always uses UTF-8 byte offsets. UTF-16 exists here only
 * to translate the wire coordinates of older language servers.
 */
enum class LspPositionEncoding(val wireName: String) {
    UTF8("utf-8"),
    UTF16("utf-16"),
}

data class LspPosition(val line: Int, val character: Int)

/**
 * Converts between internal UTF-8 byte offsets and negotiated LSP line/character
 * positions. Callers should advertise ["utf-8"] in initialize when possible.
 * LSP servers that omit positionEncoding use UTF-16 by protocol default.
 */
object LspPositionCodec {
    fun byteOffsetForPosition(
        document: ByteArray,
        position: LspPosition,
        encoding: LspPositionEncoding = LspPositionEncoding.UTF8,
    ): Int {
        require(position.line >= 0 && position.character >= 0) { "Negative LSP position" }
        val (lineStart, lineEnd) = lineBounds(document, position.line)
        var byteOffset = lineStart
        var units = 0
        while (byteOffset < lineEnd && units < position.character) {
            val codePointLength = utf8SequenceLength(document[byteOffset])
            val nextUnits = units + when (encoding) {
                LspPositionEncoding.UTF8 -> codePointLength
                LspPositionEncoding.UTF16 -> {
                    val codePoint = decodeCodePoint(document, byteOffset, codePointLength)
                    if (codePoint > 0xFFFF) 2 else 1
                }
            }
            require(nextUnits <= position.character) {
                "LSP character position splits a UTF-8 character"
            }
            byteOffset += codePointLength
            units = nextUnits
        }
        require(units == position.character) { "LSP character position is outside the line" }
        return byteOffset
    }

    fun positionForByteOffset(
        document: ByteArray,
        byteOffset: Int,
        encoding: LspPositionEncoding = LspPositionEncoding.UTF8,
    ): LspPosition {
        require(byteOffset in 0..document.size) { "Byte offset is outside the document" }
        require(isBoundary(document, byteOffset)) { "Byte offset splits a UTF-8 character" }

        var line = 0
        var lineStart = 0
        for (index in 0 until byteOffset) {
            if (document[index] == NEWLINE) {
                line++
                lineStart = index + 1
            }
        }

        var lineEnd = byteOffset
        // A position immediately before CRLF is represented at the end of the line.
        if (lineEnd > lineStart && document[lineEnd - 1] == CARRIAGE_RETURN) lineEnd--
        var units = 0
        var index = lineStart
        while (index < lineEnd) {
            val length = utf8SequenceLength(document[index])
            require(index + length <= lineEnd) { "Byte offset splits a UTF-8 character" }
            units += when (encoding) {
                LspPositionEncoding.UTF8 -> length
                LspPositionEncoding.UTF16 -> {
                    val codePoint = decodeCodePoint(document, index, length)
                    if (codePoint > 0xFFFF) 2 else 1
                }
            }
            index += length
        }
        return LspPosition(line, units)
    }

    private fun lineBounds(document: ByteArray, line: Int): Pair<Int, Int> {
        var currentLine = 0
        var start = 0
        for (index in document.indices) {
            if (document[index] == NEWLINE) {
                if (currentLine == line) {
                    val end = if (index > start && document[index - 1] == CARRIAGE_RETURN) index - 1 else index
                    return start to end
                }
                currentLine++
                start = index + 1
            }
        }
        require(currentLine == line) { "LSP line is outside the document" }
        return start to document.size
    }

    private fun utf8SequenceLength(first: Byte): Int = when (first.toInt() and 0xFF) {
        in 0x00..0x7F -> 1
        in 0xC2..0xDF -> 2
        in 0xE0..0xEF -> 3
        in 0xF0..0xF4 -> 4
        else -> throw IllegalArgumentException("Document contains invalid UTF-8")
    }

    private fun decodeCodePoint(bytes: ByteArray, start: Int, length: Int): Int {
        var value = bytes[start].toInt() and (0x7F shr length)
        for (index in 1 until length) {
            value = (value shl 6) or (bytes[start + index].toInt() and 0x3F)
        }
        return value
    }

    private fun isBoundary(bytes: ByteArray, offset: Int): Boolean =
        offset == bytes.size || (bytes[offset].toInt() and 0xC0) != 0x80

    private const val NEWLINE: Byte = 0x0A
    private const val CARRIAGE_RETURN: Byte = 0x0D
}

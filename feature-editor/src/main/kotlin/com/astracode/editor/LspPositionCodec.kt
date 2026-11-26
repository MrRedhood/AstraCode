package com.astracode.editor

/** An LSP position. Character counts are UTF-8 bytes, matching the negotiated encoding. */
data class LspPosition(val line: Int, val character: Int)

/**
 * LSP position adapter for a UTF-8-native editor.
 *
 * AstraCode advertises only "utf-8" in general.positionEncodings and requires
 * the server to select it in initialize. No UTF-16 document coordinates are
 * used by this module.
 */
object LspPositionCodec {
    const val POSITION_ENCODING = "utf-8"

    fun byteOffsetForPosition(document: ByteArray, position: LspPosition): Int {
        require(position.line >= 0 && position.character >= 0) { "Negative LSP position" }
        val (lineStart, lineEnd) = lineBounds(document, position.line)
        val byteOffset = lineStart + position.character
        require(byteOffset <= lineEnd) { "LSP character position is outside the line" }
        require(isBoundary(document, byteOffset)) {
            "LSP character position splits a UTF-8 character"
        }
        return byteOffset
    }

    fun positionForByteOffset(document: ByteArray, byteOffset: Int): LspPosition {
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

        var characterOffset = byteOffset
        if (characterOffset > lineStart && document[characterOffset - 1] == CARRIAGE_RETURN) {
            characterOffset--
        }
        return LspPosition(line, characterOffset - lineStart)
    }

    private fun lineBounds(document: ByteArray, line: Int): Pair<Int, Int> {
        var currentLine = 0
        var start = 0
        for (index in document.indices) {
            if (document[index] == NEWLINE) {
                if (currentLine == line) {
                    val end = if (index > start && document[index - 1] == CARRIAGE_RETURN) {
                        index - 1
                    } else {
                        index
                    }
                    return start to end
                }
                currentLine++
                start = index + 1
            }
        }
        require(currentLine == line) { "LSP line is outside the document" }
        return start to document.size
    }

    private fun isBoundary(bytes: ByteArray, offset: Int): Boolean =
        offset == bytes.size || (bytes[offset].toInt() and 0xC0) != 0x80

    private const val NEWLINE: Byte = 0x0A
    private const val CARRIAGE_RETURN: Byte = 0x0D
}

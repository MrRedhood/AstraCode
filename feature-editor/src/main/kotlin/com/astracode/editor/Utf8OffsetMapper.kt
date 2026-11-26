package com.astracode.editor

import java.nio.charset.StandardCharsets

/**
 * Converts Android text-widget indices at the UI boundary.
 * The editor document itself remains indexed exclusively by UTF-8 bytes.
 */
object Utf8OffsetMapper {
    fun byteOffsetForTextIndex(text: String, textIndex: Int): Int {
        require(textIndex in 0..text.length) { "Text index is outside the document" }
        require(
            textIndex == 0 || textIndex == text.length ||
                !(text[textIndex - 1].isHighSurrogate() && text[textIndex].isLowSurrogate()),
        ) { "Text index splits a Unicode character" }
        return text.substring(0, textIndex).toByteArray(StandardCharsets.UTF_8).size
    }

    fun textIndexForByteOffset(text: String, byteOffset: Int): Int {
        val bytes = text.toByteArray(StandardCharsets.UTF_8)
        require(byteOffset in 0..bytes.size) { "UTF-8 byte offset is outside the document" }
        require(byteOffset == bytes.size || (bytes[byteOffset].toInt() and 0xC0) != 0x80) {
            "UTF-8 byte offset splits a Unicode character"
        }
        return String(bytes, 0, byteOffset, StandardCharsets.UTF_8).length
    }
}

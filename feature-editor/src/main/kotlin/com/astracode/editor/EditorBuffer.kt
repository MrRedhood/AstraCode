package com.astracode.editor

/**
 * Editor-facing wrapper around the UTF-8 Piece Table.
 *
 * Every length, range, cursor and edit offset exposed here is measured in UTF-8
 * bytes. Convert to UI-specific coordinates only at the UI boundary.
 */
class EditorBuffer(
    initialText: String = "",
    historyLimit: Int = 256,
) {
    private val table = PieceTable(initialText, historyLimit)

    /** Current document size in UTF-8 bytes. */
    val length: Int get() = table.byteLength
    val byteLength: Int get() = table.byteLength
    val lineCount: Int get() = table.lineCount
    val canUndo: Boolean get() = table.canUndo
    val canRedo: Boolean get() = table.canRedo

    fun text(): String = table.toString()
    fun bytes(): ByteArray = table.toByteArray()

    fun insert(byteOffset: Int, text: String) = table.insert(byteOffset, text)

    fun delete(startByteOffset: Int, endByteOffset: Int): String =
        table.delete(startByteOffset, endByteOffset)

    fun replace(startByteOffset: Int, endByteOffset: Int, text: String): String =
        table.replace(startByteOffset, endByteOffset, text)

    fun substring(startByteOffset: Int, endByteOffset: Int): String =
        table.substring(startByteOffset, endByteOffset)

    fun applyTextFieldSnapshot(text: String) = table.replaceDocumentText(text)

    fun lineOfOffset(byteOffset: Int): Int = table.lineOfOffset(byteOffset)

    fun undo(): Boolean = table.undo()
    fun redo(): Boolean = table.redo()
    fun compact() = table.compact()
}

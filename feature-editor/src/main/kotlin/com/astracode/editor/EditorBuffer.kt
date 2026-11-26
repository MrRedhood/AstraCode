package com.astracode.editor

/**
 * Editor-facing wrapper around the UTF-8 Piece Table.
 *
 * All document lengths and edit positions are UTF-8 bytes. Platform text-widget
 * indices are translated only at the UI boundary.
 */
class EditorBuffer(
    initialText: String = "",
    historyLimit: Int = 256,
) {
    private val table = PieceTable(initialText, historyLimit)

    val length: Int get() = table.byteLength
    val byteLength: Int get() = table.byteLength
    val lineCount: Int get() = table.lineCount()
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

    fun byteOffsetForTextIndex(textIndex: Int): Int =
        Utf8OffsetMapper.byteOffsetForTextIndex(text(), textIndex)

    fun textIndexForByteOffset(byteOffset: Int): Int =
        Utf8OffsetMapper.textIndexForByteOffset(text(), byteOffset)

    /**
     * Converts a platform text-widget index into an LSP UTF-8 position.
     * The widget index is UTF-16 because that is what Kotlin String APIs use;
     * the returned LSP character coordinate is a UTF-8 byte count.
     */
    fun lspPositionForTextIndex(textIndex: Int): LspPosition =
        LspPositionCodec.positionForByteOffset(
            bytes(),
            byteOffsetForTextIndex(textIndex),
        )

    /** Converts a negotiated UTF-8 LSP position back to a platform text index. */
    fun textIndexForLspPosition(position: LspPosition): Int =
        textIndexForByteOffset(
            LspPositionCodec.byteOffsetForPosition(bytes(), position),
        )

    fun undo(): Boolean = table.undo()
    fun redo(): Boolean = table.redo()
    fun compact() = table.compact()
}

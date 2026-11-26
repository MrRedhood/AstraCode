package com.astracode.editor

/**
 * Editor-facing wrapper around the Piece Table. The buffer is the mutable
 * document source; callers request a String snapshot only for UI or persistence.
 */
class EditorBuffer(
    initialText: String = "",
    historyLimit: Int = 256,
) {
    private val table = PieceTable(initialText, historyLimit)

    val length: Int get() = table.length
    val lineCount: Int get() = table.lineCount()
    val canUndo: Boolean get() = table.canUndo
    val canRedo: Boolean get() = table.canRedo

    fun text(): String = table.toString()

    fun insert(offset: Int, text: String) = table.insert(offset, text)

    fun delete(startOffset: Int, endOffset: Int): String =
        table.delete(startOffset, endOffset)

    fun replace(startOffset: Int, endOffset: Int, text: String): String =
        table.replace(startOffset, endOffset, text)

    fun applyTextFieldSnapshot(text: String) = table.replaceDocumentText(text)

    fun undo(): Boolean = table.undo()

    fun redo(): Boolean = table.redo()

    fun compact() = table.compact()
}

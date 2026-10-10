package com.mrredhood.astracode

internal data class WorkspaceEditorTab(
    val documentId: String,
    val displayName: String,
    val mimeType: String,
    val writable: Boolean,
)

internal data class WorkspaceEditorBuffer(
    val draft: String,
    val original: String,
    val selectionStart: Int,
    val selectionEnd: Int,
    val truncated: Boolean = false,
    val previewError: String? = null,
)

internal object WorkspaceTabActions {
    const val MAX_OPEN_TABS = 8

    /** Returns null when another distinct tab would exceed the bounded tab limit. */
    fun open(current: List<WorkspaceEditorTab>, requested: WorkspaceEditorTab): List<WorkspaceEditorTab>? {
        val existingIndex = current.indexOfFirst { it.documentId == requested.documentId }
        if (existingIndex >= 0) {
            return current.mapIndexed { index, tab -> if (index == existingIndex) requested else tab }
        }
        if (current.size >= MAX_OPEN_TABS) return null
        return current + requested
    }

    fun close(current: List<WorkspaceEditorTab>, documentId: String): List<WorkspaceEditorTab> =
        current.filterNot { it.documentId == documentId }
}

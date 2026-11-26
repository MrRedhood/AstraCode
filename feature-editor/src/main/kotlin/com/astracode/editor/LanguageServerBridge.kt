package com.astracode.editor

/**
 * Transport-neutral LSP boundary. A real JSON-RPC transport can implement this
 * without making the editor depend on a particular language-server process.
 */
data class EditorDocument(
    val uri: String,
    val languageId: String,
    val version: Int,
    val text: String,
)

data class EditorTextChange(
    val startOffset: Int,
    val endOffset: Int,
    val replacement: String,
)

data class EditorDiagnostic(
    val uri: String,
    val startOffset: Int,
    val endOffset: Int,
    val severity: Severity,
    val message: String,
) {
    enum class Severity { ERROR, WARNING, INFORMATION, HINT }
}

interface LanguageServerBridge : AutoCloseable {
    fun openDocument(document: EditorDocument)
    fun changeDocument(uri: String, version: Int, changes: List<EditorTextChange>)
    fun closeDocument(uri: String)
    fun requestCompletion(uri: String, offset: Int, version: Int, callback: (Result<List<String>>) -> Unit)
    fun requestHover(uri: String, offset: Int, version: Int, callback: (Result<String?>) -> Unit)
    fun requestDefinition(uri: String, offset: Int, version: Int, callback: (Result<List<String>>) -> Unit)
    fun setDiagnosticsListener(listener: (List<EditorDiagnostic>) -> Unit)

    override fun close()
}

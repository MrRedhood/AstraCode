package com.mrredhood.astracode

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale

internal data class WorkspaceEntry(
    val documentId: String,
    val displayName: String,
    val mimeType: String,
    val sizeBytes: Long,
    val lastModifiedMillis: Long,
    val canWrite: Boolean
) {
    val isDirectory: Boolean
        get() = mimeType == DocumentsContract.Document.MIME_TYPE_DIR
}

internal data class WorkspaceTextPreview(val text: String, val truncated: Boolean)

internal class WorkspaceRepository(context: Context) {
    private val appContext = context.applicationContext
    private val resolver = appContext.contentResolver
    private val preferences = appContext.getSharedPreferences(PREFERENCES_NAME, Context.MODE_PRIVATE)

    fun savedTreeUri(): Uri? {
        val value = preferences.getString(KEY_TREE_URI, null) ?: return null
        return runCatching { Uri.parse(value) }.getOrNull()
    }

    fun saveTreeUri(uri: Uri) {
        preferences.edit().putString(KEY_TREE_URI, uri.toString()).apply()
    }

    fun clearTreeUri() {
        preferences.edit().remove(KEY_TREE_URI).apply()
    }

    fun rootDocumentId(treeUri: Uri): String = DocumentsContract.getTreeDocumentId(treeUri)

    @Throws(IOException::class, SecurityException::class)
    fun listChildren(treeUri: Uri, parentDocumentId: String): List<WorkspaceEntry> {
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocumentId)
        val projection = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
            DocumentsContract.Document.COLUMN_FLAGS
        )
        val cursor = resolver.query(childrenUri, projection, null, null, null)
            ?: throw IOException("The selected storage provider did not return a directory listing.")

        val items = cursor.use { result ->
            val idIndex = result.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID)
            val nameIndex = result.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
            val mimeIndex = result.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE)
            val sizeIndex = result.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE)
            val modifiedIndex = result.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED)
            val flagsIndex = result.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_FLAGS)
            buildList {
                while (result.moveToNext()) {
                    val flags = if (result.isNull(flagsIndex)) 0 else result.getLong(flagsIndex).toInt()
                    add(
                        WorkspaceEntry(
                            documentId = result.getString(idIndex),
                            displayName = result.getString(nameIndex) ?: "(unnamed)",
                            mimeType = result.getString(mimeIndex) ?: "application/octet-stream",
                            sizeBytes = if (result.isNull(sizeIndex)) -1L else result.getLong(sizeIndex),
                            lastModifiedMillis = if (result.isNull(modifiedIndex)) 0L else result.getLong(modifiedIndex),
                            canWrite = flags and DocumentsContract.Document.FLAG_SUPPORTS_WRITE != 0
                        )
                    )
                }
            }
        }
        return items.sortedWith(
            compareBy<WorkspaceEntry> { !it.isDirectory }
                .thenBy { it.displayName.lowercase(Locale.ROOT) }
        )
    }

    @Throws(IOException::class, SecurityException::class)
    fun readTextPreview(treeUri: Uri, documentId: String): WorkspaceTextPreview {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId)
        val input = resolver.openInputStream(documentUri)
            ?: throw IOException("The selected file could not be opened by its storage provider.")
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var truncated = false
        input.use { stream ->
            while (true) {
                val remainingWithSentinel = MAX_PREVIEW_BYTES + 1 - output.size()
                if (remainingWithSentinel <= 0) {
                    truncated = true
                    break
                }
                val read = stream.read(buffer, 0, minOf(buffer.size, remainingWithSentinel))
                if (read < 0) break
                output.write(buffer, 0, read)
                if (output.size() > MAX_PREVIEW_BYTES) {
                    truncated = true
                    break
                }
            }
        }
        val rawBytes = output.toByteArray()
        val bytes = if (rawBytes.size > MAX_PREVIEW_BYTES) rawBytes.copyOf(MAX_PREVIEW_BYTES) else rawBytes
        return WorkspaceTextPreview(String(bytes, StandardCharsets.UTF_8), truncated)
    }

    companion object {
        private const val PREFERENCES_NAME = "astracode_workspace"
        private const val KEY_TREE_URI = "selected_tree_uri"
        private const val MAX_PREVIEW_BYTES = 256 * 1024
    }
}

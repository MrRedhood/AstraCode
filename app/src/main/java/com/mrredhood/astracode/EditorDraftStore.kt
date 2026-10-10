package com.mrredhood.astracode

import android.content.Context
import android.util.AtomicFile
import java.io.File
import java.io.FileOutputStream

internal enum class EditorDraftWriteResult {
    Saved,
    TooLarge,
    Failed,
}

/** App-private drafts live outside Android Auto Backup and never write to the workspace file. */
internal class EditorDraftStore(context: Context) {
    private val directory = File(context.noBackupFilesDir, "editor-drafts")

    @Synchronized
    fun read(treeUri: String, documentId: String): EditorDraftSnapshot? {
        val file = fileFor(treeUri, documentId, createDirectory = false) ?: return null
        if (!file.isFile) return null
        val atomic = AtomicFile(file)
        return try {
            val decoded = atomic.openRead().use { input ->
                EditorDraftRecordCodec.decode(input.readBytes())
            }
            if (decoded == null) atomic.delete()
            decoded
        } catch (_: Exception) {
            runCatching { atomic.delete() }
            null
        }
    }

    @Synchronized
    fun save(treeUri: String, documentId: String, baseline: String, draft: String): EditorDraftWriteResult {
        val bytes = EditorDraftRecordCodec.encode(
            EditorDraftSnapshot(EditorDraftRecordCodec.fingerprint(baseline), draft),
        )
        if (bytes == null) {
            delete(treeUri, documentId)
            return EditorDraftWriteResult.TooLarge
        }
        val file = fileFor(treeUri, documentId, createDirectory = true)
            ?: return EditorDraftWriteResult.Failed
        val atomic = AtomicFile(file)
        var stream: FileOutputStream? = null
        return try {
            stream = atomic.startWrite()
            stream.write(bytes)
            stream.fd.sync()
            atomic.finishWrite(stream)
            EditorDraftWriteResult.Saved
        } catch (_: Exception) {
            if (stream != null) atomic.failWrite(stream)
            EditorDraftWriteResult.Failed
        }
    }

    @Synchronized
    fun delete(treeUri: String, documentId: String) {
        val file = fileFor(treeUri, documentId, createDirectory = false) ?: return
        if (file.exists()) AtomicFile(file).delete()
    }

    private fun fileFor(treeUri: String, documentId: String, createDirectory: Boolean): File? {
        if (createDirectory) {
            if (!directory.isDirectory && !directory.mkdirs()) return null
        } else if (!directory.isDirectory) {
            return null
        }
        val key = EditorDraftRecordCodec.fingerprint("$treeUri\n$documentId")
        return File(directory, "$key.draft")
    }
}

package com.mrredhood.astracode

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale

/** Executes tightly allow-listed workspace tools beneath the selected SAF tree. */
internal class AiWorkspaceToolExecutor(context: Context) {
    private val appContext = context.applicationContext
    private val repository = WorkspaceRepository(appContext)

    suspend fun execute(proposal: AiWorkspaceToolProposal): AiWorkspaceToolResult =
        withContext(Dispatchers.IO) {
            try {
                if (!AiWorkspaceToolProtocol.isValidRelativePath(
                        proposal.path,
                        allowRoot = proposal.name == AiWorkspaceToolName.LIST_DIRECTORY
                    )
                ) return@withContext failure(proposal, AiWorkspaceToolStatus.FAILED, "The requested relative path is invalid.")
                val tree = repository.savedTreeUri()
                    ?: return@withContext failure(
                        proposal, AiWorkspaceToolStatus.NO_WORKSPACE,
                        "Choose a project folder in Code before using workspace tools."
                    )
                when (proposal.name) {
                    AiWorkspaceToolName.LIST_DIRECTORY -> listDirectory(tree, proposal)
                    AiWorkspaceToolName.READ_TEXT_FILE -> readTextFile(tree, proposal)
                    AiWorkspaceToolName.CREATE_TEXT_FILE -> createTextFile(tree, proposal)
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                failure(
                    proposal, AiWorkspaceToolStatus.FAILED,
                    "The workspace provider could not complete this operation."
                )
            }
        }

    private fun listDirectory(tree: Uri, proposal: AiWorkspaceToolProposal): AiWorkspaceToolResult {
        val directory = resolveDirectory(tree, proposal.path)
            ?: return failure(proposal, AiWorkspaceToolStatus.NOT_FOUND, "The requested workspace folder was not found.")
        val entries = repository.listChildren(tree, directory).sortedWith(
            compareBy<WorkspaceEntry> { !it.isDirectory }.thenBy { it.displayName.lowercase(Locale.ROOT) }
        )
        val visible = entries.take(MAX_LIST_ENTRIES)
        val output = buildString {
            visible.forEach { entry ->
                append(safeName(entry.displayName))
                if (entry.isDirectory) append('/')
                else if (entry.sizeBytes >= 0L) append("  (").append(entry.sizeBytes).append(" bytes)")
                append('\n')
            }
            if (entries.size > MAX_LIST_ENTRIES) {
                append("[Additional entries omitted; listing is capped at ")
                    .append(MAX_LIST_ENTRIES).append(" items.]")
            }
            if (entries.isEmpty()) append("(empty folder)")
        }.take(MAX_LIST_OUTPUT_CHARS)
        return AiWorkspaceToolResult(
            proposal, AiWorkspaceToolStatus.SUCCESS,
            "Listed ${visible.size} of ${entries.size} immediate item(s).",
            output,
            listOf(
                "Resolved within the persisted Android SAF tree grant.",
                "Returned ${visible.size} item name(s); directory listing cap is $MAX_LIST_ENTRIES."
            )
        )
    }

    private fun readTextFile(tree: Uri, proposal: AiWorkspaceToolProposal): AiWorkspaceToolResult {
        val parts = proposal.path.split('/')
        val parent = resolveDirectory(tree, parts.dropLast(1).joinToString("/"))
            ?: return failure(proposal, AiWorkspaceToolStatus.NOT_FOUND, "The parent workspace folder was not found.")
        val matches = repository.listChildren(tree, parent).filter { it.displayName == parts.last() }
        if (matches.isEmpty()) return failure(proposal, AiWorkspaceToolStatus.NOT_FOUND, "The requested workspace file was not found.")
        if (matches.size != 1) return failure(proposal, AiWorkspaceToolStatus.FAILED, "The provider returned an ambiguous file name.")
        val entry = matches.single()
        if (entry.isDirectory) return failure(proposal, AiWorkspaceToolStatus.UNSUPPORTED_FILE, "The requested path is a folder, not a file.")
        if (!WorkspaceFilePolicy.supportsTextPreview(entry.displayName, entry.mimeType)) {
            return failure(proposal, AiWorkspaceToolStatus.UNSUPPORTED_FILE, "Only text and code files can be read by this tool.")
        }
        if (entry.sizeBytes > MAX_READ_BYTES) {
            return failure(proposal, AiWorkspaceToolStatus.TOO_LARGE, "The file exceeds the read-only tool limit of ${MAX_READ_BYTES / 1024} KiB.")
        }

        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, entry.documentId)
        val input = appContext.contentResolver.openInputStream(uri) ?: throw IOException("The file could not be opened.")
        val bytes = input.use { stream ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(4096)
            while (true) {
                val remaining = MAX_READ_BYTES + 1 - output.size()
                if (remaining <= 0) break
                val count = stream.read(buffer, 0, minOf(buffer.size, remaining))
                if (count < 0) break
                output.write(buffer, 0, count)
            }
            output.toByteArray()
        }
        if (bytes.size > MAX_READ_BYTES) {
            return failure(proposal, AiWorkspaceToolStatus.TOO_LARGE, "The file exceeds the read-only tool limit of ${MAX_READ_BYTES / 1024} KiB.")
        }
        val text = AiChatAttachmentPolicy.decodeUtf8(bytes)
            ?: return failure(proposal, AiWorkspaceToolStatus.INVALID_TEXT, "The file is not valid UTF-8 text.")
        return AiWorkspaceToolResult(
            proposal,
            AiWorkspaceToolStatus.SUCCESS,
            "Read ${bytes.size} byte(s) from a text/code file.",
            text,
            listOf(
                "Resolved a file beneath the persisted Android SAF tree grant.",
                "Bytes read: ${bytes.size}.",
                "SHA-256: ${sha256(bytes)}."
            )
        )
    }


    private fun createTextFile(tree: Uri, proposal: AiWorkspaceToolProposal): AiWorkspaceToolResult {
        val content = proposal.content
            ?: return failure(proposal, AiWorkspaceToolStatus.INVALID_CONTENT, "The file content was missing.")
        if (!AiWorkspaceToolProtocol.isValidCreateContent(content)) {
            return failure(proposal, AiWorkspaceToolStatus.INVALID_CONTENT, "File content must be valid text and no larger than 15 MiB.")
        }
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        val parts = proposal.path.split('/')
        if (parts.isEmpty() || parts.any { WorkspaceFilePolicy.validateName(it) != null }) {
            return failure(proposal, AiWorkspaceToolStatus.FAILED, "The requested relative file path is invalid.")
        }
        val fileName = parts.last()
        if (!WorkspaceFilePolicy.supportsAiTextCreate(fileName)) {
            return failure(proposal, AiWorkspaceToolStatus.UNSUPPORTED_FILE, "AI creation is restricted to recognized text/code file names.")
        }
        val parentPath = parts.dropLast(1).joinToString("/")
        val parentId = resolveDirectory(tree, parentPath)
            ?: return failure(proposal, AiWorkspaceToolStatus.NOT_FOUND, "The destination workspace folder was not found.")
        val existing = repository.listChildren(tree, parentId).filter { it.displayName == fileName }
        if (existing.isNotEmpty()) {
            return failure(
                proposal,
                AiWorkspaceToolStatus.ALREADY_EXISTS,
                "A file or folder already exists at this exact path. Nothing was overwritten."
            )
        }

        var createdUri: Uri? = null
        fun cleanupCreated(): Boolean {
            val uri = createdUri ?: return true
            return runCatching { DocumentsContract.deleteDocument(appContext.contentResolver, uri) }
                .getOrDefault(false)
        }

        try {
            val parentUri = DocumentsContract.buildDocumentUriUsingTree(tree, parentId)
            val created = DocumentsContract.createDocument(
                appContext.contentResolver,
                parentUri,
                WorkspaceFilePolicy.mimeTypeForNewFile(fileName),
                fileName
            ) ?: return failure(proposal, AiWorkspaceToolStatus.FAILED, "The storage provider did not create the requested file.")
            createdUri = created

            val documentId = DocumentsContract.getDocumentId(created)
            val entries = repository.listChildren(tree, parentId).filter { it.documentId == documentId }
            if (entries.size != 1 || entries.single().displayName != fileName || entries.single().isDirectory) {
                val removed = cleanupCreated()
                return failure(
                    proposal,
                    AiWorkspaceToolStatus.FAILED,
                    if (removed) "The storage provider did not preserve the exact requested file name. The new item was removed."
                    else "The storage provider changed the requested file name and would not confirm cleanup. Inspect the destination folder before retrying."
                )
            }

            val targetUri = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)
            val output = appContext.contentResolver.openOutputStream(targetUri, "wt")
                ?: throw IOException("The provider did not open the new file for writing.")
            output.use { stream ->
                stream.write(bytes)
                stream.flush()
            }

            val input = appContext.contentResolver.openInputStream(targetUri)
                ?: throw IOException("The provider did not allow the saved file to be verified.")
            val actual = input.use { stream ->
                val outputBytes = ByteArrayOutputStream()
                val buffer = ByteArray(4096)
                while (true) {
                    val remaining = AiWorkspaceToolProtocol.MAX_CREATE_BYTES + 1 - outputBytes.size()
                    if (remaining <= 0) break
                    val count = stream.read(buffer, 0, minOf(buffer.size, remaining))
                    if (count < 0) break
                    outputBytes.write(buffer, 0, count)
                }
                outputBytes.toByteArray()
            }
            if (!actual.contentEquals(bytes)) {
                val removed = cleanupCreated()
                return AiWorkspaceToolResult(
                    proposal,
                    AiWorkspaceToolStatus.VERIFICATION_FAILED,
                    if (removed) "The new file did not match the requested bytes and was removed."
                    else "The new file could not be verified and cleanup was not confirmed. Inspect the destination folder before retrying.",
                    evidence = listOf("Requested bytes: " + bytes.size + ".", "Read-back bytes: " + actual.size + ".")
                )
            }

            return AiWorkspaceToolResult(
                proposal,
                AiWorkspaceToolStatus.SUCCESS,
                "Created and verified a new text/code file. Existing files are never overwritten.",
                evidence = listOf(
                    "Explicit user approval was required before execution.",
                    "Destination is beneath the persisted Android SAF workspace tree grant.",
                    "Created path: " + proposal.path + ".",
                    "Bytes written and read back: " + actual.size + ".",
                    "SHA-256: " + sha256(actual) + ".",
                    "Verification: read-back bytes exactly match the requested content."
                )
            )
        } catch (cancelled: CancellationException) {
            cleanupCreated()
            throw cancelled
        } catch (_: Exception) {
            val removed = cleanupCreated()
            return failure(
                proposal,
                AiWorkspaceToolStatus.FAILED,
                if (removed) "Could not create and verify the requested file. A partial new file was removed; existing files were not overwritten."
                else "Could not create and verify the requested file, and cleanup of a possible partial file was not confirmed. Inspect the destination folder before retrying."
            )
        }
    }

    private fun resolveDirectory(tree: Uri, relativePath: String): String? {
        var currentId = repository.rootDocumentId(tree)
        if (relativePath.isEmpty()) return currentId
        relativePath.split('/').forEach { segment ->
            val matches = repository.listChildren(tree, currentId).filter {
                it.isDirectory && it.displayName == segment
            }
            if (matches.size != 1) return null
            currentId = matches.single().documentId
        }
        return currentId
    }

    private fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    private fun safeName(value: String): String =
        value.filterNot { it.isISOControl() || it == '\\' || it == '/' }
            .take(120).ifBlank { "(unnamed)" }

    private fun failure(
        proposal: AiWorkspaceToolProposal,
        status: AiWorkspaceToolStatus,
        summary: String
    ) = AiWorkspaceToolResult(proposal, status, summary)

    companion object {
        const val MAX_READ_BYTES = 16 * 1024
        const val MAX_LIST_ENTRIES = 40
        private const val MAX_LIST_OUTPUT_CHARS = 8 * 1024
    }
}

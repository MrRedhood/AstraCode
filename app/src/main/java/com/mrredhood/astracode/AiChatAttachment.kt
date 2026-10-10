package com.mrredhood.astracode

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale

internal data class AiChatAttachment(
    val sourceUri: String,
    val name: String,
    val mimeType: String,
    val byteCount: Int,
    val text: String
)
internal data class AiChatAttachmentReadResult(val attachments: List<AiChatAttachment>, val warnings: List<String>)
internal data class AiChatAttachmentMessage(val providerContent: String, val displayContent: String)

internal object AiChatAttachmentPolicy {
    const val MAX_ATTACHMENTS = 3
    const val MAX_FILE_BYTES = 16 * 1024
    const val MAX_TOTAL_BYTES = 32 * 1024
    private val acceptedMimeTypes = setOf(
        "application/json", "application/xml", "application/javascript", "application/x-javascript",
        "application/xhtml+xml", "application/yaml", "application/x-yaml", "application/toml",
        "application/sql", "application/x-sh", "application/x-httpd-php"
    )
    private val acceptedExtensions = setOf(
        ".txt", ".md", ".markdown", ".kt", ".kts", ".java", ".js", ".mjs", ".cjs", ".ts",
        ".tsx", ".jsx", ".py", ".pyi", ".html", ".htm", ".css", ".scss", ".json", ".xml",
        ".yaml", ".yml", ".gradle", ".properties", ".toml", ".sh", ".sql", ".c", ".h", ".cc",
        ".cpp", ".hpp", ".go", ".rs", ".swift", ".dart", ".php", ".rb", ".vue", ".svelte",
        ".ini", ".cfg", ".conf", ".bat", ".ps1", ".r", ".m", ".mm", ".pl", ".lua", ".ex",
        ".exs", ".gitignore"
    )

    fun isTextLike(name: String, mimeType: String?): Boolean {
        val mime = mimeType.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
        if (mime.startsWith("text/") || mime in acceptedMimeTypes) return true
        val lowerName = name.lowercase(Locale.ROOT)
        return lowerName == ".gitignore" || acceptedExtensions.any(lowerName::endsWith)
    }

    fun decodeUtf8(bytes: ByteArray): String? {
        if (bytes.any { it == 0.toByte() }) return null
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString()
        } catch (_: Exception) {
            null
        }
    }

    fun composeMessage(prompt: String, attachments: List<AiChatAttachment>): AiChatAttachmentMessage {
        val question = prompt.trim()
        require(question.isNotEmpty() || attachments.isNotEmpty()) { "Enter a message or attach a text file." }
        require(attachments.size <= MAX_ATTACHMENTS) { "Too many attachments." }
        require(attachments.sumOf { it.byteCount } <= MAX_TOTAL_BYTES) { "Attachments exceed the combined size limit." }

        val display = buildString {
            append(question.ifBlank { "Please review the attached text file(s)." })
            if (attachments.isNotEmpty()) {
                append("\n\nAttached files:\n")
                attachments.forEach { item ->
                    append("• ")
                    append(safeLabel(item.name))
                    append(" (")
                    append(item.byteCount)
                    append(" bytes)\n")
                }
            }
        }.trimEnd()

        val provider = buildString {
            if (question.isNotBlank()) append(question)
            else if (attachments.isNotEmpty()) append("Please review the attached text file(s).")
            attachments.forEach { item ->
                append("\n\n--- BEGIN ATTACHED FILE: ")
                append(safeLabel(item.name))
                append(" ---\n")
                append(item.text)
                append("\n--- END ATTACHED FILE: ")
                append(safeLabel(item.name))
                append(" ---")
            }
        }.ifBlank { "Please review the attached text file(s)." }
        return AiChatAttachmentMessage(provider, display)
    }

    private fun safeLabel(value: String): String =
        value.filterNot { it.isISOControl() || it == '/' || it == '\\' }.take(100).ifBlank { "text attachment" }
}

/** Reads only user-selected SAF documents; workspace files are never attached implicitly. */
internal class AiChatAttachmentReader(private val context: Context) {
    suspend fun read(uris: List<Uri>, existing: List<AiChatAttachment>): AiChatAttachmentReadResult =
        withContext(Dispatchers.IO) {
            val added = ArrayList<AiChatAttachment>()
            val warnings = ArrayList<String>()
            val seen = existing.mapTo(mutableSetOf()) { it.sourceUri }
            var acceptedBytes = existing.sumOf { it.byteCount }
            var acceptedCount = existing.size

            for ((index, uri) in uris.distinctBy { it.toString() }.withIndex()) {
                val uriKey = uri.toString()
                if (!seen.add(uriKey)) continue
                if (acceptedCount >= AiChatAttachmentPolicy.MAX_ATTACHMENTS) {
                    warnings.add("Only \${AiChatAttachmentPolicy.MAX_ATTACHMENTS} attachments are allowed per message.")
                    break
                }
                try {
                    val metadata = queryMetadata(uri, index)
                    val name = metadata.first
                    val mimeType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
                    if (!AiChatAttachmentPolicy.isTextLike(name, mimeType)) {
                        warnings.add("Skipped $name: choose a text or code file.")
                        continue
                    }
                    val declaredBytes = metadata.second
                    if (declaredBytes != null && declaredBytes > AiChatAttachmentPolicy.MAX_FILE_BYTES) {
                        warnings.add("Skipped $name: each file must be at most 16 KiB.")
                        continue
                    }
                    if (acceptedBytes >= AiChatAttachmentPolicy.MAX_TOTAL_BYTES) {
                        warnings.add("Skipped $name: combined attachments are limited to 32 KiB.")
                        continue
                    }
                    val stream = context.contentResolver.openInputStream(uri)
                        ?: throw IllegalStateException("File could not be opened.")
                    val bytes = stream.use { input ->
                        val output = ByteArrayOutputStream()
                        val buffer = ByteArray(4096)
                        while (true) {
                            val remaining = AiChatAttachmentPolicy.MAX_FILE_BYTES + 1 - output.size()
                            if (remaining <= 0) break
                            val count = input.read(buffer, 0, minOf(buffer.size, remaining))
                            if (count < 0) break
                            output.write(buffer, 0, count)
                        }
                        output.toByteArray()
                    }
                    if (bytes.size > AiChatAttachmentPolicy.MAX_FILE_BYTES) {
                        warnings.add("Skipped $name: each file must be at most 16 KiB.")
                        continue
                    }
                    if (acceptedBytes + bytes.size > AiChatAttachmentPolicy.MAX_TOTAL_BYTES) {
                        warnings.add("Skipped $name: combined attachments are limited to 32 KiB.")
                        continue
                    }
                    val text = AiChatAttachmentPolicy.decodeUtf8(bytes)
                    if (text == null) {
                        warnings.add("Skipped $name: file is not valid UTF-8 text.")
                        continue
                    }
                    added.add(AiChatAttachment(uriKey, sanitizeName(name), mimeType.orEmpty().take(100), bytes.size, text))
                    acceptedBytes += bytes.size
                    acceptedCount++
                } catch (error: Exception) {
                    if (error is CancellationException) throw error
                    warnings.add("Skipped selected file \${index + 1}: it could not be read.")
                }
            }
            AiChatAttachmentReadResult(added, warnings)
        }

    private fun queryMetadata(uri: Uri, index: Int): Pair<String, Long?> {
        var name = "text-attachment-\${index + 1}.txt"
        var size: Long? = null
        context.contentResolver.query(
            uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameColumn >= 0 && !cursor.isNull(nameColumn)) name = cursor.getString(nameColumn) ?: name
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn).takeIf { it >= 0L }
            }
        }
        return sanitizeName(name) to size
    }

    private fun sanitizeName(value: String): String =
        value.substringAfterLast('/').substringAfterLast('\\')
            .filterNot { it.isISOControl() || it == '/' || it == '\\' }
            .trim().take(100).ifBlank { "text attachment" }
}

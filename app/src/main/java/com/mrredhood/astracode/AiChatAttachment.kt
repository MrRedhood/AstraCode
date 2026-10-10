package com.mrredhood.astracode

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Locale
import java.util.UUID

/** Metadata is persisted with chat messages; raw bytes are kept in bounded app-private files. */
data class AiChatAttachment internal constructor(
    internal val id: String,
    internal val name: String,
    internal val mimeType: String,
    internal val byteCount: Int,
    internal val sourceUri: String = "",
    /** Populated only for the duration of a provider request; never persisted in SQLite. */
    internal val data: ByteArray? = null
) {
    internal val storageName: String get() = id + ".blob"
}

internal data class AiChatAttachmentReadResult(val attachments: List<AiChatAttachment>, val warnings: List<String>)
internal data class AiChatAttachmentMessage(val providerContent: String, val displayContent: String)

internal object AiChatAttachmentPolicy {
    const val MAX_ATTACHMENTS = 10
    const val MAX_FILE_BYTES = 25 * 1024 * 1024
    /** Raw attachment data allowed across all messages in one provider request. */
    const val MAX_TOTAL_BYTES = 100 * 1024 * 1024
    const val MAX_STORED_BYTES = 1024L * 1024L * 1024L

    private val textExtensions = setOf(
        "txt", "md", "markdown", "kt", "kts", "java", "js", "mjs", "cjs", "ts",
        "tsx", "jsx", "py", "pyi", "html", "htm", "css", "scss", "json", "xml",
        "yaml", "yml", "gradle", "properties", "toml", "sh", "sql", "c", "h",
        "cc", "cpp", "hpp", "go", "rs", "swift", "dart", "php", "rb", "vue",
        "svelte", "ini", "cfg", "conf", "bat", "ps1", "r", "m", "mm", "pl",
        "lua", "ex", "exs", "gitignore", "editorconfig", "csv", "log", "jsonl"
    )
    private val knownTextMimes = setOf(
        "application/json", "application/xml", "application/javascript",
        "application/x-javascript", "application/xhtml+xml", "application/yaml",
        "application/x-yaml", "application/toml", "application/sql",
        "application/x-sh", "application/x-httpd-php", "application/csv"
    )

    fun isTextLike(name: String, mimeType: String?): Boolean {
        val mime = normalizeMimeType(mimeType)
        if (mime.startsWith("text/") || mime in knownTextMimes) return true
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return name.equals(".gitignore", ignoreCase = true) ||
            name.equals(".editorconfig", ignoreCase = true) || extension in textExtensions
    }

    fun isInlineImageMime(mimeType: String): Boolean =
        normalizeMimeType(mimeType) in setOf("image/png", "image/jpeg", "image/webp", "image/gif")

    fun isAudioMime(mimeType: String): Boolean = normalizeMimeType(mimeType).startsWith("audio/")
    fun isVideoMime(mimeType: String): Boolean = normalizeMimeType(mimeType).startsWith("video/")
    fun isPdf(attachment: AiChatAttachment): Boolean =
        normalizeMimeType(attachment.mimeType) == "application/pdf" ||
            attachment.name.endsWith(".pdf", ignoreCase = true)

    fun normalizeMimeType(mimeType: String?): String {
        val cleaned = mimeType.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
        return if (cleaned.matches(Regex("[a-z0-9!#$&^_.+-]{1,127}/[a-z0-9!#$&^_.+-]{1,127}"))) cleaned
        else "application/octet-stream"
    }

    fun decodeUtf8(bytes: ByteArray): String? {
        if (bytes.any { it == 0.toByte() }) return null
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes)).toString()
        } catch (_: Exception) {
            null
        }
    }

    fun composeMessage(prompt: String, attachments: List<AiChatAttachment>): AiChatAttachmentMessage {
        val question = prompt.trim()
        require(question.isNotEmpty() || attachments.isNotEmpty()) { "Enter a message or attach a file." }
        require(attachments.size <= MAX_ATTACHMENTS) { "Too many attachments." }
        require(attachments.all { it.byteCount in 0..MAX_FILE_BYTES }) { "Each attachment must be no larger than 25 MiB." }
        require(attachments.sumOf { it.byteCount.toLong() } <= MAX_TOTAL_BYTES) {
            "Attachments selected for one message must total no more than 100 MiB."
        }
        val display = buildString {
            append(question.ifBlank { "Please review the attached file(s)." })
            if (attachments.isNotEmpty()) {
                append("\n\nAttached files:\n")
                attachments.forEach { item ->
                    append("• ").append(safeLabel(item.name)).append(" · ")
                    append(formatBytes(item.byteCount.toLong())).append(" · ")
                    append(item.mimeType).append('\n')
                }
            }
        }.trimEnd()
        return AiChatAttachmentMessage(
            providerContent = question.ifBlank { "Please review the attached file(s)." },
            displayContent = display
        )
    }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1024L * 1024L -> String.format(Locale.ROOT, "%.1f MiB", bytes / (1024.0 * 1024.0))
        bytes >= 1024L -> String.format(Locale.ROOT, "%.1f KiB", bytes / 1024.0)
        else -> bytes.toString() + " B"
    }

    fun safeLabel(value: String): String =
        value.filterNot { it.isISOControl() || it == '/' || it == '\\' }.take(120).ifBlank { "attachment" }
}

/** Stable compact JSON codec; private file bytes are not embedded in the message database. */
internal object AiChatAttachmentMetadataCodec {
    fun encode(attachments: List<AiChatAttachment>): String {
        val array = JSONArray()
        attachments.forEach { item ->
            require(isSafeId(item.id)) { "Invalid attachment ID" }
            array.put(
                JSONObject()
                    .put("id", item.id)
                    .put("name", AiChatAttachmentPolicy.safeLabel(item.name))
                    .put("mimeType", AiChatAttachmentPolicy.normalizeMimeType(item.mimeType))
                    .put("byteCount", item.byteCount)
            )
        }
        return array.toString()
    }

    fun decode(encoded: String?): List<AiChatAttachment> {
        if (encoded.isNullOrBlank()) return emptyList()
        return try {
            val array = JSONArray(encoded)
            buildList {
                for (index in 0 until array.length()) {
                    val item = array.optJSONObject(index) ?: continue
                    val id = item.optString("id")
                    val name = item.optString("name")
                    val size = item.optLong("byteCount", -1L)
                    if (!isSafeId(id) || name.isBlank() || size !in 0L..AiChatAttachmentPolicy.MAX_FILE_BYTES.toLong()) continue
                    add(
                        AiChatAttachment(
                            id = id,
                            name = AiChatAttachmentPolicy.safeLabel(name),
                            mimeType = AiChatAttachmentPolicy.normalizeMimeType(item.optString("mimeType")),
                            byteCount = size.toInt()
                        )
                    )
                }
            }
        } catch (_: JSONException) {
            emptyList()
        }
    }

    fun isSafeId(value: String): Boolean =
        runCatching { UUID.fromString(value).toString() == value }.getOrDefault(false)
}

/** Imports any MIME/file extension without loading the full file into memory. */
internal class AiChatAttachmentStorage(context: Context) {
    private val directory = File(context.applicationContext.filesDir, DIRECTORY_NAME).apply {
        if (!exists() && !mkdirs()) throw IOException("Could not create attachment storage.")
    }

    fun importFromUri(
        context: Context,
        uri: Uri,
        displayName: String,
        mimeType: String,
        maxBytesForThisImport: Int
    ): AiChatAttachment {
        require(maxBytesForThisImport in 1..AiChatAttachmentPolicy.MAX_FILE_BYTES)
        val freeQuota = AiChatAttachmentPolicy.MAX_STORED_BYTES - storedBytes()
        val hardLimit = minOf(maxBytesForThisImport.toLong(), freeQuota).coerceAtLeast(0L)
        if (hardLimit <= 0L) throw AiChatAttachmentStorageException("AstraCode attachment storage is full. Delete older chats or attachments and retry.")
        val id = UUID.randomUUID().toString()
        val temp = File(directory, id + ".tmp")
        val finalFile = File(directory, id + ".blob")
        try {
            val input = context.contentResolver.openInputStream(uri) ?: throw IOException("The selected file could not be opened.")
            var total = 0L
            input.use { source ->
                FileOutputStream(temp).use { target ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    while (true) {
                        val remaining = hardLimit + 1L - total
                        if (remaining <= 0L) break
                        val count = source.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (count < 0) break
                        target.write(buffer, 0, count)
                        total += count
                        if (total > hardLimit) break
                    }
                    target.flush()
                }
            }
            if (total > hardLimit) {
                if (hardLimit < AiChatAttachmentPolicy.MAX_FILE_BYTES) {
                    throw AiChatAttachmentStorageException("The selected file would exceed the 100 MiB per-request payload or 1 GiB local-storage quota.")
                }
                throw AiChatAttachmentTooLargeException()
            }
            if (total > AiChatAttachmentPolicy.MAX_FILE_BYTES) throw AiChatAttachmentTooLargeException()
            if (!temp.renameTo(finalFile)) throw IOException("The selected file could not be saved locally.")
            return AiChatAttachment(
                id = id,
                name = AiChatAttachmentPolicy.safeLabel(displayName),
                mimeType = AiChatAttachmentPolicy.normalizeMimeType(mimeType),
                byteCount = total.toInt(),
                sourceUri = uri.toString()
            )
        } catch (error: Exception) {
            temp.delete()
            finalFile.delete()
            throw error
        }
    }

    fun readBytes(attachment: AiChatAttachment): ByteArray {
        if (!AiChatAttachmentMetadataCodec.isSafeId(attachment.id)) {
            throw AiChatAttachmentStorageException("An attached file reference is invalid.")
        }
        val file = File(directory, attachment.storageName)
        if (!file.isFile || file.length() != attachment.byteCount.toLong() ||
            file.length() > AiChatAttachmentPolicy.MAX_FILE_BYTES
        ) {
            throw AiChatAttachmentStorageException(
                "Attachment '" + AiChatAttachmentPolicy.safeLabel(attachment.name) +
                    "' is no longer available. Attach it again before sending."
            )
        }
        return file.readBytes()
    }

    fun hydrateForRequest(messages: List<AiChatMessage>): List<AiChatMessage> {
        // Prefer the newest message's files (especially the message being sent now). If older turns
        // include files that would push the request over the mobile memory budget, make that
        // omission explicit in the model input instead of failing the entire new request.
        var remainingBytes = AiChatAttachmentPolicy.MAX_TOTAL_BYTES.toLong()
        val hydrated = arrayOfNulls<AiChatMessage>(messages.size)
        for (index in messages.indices.reversed()) {
            val message = messages[index]
            if (message.attachments.isEmpty()) {
                hydrated[index] = message
                continue
            }
            val required = message.attachments.sumOf { it.byteCount.toLong() }
            if (required > remainingBytes) {
                hydrated[index] = message.copy(
                    content = message.content +
                        "\n[File payloads from this earlier turn are omitted from this request because the 100 MiB attachment budget is reserved for newer files.]",
                    attachments = emptyList()
                )
            } else {
                val data = message.attachments.map { attachment ->
                    attachment.copy(data = readBytes(attachment))
                }
                hydrated[index] = message.copy(attachments = data)
                remainingBytes -= required
            }
        }
        return hydrated.map { requireNotNull(it) }
    }

    fun delete(attachment: AiChatAttachment) {
        if (AiChatAttachmentMetadataCodec.isSafeId(attachment.id)) File(directory, attachment.storageName).delete()
    }

    fun cleanup(retainedIds: Set<String>) {
        directory.listFiles()?.forEach { file ->
            val id = file.name.removeSuffix(".blob")
            if (!file.name.endsWith(".blob") || !AiChatAttachmentMetadataCodec.isSafeId(id) || id !in retainedIds) {
                file.delete()
            }
        }
    }

    private fun storedBytes(): Long =
        directory.listFiles()?.asSequence()?.filter { it.isFile && it.name.endsWith(".blob") }?.sumOf { it.length() } ?: 0L

    companion object { const val DIRECTORY_NAME = "ai_chat_attachments" }
}

internal class AiChatAttachmentReader(
    context: Context,
    private val storage: AiChatAttachmentStorage
) {
    private val appContext = context.applicationContext

    suspend fun read(uris: List<Uri>, existing: List<AiChatAttachment>): AiChatAttachmentReadResult =
        withContext(Dispatchers.IO) {
            val added = ArrayList<AiChatAttachment>()
            val warnings = ArrayList<String>()
            val seen = existing.mapTo(mutableSetOf()) { it.sourceUri }
            var acceptedBytes = existing.sumOf { it.byteCount.toLong() }
            var acceptedCount = existing.size

            for ((index, uri) in uris.distinctBy { it.toString() }.withIndex()) {
                val uriKey = uri.toString()
                if (!seen.add(uriKey)) continue
                if (acceptedCount >= AiChatAttachmentPolicy.MAX_ATTACHMENTS) {
                    warnings.add("Only " + AiChatAttachmentPolicy.MAX_ATTACHMENTS + " attachments are allowed per message.")
                    break
                }
                val metadata = try {
                    queryMetadata(uri, index)
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: Exception) {
                    warnings.add("Skipped selected file " + (index + 1) + ": its details could not be read.")
                    continue
                }
                val name = metadata.first
                val declaredSize = metadata.second
                if (declaredSize != null && declaredSize > AiChatAttachmentPolicy.MAX_FILE_BYTES) {
                    warnings.add("Skipped " + name + ": the maximum file size is 25 MiB.")
                    continue
                }
                val remaining = AiChatAttachmentPolicy.MAX_TOTAL_BYTES - acceptedBytes
                if (remaining <= 0L) {
                    warnings.add("Skipped " + name + ": attachments in one message are limited to 100 MiB total.")
                    continue
                }
                val providerType = runCatching { appContext.contentResolver.getType(uri) }.getOrNull()
                val inferredType = inferMimeType(name)
                val type = if (providerType.isNullOrBlank() || providerType == "application/octet-stream") {
                    inferredType ?: "application/octet-stream"
                } else providerType
                try {
                    val imported = storage.importFromUri(
                        appContext,
                        uri,
                        name,
                        type,
                        minOf(AiChatAttachmentPolicy.MAX_FILE_BYTES.toLong(), remaining).toInt()
                    )
                    added.add(imported)
                    acceptedBytes += imported.byteCount
                    acceptedCount++
                } catch (cancelled: CancellationException) {
                    throw cancelled
                } catch (_: AiChatAttachmentTooLargeException) {
                    warnings.add("Skipped " + name + ": the maximum file size is 25 MiB.")
                } catch (error: AiChatAttachmentStorageException) {
                    warnings.add("Skipped " + name + ": " + (error.message ?: "the attachment could not be stored."))
                } catch (_: Exception) {
                    warnings.add("Skipped " + name + ": it could not be copied to local attachment storage.")
                }
            }
            AiChatAttachmentReadResult(added, warnings)
        }

    private fun queryMetadata(uri: Uri, index: Int): Pair<String, Long?> {
        var name = "attachment-" + (index + 1)
        var size: Long? = null
        appContext.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeColumn = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (nameColumn >= 0 && !cursor.isNull(nameColumn)) name = cursor.getString(nameColumn) ?: name
                if (sizeColumn >= 0 && !cursor.isNull(sizeColumn)) size = cursor.getLong(sizeColumn).takeIf { it >= 0L }
            }
        }
        return AiChatAttachmentPolicy.safeLabel(name) to size
    }

    private fun inferMimeType(name: String): String? {
        val extension = name.substringAfterLast('.', "").lowercase(Locale.ROOT)
        return extension.takeIf { it.isNotBlank() }?.let { MimeTypeMap.getSingleton().getMimeTypeFromExtension(it) }
    }
}

internal open class AiChatAttachmentStorageException(message: String) : IOException(message)
internal class AiChatAttachmentTooLargeException :
    AiChatAttachmentStorageException("The selected file exceeds the 25 MiB file attachment limit.")

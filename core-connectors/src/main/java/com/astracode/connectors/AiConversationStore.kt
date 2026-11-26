package com.astracode.connectors

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

enum class AiConversationRole(val wireValue: String) {
    USER("user"),
    ASSISTANT("assistant"),
    SYSTEM("system");

    companion object {
        fun fromWireValue(value: String): AiConversationRole =
            entries.firstOrNull { it.wireValue == value } ?: SYSTEM
    }
}

data class AiConversationThread(
    val id: String,
    val workspaceId: String,
    val title: String,
    val createdAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
    val messageCount: Int,
)

data class AiConversationMessage(
    val id: Long,
    val threadId: String,
    val role: AiConversationRole,
    val content: String,
    val providerId: String?,
    val modelId: String?,
    val createdAtEpochMillis: Long,
    val durationMillis: Long?,
    val estimatedInputTokens: Long?,
)

/**
 * Workspace-scoped local conversation storage.
 *
 * Only conversation text and non-secret request metadata are persisted here.
 * Provider credentials remain in ApiKeyVault.
 */
class AiConversationStore(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    DATABASE_NAME,
    null,
    DATABASE_VERSION,
) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE conversations (
                id TEXT PRIMARY KEY NOT NULL,
                workspace_id TEXT NOT NULL,
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE INDEX conversations_workspace_updated
            ON conversations(workspace_id, updated_at DESC)
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                thread_id TEXT NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                provider_id TEXT,
                model_id TEXT,
                created_at INTEGER NOT NULL,
                duration_ms INTEGER,
                estimated_input_tokens INTEGER,
                FOREIGN KEY(thread_id) REFERENCES conversations(id) ON DELETE CASCADE
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX messages_thread_created ON messages(thread_id, created_at, id)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        throw IllegalStateException(
            "Unsupported AI conversation database upgrade: " + oldVersion + " -> " + newVersion,
        )
    }

    fun createThread(
        workspaceId: String,
        title: String = DEFAULT_TITLE,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): AiConversationThread {
        validateWorkspaceId(workspaceId)
        val normalizedTitle = normalizeTitle(title)
        val id = UUID.randomUUID().toString()
        val values = ContentValues().apply {
            put("id", id)
            put("workspace_id", workspaceId)
            put("title", normalizedTitle)
            put("created_at", nowEpochMillis)
            put("updated_at", nowEpochMillis)
        }
        writableDatabase.insertOrThrow("conversations", null, values)
        return AiConversationThread(id, workspaceId, normalizedTitle, nowEpochMillis, nowEpochMillis, 0)
    }

    fun getThread(threadId: String): AiConversationThread? {
        readableDatabase.query(
            "conversations",
            THREAD_COLUMNS,
            "id = ?",
            arrayOf(threadId),
            null,
            null,
            null,
            "1",
        ).use { cursor ->
            if (!cursor.moveToFirst()) return null
            return cursor.toThread(0)
        }
    }

    fun listThreads(
        workspaceId: String,
        searchQuery: String = "",
        limit: Int = MAX_LISTED_THREADS,
    ): List<AiConversationThread> {
        validateWorkspaceId(workspaceId)
        require(limit in 1..MAX_LISTED_THREADS)
        val search = searchQuery.trim()
        val where = if (search.isBlank()) {
            "c.workspace_id = ?"
        } else {
            "c.workspace_id = ? AND (" +
                "instr(lower(c.title), lower(?)) > 0 OR EXISTS (" +
                "SELECT 1 FROM messages m WHERE m.thread_id = c.id AND instr(lower(m.content), lower(?)) > 0))"
        }
        val args = if (search.isBlank()) {
            arrayOf(workspaceId)
        } else {
            arrayOf(workspaceId, search, search)
        }
        val sql = """
            SELECT c.id, c.workspace_id, c.title, c.created_at, c.updated_at,
                (SELECT COUNT(*) FROM messages m WHERE m.thread_id = c.id) AS message_count
            FROM conversations c
            WHERE $where
            ORDER BY c.updated_at DESC
            LIMIT ?
        """.trimIndent()
        val selectionArgs = args + limit.toString()
        val results = mutableListOf<AiConversationThread>()
        readableDatabase.rawQuery(sql, selectionArgs).use { cursor ->
            while (cursor.moveToNext()) results += cursor.toThread(5)
        }
        return results
    }

    fun renameThread(threadId: String, title: String, nowEpochMillis: Long = System.currentTimeMillis()) {
        val values = ContentValues().apply {
            put("title", normalizeTitle(title))
            put("updated_at", nowEpochMillis)
        }
        check(writableDatabase.update("conversations", values, "id = ?", arrayOf(threadId)) == 1) {
            "Conversation was not found"
        }
    }

    fun deleteThread(threadId: String) {
        check(writableDatabase.delete("conversations", "id = ?", arrayOf(threadId)) == 1) {
            "Conversation was not found"
        }
    }

    fun appendMessage(
        threadId: String,
        role: AiConversationRole,
        content: String,
        providerId: String? = null,
        modelId: String? = null,
        durationMillis: Long? = null,
        estimatedInputTokens: Long? = null,
        nowEpochMillis: Long = System.currentTimeMillis(),
    ): AiConversationMessage {
        require(content.isNotBlank()) { "Conversation messages cannot be empty" }
        require(content.length <= MAX_MESSAGE_CHARS) { "Conversation message is too large" }
        require(durationMillis == null || durationMillis >= 0)
        require(estimatedInputTokens == null || estimatedInputTokens >= 0)
        val threadBeforeInsert = getThread(threadId)
            ?: throw IllegalArgumentException("Conversation was not found")
        val db = writableDatabase
        db.beginTransaction()
        val id: Long
        try {
            val values = ContentValues().apply {
                put("thread_id", threadId)
                put("role", role.wireValue)
                put("content", content)
                putNullable("provider_id", providerId)
                putNullable("model_id", modelId)
                put("created_at", nowEpochMillis)
                putNullable("duration_ms", durationMillis)
                putNullable("estimated_input_tokens", estimatedInputTokens)
            }
            id = db.insertOrThrow("messages", null, values)
            val title = if (role == AiConversationRole.USER && threadBeforeInsert.messageCount == 0) {
                normalizeTitle(content)
            } else {
                threadBeforeInsert.title
            }
            db.update(
                "conversations",
                ContentValues().apply {
                    put("title", title)
                    put("updated_at", nowEpochMillis)
                },
                "id = ?",
                arrayOf(threadId),
            )
            db.execSQL(
                """
                DELETE FROM messages
                WHERE thread_id = ? AND id NOT IN (
                    SELECT id FROM messages WHERE thread_id = ?
                    ORDER BY created_at DESC, id DESC LIMIT ?
                )
                """.trimIndent(),
                arrayOf(threadId, threadId, MAX_MESSAGES_PER_THREAD),
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        return AiConversationMessage(
            id = id,
            threadId = threadId,
            role = role,
            content = content,
            providerId = providerId,
            modelId = modelId,
            createdAtEpochMillis = nowEpochMillis,
            durationMillis = durationMillis,
            estimatedInputTokens = estimatedInputTokens,
        )
    }

    fun loadMessages(threadId: String, limit: Int = MAX_MESSAGES_PER_THREAD): List<AiConversationMessage> {
        require(limit in 1..MAX_MESSAGES_PER_THREAD)
        val results = mutableListOf<AiConversationMessage>()
        readableDatabase.query(
            "messages",
            MESSAGE_COLUMNS,
            "thread_id = ?",
            arrayOf(threadId),
            null,
            null,
            "created_at DESC, id DESC",
            limit.toString(),
        ).use { cursor ->
            while (cursor.moveToNext()) results += cursor.toMessage()
        }
        return results.asReversed()
    }

    /**
     * Builds bounded historical context for the next request. The caller adds
     * the current user prompt separately, so it should call this before saving
     * that new prompt.
     */
    fun buildHistoryContext(threadId: String, maxMessages: Int = MAX_CONTEXT_MESSAGES): String {
        require(maxMessages in 1..MAX_CONTEXT_MESSAGES)
        val messages = loadMessages(threadId, maxMessages)
        if (messages.isEmpty()) return ""
        val output = StringBuilder()
        output.appendLine("Earlier conversation messages (reference only; the current task has priority):")
        messages.forEach { message ->
            val label = when (message.role) {
                AiConversationRole.USER -> "User"
                AiConversationRole.ASSISTANT -> "AstraCode assistant"
                AiConversationRole.SYSTEM -> "System"
            }
            val remaining = MAX_HISTORY_CONTEXT_CHARS - output.length
            if (remaining <= 0) return@forEach
            val excerpt = message.content.take(minOf(MAX_HISTORY_MESSAGE_CHARS, remaining))
            output.append('[').append(label).appendLine(']')
            output.appendLine(excerpt)
        }
        if (output.length > MAX_HISTORY_CONTEXT_CHARS) {
            output.setLength(MAX_HISTORY_CONTEXT_CHARS)
        }
        return output.toString()
    }

    fun exportThread(threadId: String): String {
        val thread = getThread(threadId) ?: throw IllegalArgumentException("Conversation was not found")
        val messages = JSONArray()
        loadMessages(threadId).forEach { message ->
            messages.put(
                JSONObject()
                    .put("id", message.id)
                    .put("role", message.role.wireValue)
                    .put("content", message.content)
                    .put("provider", message.providerId ?: JSONObject.NULL)
                    .put("model", message.modelId ?: JSONObject.NULL)
                    .put("created_at", message.createdAtEpochMillis)
                    .put("duration_ms", message.durationMillis ?: JSONObject.NULL)
                    .put("estimated_input_tokens", message.estimatedInputTokens ?: JSONObject.NULL),
            )
        }
        return JSONObject()
            .put("format", "astracode-ai-conversation")
            .put("version", 1)
            .put(
                "conversation",
                JSONObject()
                    .put("id", thread.id)
                    .put("workspace_id", thread.workspaceId)
                    .put("title", thread.title)
                    .put("created_at", thread.createdAtEpochMillis)
                    .put("updated_at", thread.updatedAtEpochMillis),
            )
            .put("messages", messages)
            .toString(2)
    }

    private fun normalizeTitle(value: String): String {
        val title = value.trim().replace(Regex("\\s+"), " ").take(MAX_TITLE_CHARS)
        return title.ifBlank { DEFAULT_TITLE }
    }

    private fun validateWorkspaceId(value: String) {
        require(value.isNotBlank() && value.length <= MAX_WORKSPACE_ID_CHARS) {
            "A valid workspace ID is required"
        }
    }

    private fun ContentValues.putNullable(key: String, value: String?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private fun ContentValues.putNullable(key: String, value: Long?) {
        if (value == null) putNull(key) else put(key, value)
    }

    private fun android.database.Cursor.toThread(messageCountIndex: Int): AiConversationThread =
        AiConversationThread(
            id = getString(getColumnIndexOrThrow("id")),
            workspaceId = getString(getColumnIndexOrThrow("workspace_id")),
            title = getString(getColumnIndexOrThrow("title")),
            createdAtEpochMillis = getLong(getColumnIndexOrThrow("created_at")),
            updatedAtEpochMillis = getLong(getColumnIndexOrThrow("updated_at")),
            messageCount = getInt(messageCountIndex),
        )

    private fun android.database.Cursor.toMessage(): AiConversationMessage {
        fun nullableString(column: String): String? {
            val index = getColumnIndexOrThrow(column)
            return if (isNull(index)) null else getString(index)
        }
        fun nullableLong(column: String): Long? {
            val index = getColumnIndexOrThrow(column)
            return if (isNull(index)) null else getLong(index)
        }
        return AiConversationMessage(
            id = getLong(getColumnIndexOrThrow("id")),
            threadId = getString(getColumnIndexOrThrow("thread_id")),
            role = AiConversationRole.fromWireValue(getString(getColumnIndexOrThrow("role"))),
            content = getString(getColumnIndexOrThrow("content")),
            providerId = nullableString("provider_id"),
            modelId = nullableString("model_id"),
            createdAtEpochMillis = getLong(getColumnIndexOrThrow("created_at")),
            durationMillis = nullableLong("duration_ms"),
            estimatedInputTokens = nullableLong("estimated_input_tokens"),
        )
    }

    private companion object {
        const val DATABASE_NAME = "astracode_ai_conversations.db"
        const val DATABASE_VERSION = 1
        const val DEFAULT_TITLE = "New conversation"
        const val MAX_WORKSPACE_ID_CHARS = 256
        const val MAX_TITLE_CHARS = 80
        const val MAX_MESSAGE_CHARS = 250_000
        const val MAX_LISTED_THREADS = 200
        const val MAX_MESSAGES_PER_THREAD = 200
        const val MAX_CONTEXT_MESSAGES = 12
        const val MAX_HISTORY_MESSAGE_CHARS = 3_000
        const val MAX_HISTORY_CONTEXT_CHARS = 16_000
        val THREAD_COLUMNS = arrayOf(
            "id", "workspace_id", "title", "created_at", "updated_at",
            "(SELECT COUNT(*) FROM messages m WHERE m.thread_id = conversations.id) AS message_count",
        )
        val MESSAGE_COLUMNS = arrayOf(
            "id", "thread_id", "role", "content", "provider_id", "model_id",
            "created_at", "duration_ms", "estimated_input_tokens",
        )
    }
}

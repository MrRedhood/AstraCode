package com.mrredhood.astracode

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

internal data class AiChatSessionSummary(val id: Long, val title: String, val updatedAtMillis: Long)

/** Local durable conversation storage. Chat is excluded from Android backup because prompts can contain private code. */
internal class AiChatSessionStore(
    context: Context,
    databaseName: String = DATABASE_NAME
) : SQLiteOpenHelper(context.applicationContext, databaseName, null, DATABASE_VERSION) {
    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE chat_sessions (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                title TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL(
            """
            CREATE TABLE chat_messages (
                id INTEGER PRIMARY KEY AUTOINCREMENT,
                session_id INTEGER NOT NULL,
                role TEXT NOT NULL,
                content TEXT NOT NULL,
                created_at INTEGER NOT NULL,
                FOREIGN KEY(session_id) REFERENCES chat_sessions(id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX chat_messages_session_id_id ON chat_messages(session_id, id)")
        db.execSQL("CREATE INDEX chat_sessions_updated_at ON chat_sessions(updated_at DESC, id DESC)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Future schema changes must use explicit, additive migrations.
    }

    @Synchronized
    fun createSession(nowMillis: Long = System.currentTimeMillis()): Long {
        val values = ContentValues().apply {
            put("title", NEW_SESSION_TITLE)
            put("created_at", nowMillis)
            put("updated_at", nowMillis)
        }
        val db = writableDatabase
        val id = db.insertOrThrow("chat_sessions", null, values)
        pruneSessions(db)
        return id
    }

    @Synchronized
    fun listSessions(limit: Int = MAX_SESSIONS): List<AiChatSessionSummary> {
        val safeLimit = limit.coerceIn(1, MAX_SESSIONS)
        val result = ArrayList<AiChatSessionSummary>(safeLimit)
        readableDatabase.query(
            "chat_sessions", arrayOf("id", "title", "updated_at"),
            null, null, null, null, "updated_at DESC, id DESC", safeLimit.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result.add(AiChatSessionSummary(cursor.getLong(0), cursor.getString(1).take(MAX_TITLE_CHARS), cursor.getLong(2)))
            }
        }
        return result
    }

    @Synchronized
    fun loadMessages(sessionId: Long): List<AiChatMessage> {
        val result = ArrayList<AiChatMessage>(MAX_MESSAGES_PER_SESSION)
        readableDatabase.query(
            "chat_messages", arrayOf("role", "content"),
            "session_id = ?", arrayOf(sessionId.toString()),
            null, null, "id DESC", MAX_MESSAGES_PER_SESSION.toString()
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val role = parsePersistedRole(cursor)
                val content = cursor.getString(1)
                if (role != null && content.isNotBlank()) result.add(AiChatMessage(role, boundMessage(content)))
            }
        }
        result.reverse()
        return result
    }

    @Synchronized
    fun appendMessage(sessionId: Long, message: AiChatMessage, nowMillis: Long = System.currentTimeMillis()) {
        require(message.role == AiMessageRole.USER || message.role == AiMessageRole.ASSISTANT) {
            "System instructions must not be persisted as conversation messages"
        }
        val db = writableDatabase
        db.beginTransaction()
        try {
            val exists = db.query("chat_sessions", arrayOf("id"), "id = ?", arrayOf(sessionId.toString()), null, null, null, "1")
                .use { it.moveToFirst() }
            require(exists) { "Conversation no longer exists" }

            val values = ContentValues().apply {
                put("session_id", sessionId)
                put("role", message.role.name)
                put("content", boundMessage(message.content))
                put("created_at", nowMillis)
            }
            db.insertOrThrow("chat_messages", null, values)
            db.execSQL(
                """
                DELETE FROM chat_messages WHERE session_id = ?
                AND id NOT IN (
                    SELECT id FROM chat_messages WHERE session_id = ?
                    ORDER BY id DESC LIMIT ?
                )
                """.trimIndent(),
                arrayOf(sessionId.toString(), sessionId.toString(), MAX_MESSAGES_PER_SESSION.toString())
            )
            db.update("chat_sessions", ContentValues().apply { put("updated_at", nowMillis) }, "id = ?", arrayOf(sessionId.toString()))
            if (message.role == AiMessageRole.USER) {
                db.update(
                    "chat_sessions",
                    ContentValues().apply { put("title", normalizedTitle(message.content)) },
                    "id = ? AND title = ?",
                    arrayOf(sessionId.toString(), NEW_SESSION_TITLE)
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
        pruneSessions(db)
    }

    @Synchronized
    fun deleteSession(sessionId: Long): Boolean =
        writableDatabase.delete("chat_sessions", "id = ?", arrayOf(sessionId.toString())) > 0

    private fun pruneSessions(db: SQLiteDatabase) {
        db.execSQL(
            """
            DELETE FROM chat_sessions WHERE id NOT IN (
                SELECT id FROM chat_sessions ORDER BY updated_at DESC, id DESC LIMIT ?
            )
            """.trimIndent(),
            arrayOf(MAX_SESSIONS.toString())
        )
    }

    private fun parsePersistedRole(cursor: Cursor): AiMessageRole? =
        runCatching { AiMessageRole.valueOf(cursor.getString(0)) }.getOrNull()
            ?.takeIf { it == AiMessageRole.USER || it == AiMessageRole.ASSISTANT }

    private fun normalizedTitle(content: String): String {
        val normalized = content.replace(WHITESPACE, " ").trim()
        return if (normalized.isEmpty()) NEW_SESSION_TITLE else safeTitle(normalized)
    }

    private fun safeTitle(value: String): String {
        if (value.length <= MAX_TITLE_CHARS) return value
        var end = MAX_TITLE_CHARS
        if (end > 0 && Character.isHighSurrogate(value[end - 1]) && end < value.length && Character.isLowSurrogate(value[end])) end--
        return value.substring(0, end)
    }

    private fun boundMessage(content: String): String {
        if (content.length <= MAX_MESSAGE_CHARS) return content
        var end = MAX_MESSAGE_CHARS
        if (end > 0 && Character.isHighSurrogate(content[end - 1]) && end < content.length && Character.isLowSurrogate(content[end])) end--
        return content.substring(0, end)
    }

    companion object {
        const val MAX_SESSIONS = 25
        const val MAX_MESSAGES_PER_SESSION = 60
        const val MAX_TITLE_CHARS = 44
        const val MAX_MESSAGE_CHARS = 60_000
        const val NEW_SESSION_TITLE = "New chat"
        const val DATABASE_NAME = "astracode_chat.db"
        private const val DATABASE_VERSION = 1
        private val WHITESPACE = Regex("\\s+")
    }
}

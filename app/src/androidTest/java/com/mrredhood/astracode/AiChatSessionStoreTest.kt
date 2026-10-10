package com.mrredhood.astracode

import android.content.Context
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class AiChatSessionStoreTest {
    private val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun messagesAndTitleSurviveStoreRecreation() {
        withTemporaryDatabase { name ->
            val first = AiChatSessionStore(context, name)
            val sessionId: Long
            try {
                sessionId = first.createSession()
                first.appendMessage(sessionId, AiChatMessage(AiMessageRole.USER, "  How do I find files?  "), 100L)
                first.appendMessage(sessionId, AiChatMessage(AiMessageRole.ASSISTANT, "Use the workspace search."), 200L)
            } finally { first.close() }
            val reopened = AiChatSessionStore(context, name)
            try {
                val session = reopened.listSessions().single()
                assertEquals(sessionId, session.id)
                assertEquals("How do I find files?", session.title)
                assertEquals(
                    listOf(
                        AiChatMessage(AiMessageRole.USER, "  How do I find files?  "),
                        AiChatMessage(AiMessageRole.ASSISTANT, "Use the workspace search.")
                    ),
                    reopened.loadMessages(sessionId)
                )
            } finally { reopened.close() }
        }
    }

    @Test
    fun messageHistoryIsBoundedToMostRecentMessages() {
        withTemporaryDatabase { name ->
            val store = AiChatSessionStore(context, name)
            try {
                val id = store.createSession()
                repeat(AiChatSessionStore.MAX_MESSAGES_PER_SESSION + 15) { index ->
                    store.appendMessage(id, AiChatMessage(AiMessageRole.USER, "message-" + index), 1_000L + index)
                }
                val loaded = store.loadMessages(id)
                assertEquals(AiChatSessionStore.MAX_MESSAGES_PER_SESSION, loaded.size)
                assertEquals("message-15", loaded.first().content)
                assertEquals("message-74", loaded.last().content)
            } finally { store.close() }
        }
    }

    @Test
    fun sessionHistoryIsBoundedAndNewestFirst() {
        withTemporaryDatabase { name ->
            val store = AiChatSessionStore(context, name)
            try {
                repeat(AiChatSessionStore.MAX_SESSIONS + 5) { store.createSession(10_000L + it) }
                val sessions = store.listSessions()
                assertEquals(AiChatSessionStore.MAX_SESSIONS, sessions.size)
                assertTrue(sessions.zipWithNext().all { (a, b) -> a.updatedAtMillis >= b.updatedAtMillis })
            } finally { store.close() }
        }
    }

    @Test
    fun deletingSessionAlsoDeletesMessagesAndSystemMessagesAreRejected() {
        withTemporaryDatabase { name ->
            val store = AiChatSessionStore(context, name)
            try {
                val id = store.createSession()
                store.appendMessage(id, AiChatMessage(AiMessageRole.USER, "private prompt"))
                assertTrue(runCatching {
                    store.appendMessage(id, AiChatMessage(AiMessageRole.SYSTEM, "internal instructions"))
                }.exceptionOrNull() is IllegalArgumentException)
                assertTrue(store.deleteSession(id))
                assertFalse(store.deleteSession(id))
                assertTrue(store.loadMessages(id).isEmpty())
                assertTrue(store.listSessions().isEmpty())
            } finally { store.close() }
        }
    }

    private fun withTemporaryDatabase(block: (String) -> Unit) {
        val name = "chat-test-" + UUID.randomUUID().toString().replace("-", "") + ".db"
        context.deleteDatabase(name)
        try { block(name) } finally { context.deleteDatabase(name) }
    }
}

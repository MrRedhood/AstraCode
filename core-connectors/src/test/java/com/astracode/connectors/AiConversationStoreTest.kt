package com.astracode.connectors

import android.content.Context
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Before
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class AiConversationStoreTest {
    private lateinit var context: Context
    private lateinit var store: AiConversationStore

    @Before
    fun setUp() {
        context = RuntimeEnvironment.getApplication()
        context.deleteDatabase("astracode_ai_conversations.db")
        store = AiConversationStore(context)
    }

    @After
    fun tearDown() {
        store.close()
        context.deleteDatabase("astracode_ai_conversations.db")
    }

    @Test
    fun createsWorkspaceScopedThreadAndAutoTitlesFirstUserMessage() {
        val thread = store.createThread("workspace-one", nowEpochMillis = 100L)
        assertEquals("New conversation", thread.title)
        assertEquals(0, thread.messageCount)

        store.appendMessage(
            threadId = thread.id,
            role = AiConversationRole.USER,
            content = "Build a settings screen",
            providerId = "openai",
            modelId = "gpt-example",
            estimatedInputTokens = 12,
            nowEpochMillis = 200L,
        )

        val saved = store.getThread(thread.id)
        assertNotNull(saved)
        assertEquals("Build a settings screen", saved.title)
        assertEquals(1, saved.messageCount)
        assertEquals(200L, saved.updatedAtEpochMillis)
        assertEquals("workspace-one", saved.workspaceId)
    }

    @Test
    fun savesMessagesAndBuildsBoundedHistoryContext() {
        val thread = store.createThread("workspace-one")
        store.appendMessage(thread.id, AiConversationRole.USER, "Create a login screen")
        store.appendMessage(
            thread.id,
            AiConversationRole.ASSISTANT,
            "Summary: created a login proposal",
            providerId = "anthropic",
            modelId = "claude-example",
            durationMillis = 1_500L,
            estimatedInputTokens = 800L,
        )

        val messages = store.loadMessages(thread.id)
        assertEquals(2, messages.size)
        assertEquals(AiConversationRole.USER, messages[0].role)
        assertEquals(AiConversationRole.ASSISTANT, messages[1].role)
        assertEquals(1_500L, messages[1].durationMillis)
        assertEquals("claude-example", messages[1].modelId)

        val history = store.buildHistoryContext(thread.id)
        assertTrue(history.contains("Create a login screen"))
        assertTrue(history.contains("created a login proposal"))
        assertTrue(history.contains("reference only"))
        assertTrue(history.length <= 16_000)
    }

    @Test
    fun keepsCustomThreadTitleWhenFirstUserMessageArrives() {
        val thread = store.createThread("workspace-one", "My chosen title")
        store.appendMessage(thread.id, AiConversationRole.USER, "First user message")

        assertEquals("My chosen title", store.getThread(thread.id)?.title)
    }

    @Test
    fun searchesOnlyWithinRequestedWorkspace() {
        val first = store.createThread("workspace-one", "Alpha thread")
        store.appendMessage(first.id, AiConversationRole.USER, "Configure a Kotlin compiler")

        val second = store.createThread("workspace-two", "Beta thread")
        store.appendMessage(second.id, AiConversationRole.USER, "Configure a Rust compiler")

        val titleMatches = store.listThreads("workspace-one", "alpha")
        assertEquals(listOf(first.id), titleMatches.map { it.id })

        val messageMatches = store.listThreads("workspace-one", "Kotlin compiler")
        assertEquals(listOf(first.id), messageMatches.map { it.id })

        assertTrue(store.listThreads("workspace-one", "Rust compiler").isEmpty())
        assertTrue(store.listThreads("workspace-two", "Rust compiler").any { it.id == second.id })
    }

    @Test
    fun renamesAndDeletesThreadAndCascadesMessages() {
        val thread = store.createThread("workspace-one")
        store.appendMessage(thread.id, AiConversationRole.USER, "A conversation to remove")

        store.renameThread(thread.id, "  Updated   title  ", nowEpochMillis = 500L)
        val renamed = store.getThread(thread.id)
        assertNotNull(renamed)
        assertEquals("Updated title", renamed.title)
        assertEquals(500L, renamed.updatedAtEpochMillis)

        store.deleteThread(thread.id)
        assertNull(store.getThread(thread.id))
        assertTrue(store.loadMessages(thread.id).isEmpty())
    }

    @Test
    fun exportsMessagesAndMetadataWithoutCredentialFields() {
        val thread = store.createThread("workspace-one", "Export test")
        store.appendMessage(
            thread.id,
            AiConversationRole.USER,
            "Make a file browser",
            providerId = "openrouter",
            modelId = "model-id",
        )
        val exported = store.exportThread(thread.id)

        assertTrue(exported.contains("astracode-ai-conversation"))
        assertTrue(exported.contains("Export test"))
        assertTrue(exported.contains("openrouter"))
        assertTrue(exported.contains("model-id"))
        assertFalse(exported.contains("api_key"))
        assertFalse(exported.contains("password"))
        assertFalse(exported.contains("Bearer "))
        assertFalse(exported.contains("sk-"))
    }

    @Test
    fun capsStoredMessageCountPerThread() {
        val thread = store.createThread("workspace-one")
        repeat(205) { index ->
            store.appendMessage(
                thread.id,
                AiConversationRole.USER,
                "Message " + index,
                nowEpochMillis = index.toLong() + 1L,
            )
        }

        val saved = store.getThread(thread.id)
        assertNotNull(saved)
        assertEquals(200, saved.messageCount)
        val messages = store.loadMessages(thread.id)
        assertEquals(200, messages.size)
        assertTrue(messages.first().content.contains("Message 5"))
        assertTrue(messages.last().content.contains("Message 204"))
    }

    @Test
    fun normalizesLongOrBlankThreadTitles() {
        val blank = store.createThread("workspace-one", "   ")
        assertEquals("New conversation", blank.title)

        val longTitle = store.createThread("workspace-one", "x".repeat(120))
        assertEquals(80, longTitle.title.length)
    }
}

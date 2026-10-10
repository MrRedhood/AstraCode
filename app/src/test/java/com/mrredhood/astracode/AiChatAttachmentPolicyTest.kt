package com.mrredhood.astracode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AiChatAttachmentPolicyTest {
    private fun file(name: String = "sample.kt", text: String = "fun main() {}") =
        AiChatAttachment("content://example/$name", name, "text/plain", text.toByteArray().size, text)

    @Test
    fun acceptsKnownTextAndCodeTypesButRejectsBinaryFormats() {
        assertTrue(AiChatAttachmentPolicy.isTextLike("MainActivity.kt", "application/octet-stream"))
        assertTrue(AiChatAttachmentPolicy.isTextLike("notes.txt", null))
        assertTrue(AiChatAttachmentPolicy.isTextLike("response.json", "application/json"))
        assertFalse(AiChatAttachmentPolicy.isTextLike("archive.zip", "application/octet-stream"))
        assertFalse(AiChatAttachmentPolicy.isTextLike("picture.png", "image/png"))
        assertFalse(AiChatAttachmentPolicy.isTextLike("document.pdf", "application/pdf"))
    }

    @Test
    fun rejectsMalformedUtf8AndNulContainingBinaryPayloads() {
        assertNull(AiChatAttachmentPolicy.decodeUtf8(byteArrayOf(0xC3.toByte(), 0x28)))
        assertNull(AiChatAttachmentPolicy.decodeUtf8(byteArrayOf(65, 0, 66)))
        assertEquals("hi 🌌", AiChatAttachmentPolicy.decodeUtf8("hi 🌌".toByteArray()))
    }

    @Test
    fun composesProviderTextWithASeparateVisibleAttachmentSummary() {
        val attachment = file("Reader.kt", "class Reader")
        val composed = AiChatAttachmentPolicy.composeMessage("Explain this", listOf(attachment))
        assertTrue(composed.providerContent.contains("class Reader"))
        assertTrue(composed.providerContent.contains("BEGIN ATTACHED FILE: Reader.kt"))
        assertTrue(composed.displayContent.contains("Attached files:"))
        assertTrue(composed.displayContent.contains("Reader.kt"))
        assertFalse(composed.displayContent.contains("class Reader"))
    }

    @Test
    fun allowsAttachmentOnlyMessageAndRejectsEmptyMessage() {
        val composed = AiChatAttachmentPolicy.composeMessage("", listOf(file()))
        assertTrue(composed.providerContent.contains("sample.kt"))
        assertTrue(runCatching {
            AiChatAttachmentPolicy.composeMessage("  ", emptyList())
        }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun enforcesAttachmentCountAndCombinedByteCaps() {
        val three = listOf(file("a.kt"), file("b.kt"), file("c.kt"))
        assertTrue(runCatching {
            AiChatAttachmentPolicy.composeMessage("review", three + file("d.kt"))
        }.exceptionOrNull() is IllegalArgumentException)
        val tooLarge = file(text = "x".repeat(AiChatAttachmentPolicy.MAX_TOTAL_BYTES + 1))
            .copy(byteCount = AiChatAttachmentPolicy.MAX_TOTAL_BYTES + 1)
        assertTrue(runCatching {
            AiChatAttachmentPolicy.composeMessage("review", listOf(tooLarge))
        }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun reportsMobileFriendlyAttachmentBounds() {
        assertEquals(3, AiChatAttachmentPolicy.MAX_ATTACHMENTS)
        assertEquals(16 * 1024, AiChatAttachmentPolicy.MAX_FILE_BYTES)
        assertEquals(32 * 1024, AiChatAttachmentPolicy.MAX_TOTAL_BYTES)
    }
}

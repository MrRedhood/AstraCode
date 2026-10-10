package com.mrredhood.astracode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.UUID

class AiChatAttachmentPolicyTest {
    private fun attachment(
        name: String = "sample.kt",
        mimeType: String = "text/plain",
        bytes: Int = 14,
        data: ByteArray? = null
    ) = AiChatAttachment(UUID.randomUUID().toString(), name, mimeType, bytes, data = data)

    @Test
    fun pickerPolicyAllowsTextImagesAudioVideoDocumentsAndArbitraryFormats() {
        listOf(
            attachment("Main.kt", "text/plain"),
            attachment("photo.png", "image/png"),
            attachment("voice.mp3", "audio/mpeg"),
            attachment("clip.mp4", "video/mp4"),
            attachment("report.pdf", "application/pdf"),
            attachment("slides.pptx", "application/vnd.openxmlformats-officedocument.presentationml.presentation"),
            attachment("archive.7z", "application/x-7z-compressed")
        ).forEach { file ->
            val message = AiChatAttachmentPolicy.composeMessage("Please inspect", listOf(file))
            assertTrue(file.name, message.displayContent.contains(file.name))
        }
    }

    @Test
    fun recognizesTextForProvidersAndRejectsMalformedUtf8WhenDecoded() {
        assertTrue(AiChatAttachmentPolicy.isTextLike("MainActivity.kt", "application/octet-stream"))
        assertTrue(AiChatAttachmentPolicy.isTextLike("notes.txt", null))
        assertTrue(AiChatAttachmentPolicy.isTextLike("response.json", "application/json"))
        assertFalse(AiChatAttachmentPolicy.isTextLike("archive.zip", "application/octet-stream"))
        assertNull(AiChatAttachmentPolicy.decodeUtf8(byteArrayOf(0xC3.toByte(), 0x28)))
        assertNull(AiChatAttachmentPolicy.decodeUtf8(byteArrayOf(65, 0, 66)))
        assertEquals("hi 🌌", AiChatAttachmentPolicy.decodeUtf8("hi 🌌".toByteArray()))
    }

    @Test
    fun messageDisplayShowsOnlyPromptAndFileMetadataNotRawBytes() {
        val binary = attachment("photo.png", "image/png", bytes = 3, data = byteArrayOf(1, 2, 3))
        val composed = AiChatAttachmentPolicy.composeMessage("Describe this", listOf(binary))
        assertEquals("Describe this", composed.providerContent)
        assertTrue(composed.displayContent.contains("Attached files:"))
        assertTrue(composed.displayContent.contains("photo.png"))
        assertTrue(composed.displayContent.contains("3 B"))
        assertFalse(composed.displayContent.contains("data:image"))
    }

    @Test
    fun allowsAttachmentOnlyMessagesAndRejectsEmptyMessage() {
        val composed = AiChatAttachmentPolicy.composeMessage("", listOf(attachment()))
        assertTrue(composed.providerContent.contains("attached file"))
        assertTrue(runCatching {
            AiChatAttachmentPolicy.composeMessage("  ", emptyList())
        }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun enforcesThreeFilesAnd25MibPerFileAndPerRequest() {
        val three = listOf(attachment("a.txt"), attachment("b.txt"), attachment("c.txt"))
        assertTrue(runCatching {
            AiChatAttachmentPolicy.composeMessage("review", three + attachment("d.txt"))
        }.exceptionOrNull() is IllegalArgumentException)
        assertEquals(25 * 1024 * 1024, AiChatAttachmentPolicy.MAX_FILE_BYTES)
        assertEquals(25 * 1024 * 1024, AiChatAttachmentPolicy.MAX_TOTAL_BYTES)
        val maxFile = attachment("large.bin", "application/octet-stream", AiChatAttachmentPolicy.MAX_FILE_BYTES)
        assertTrue(AiChatAttachmentPolicy.composeMessage("review", listOf(maxFile)).displayContent.contains("25.0 MiB"))
        val tooLarge = maxFile.copy(byteCount = AiChatAttachmentPolicy.MAX_FILE_BYTES + 1)
        assertTrue(runCatching {
            AiChatAttachmentPolicy.composeMessage("review", listOf(tooLarge))
        }.exceptionOrNull() is IllegalArgumentException)
        val aggregateTooLarge = listOf(
            attachment("a.bin", "application/octet-stream", 13 * 1024 * 1024),
            attachment("b.bin", "application/octet-stream", 13 * 1024 * 1024)
        )
        assertTrue(runCatching {
            AiChatAttachmentPolicy.composeMessage("review", aggregateTooLarge)
        }.exceptionOrNull() is IllegalArgumentException)
    }

    @Test
    fun attachmentMetadataRoundTripsWithoutPersistingRawBytesOrUri() {
        val input = attachment(
            name = "image.png",
            mimeType = "image/png",
            bytes = 3,
            data = byteArrayOf(1, 2, 3)
        ).copy(sourceUri = "content://private/provider/document/42")
        val stored = AiChatAttachmentMetadataCodec.encode(listOf(input))
        assertFalse(stored.contains("content://"))
        assertFalse(stored.contains("AQID"))
        val decoded = AiChatAttachmentMetadataCodec.decode(stored).single()
        assertEquals(input.id, decoded.id)
        assertEquals(input.name, decoded.name)
        assertEquals(input.mimeType, decoded.mimeType)
        assertEquals(input.byteCount, decoded.byteCount)
        assertNull(decoded.data)
    }

    @Test
    fun normalizesMimeTypeAndFormatsHumanReadableSizes() {
        assertEquals("image/png", AiChatAttachmentPolicy.normalizeMimeType("IMAGE/PNG; charset=binary"))
        assertEquals("application/octet-stream", AiChatAttachmentPolicy.normalizeMimeType("bad mime value"))
        assertEquals("25.0 MiB", AiChatAttachmentPolicy.formatBytes(25L * 1024 * 1024))
    }
}

package com.astracode.connectors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiSecretRedactorTest {
    @Test
    fun redactsCommonProviderTokensAndMarksFileProtected() {
        val source = """
            val openAiKey = "sk-abcdefghijklmnopqrstuvwxyz123456"
            val github = "ghp_abcdefghijklmnopqrstuvwxyz123456"
            val auth = "Bearer abcdefghijklmnopqrstuvwxyz123456"
        """.trimIndent()

        val result = AiSecretRedactor.redact(source)
        assertTrue(result.wasRedacted)
        assertEquals(3, result.redactionCount)
        assertFalse(result.content.contains("sk-abcdefghijklmnopqrstuvwxyz"))
        assertFalse(result.content.contains("ghp_abcdefghijklmnopqrstuvwxyz"))
        assertFalse(result.content.contains("Bearer abcdefghijklmnopqrstuvwxyz"))
        assertTrue(result.content.contains("[REDACTED]"))
    }

    @Test
    fun redactsGenericEnvironmentSecretsAndCommonProviderTokens() {
        val source = """
            AWS_SECRET_ACCESS_KEY=abcdef0123456789secret
            SLACK_TOKEN=xoxb-1234567890-abcdefghijk
            NPM_TOKEN=npm_abcdefghijklmnopqrstuvwxyz1234567890
            val stripe = "sk_live_abcdefghijklmnop123456"
            val gitlab = "glpat-abcdefghijklmnopqrstuvwxyz123456"
            val huggingFace = "hf_abcdefghijklmnopqrstuvwxyz123456"
        """.trimIndent()

        val result = AiSecretRedactor.redact(source)
        assertTrue(result.wasRedacted)
        assertFalse(result.content.contains("abcdef0123456789secret"))
        assertFalse(result.content.contains("xoxb-1234567890-abcdefghijk"))
        assertFalse(result.content.contains("npm_abcdefghijklmnopqrstuvwxyz"))
        assertFalse(result.content.contains("sk_live_abcdefghijklmnop"))
        assertFalse(result.content.contains("glpat-abcdefghijklmnopqrstuvwxyz"))
        assertFalse(result.content.contains("hf_abcdefghijklmnopqrstuvwxyz"))
    }

    @Test
    fun redactsEnvironmentAssignmentsAndPrivateKeyBlocks() {
        val source = """
            OPENAI_API_KEY=abcdef0123456789secret
            -----BEGIN RSA PRIVATE KEY-----
            very-secret-private-material
            -----END RSA PRIVATE KEY-----
        """.trimIndent()

        val result = AiSecretRedactor.redact(source)
        assertTrue(result.wasRedacted)
        assertFalse(result.content.contains("abcdef0123456789secret"))
        assertFalse(result.content.contains("very-secret-private-material"))
        assertTrue(result.content.contains("[REDACTED PRIVATE KEY]"))
    }

    @Test
    fun leavesOrdinarySourceTextUnchanged() {
        val source = """
            fun calculateTotal(items: List<Int>): Int = items.sum()
            val message = "hello world"
        """.trimIndent()

        val result = AiSecretRedactor.redact(source)
        assertFalse(result.wasRedacted)
        assertEquals(0, result.redactionCount)
        assertEquals(source, result.content)
    }

    @Test
    fun protectsDiskSecretWhenUnsavedDraftHasRemovedIt() {
        val disk = "OPENAI_API_KEY=sk-abcdefghijklmnopqrstuvwxyz123456"
        val draft = "OPENAI_API_KEY=[removed in editor]"
        val result = AiSecretRedactor.redactWorkspaceSnapshot(disk, draft)
        assertTrue(result.writeProtected)
        assertEquals(draft, result.content)
        assertFalse(result.content.contains("sk-abcdefghijklmnopqrstuvwxyz"))
    }

    @Test
    fun protectsAndRedactsSecretsAddedOnlyInUnsavedDraft() {
        val disk = "fun main() = Unit"
        val draft = "val token = \"ghp_abcdefghijklmnopqrstuvwxyz123456\""
        val result = AiSecretRedactor.redactWorkspaceSnapshot(disk, draft)
        assertTrue(result.writeProtected)
        assertFalse(result.content.contains("ghp_abcdefghijklmnopqrstuvwxyz"))
        assertTrue(result.content.contains("[REDACTED]"))
    }

    @Test
    fun ordinaryDiskAndDraftRemainEditable() {
        val result = AiSecretRedactor.redactWorkspaceSnapshot(
            diskContent = "fun main() = Unit",
            draftContent = "fun main() = println(\"hello\")",
        )
        assertFalse(result.writeProtected)
        assertEquals("fun main() = println(\"hello\")", result.content)
    }
}

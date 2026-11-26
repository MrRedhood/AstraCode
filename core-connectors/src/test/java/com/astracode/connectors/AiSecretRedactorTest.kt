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
}

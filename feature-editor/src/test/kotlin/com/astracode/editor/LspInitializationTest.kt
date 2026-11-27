package com.astracode.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LspInitializationTest {
    @Test
    fun initializeRequestAdvertisesOnlyUtf8() {
        val request = LspInitializeParams(
            processId = 1234,
            rootUri = "file:///workspace/AstraCode",
        )
        val capabilities = request.toWireMap()["capabilities"] as Map<*, *>
        val general = capabilities["general"] as Map<*, *>

        assertEquals(listOf("utf-8"), general["positionEncodings"])
        val textDocument = capabilities["textDocument"] as Map<*, *>
        assertTrue(textDocument.containsKey("completion"))
        assertTrue(textDocument.containsKey("hover"))
        assertTrue(textDocument.containsKey("definition"))
        assertTrue(textDocument.containsKey("references"))
        assertTrue(textDocument.containsKey("rename"))
        assertTrue(textDocument.containsKey("publishDiagnostics"))
        val workspace = capabilities["workspace"] as Map<*, *>
        assertEquals(false, workspace["applyEdit"])
        assertEquals(true, (workspace["workspaceEdit"] as Map<*, *>)["documentChanges"])
        assertEquals(1234L, request.processId)
        assertTrue(request.toWireMap().containsKey("clientInfo"))
    }

    @Test
    fun serverExplicitlySelectingUtf8NegotiatesSuccessfully() {
        val negotiated = LspPositionEncodingNegotiator.negotiate(
            LspServerCapabilities(positionEncoding = "utf-8"),
        )
        assertEquals("utf-8", negotiated.positionEncoding)
    }

    @Test
    fun utf8SelectionIsCaseNormalized() {
        val negotiated = LspPositionEncodingNegotiator.negotiate(
            LspServerCapabilities(positionEncoding = " UTF-8 "),
        )
        assertEquals("utf-8", negotiated.positionEncoding)
    }

    @Test
    fun omittedServerEncodingIsRejectedBecauseLspDefaultsToUtf16() {
        val error = assertFailsWith<LspPositionEncodingException> {
            LspPositionEncodingNegotiator.negotiate(LspServerCapabilities())
        }
        assertTrue(error.message.orEmpty().contains("utf-16"))
    }

    @Test
    fun explicitUtf16ServerEncodingIsRejected() {
        assertFailsWith<LspPositionEncodingException> {
            LspPositionEncodingNegotiator.negotiate(
                LspServerCapabilities(positionEncoding = "utf-16"),
            )
        }
    }

    @Test
    fun unsupportedServerEncodingIsRejected() {
        assertFailsWith<LspPositionEncodingException> {
            LspPositionEncodingNegotiator.negotiate(
                LspServerCapabilities(positionEncoding = "utf-32"),
            )
        }
    }

    @Test
    fun initializeResultMustExplicitlyNegotiateUtf8() {
        val negotiated = LspPositionEncodingNegotiator.negotiateInitializeResult(
            mapOf("capabilities" to mapOf("positionEncoding" to "utf-8")),
        )
        assertEquals("utf-8", negotiated.positionEncoding)
        assertFailsWith<LspPositionEncodingException> {
            LspPositionEncodingNegotiator.negotiateInitializeResult(
                mapOf("capabilities" to emptyMap<String, Any?>()),
            )
        }
    }

    @Test
    fun initializeResultMustContainServerCapabilities() {
        assertFailsWith<LspPositionEncodingException> {
            LspPositionEncodingNegotiator.negotiateInitializeResult(emptyMap<String, Any?>())
        }
    }

    @Test
    fun clientCannotAdvertiseAnAdditionalFallbackEncoding() {
        assertFailsWith<IllegalArgumentException> {
            LspGeneralClientCapabilities(listOf("utf-8", "utf-16"))
        }
    }
}

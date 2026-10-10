package com.mrredhood.astracode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AiWorkspaceToolProtocolTest {
    private fun marker(json: String) =
        AiWorkspaceToolProtocol.START_MARKER + json + AiWorkspaceToolProtocol.END_MARKER

    @Test
    fun ordinaryAssistantTextIsNotTreatedAsAToolCall() {
        assertTrue(AiWorkspaceToolProtocol.parse("Let me explain the code.") is AiWorkspaceToolParseResult.NotToolCall)
    }

    @Test
    fun parsesAllowListedDirectoryAndFileReadRequests() {
        val list = AiWorkspaceToolProtocol.parse(
            marker("""{"name":"workspace_list","path":"app/src","reason":"Find source files"}""")
        ) as AiWorkspaceToolParseResult.Proposed
        assertEquals(AiWorkspaceToolName.LIST_DIRECTORY, list.proposal.name)
        assertEquals("app/src", list.proposal.path)

        val read = AiWorkspaceToolProtocol.parse(
            marker("""{"name":"workspace_read","path":"app/src/MainActivity.kt","reason":"Inspect navigation"}""")
        ) as AiWorkspaceToolParseResult.Proposed
        assertEquals(AiWorkspaceToolName.READ_TEXT_FILE, read.proposal.name)
        assertEquals("app/src/MainActivity.kt", read.proposal.path)
    }

    @Test
    fun rejectsWritesAndUnknownToolNames() {
        val result = AiWorkspaceToolProtocol.parse(
            marker("""{"name":"workspace_write","path":"README.md","reason":"Change the readme"}""")
        ) as AiWorkspaceToolParseResult.Invalid
        assertEquals(AiWorkspaceToolParseFailure.UNSUPPORTED_TOOL, result.failure)
    }

    @Test
    fun rejectsPathTraversalAbsolutePathsAndExcessiveDepth() {
        listOf("../secrets.txt", "src/../../secrets.txt", "/README.md", "src\\Main.kt", "a//b", "a/./b")
            .forEach { path ->
                assertTrue(path, !AiWorkspaceToolProtocol.isValidRelativePath(path, allowRoot = false))
            }
        val tooDeep = (1..AiWorkspaceToolProtocol.MAX_PATH_DEPTH + 1).joinToString("/") { "d$it" }
        assertTrue(!AiWorkspaceToolProtocol.isValidRelativePath(tooDeep, allowRoot = false))
        assertTrue(AiWorkspaceToolProtocol.isValidRelativePath("", allowRoot = true))
        assertTrue(!AiWorkspaceToolProtocol.isValidRelativePath("", allowRoot = false))
    }

    @Test
    fun requiresAnExactEnvelopeAndExactFields() {
        assertEquals(
            AiWorkspaceToolParseFailure.MALFORMED_ENVELOPE,
            (AiWorkspaceToolProtocol.parse(
                "Here you go " + marker("""{"name":"workspace_list","path":"","reason":"List"}""")
            ) as AiWorkspaceToolParseResult.Invalid).failure
        )
        assertEquals(
            AiWorkspaceToolParseFailure.UNKNOWN_FIELDS,
            (AiWorkspaceToolProtocol.parse(
                marker("""{"name":"workspace_list","path":"","reason":"List","command":"rm -rf"}""")
            ) as AiWorkspaceToolParseResult.Invalid).failure
        )
    }

    @Test
    fun rejectsMissingReasonAndBlankReason() {
        assertEquals(
            AiWorkspaceToolParseFailure.MALFORMED_JSON,
            (AiWorkspaceToolProtocol.parse(marker("""{"name":"workspace_list","path":""}"""))
                as AiWorkspaceToolParseResult.Invalid).failure
        )
        assertEquals(
            AiWorkspaceToolParseFailure.INVALID_REASON,
            (AiWorkspaceToolProtocol.parse(marker("""{"name":"workspace_list","path":"","reason":" "}"""))
                as AiWorkspaceToolParseResult.Invalid).failure
        )
    }

    @Test
    fun parserNeverAllowsAnOversizedEnvelope() {
        val longReason = "x".repeat(AiWorkspaceToolProtocol.MAX_ENVELOPE_CHARS + 10)
        val json = org.json.JSONObject().put("name", "workspace_list").put("path", "").put("reason", longReason)
        val result = AiWorkspaceToolProtocol.parse(marker(json.toString())) as AiWorkspaceToolParseResult.Invalid
        assertEquals(AiWorkspaceToolParseFailure.MALFORMED_ENVELOPE, result.failure)
    }
}

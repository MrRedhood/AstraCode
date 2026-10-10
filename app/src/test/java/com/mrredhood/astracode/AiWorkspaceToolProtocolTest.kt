package com.mrredhood.astracode

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
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
    fun parsesCreateFileProposalWithExactContentField() {
        val content = "package demo\n\nfun answer() = 42\n"
        val envelope = org.json.JSONObject()
            .put("name", "workspace_create_file")
            .put("path", "src/Answer.kt")
            .put("reason", "Add a small helper")
            .put("content", content)
        val proposal = AiWorkspaceToolProtocol.parse(marker(envelope.toString()))
            as AiWorkspaceToolParseResult.Proposed
        assertEquals(AiWorkspaceToolName.CREATE_TEXT_FILE, proposal.proposal.name)
        assertEquals("src/Answer.kt", proposal.proposal.path)
        assertEquals(content, proposal.proposal.content)
    }

    @Test
    fun createFileProtocolRejectsUnsafePathsBinaryNamesAndOversizedContent() {
        fun parse(path: String, content: String) = AiWorkspaceToolProtocol.parse(
            marker(
                org.json.JSONObject()
                    .put("name", "workspace_create_file")
                    .put("path", path)
                    .put("reason", "Create a file")
                    .put("content", content)
                    .toString()
            )
        ) as AiWorkspaceToolParseResult.Invalid

        assertEquals(AiWorkspaceToolParseFailure.INVALID_PATH, parse("../secret.kt", "").failure)
        assertEquals(AiWorkspaceToolParseFailure.UNSUPPORTED_FILE, parse("image.png", "").failure)
        assertEquals(
            AiWorkspaceToolParseFailure.INVALID_CONTENT,
            parse("src/Large.kt", "x".repeat(AiWorkspaceToolProtocol.MAX_CREATE_BYTES + 1)).failure
        )
        assertEquals(
            AiWorkspaceToolParseFailure.INVALID_CONTENT,
            parse("src/Unsafe.kt", "fun x() = 1\u0000").failure
        )
    }


    @Test
    fun approvalAndDeclineAuditRecordsBindToolPathReasonAndFileHash() {
        val content = "package demo\nfun answer() = 42\n"
        val proposal = AiWorkspaceToolProposal(
            AiWorkspaceToolName.CREATE_TEXT_FILE,
            "src/Answer.kt",
            "Add a small helper",
            content
        )
        val approved = AiWorkspaceToolAudit.approvalMessage(proposal).content
        val expectedHash = java.security.MessageDigest.getInstance("SHA-256")
            .digest(content.toByteArray(Charsets.UTF_8))
            .joinToString("") { byte -> "%02x".format(byte) }

        assertTrue(approved.contains("Decision: approved by the user before execution"))
        assertTrue(approved.contains("Tool: workspace_create_file"))
        assertTrue(approved.contains("Path: src/Answer.kt"))
        assertTrue(approved.contains("Reason: Add a small helper"))
        assertTrue(approved.contains("Proposed content SHA-256: " + expectedHash))
        assertFalse(approved.contains(content))

        val declined = AiWorkspaceToolAudit.declineMessage(proposal).content
        assertTrue(declined.contains("Decision: declined by the user"))
        assertTrue(declined.contains("Path: src/Answer.kt"))
        assertTrue(declined.contains("Reason: Add a small helper"))
        assertTrue(declined.contains("no workspace operation was run"))
    }

    @Test
    fun createFileProposalRejectsUnexpectedFields() {
        val envelope = org.json.JSONObject()
            .put("name", "workspace_create_file")
            .put("path", "src/New.kt")
            .put("reason", "Create a file")
            .put("content", "fun newFile() = true")
            .put("overwrite", true)
        val invalid = AiWorkspaceToolProtocol.parse(marker(envelope.toString()))
            as AiWorkspaceToolParseResult.Invalid
        assertEquals(AiWorkspaceToolParseFailure.UNKNOWN_FIELDS, invalid.failure)
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
    fun parserRejectsEnvelopeLengthOutsideTheConfiguredBound() {
        assertTrue(AiWorkspaceToolProtocol.isEnvelopeLengthAllowed(AiWorkspaceToolProtocol.MAX_ENVELOPE_CHARS))
        assertFalse(AiWorkspaceToolProtocol.isEnvelopeLengthAllowed(AiWorkspaceToolProtocol.MAX_ENVELOPE_CHARS + 1))
        assertFalse(AiWorkspaceToolProtocol.isEnvelopeLengthAllowed(0))
    }

    @Test
    fun acceptsFifteenMibCreateContentAndRejectsAnythingLarger() {
        assertEquals(15 * 1024 * 1024, AiWorkspaceToolProtocol.MAX_CREATE_BYTES)
        assertTrue(AiWorkspaceToolProtocol.MAX_ENVELOPE_CHARS >= 2 * AiWorkspaceToolProtocol.MAX_CREATE_BYTES + 4096)
        val maximum = "x".repeat(AiWorkspaceToolProtocol.MAX_CREATE_BYTES)
        assertTrue(AiWorkspaceToolProtocol.isValidCreateContent(maximum))
        assertFalse(AiWorkspaceToolProtocol.isValidCreateContent(maximum + "x"))
    }
}

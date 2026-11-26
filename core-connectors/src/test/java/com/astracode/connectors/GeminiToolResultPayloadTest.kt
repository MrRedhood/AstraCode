package com.astracode.connectors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.json.JSONArray

class GeminiToolResultPayloadTest {
    @Test
    fun serializesResultAsRestContentArrayWithMatchingCallIdAndName() {
        val payload = GeminiToolResultPayload.from(
            listOf(
                AiToolResult(
                    callId = "call-123",
                    name = "read_workspace_file",
                    content = """{"path":"src/Main.kt","content":"hello"}""",
                    succeeded = true,
                ),
            ),
        )
        val item = payload.getJSONObject(0)
        assertEquals("function_result", item.getString("type"))
        assertEquals("call-123", item.getString("call_id"))
        assertEquals("read_workspace_file", item.getString("name"))
        val result = item.getJSONArray("result")
        assertEquals(1, result.length())
        assertEquals("text", result.getJSONObject(0).getString("type"))
        assertTrue(result.getJSONObject(0).getString("text").contains("src/Main.kt"))
        assertTrue(item.opt("result") is JSONArray)
    }

    @Test
    fun marksFailedToolResultsAsErrors() {
        val payload = GeminiToolResultPayload.from(
            listOf(AiToolResult("call-err", "read_workspace_file", """{"error":"not found"}""", false)),
        )
        assertTrue(payload.getJSONObject(0).getBoolean("is_error"))
    }

    @Test
    fun successfulToolResultsAreNotMarkedAsErrors() {
        val payload = GeminiToolResultPayload.from(
            listOf(AiToolResult("call-ok", "list_workspace_files", """{"files":[]}""", true)),
        )
        assertFalse(payload.getJSONObject(0).optBoolean("is_error", false))
    }
}

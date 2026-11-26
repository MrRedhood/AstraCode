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
        val result = item.getJSONObject("result")
        val content = result.getJSONArray("content")
        assertEquals(1, content.length())
        assertEquals("text", content.getJSONObject(0).getString("type"))
        assertTrue(content.getJSONObject(0).getString("text").contains("src/Main.kt"))
        assertTrue(item.opt("result") is org.json.JSONObject)
    }

    @Test
    fun failedToolResultsCarryErrorTextInDocumentedResultContent() {
        val payload = GeminiToolResultPayload.from(
            listOf(AiToolResult("call-err", "read_workspace_file", """{"error":"not found"}""", false)),
        )
        val item = payload.getJSONObject(0)
        val text = item.getJSONObject("result")
            .getJSONArray("content")
            .getJSONObject(0)
            .getString("text")
        assertTrue(text.contains("not found"))
        assertFalse(item.has("is_error"))
    }

    @Test
    fun successfulToolResultsUseTheSameDocumentedResultShape() {
        val payload = GeminiToolResultPayload.from(
            listOf(AiToolResult("call-ok", "list_workspace_files", """{"files":[]}""", true)),
        )
        val item = payload.getJSONObject(0)
        assertTrue(item.getJSONObject("result").has("content"))
        assertFalse(item.has("is_error"))
    }
}

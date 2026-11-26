package com.astracode.connectors

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the Interactions API REST function_result payload.
 *
 * The current REST quickstart models result as a list of text content blocks.
 * Keep this conversion separate so it can be tested without making live calls.
 */
internal object GeminiToolResultPayload {
    fun from(results: List<AiToolResult>): JSONArray {
        val input = JSONArray()
        results.forEach { result ->
            input.put(
                JSONObject()
                    .put("type", "function_result")
                    .put("name", result.name)
                    .put("call_id", result.callId)
                    .put(
                        "result",
                        JSONArray().put(
                            JSONObject()
                                .put("type", "text")
                                .put("text", result.content),
                        ),
                    )
                    .put("is_error", !result.succeeded),
            )
        }
        return input
    }
}

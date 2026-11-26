package com.astracode.connectors

import org.json.JSONArray
import org.json.JSONObject

/**
 * Builds the Interactions API REST function_result payload.
 *
 * The REST streaming continuation shape is a result object containing a
 * content array of text blocks. Keep this conversion isolated and testable.
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
                        JSONObject().put(
                            "content",
                            JSONArray().put(
                                JSONObject()
                                    .put("type", "text")
                                    .put("text", result.content),
                            ),
                        ),
                    ),
            )
        }
        return input
    }
}

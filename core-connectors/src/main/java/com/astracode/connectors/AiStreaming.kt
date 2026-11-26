package com.astracode.connectors

import org.json.JSONObject
import java.io.BufferedReader
import java.io.IOException

sealed interface AiStreamEvent {
    data class TextDelta(val text: String, val agentId: String? = null) : AiStreamEvent
    data class Status(val message: String, val agentId: String? = null) : AiStreamEvent
    data class ToolStarted(val name: String, val agentId: String? = null) : AiStreamEvent
    data class ToolFinished(
        val name: String,
        val succeeded: Boolean,
        val agentId: String? = null,
    ) : AiStreamEvent
}

data class AiStreamToolCall(
    val id: String,
    val name: String,
    val arguments: JSONObject,
)

data class AiStreamResult(
    val text: String,
    val toolCalls: List<AiStreamToolCall>,
    val finalPayload: JSONObject?,
    val interactionId: String? = null,
)

/** Incrementally converts provider-specific SSE data events into common output. */
class AiStreamAccumulator(private val provider: AiProvider) {
    private data class ToolBuilder(
        var id: String = "",
        var name: String = "",
        val arguments: StringBuilder = StringBuilder(),
    )

    private val text = StringBuilder()
    private val tools = linkedMapOf<String, ToolBuilder>()
    private var finalPayload: JSONObject? = null
    private var currentAnthropicIndex = "0"
    private var geminiInteractionId = ""

    fun accept(data: String, onEvent: (AiStreamEvent) -> Unit = {}) {
        if (data.isBlank() || data == "[DONE]") return
        val json = try {
            JSONObject(data)
        } catch (error: Exception) {
            throw AiProviderProtocolException(
                provider.displayName + " stream returned malformed JSON",
                error,
            )
        }
        json.optJSONObject("error")?.let { error ->
            val code = error.optString("code").lowercase()
            val retryable = code.contains("rate") || code.contains("overloaded") ||
                code.contains("timeout") || code.contains("server") ||
                code.contains("temporarily_unavailable")
            throw AiProviderRequestException(
                error.optString("message", "The AI stream failed"),
                error.optInt("status").takeIf { it > 0 },
                retryable,
            )
        }

        when (provider) {
            AiProvider.OPENAI -> acceptOpenAi(json, onEvent)
            AiProvider.GOOGLE_GEMINI -> acceptGemini(json, onEvent)
            AiProvider.ANTHROPIC -> acceptAnthropic(json, onEvent)
            AiProvider.OPENROUTER, AiProvider.OPENAI_COMPATIBLE -> acceptChatCompletion(json, onEvent)
        }
    }

    fun result(): AiStreamResult {
        val parsed = tools.map { (key, builder) ->
            val args = try {
                if (builder.arguments.isBlank()) JSONObject() else JSONObject(builder.arguments.toString())
            } catch (error: Exception) {
                throw AiProviderProtocolException(
                    "The AI returned malformed arguments for tool " + builder.name,
                    error,
                )
            }
            AiStreamToolCall(
                id = builder.id.ifBlank { key },
                name = builder.name,
                arguments = args,
            )
        }
        return AiStreamResult(text.toString(), parsed, finalPayload, geminiInteractionId.ifBlank { null })
    }

    fun interactionId(): String = geminiInteractionId

    private fun acceptOpenAi(json: JSONObject, onEvent: (AiStreamEvent) -> Unit) {
        when (json.optString("type")) {
            "response.output_text.delta" -> appendText(json.optString("delta"), onEvent)
            "response.output_item.added", "response.output_item.done" -> {
                val item = json.optJSONObject("item") ?: return
                if (item.optString("type") == "function_call") {
                    val index = json.optInt("output_index", tools.size).toString()
                    val tool = tools.getOrPut(index) { ToolBuilder() }
                    tool.id = item.optString("call_id", item.optString("id", tool.id))
                    val previousName = tool.name
                    tool.name = item.optString("name", tool.name)
                    val arguments = item.optString("arguments")
                    if (arguments.isNotBlank() && tool.arguments.isEmpty()) {
                        appendToolArguments(tool, arguments)
                    }
                    if (previousName.isBlank() && tool.name.isNotBlank()) {
                        onEvent(AiStreamEvent.ToolStarted(tool.name))
                    }
                }
            }
            "response.function_call_arguments.delta" -> {
                val key = json.optInt("output_index", tools.size).toString()
                val tool = tools.getOrPut(key) { ToolBuilder(id = json.optString("item_id")) }
                appendToolArguments(tool, json.optString("delta"))
            }
            "response.completed", "response.incomplete" -> {
                finalPayload = json.optJSONObject("response")
            }
            "error" -> throw streamError(json, "OpenAI streaming failed")
        }
    }

    private fun acceptGemini(json: JSONObject, onEvent: (AiStreamEvent) -> Unit) {
        when (json.optString("event_type")) {
            "interaction.created" -> {
                geminiInteractionId = json.optJSONObject("interaction")?.optString("id").orEmpty()
            }
            "step.start" -> {
                val step = json.optJSONObject("step") ?: return
                if (step.optString("type") == "function_call") {
                    val index = json.optInt("index", tools.size).toString()
                    val tool = tools.getOrPut(index) { ToolBuilder() }
                    tool.id = step.optString("id", tool.id)
                    tool.name = step.optString("name", tool.name)
                    step.optJSONObject("arguments")?.let { arguments ->
                        if (arguments.length() > 0) appendToolArguments(tool, arguments.toString())
                    }
                    if (tool.name.isNotBlank()) onEvent(AiStreamEvent.ToolStarted(tool.name))
                }
            }
            "step.delta" -> {
                val delta = json.optJSONObject("delta") ?: return
                when (delta.optString("type")) {
                    "text" -> appendText(delta.optString("text"), onEvent)
                    "arguments_delta" -> {
                        val key = json.optInt("index", tools.size).toString()
                        val tool = tools.getOrPut(key) { ToolBuilder() }
                        appendToolArguments(tool, delta.optString("arguments"))
                    }
                }
            }
            "interaction.completed" -> {
                finalPayload = json.optJSONObject("interaction")
                if (geminiInteractionId.isBlank()) {
                    geminiInteractionId = finalPayload?.optString("id").orEmpty()
                }
            }
            "error" -> throw streamError(json, "Gemini streaming failed")
        }
    }

    private fun acceptAnthropic(json: JSONObject, onEvent: (AiStreamEvent) -> Unit) {
        when (json.optString("type")) {
            "content_block_start" -> {
                val index = json.optInt("index", 0).toString()
                currentAnthropicIndex = index
                val block = json.optJSONObject("content_block") ?: return
                if (block.optString("type") == "tool_use") {
                    val tool = tools.getOrPut(index) { ToolBuilder() }
                    tool.id = block.optString("id", tool.id)
                    tool.name = block.optString("name", tool.name)
                    block.optJSONObject("input")?.let {
                        if (it.length() > 0) appendToolArguments(tool, it.toString())
                    }
                    onEvent(AiStreamEvent.ToolStarted(tool.name))
                }
            }
            "content_block_delta" -> {
                val index = json.optInt("index", currentAnthropicIndex.toIntOrNull() ?: 0).toString()
                val delta = json.optJSONObject("delta") ?: return
                when (delta.optString("type")) {
                    "text_delta" -> appendText(delta.optString("text"), onEvent)
                    "input_json_delta" -> {
                        val tool = tools.getOrPut(index) { ToolBuilder() }
                        appendToolArguments(tool, delta.optString("partial_json"))
                    }
                }
            }
            "message_delta" -> {
                finalPayload = JSONObject()
                    .put(
                        "status",
                        if (json.optJSONObject("delta")?.optString("stop_reason") == "max_tokens") {
                            "incomplete"
                        } else {
                            "completed"
                        },
                    )
                    .put("stop_reason", json.optJSONObject("delta")?.optString("stop_reason").orEmpty())
            }
            "error" -> throw streamError(json, "Anthropic streaming failed")
        }
    }

    private fun acceptChatCompletion(json: JSONObject, onEvent: (AiStreamEvent) -> Unit) {
        val choices = json.optJSONArray("choices")
        val choice = choices?.optJSONObject(0) ?: return
        val finishReason = choice.optString("finish_reason")
        if (finishReason.isNotBlank() && finishReason != "null") {
            finalPayload = JSONObject().put(
                "status",
                if (finishReason == "length" || finishReason == "content_filter") "incomplete" else "completed",
            ).put("finish_reason", finishReason)
        }
        val delta = choice.optJSONObject("delta") ?: return
        val content = delta.opt("content")
        when (content) {
            is String -> appendText(content, onEvent)
            is org.json.JSONArray -> {
                for (i in 0 until content.length()) {
                    val part = content.optJSONObject(i) ?: continue
                    if (part.optString("type") == "text") appendText(part.optString("text"), onEvent)
                }
            }
        }
        val calls = delta.optJSONArray("tool_calls") ?: return
        for (i in 0 until calls.length()) {
            val call = calls.optJSONObject(i) ?: continue
            val index = call.optInt("index", i).toString()
            val builder = tools.getOrPut(index) { ToolBuilder() }
            if (call.has("id")) builder.id = call.optString("id")
            val function = call.optJSONObject("function") ?: continue
            if (function.has("name")) {
                builder.name = function.optString("name")
                onEvent(AiStreamEvent.ToolStarted(builder.name))
            }
            if (function.has("arguments")) {
                appendToolArguments(builder, function.optString("arguments"))
            }
        }
    }

    private fun streamError(json: JSONObject, fallbackMessage: String): AiProviderRequestException {
        val error = json.optJSONObject("error")
        val message = error?.optString("message")?.takeIf { it.isNotBlank() }
            ?: json.optString("message").takeIf { it.isNotBlank() }
            ?: fallbackMessage
        val code = listOf(
            error?.optString("type").orEmpty(),
            error?.optString("code").orEmpty(),
            json.optString("code"),
            message,
        ).joinToString(" ").lowercase()
        val retryable = listOf(
            "rate_limit",
            "rate limit",
            "overloaded",
            "timeout",
            "temporarily unavailable",
            "temporarily_unavailable",
            "internal server",
            "server_error",
            "service unavailable",
        ).any { it in code }
        return AiProviderRequestException(message, null, retryable)
    }

    private fun appendText(value: String, onEvent: (AiStreamEvent) -> Unit) {
        if (value.isEmpty()) return
        if (text.length + value.length > MAX_STREAM_TEXT_CHARS) {
            throw AiProviderProtocolException("The AI stream exceeded the output size limit")
        }
        text.append(value)
        onEvent(AiStreamEvent.TextDelta(value))
    }

    private fun appendToolArguments(builder: ToolBuilder, value: String) {
        if (builder.arguments.length + value.length > MAX_TOOL_ARGUMENT_CHARS) {
            throw AiProviderProtocolException("The AI tool arguments exceeded the size limit")
        }
        builder.arguments.append(value)
    }

    private companion object {
        const val MAX_STREAM_TEXT_CHARS = 2 * 1024 * 1024
        const val MAX_TOOL_ARGUMENT_CHARS = 128 * 1024
    }
}

/**
 * Reads standard SSE frames. Multiple data lines in a frame are joined with
 * newlines, comments are ignored, and an unterminated final frame is delivered.
 */
object AiSseReader {
    fun read(
        reader: BufferedReader,
        accumulator: AiStreamAccumulator,
        onEvent: (AiStreamEvent) -> Unit = {},
    ) {
        val dataLines = mutableListOf<String>()
        var frameCharacters = 0
        var totalCharacters = 0L
        while (true) {
            val line = readBoundedLine(reader, MAX_SSE_LINE_CHARS) ?: break
            totalCharacters += line.length.toLong()
            if (totalCharacters > MAX_SSE_STREAM_CHARS) {
                throw AiProviderProtocolException("The AI event stream exceeded the size limit")
            }
            if (line.isEmpty()) {
                dispatch(dataLines, accumulator, onEvent)
                dataLines.clear()
                frameCharacters = 0
                continue
            }
            if (line.startsWith(":")) continue
            if (line.startsWith("data:")) {
                val data = line.substring(5).trimStart()
                frameCharacters += data.length + 1
                if (frameCharacters > MAX_SSE_FRAME_CHARS) {
                    throw AiProviderProtocolException("An AI event exceeded the size limit")
                }
                dataLines += data
            }
        }
        dispatch(dataLines, accumulator, onEvent)
    }

    /**
     * BufferedReader.readLine() can allocate an unbounded line before returning.
     * Read incrementally so a faulty provider cannot force an arbitrarily large
     * allocation by omitting SSE newlines.
     */
    private fun readBoundedLine(reader: BufferedReader, maximumCharacters: Int): String? {
        val line = StringBuilder()
        var sawCharacter = false
        while (true) {
            val value = reader.read()
            if (value < 0) return if (sawCharacter) line.toString() else null
            if (value == '\n'.code) return line.toString().removeSuffix("\r")
            sawCharacter = true
            if (line.length >= maximumCharacters) {
                throw AiProviderProtocolException("An SSE line exceeded the size limit")
            }
            line.append(value.toChar())
        }
    }

    private fun dispatch(
        dataLines: List<String>,
        accumulator: AiStreamAccumulator,
        onEvent: (AiStreamEvent) -> Unit,
    ) {
        if (dataLines.isEmpty()) return
        accumulator.accept(dataLines.joinToString("\n"), onEvent)
    }

    private const val MAX_SSE_LINE_CHARS = 2 * 1024 * 1024
    private const val MAX_SSE_FRAME_CHARS = 2 * 1024 * 1024
    private const val MAX_SSE_STREAM_CHARS = 8L * 1024L * 1024L
}

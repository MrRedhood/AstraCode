package com.astracode.connectors

import java.io.BufferedReader
import java.io.StringReader
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.json.JSONObject

class AiStreamingTest {
    @Test
    fun parsesOpenAiTextAndFragmentedFunctionArguments() {
        val accumulator = AiStreamAccumulator(AiProvider.OPENAI)
        accumulator.accept("""{"type":"response.output_text.delta","delta":"Working"}""")
        accumulator.accept(
            """{"type":"response.output_item.added","output_index":0,"item":{"type":"function_call","id":"item-1","call_id":"call-1","name":"read_workspace_file","arguments":""}}""",
        )
        accumulator.accept(
            """{"type":"response.function_call_arguments.delta","output_index":0,"item_id":"item-1","delta":"{\"path\":\"src/Main.kt\"}"}""",
        )
        accumulator.accept(
            """{"type":"response.output_item.done","output_index":0,"item":{"type":"function_call","id":"item-1","call_id":"call-1","name":"read_workspace_file","arguments":"{\"path\":\"src/Main.kt\"}"}}""",
        )
        val result = accumulator.result()
        assertEquals("Working", result.text)
        assertEquals("call-1", result.toolCalls.single().id)
        assertEquals("src/Main.kt", result.toolCalls.single().arguments.optString("path"))
    }

    @Test
    fun openAiFailedResponsePreservesRetryableServerError() {
        val accumulator = AiStreamAccumulator(AiProvider.OPENAI)
        val error = kotlin.test.assertFailsWith<AiProviderRequestException> {
            accumulator.accept(
                """{"type":"response.failed","response":{"status":"failed","error":{"code":"server_error","message":"Temporary server issue"}}}""",
            )
        }
        assertTrue(error.retryable)
        assertEquals("Temporary server issue", error.message)
    }

    @Test
    fun openAiFailedResponseDoesNotRetryInvalidRequest() {
        val accumulator = AiStreamAccumulator(AiProvider.OPENAI)
        val error = kotlin.test.assertFailsWith<AiProviderRequestException> {
            accumulator.accept(
                """{"type":"response.failed","response":{"status":"failed","error":{"code":"invalid_prompt","message":"Invalid prompt"}}}""",
            )
        }
        assertTrue(!error.retryable)
    }

    @Test
    fun parsesGeminiFunctionCallAndContinuationId() {
        val accumulator = AiStreamAccumulator(AiProvider.GOOGLE_GEMINI)
        accumulator.accept(
            """{"event_type":"interaction.created","interaction":{"id":"interaction-1"}}""",
        )
        accumulator.accept(
            """{"event_type":"step.start","index":0,"step":{"type":"function_call","id":"call-1","name":"search_workspace_files","arguments":{}}}""",
        )
        accumulator.accept(
            """{"event_type":"step.delta","index":0,"delta":{"type":"arguments_delta","arguments":"{\"query\":\"hello\"}"}}""",
        )
        accumulator.accept(
            """{"event_type":"interaction.completed","interaction":{"id":"interaction-1","status":"requires_action"}}""",
        )
        val result = accumulator.result()
        assertEquals("interaction-1", result.interactionId)
        assertEquals("search_workspace_files", result.toolCalls.single().name)
        assertEquals("hello", result.toolCalls.single().arguments.optString("query"))
    }

    @Test
    fun parsesAnthropicTextAndToolUseBlocks() {
        val accumulator = AiStreamAccumulator(AiProvider.ANTHROPIC)
        accumulator.accept(
            """{"type":"content_block_start","index":0,"content_block":{"type":"tool_use","id":"tool-1","name":"read_workspace_file","input":{}}}""",
        )
        accumulator.accept(
            """{"type":"content_block_delta","index":0,"delta":{"type":"input_json_delta","partial_json":"{\"path\":\"src/A.kt\"}"}}""",
        )
        accumulator.accept(
            """{"type":"content_block_start","index":1,"content_block":{"type":"text","text":""}}""",
        )
        accumulator.accept(
            """{"type":"content_block_delta","index":1,"delta":{"type":"text_delta","text":"Finished"}}""",
        )
        val result = accumulator.result()
        assertEquals("Finished", result.text)
        assertEquals("src/A.kt", result.toolCalls.single().arguments.optString("path"))
    }

    @Test
    fun parsesChatCompletionsToolCallFragments() {
        val accumulator = AiStreamAccumulator(AiProvider.OPENROUTER)
        accumulator.accept(
            """{"choices":[{"delta":{"tool_calls":[{"index":0,"id":"call-2","type":"function","function":{"name":"list_workspace_files","arguments":"{}"}}]}}]}""",
        )
        val result = accumulator.result()
        assertEquals("call-2", result.toolCalls.single().id)
        assertEquals("list_workspace_files", result.toolCalls.single().name)
        assertEquals(0, result.toolCalls.single().arguments.length())
    }

    @Test
    fun parsesOpenAiUsageAndCachedInputTokens() {
        val accumulator = AiStreamAccumulator(AiProvider.OPENAI)
        accumulator.accept(
            """{"type":"response.completed","response":{"status":"completed","usage":{"input_tokens":100,"output_tokens":20,"total_tokens":120,"input_tokens_details":{"cached_tokens":30}}}}""",
        )
        val usage = accumulator.result().usage ?: error("Expected OpenAI usage")
        assertEquals(100L, usage.inputTokens)
        assertEquals(20L, usage.outputTokens)
        assertEquals(120L, usage.totalTokens)
        assertEquals(30L, usage.cachedInputTokens)
    }

    @Test
    fun parsesGeminiInteractionsUsage() {
        val accumulator = AiStreamAccumulator(AiProvider.GOOGLE_GEMINI)
        accumulator.accept(
            """{"event_type":"interaction.completed","interaction":{"id":"usage-1","status":"completed","usage":{"total_input_tokens":80,"total_output_tokens":15,"total_tokens":95,"total_cached_tokens":12}}}""",
        )
        val usage = accumulator.result().usage ?: error("Expected Gemini usage")
        assertEquals(80L, usage.inputTokens)
        assertEquals(15L, usage.outputTokens)
        assertEquals(95L, usage.totalTokens)
        assertEquals(12L, usage.cachedInputTokens)
    }

    @Test
    fun accumulatesAnthropicMessageStartAndDeltaUsage() {
        val accumulator = AiStreamAccumulator(AiProvider.ANTHROPIC)
        accumulator.accept(
            """{"type":"message_start","message":{"usage":{"input_tokens":40,"cache_read_input_tokens":8,"cache_creation_input_tokens":2}}}""",
        )
        accumulator.accept(
            """{"type":"message_delta","delta":{"stop_reason":"end_turn"},"usage":{"output_tokens":10}}""",
        )
        val usage = accumulator.result().usage ?: error("Expected Anthropic usage")
        assertEquals(40L, usage.inputTokens)
        assertEquals(10L, usage.outputTokens)
        assertEquals(50L, usage.totalTokens)
        assertEquals(10L, usage.cachedInputTokens)
    }

    @Test
    fun parsesOpenRouterUsageChunkWithEmptyChoices() {
        val accumulator = AiStreamAccumulator(AiProvider.OPENROUTER)
        accumulator.accept(
            """{"choices":[{"delta":{"content":"Done"},"finish_reason":"stop"}]}""",
        )
        accumulator.accept(
            """{"choices":[],"usage":{"prompt_tokens":25,"completion_tokens":7,"total_tokens":32,"prompt_tokens_details":{"cached_tokens":5}}}""",
        )
        val result = accumulator.result()
        val usage = result.usage ?: error("Expected OpenRouter usage")
        assertEquals(25L, usage.inputTokens)
        assertEquals(7L, usage.outputTokens)
        assertEquals(32L, usage.totalTokens)
        assertEquals(5L, usage.cachedInputTokens)
    }

    @Test
    fun tokenUsageCostUsesPublishedCacheReadPriceOnlyWhenAvailable() {
        val usage = AiTokenUsage(
            inputTokens = 100,
            outputTokens = 20,
            cachedInputTokens = 50,
        )
        val pricing = AiModelPricing(
            inputUsdPerMillionTokens = 1.0,
            outputUsdPerMillionTokens = 2.0,
            cachedInputUsdPerMillionTokens = 0.1,
        )
        assertEquals(0.000095, usage.withPricing(pricing).estimatedCostUsd!!, 0.0000000001)
        assertEquals(
            null,
            usage.withPricing(
                AiModelPricing(
                    inputUsdPerMillionTokens = 1.0,
                    outputUsdPerMillionTokens = 2.0,
                ),
            ).estimatedCostUsd,
        )
    }

    @Test
    fun tokenUsageAggregationDoesNotInventMissingMetricsOrPrice() {
        val first = AiTokenUsage(
            inputTokens = 10,
            outputTokens = 5,
            totalTokens = 15,
            cachedInputTokens = 2,
            estimatedCostUsd = 0.01,
        )
        val second = AiTokenUsage(
            inputTokens = 20,
            outputTokens = 8,
            totalTokens = 28,
            cachedInputTokens = 4,
            estimatedCostUsd = 0.02,
        )
        val total = first.plus(second)
        assertEquals(30L, total.inputTokens)
        assertEquals(13L, total.outputTokens)
        assertEquals(43L, total.totalTokens)
        assertEquals(6L, total.cachedInputTokens)
        assertEquals(0.03, total.estimatedCostUsd!!, 0.0000000001)

        val unknownCost = first.plus(second.copy(estimatedCostUsd = null))
        assertEquals(null, unknownCost.estimatedCostUsd)
    }

    @Test
    fun sseReaderRejectsAnUnboundedLine() {
        val input = BufferedReader(StringReader("data: " + "x".repeat(2 * 1024 * 1024 + 1)))
        val accumulator = AiStreamAccumulator(AiProvider.OPENAI)
        kotlin.test.assertFailsWith<AiProviderProtocolException> {
            AiSseReader.read(input, accumulator)
        }
    }


    @Test
    fun gatewayTimeoutEventIsMarkedRetryable() {
        val accumulator = AiStreamAccumulator(AiProvider.GOOGLE_GEMINI)
        val error = kotlin.test.assertFailsWith<AiProviderRequestException> {
            accumulator.accept(
                """{"event_type":"error","error":{"code":"gateway_timeout","message":"Gateway timeout"}}""",
            )
        }
        assertTrue(error.retryable)
    }

    @Test
    fun malformedStreamJsonIsNotAProviderAvailabilityFailure() {
        val accumulator = AiStreamAccumulator(AiProvider.OPENAI)
        kotlin.test.assertFailsWith<AiProviderProtocolException> {
            accumulator.accept("{not-json")
        }
    }

    @Test
    fun sseReaderCombinesMultipleDataLinesAndIgnoresComments() {
        val input = BufferedReader(
            StringReader(
                ": keep-alive\n" +
                    "data: {\"type\":\"response.output_text.delta\",\n" +
                    "data: \"delta\":\"Hello\"}\n\n" +
                    "data: {\"type\":\"response.output_text.delta\",\"delta\":\"!\"}\n\n",
            ),
        )
        val accumulator = AiStreamAccumulator(AiProvider.OPENAI)
        AiSseReader.read(input, accumulator)
        assertEquals("Hello!", accumulator.result().text)
        assertTrue(accumulator.result().toolCalls.isEmpty())
    }
}

package com.astracode.connectors

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.json.JSONObject

class AiToolRegistryTest {
    private val registry = AiToolRegistry(
        listOf(
            WorkspaceFileSnapshot("src/Main.kt", "fun main() = println(\"hello\")"),
            WorkspaceFileSnapshot("src/Other.kt", "class Other"),
        ),
    )

    @Test
    fun listsOnlyFilesInTheApprovedContext() {
        val result = registry.execute(AiToolCall("1", "list_workspace_files", JSONObject()))
        assertTrue(result.succeeded)
        assertTrue(result.content.contains("src/Main.kt"))
        assertTrue(result.content.contains("src/Other.kt"))
        assertFalse(result.content.contains("secret.env"))
    }

    @Test
    fun readsOnlyExactApprovedPath() {
        val valid = registry.execute(
            AiToolCall("2", "read_workspace_file", JSONObject().put("path", "src/Main.kt")),
        )
        assertTrue(valid.succeeded)
        assertTrue(valid.content.contains("println"))

        val outside = registry.execute(
            AiToolCall("3", "read_workspace_file", JSONObject().put("path", "../secret.env")),
        )
        assertFalse(outside.succeeded)
    }

    @Test
    fun searchReturnsBoundedMatchingLines() {
        val result = registry.execute(
            AiToolCall("4", "search_workspace_files", JSONObject().put("query", "println")),
        )
        assertTrue(result.succeeded)
        assertTrue(result.content.contains("src/Main.kt"))
        assertEquals(1, JSONObject(result.content).getJSONArray("matches").length())
    }

    @Test
    fun exactlyFortySearchMatchesAreNotReportedAsTruncated() {
        val lines = (1..40).joinToString("\n") { "needle $it" }
        val onlyForty = AiToolRegistry(listOf(WorkspaceFileSnapshot("src/Only.kt", lines)))
            .execute(AiToolCall("forty", "search_workspace_files", JSONObject().put("query", "needle")))
        val fortyJson = JSONObject(onlyForty.content)
        assertEquals(40, fortyJson.getJSONArray("matches").length())
        assertFalse(fortyJson.getBoolean("truncated"))
    }

    @Test
    fun moreThanFortySearchMatchesAreTruncated() {
        val lines = (1..41).joinToString("\n") { "needle $it" }
        val moreThanForty = AiToolRegistry(listOf(WorkspaceFileSnapshot("src/Many.kt", lines)))
            .execute(AiToolCall("many", "search_workspace_files", JSONObject().put("query", "needle")))
        val manyJson = JSONObject(moreThanForty.content)
        assertEquals(40, manyJson.getJSONArray("matches").length())
        assertTrue(manyJson.getBoolean("truncated"))
    }

    @Test
    fun profileCanExposeOnlyAnExplicitToolSubset() {
        val restricted = AiToolRegistry(
            listOf(WorkspaceFileSnapshot("src/Main.kt", "class Main")),
            setOf("list_workspace_files"),
        )
        assertEquals(listOf("list_workspace_files"), restricted.definitions.map { it.name })
        assertTrue(
            restricted.execute(AiToolCall("list", "list_workspace_files", JSONObject())).succeeded,
        )
        assertFalse(
            restricted.execute(
                AiToolCall("read", "read_workspace_file", JSONObject().put("path", "src/Main.kt")),
            ).succeeded,
        )
    }

    @Test
    fun profileCannotDeclareAnUnknownTool() {
        assertFailsWith<IllegalArgumentException> {
            AiToolRegistry(emptyList(), setOf("run_shell"))
        }
    }

    @Test
    fun rejectsUnknownToolsAndUnexpectedArguments() {
        assertFalse(registry.execute(AiToolCall("5", "run_shell", JSONObject())).succeeded)
        assertFalse(
            registry.execute(
                AiToolCall(
                    "6",
                    "read_workspace_file",
                    JSONObject().put("path", "src/Main.kt").put("write", true),
                ),
            ).succeeded,
        )
    }
}

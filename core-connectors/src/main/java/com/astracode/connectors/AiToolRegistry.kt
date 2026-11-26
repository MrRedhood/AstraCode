package com.astracode.connectors

import org.json.JSONArray
import org.json.JSONObject

enum class AiToolPermission {
    READ_WORKSPACE_CONTEXT,
}

data class AiToolDefinition(
    val name: String,
    val description: String,
    val permission: AiToolPermission,
    val parameters: JSONObject,
)

data class AiToolCall(
    val id: String,
    val name: String,
    val arguments: JSONObject,
)

data class AiToolResult(
    val callId: String,
    val name: String,
    val content: String,
    val succeeded: Boolean,
)

/**
 * The model receives only read-only tools over the bounded context that the
 * user already selected for this request. No shell, network, writes or deletes.
 */
class AiToolRegistry(files: List<WorkspaceFileSnapshot>) {
    private val snapshots = files.associateBy { it.path }

    val definitions: List<AiToolDefinition> = listOf(
        AiToolDefinition(
            name = "list_workspace_files",
            description = "List file paths already included in this request's safe workspace context.",
            permission = AiToolPermission.READ_WORKSPACE_CONTEXT,
            parameters = objectSchema(JSONObject(), emptyList()),
        ),
        AiToolDefinition(
            name = "read_workspace_file",
            description = "Read a file from the safe workspace context by its exact workspace-relative path.",
            permission = AiToolPermission.READ_WORKSPACE_CONTEXT,
            parameters = objectSchema(
                JSONObject().put(
                    "path",
                    JSONObject().put("type", "string").put("description", "Exact workspace-relative file path"),
                ),
                listOf("path"),
            ),
        ),
        AiToolDefinition(
            name = "search_workspace_files",
            description = "Search text inside the safe workspace context. Returns a limited set of matching lines.",
            permission = AiToolPermission.READ_WORKSPACE_CONTEXT,
            parameters = objectSchema(
                JSONObject().put(
                    "query",
                    JSONObject().put("type", "string").put("description", "Text to search for"),
                ),
                listOf("query"),
            ),
        ),
    )

    fun execute(call: AiToolCall): AiToolResult {
        return try {
            val result = when (call.name) {
                "list_workspace_files" -> listFiles()
                "read_workspace_file" -> readFile(call.arguments)
                "search_workspace_files" -> searchFiles(call.arguments)
                else -> throw IllegalArgumentException("This tool is not available")
            }
            AiToolResult(call.id, call.name, result, true)
        } catch (error: Exception) {
            AiToolResult(
                call.id,
                call.name,
                JSONObject().put("error", error.message ?: "Tool execution failed").toString(),
                false,
            )
        }
    }

    private fun listFiles(): String {
        val paths = JSONArray()
        snapshots.keys.sorted().take(MAX_LISTED_PATHS).forEach { paths.put(it) }
        return JSONObject()
            .put("files", paths)
            .put("truncated", snapshots.size > MAX_LISTED_PATHS)
            .toString()
    }

    private fun readFile(arguments: JSONObject): String {
        requireExactKeys(arguments, setOf("path"))
        val path = arguments.optString("path")
        validateWorkspacePath(path)
        val file = snapshots[path] ?: throw IllegalArgumentException(
            "That file is outside the approved context or was not included in this request",
        )
        val bytes = file.content.toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_TOOL_FILE_BYTES) { "The requested file exceeds the tool output limit" }
        return JSONObject().put("path", path).put("content", file.content).toString()
    }

    private fun searchFiles(arguments: JSONObject): String {
        requireExactKeys(arguments, setOf("query"))
        val query = arguments.optString("query").trim()
        require(query.length in 2..MAX_SEARCH_QUERY_LENGTH) {
            "Search query must contain 2 to " + MAX_SEARCH_QUERY_LENGTH + " characters"
        }
        val results = JSONArray()
        var count = 0
        snapshots.values.sortedBy { it.path }.forEach { file ->
            file.content.lineSequence().forEachIndexed { lineNumber, line ->
                if (line.contains(query, ignoreCase = true) && count < MAX_SEARCH_RESULTS) {
                    results.put(
                        JSONObject()
                            .put("path", file.path)
                            .put("line", lineNumber + 1)
                            .put("text", line.take(MAX_RESULT_LINE_CHARS)),
                    )
                    count++
                }
            }
        }
        return JSONObject()
            .put("query", query)
            .put("matches", results)
            .put("truncated", count == MAX_SEARCH_RESULTS)
            .toString()
    }

    private fun validateWorkspacePath(path: String) {
        require(path.isNotBlank() && path.length <= MAX_PATH_CHARS)
        require(!path.startsWith("/") && !path.contains('\\') && !path.contains('\u0000'))
        require(path.split('/').none { it.isBlank() || it == "." || it == ".." })
        require(!path.split('/').any { it.equals(".git", true) || it.startsWith(".astracode", true) })
    }

    private fun requireExactKeys(value: JSONObject, expected: Set<String>) {
        val keys = value.keys().asSequence().toSet()
        require(keys == expected) { "Tool arguments do not match the declared schema" }
    }

    private fun objectSchema(properties: JSONObject, required: List<String>): JSONObject {
        val requiredArray = JSONArray()
        required.forEach { requiredArray.put(it) }
        return JSONObject()
            .put("type", "object")
            .put("properties", properties)
            .put("required", requiredArray)
            .put("additionalProperties", false)
    }

    private companion object {
        const val MAX_LISTED_PATHS = 200
        const val MAX_TOOL_FILE_BYTES = 64 * 1024
        const val MAX_SEARCH_QUERY_LENGTH = 200
        const val MAX_SEARCH_RESULTS = 40
        const val MAX_RESULT_LINE_CHARS = 500
        const val MAX_PATH_CHARS = 512
    }
}

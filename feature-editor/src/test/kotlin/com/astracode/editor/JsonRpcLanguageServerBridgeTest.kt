package com.astracode.editor

import java.io.Closeable
import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class JsonRpcLanguageServerBridgeTest {
    private class FakeEndpoint {
        data class Request(val method: String, val params: Any?, val future: CompletableFuture<Any?>)
        data class Notification(val method: String, val params: Any?)

        val requests = mutableListOf<Request>()
        val notifications = mutableListOf<Notification>()
        var notificationHandler: ((String, Map<String, Any?>) -> Unit)? = null

        fun request(method: String, params: Any?): CompletableFuture<Any?> =
            CompletableFuture<Any?>().also { requests += Request(method, params, it) }

        fun notify(method: String, params: Any?) {
            notifications += Notification(method, params)
        }

        fun subscribe(handler: (String, Map<String, Any?>) -> Unit): Closeable {
            notificationHandler = handler
            return Closeable { notificationHandler = null }
        }

        fun bridge() = JsonRpcLanguageServerBridge(::request, ::notify, ::subscribe)

        fun publish(method: String, params: Map<String, Any?>) {
            notificationHandler?.invoke(method, params)
        }
    }

    private fun map(value: Any?): Map<*, *> = value as Map<*, *>

    @Test
    fun opensAndChangesDocumentsUsingUtf8Positions() {
        val endpoint = FakeEndpoint()
        val bridge = endpoint.bridge()
        val uri = "file:///workspace/Main.kt"
        bridge.openDocument(EditorDocument(uri, "kotlin", 1, "Aह😀B"))

        assertEquals("textDocument/didOpen", endpoint.notifications[0].method)
        val openParams = map(endpoint.notifications[0].params)
        val openDocument = map(openParams["textDocument"])
        assertEquals("Aह😀B", openDocument["text"])

        bridge.changeDocument(uri, 2, listOf(EditorTextChange(1, 4, "नम")))
        assertEquals("textDocument/didChange", endpoint.notifications[1].method)
        val changeParams = map(endpoint.notifications[1].params)
        val contentChange = (changeParams["contentChanges"] as List<*>).single()
        val range = map(map(contentChange)["range"])
        assertEquals(mapOf("line" to 0, "character" to 1), map(range["start"]))
        assertEquals(mapOf("line" to 0, "character" to 4), map(range["end"]))
        assertEquals(3, map(contentChange)["rangeLength"])
        assertEquals("नम", map(contentChange)["text"])

        bridge.changeDocument(uri, 3, listOf(EditorTextChange(7, 11, "🌍")))
        val secondRange = map(map((map(endpoint.notifications[2].params)["contentChanges"] as List<*>).single())["range"])
        assertEquals(mapOf("line" to 0, "character" to 7), map(secondRange["start"]))
        assertEquals(mapOf("line" to 0, "character" to 11), map(secondRange["end"]))
        bridge.close()
    }

    @Test
    fun rejectsInvalidUtf8RangesWithoutChangingDocumentVersion() {
        val endpoint = FakeEndpoint()
        val bridge = endpoint.bridge()
        val uri = "file:///workspace/Main.kt"
        bridge.openDocument(EditorDocument(uri, "kotlin", 1, "A😀B"))

        assertFailsWith<IllegalArgumentException> {
            bridge.changeDocument(uri, 2, listOf(EditorTextChange(2, 3, "x")))
        }
        assertFailsWith<IllegalArgumentException> {
            bridge.changeDocument(uri, 2, listOf(EditorTextChange(1, 7, "x")))
        }
        bridge.changeDocument(uri, 2, listOf(EditorTextChange(1, 5, "X")))
        assertEquals(2, map(map(endpoint.notifications.last().params)["textDocument"])["version"])
        bridge.close()
    }

    @Test
    fun completionHoverAndDefinitionUseCurrentDocumentVersion() {
        val endpoint = FakeEndpoint()
        val bridge = endpoint.bridge()
        val uri = "file:///workspace/Main.kt"
        bridge.openDocument(EditorDocument(uri, "kotlin", 4, "fun main() = Unit"))

        var completions: Result<List<String>>? = null
        bridge.requestCompletion(uri, 4, 4) { completions = it }
        val completionRequest = endpoint.requests.last()
        assertEquals("textDocument/completion", completionRequest.method)
        assertEquals(mapOf("line" to 0, "character" to 4), map(map(completionRequest.params)["position"]))
        completionRequest.future.complete(
            mapOf("items" to listOf(mapOf("label" to "println"), mapOf("label" to "print"))),
        )
        assertEquals(listOf("println", "print"), completions?.getOrThrow())

        var hover: Result<String?>? = null
        bridge.requestHover(uri, 4, 4) { hover = it }
        endpoint.requests.last().future.complete(
            mapOf("contents" to mapOf("kind" to "markdown", "value" to "**main** function")),
        )
        assertEquals("**main** function", hover?.getOrThrow())

        var definitions: Result<List<String>>? = null
        bridge.requestDefinition(uri, 4, 4) { definitions = it }
        endpoint.requests.last().future.complete(
            mapOf(
                "uri" to "file:///workspace/Other.kt",
                "range" to mapOf("start" to mapOf("line" to 2, "character" to 5)),
            ),
        )
        assertEquals(listOf("file:///workspace/Other.kt#3:6"), definitions?.getOrThrow())
        bridge.close()
    }

    @Test
    fun staleRequestVersionsFailBeforeSendingRequests() {
        val endpoint = FakeEndpoint()
        val bridge = endpoint.bridge()
        val uri = "file:///workspace/Main.kt"
        bridge.openDocument(EditorDocument(uri, "kotlin", 7, "hello"))

        var result: Result<String?>? = null
        bridge.requestHover(uri, 0, 6) { result = it }
        assertTrue(result?.isFailure == true)
        assertTrue(endpoint.requests.isEmpty())
        bridge.close()
    }

    @Test
    fun publishesUtf8DiagnosticsAndDropsStaleVersions() {
        val endpoint = FakeEndpoint()
        val bridge = endpoint.bridge()
        val uri = "file:///workspace/Main.kt"
        bridge.openDocument(EditorDocument(uri, "kotlin", 2, "Aह😀B"))

        val updates = mutableListOf<List<EditorDiagnostic>>()
        bridge.setDiagnosticsListener { updates += it }
        val diagnostic = mapOf(
            "range" to mapOf(
                "start" to mapOf("line" to 0, "character" to 1),
                "end" to mapOf("line" to 0, "character" to 4),
            ),
            "severity" to 2,
            "message" to "Unused value",
        )
        endpoint.publish(
            "textDocument/publishDiagnostics",
            mapOf("uri" to uri, "version" to 2, "diagnostics" to listOf(diagnostic)),
        )
        assertEquals(1, updates.size)
        assertEquals(1, updates.single().single().startOffset)
        assertEquals(4, updates.single().single().endOffset)
        assertEquals(EditorDiagnostic.Severity.WARNING, updates.single().single().severity)

        endpoint.publish(
            "textDocument/publishDiagnostics",
            mapOf("uri" to uri, "version" to 1, "diagnostics" to listOf(diagnostic)),
        )
        assertEquals(1, updates.size)
        bridge.close()
    }

    @Test
    fun closesDocumentsAndUnsubscribesFromServerNotifications() {
        val endpoint = FakeEndpoint()
        val bridge = endpoint.bridge()
        val uri = "file:///workspace/Main.kt"
        bridge.openDocument(EditorDocument(uri, "kotlin", 1, "fun main() {}"))
        bridge.closeDocument(uri)
        assertEquals("textDocument/didClose", endpoint.notifications.last().method)
        bridge.close()
        assertFalse(endpoint.notificationHandler != null)
    }
}

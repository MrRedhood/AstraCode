package com.astracode.editor

import java.util.concurrent.CompletableFuture
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class LspJsonRpcClientTest {
    @Test
    fun correlatesRequestResponseAndUsesJsonRpcVersion() {
        val sent = mutableListOf<String>()
        val client = LspJsonRpcClient(sendMessage = sent::add)
        val response = client.request("initialize", mapOf("processId" to 99L))

        assertEquals(1, client.pendingRequestCount)
        val request = LspJsonCodec.decodeObject(sent.single())
        assertEquals("2.0", request["jsonrpc"])
        assertEquals("initialize", request["method"])
        assertEquals(1L, request["id"])
        val params = request["params"] as Map<*, *>
        assertEquals(99L, params["processId"])

        client.acceptMessage(
            LspJsonCodec.encode(
                mapOf("jsonrpc" to "2.0", "id" to 1L, "result" to mapOf("capabilities" to emptyMap<String, Any?>())),
            ),
        )
        assertEquals(emptyMap<String, Any?>(), (response.join() as Map<*, *>)["capabilities"])
        assertEquals(0, client.pendingRequestCount)
        client.close()
    }

    @Test
    fun resolvesOutOfOrderResponsesByRequestId() {
        val sent = mutableListOf<String>()
        val client = LspJsonRpcClient(sendMessage = sent::add)
        val first = client.request("textDocument/hover")
        val second = client.request("textDocument/definition")

        client.acceptMessage(LspJsonCodec.encode(mapOf("jsonrpc" to "2.0", "id" to 2L, "result" to "definition")))
        client.acceptMessage(LspJsonCodec.encode(mapOf("jsonrpc" to "2.0", "id" to 1L, "result" to "hover")))

        assertEquals("hover", first.join())
        assertEquals("definition", second.join())
        client.close()
    }

    @Test
    fun mapsServerErrorsIntoTypedExceptions() {
        val sent = mutableListOf<String>()
        val client = LspJsonRpcClient(sendMessage = sent::add)
        val response = client.request("unknown/method")
        client.acceptMessage(
            LspJsonCodec.encode(
                mapOf(
                    "jsonrpc" to "2.0",
                    "id" to 1L,
                    "error" to mapOf("code" to -32601, "message" to "Method not found"),
                ),
            ),
        )
        val failure = runCatching { response.join() }.exceptionOrNull()
        val error = failure?.cause as? LspJsonRpcError
            ?: throw AssertionError("Expected LspJsonRpcError as the completion cause", failure)
        assertEquals(-32601, error.code)
        client.close()
    }

    @Test
    fun notificationsHaveNoRequestIdAndAreDeliveredToListener() {
        val sent = mutableListOf<String>()
        val received = mutableListOf<Pair<String, Map<String, Any?>>>()
        val client = LspJsonRpcClient(
            sendMessage = sent::add,
            onNotification = { method, params -> received += method to params },
        )

        client.notify("initialized", emptyMap<String, Any?>())
        val notification = LspJsonCodec.decodeObject(sent.single())
        assertEquals("initialized", notification["method"])
        assertTrue(!notification.containsKey("id"))

        client.acceptMessage(
            """{"jsonrpc":"2.0","method":"window/logMessage","params":{"message":"ready"}}""",
        )
        assertEquals("window/logMessage", received.single().first)
        assertEquals("ready", received.single().second["message"])
        client.close()
    }

    @Test
    fun answersServerRequestsAndReturnsMethodNotFoundByDefault() {
        val sent = mutableListOf<String>()
        val client = LspJsonRpcClient(sendMessage = sent::add)
        client.acceptMessage(
            """{"jsonrpc":"2.0","id":7,"method":"workspace/configuration","params":{}}""",
        )

        val response = LspJsonCodec.decodeObject(sent.single())
        assertEquals(7L, response["id"])
        val error = response["error"] as Map<*, *>
        assertEquals(-32601L, error["code"])
        client.close()
    }

    @Test
    fun cancellationRemovesPendingRequestAndUnknownLateResponseIsIgnored() {
        val sent = mutableListOf<String>()
        val client = LspJsonRpcClient(sendMessage = sent::add)
        val pending: CompletableFuture<Any?> = client.request("textDocument/hover")
        pending.cancel(true)
        assertEquals(0, client.pendingRequestCount)

        client.acceptMessage(
            LspJsonCodec.encode(mapOf("jsonrpc" to "2.0", "id" to 1L, "result" to "late")),
        )
        assertEquals(0, client.pendingRequestCount)
        client.close()
    }

    @Test
    fun malformedMessagesAreReportedWithoutCompletingOtherRequests() {
        val sent = mutableListOf<String>()
        val errors = mutableListOf<Throwable>()
        val client = LspJsonRpcClient(
            sendMessage = sent::add,
            onProtocolError = errors::add,
        )
        val response = client.request("initialize")
        client.acceptMessage("""{"jsonrpc":"1.0","id":1,"result":{}}""")
        assertEquals(1, errors.size)
        assertTrue(!response.isDone)
        client.failAll(IllegalStateException("transport stopped"))
        assertFailsWith<Exception> { response.join() }
    }

    @Test
    fun clientRejectsTooManyPendingRequestsAndClosedOperations() {
        val sent = mutableListOf<String>()
        val client = LspJsonRpcClient(sendMessage = sent::add)
        repeat(128) { client.request("test/request") }
        assertEquals(128, client.pendingRequestCount)
        assertFailsWith<IllegalArgumentException> { client.request("overflow") }
        client.close()
        assertFailsWith<IllegalStateException> { client.notify("after-close") }
    }
}

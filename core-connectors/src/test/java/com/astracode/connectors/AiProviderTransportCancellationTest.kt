package com.astracode.connectors

import kotlin.test.Test
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AiProviderTransportCancellationTest {
    @Test
    fun cancelPreventsNewProviderConnectionsUntilNextOperationStarts() {
        val transport = AiProviderTransport()

        transport.cancelActiveRequests()
        assertTrue(transport.isCancellationRequested())
        assertFailsWith<AiRequestCancelledException> {
            transport.getJson(
                "https://api.openai.com/v1/models",
                "not-a-real-key",
                AiProvider.OPENAI,
            )
        }

        transport.beginOperation()
        assertFalse(transport.isCancellationRequested())
    }

    @Test
    fun repeatedCancellationIsSafe() {
        val transport = AiProviderTransport()
        transport.cancelActiveRequests()
        transport.cancelActiveRequests()
        assertTrue(transport.isCancellationRequested())
    }
}

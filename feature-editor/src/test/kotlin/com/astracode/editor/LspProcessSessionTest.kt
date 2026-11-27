package com.astracode.editor

import java.nio.file.Files
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LspProcessSessionTest {
    @Test
    fun unexpectedProcessExitMarksSessionFailedAndNotifiesOnce() {
        val workspace = Files.createTempDirectory("astracode-lsp-session").toFile()
        val failureCount = AtomicInteger(0)
        val failureSignal = CountDownLatch(1)
        try {
            val session = LspProcessSession.start(
                command = listOf("sh", "-c", "exit 0"),
                workspaceRoot = workspace,
                workingDirectory = workspace,
                onFailure = {
                    failureCount.incrementAndGet()
                    failureSignal.countDown()
                },
            )

            assertTrue(failureSignal.await(5, TimeUnit.SECONDS))
            assertEquals(LspProcessSessionState.FAILED, session.state)
            assertEquals(1, failureCount.get())
            session.close()
        } finally {
            workspace.deleteRecursively()
        }
    }

    @Test
    fun explicitCloseDoesNotReportUnexpectedProcessFailure() {
        val workspace = Files.createTempDirectory("astracode-lsp-close").toFile()
        val failureCount = AtomicInteger(0)
        try {
            val session = LspProcessSession.start(
                command = listOf("sh", "-c", "sleep 10"),
                workspaceRoot = workspace,
                workingDirectory = workspace,
                onFailure = { failureCount.incrementAndGet() },
            )
            session.close()
            assertEquals(LspProcessSessionState.CLOSED, session.state)
            assertEquals(0, failureCount.get())
        } finally {
            workspace.deleteRecursively()
        }
    }
}

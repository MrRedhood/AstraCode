package com.astracode.terminal

import java.io.File
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class TerminalCommandRunnerTest {
    @Test
    fun runsCommandsAndCapturesBothStreams() {
        val root = Files.createTempDirectory("astracode-terminal").toFile()
        try {
            val runner = TerminalCommandRunner(root, shellOverride = "/bin/sh")
            val result = runner.execute("printf 'out'; printf 'err' >&2; exit 7")
            assertEquals("out", result.standardOutput)
            assertEquals("err", result.standardError)
            assertEquals(7, result.exitCode)
            assertTrue(!result.succeeded)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun refusesWorkingDirectoriesOutsideTheWorkspace() {
        val root = Files.createTempDirectory("astracode-terminal-root").toFile()
        val outside = Files.createTempDirectory("astracode-terminal-outside").toFile()
        try {
            val runner = TerminalCommandRunner(root, shellOverride = "/bin/sh")
            assertFailsWith<SecurityException> {
                runner.execute("pwd", outside)
            }
        } finally {
            root.deleteRecursively()
            outside.deleteRecursively()
        }
    }

    @Test
    fun capsCombinedCapturedOutputAcrossBothStreams() {
        val root = Files.createTempDirectory("astracode-terminal-output").toFile()
        try {
            val runner = TerminalCommandRunner(
                root,
                maxOutputCharacters = 20,
                shellOverride = "/bin/sh",
            )
            val result = runner.execute(
                "printf 'abcdefghijklmnopqrstuvwxyz'; printf 'ABCDEFGHIJKLMNOPQRSTUVWXYZ' >&2",
            )
            assertEquals(20, result.standardOutput.length + result.standardError.length)
            assertTrue(result.outputTruncated)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun timeoutTerminatesBackgroundChildProcesses() {
        val root = Files.createTempDirectory("astracode-terminal-timeout").toFile()
        try {
            val runner = TerminalCommandRunner(root, shellOverride = "/bin/sh")
            val marker = File(root, "timeout-child-finished")
            val result = runner.execute(
                "(sleep 1; printf done > '${marker.absolutePath}') & wait",
                timeoutMillis = 200L,
            )

            assertTrue(result.timedOut)
            Thread.sleep(1_200L)
            assertTrue(!marker.exists(), "A background child survived the timed-out command")
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun normalCommandExitDoesNotWaitForInheritedBackgroundOutputPipes() {
        val root = Files.createTempDirectory("astracode-terminal-pipes").toFile()
        try {
            val runner = TerminalCommandRunner(root, shellOverride = "/bin/sh")
            val startedAt = System.nanoTime()
            val result = runner.execute("(sleep 3; printf late-output) & exit 0")
            val durationMillis = (System.nanoTime() - startedAt) / 1_000_000L

            assertEquals(0, result.exitCode)
            assertTrue(
                durationMillis < 2_000L,
                "Output readers waited for a background process to close inherited pipes ($durationMillis ms)",
            )
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun normalCommandExitCleansUpDetachedBackgroundChildren() {
        val root = Files.createTempDirectory("astracode-terminal-background").toFile()
        try {
            val runner = TerminalCommandRunner(root, shellOverride = "/bin/sh")
            val marker = File(root, "background-child-finished")
            val result = runner.execute(
                "(sleep 1; printf done > '${marker.absolutePath}') >/dev/null 2>&1 & exit 0",
            )

            assertEquals(0, result.exitCode)
            Thread.sleep(1_200L)
            assertTrue(!marker.exists(), "A background child survived its parent shell")
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun interruptionTerminatesChildrenAndRestoresInterruptFlag() {
        val root = Files.createTempDirectory("astracode-terminal-interrupt").toFile()
        try {
            val runner = TerminalCommandRunner(root, shellOverride = "/bin/sh")
            val started = File(root, "interrupt-child-started")
            val marker = File(root, "interrupt-child-finished")
            val observedFailure = AtomicReference<Throwable?>(null)
            val interruptRestored = AtomicBoolean(false)
            val worker = Thread {
                try {
                    runner.execute(
                        "(printf started > '${started.absolutePath}'; sleep 2; printf done > '${marker.absolutePath}') & wait",
                    )
                } catch (failure: Throwable) {
                    observedFailure.set(failure)
                    interruptRestored.set(Thread.currentThread().isInterrupted)
                }
            }

            worker.start()
            val deadline = System.nanoTime() + 3_000_000_000L
            while (!started.exists() && worker.isAlive && System.nanoTime() < deadline) {
                Thread.sleep(10L)
            }
            assertTrue(started.exists(), "The background command did not start")
            worker.interrupt()
            worker.join(3_000L)

            assertTrue(!worker.isAlive, "Interrupted terminal execution did not finish")
            assertTrue(observedFailure.get() is InterruptedException)
            assertTrue(interruptRestored.get(), "The caller's interrupt flag was not restored")
            Thread.sleep(2_200L)
            assertTrue(!marker.exists(), "A child process survived interrupted execution")
        } finally {
            root.deleteRecursively()
        }
    }
}

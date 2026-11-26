package com.astracode.terminal

import java.io.File
import java.nio.file.Files
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
    fun capsCapturedOutput() {
        val root = Files.createTempDirectory("astracode-terminal-output").toFile()
        try {
            val runner = TerminalCommandRunner(
                root,
                maxOutputCharacters = 20,
                shellOverride = "/bin/sh",
            )
            val result = runner.execute("printf 'abcdefghijklmnopqrstuvwxyz'")
            assertEquals(20, result.standardOutput.length)
            assertTrue(result.outputTruncated)
        } finally {
            root.deleteRecursively()
        }
    }
}

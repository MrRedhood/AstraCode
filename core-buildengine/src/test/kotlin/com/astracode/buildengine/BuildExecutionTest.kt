package com.astracode.buildengine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuildExecutionTest {
    @Test
    fun capturesStdoutAndStderrAndPreservesExitCode() {
        val root = createTempDir(prefix = "astracode-build-")
        try {
            assumeShellAvailable()
            val result = BuildProcessRunner().run(
                profile(
                    root,
                    listOf("/bin/sh", "-c", "printf 'out'; printf 'err' >&2; exit 7"),
                ),
            )
            assertEquals(7, result.exitCode)
            assertFalse(result.succeeded)
            assertTrue(result.output.contains("out"))
            assertTrue(result.output.contains("err"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun timesOutLongRunningBuildCommand() {
        val root = createTempDir(prefix = "astracode-build-")
        try {
            assumeShellAvailable()
            val result = BuildProcessRunner().run(
                profile(
                    root,
                    listOf("/bin/sh", "-c", "exec sleep 5"),
                    timeoutMillis = 250,
                ),
            )
            assertTrue(result.timedOut)
            assertFalse(result.succeeded)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun rejectsWorkingDirectoryOutsideWorkspace() {
        val root = createTempDir(prefix = "astracode-build-")
        try {
            val result = BuildProcessRunner().run(
                BuildExecutionProfile(
                    taskName = "outside-path",
                    kind = BuildTaskKind.CUSTOM,
                    workspaceRoot = root,
                    workingDirectory = root.parentFile,
                    command = listOf("/bin/true"),
                ),
            )
            assertTrue(result.failureMessage.orEmpty().contains("outside"))
            assertFalse(result.succeeded)
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun redactsEnvironmentSecretsFromStoredAndStreamedOutput() {
        val root = createTempDir(prefix = "astracode-build-")
        try {
            assumeShellAvailable()
            val streamed = StringBuilder()
            val result = BuildProcessRunner().run(
                profile(
                    root,
                    listOf("/bin/sh", "-c", "printf '%s' \"$API_TOKEN\""),
                    environment = mapOf("API_TOKEN" to "secret-value-123"),
                ),
            ) { event ->
                if (event is BuildExecutionEvent.Output) streamed.append(event.text)
            }
            assertTrue(result.succeeded)
            assertFalse(result.output.contains("secret-value-123"))
            assertFalse(streamed.contains("secret-value-123"))
            assertTrue(result.output.contains("<redacted>"))
        } finally {
            root.deleteRecursively()
        }
    }

    @Test
    fun boundedOutputKeepsOnlyTheLatestCharacters() {
        val output = BoundedBuildOutput(8)
        output.append("first-")
        output.append("second")
        val snapshot = output.snapshot()
        assertEquals("t-second", snapshot.text)
        assertTrue(snapshot.truncated)
    }

    @Test
    fun redactorHandlesSecretsSplitAcrossReadChunks() {
        val redactor = BuildOutputRedactor(listOf("token123"))
        assertEquals("", redactor.append("abc-to"))
        assertEquals("abc-", redactor.append("ken123-xyz").take(4))
        assertEquals("<redacted>-xyz", redactor.finish())
    }

    @Test
    fun redactorPassesThroughWhenNoSecretsAreConfigured() {
        val redactor = BuildOutputRedactor(emptyList())
        assertEquals("ordinary build output", redactor.append("ordinary build output"))
        assertEquals("", redactor.finish())
    }

    private fun profile(
        root: File,
        command: List<String>,
        timeoutMillis: Long = 5_000,
        environment: Map<String, String> = emptyMap(),
    ) = BuildExecutionProfile(
        taskName = "test-build",
        kind = BuildTaskKind.TEST,
        workspaceRoot = root,
        workingDirectory = root,
        command = command,
        environment = environment,
        timeoutMillis = timeoutMillis,
        maximumOutputCharacters = 8_192,
    )

    private fun assumeShellAvailable() {
        org.junit.jupiter.api.Assumptions.assumeTrue(File("/bin/sh").canExecute())
    }
}

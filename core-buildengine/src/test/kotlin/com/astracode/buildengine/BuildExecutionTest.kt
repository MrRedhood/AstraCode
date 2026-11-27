package com.astracode.buildengine

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BuildExecutionTest {
    @Test
    fun capturesStdoutAndStderrAndPreservesExitCode() {
        val root = java.nio.file.Files.createTempDirectory("astracode-build-").toFile()
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
        val root = java.nio.file.Files.createTempDirectory("astracode-build-").toFile()
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
        val root = java.nio.file.Files.createTempDirectory("astracode-build-").toFile()
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
        val root = java.nio.file.Files.createTempDirectory("astracode-build-").toFile()
        try {
            assumeShellAvailable()
            val streamed = StringBuilder()
            val result = BuildProcessRunner().run(
                profile(
                    root,
                    listOf("/bin/sh", "-c", "printf '%s' \"\$API_TOKEN\""),
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


    @Test
    fun redactorPrefersLongerSecretsWhenOneSecretPrefixesAnother() {
        val redactor = BuildOutputRedactor(listOf("abc", "abcd"))
        assertEquals("", redactor.append("abcd"))
        assertEquals("", redactor.append("!"))
        assertEquals("<redacted>!", redactor.finish())
    }

    @Test
    fun redactorWithholdsAnIncompleteSupplementaryUnicodeCharacterBetweenChunks() {
        val redactor = BuildOutputRedactor(emptyList())
        assertEquals("A", redactor.append("A\uD83D"))
        assertEquals("😀B", redactor.append("\uDE00B"))
        assertEquals("", redactor.finish())
    }

    @Test
    fun redactorHandlesAnEmptySecretAlongsideRealSecrets() {
        val redactor = BuildOutputRedactor(listOf("", "token"))
        val output = redactor.append("before-to") +
            redactor.append("ken-after") +
            redactor.finish()
        assertEquals("before-<redacted>-after", output)
    }

    @Test
    fun boundedOutputKeepsOnlyTheTailOfOneVeryLargeChunk() {
        val output = BoundedBuildOutput(8)
        output.append("x".repeat(200_000) + "TAIL")
        val snapshot = output.snapshot()
        assertEquals("xxxxTAIL", snapshot.text)
        assertTrue(snapshot.truncated)
    }

    @Test
    fun boundedOutputDoesNotMarkAnExactCapacityWriteAsTruncated() {
        val output = BoundedBuildOutput(4)
        output.append("1234")
        assertEquals("1234", output.snapshot().text)
        assertFalse(output.snapshot().truncated)
    }

    @Test
    fun boundedOutputPreservesSupplementaryCharactersAtTheTailBoundary() {
        val output = BoundedBuildOutput(2)
        output.append("123456😀x")
        assertEquals("😀x", output.snapshot().text)
        assertEquals(3, output.snapshot().text.length)
        assertTrue(output.snapshot().truncated)
    }

    @Test
    fun boundedOutputReturnsDetachedSnapshotsAndKeepsTruncationSticky() {
        val output = BoundedBuildOutput(3)
        output.append("abc")
        val first = output.snapshot()
        output.append("d")
        val second = output.snapshot()

        assertEquals("abc", first.text)
        assertFalse(first.truncated)
        assertEquals("bcd", second.text)
        assertTrue(second.truncated)
    }

    @Test
    fun boundedOutputRemainsBoundedDuringConcurrentAppends() {
        val output = BoundedBuildOutput(64)
        val workers = List(4) {
            Thread {
                repeat(500) { output.append("item") }
            }.apply { start() }
        }
        workers.forEach { it.join() }

        val snapshot = output.snapshot()
        assertTrue(snapshot.text.length <= 65)
        assertTrue(snapshot.truncated)
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

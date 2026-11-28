package com.astracode.terminal

import java.io.Closeable
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.CancellationException
import java.util.concurrent.ExecutionException
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.TimeoutException
import java.util.concurrent.atomic.AtomicInteger

data class TerminalCommandResult(
    val command: String,
    val workingDirectory: String,
    val exitCode: Int?,
    val standardOutput: String,
    val standardError: String,
    val timedOut: Boolean,
    val outputTruncated: Boolean,
    val durationMillis: Long,
) {
    val succeeded: Boolean get() = !timedOut && exitCode == 0
}

private data class CapturedOutput(
    val text: String,
    val truncated: Boolean,
)

private class OutputBudget(maxCharacters: Int) {
    private val remaining = AtomicInteger(maxCharacters)

    fun take(requested: Int): Int {
        while (true) {
            val available = remaining.get()
            if (available <= 0) return 0
            val granted = minOf(available, requested)
            if (remaining.compareAndSet(available, available - granted)) return granted
        }
    }
}

/**
 * Executes user-entered commands as the Android app's normal UID.
 *
 * The workspace is the initial working directory, not a filesystem sandbox:
 * shell commands can access any paths allowed by the app's Android permissions.
 * On Android this uses /system/bin/sh and OS-provided utilities (normally
 * Toybox). It is deliberately not described as a full Linux distribution:
 * PRoot, a Linux root filesystem, package managers and PTY-backed TUI programs
 * require a separate, device-compatible userland installation.
 *
 * Commands run in their own process session. This lets timeout and cancellation
 * signals reach ordinary child processes without signaling the Android app.
 */
class TerminalCommandRunner(
    workspaceRoot: File,
    private val maxOutputCharacters: Int = 128 * 1024,
    private val maximumTimeoutMillis: Long = 120_000L,
    shellOverride: String? = null,
) {
    val workspaceRoot: File = workspaceRoot.canonicalFile
    val shellPath: String = shellOverride ?: detectShell()

    private val sessionLauncher = findUtility("setsid")
    private val signalLauncher = findUtility("kill")

    val shellDescription: String
        get() = if (shellPath == "/system/bin/sh") {
            "Android system shell (/system/bin/sh)"
        } else {
            "POSIX shell ($shellPath)"
        }

    init {
        require(maxOutputCharacters > 0) { "maxOutputCharacters must be positive" }
        require(maximumTimeoutMillis > 0) { "maximumTimeoutMillis must be positive" }
        this.workspaceRoot.mkdirs()
        require(this.workspaceRoot.isDirectory) { "Terminal workspace is not a directory" }
        require(File(shellPath).canExecute()) { "Shell executable is not available: $shellPath" }
    }

    @Throws(Exception::class)
    fun execute(
        command: String,
        workingDirectory: File = workspaceRoot,
        timeoutMillis: Long = 60_000L,
    ): TerminalCommandResult {
        require(command.isNotBlank()) { "Enter a command first" }
        require(command.length <= 8_192) { "The command is too long" }
        require(timeoutMillis in 100L..maximumTimeoutMillis) { "The command timeout is outside the allowed range" }

        val cwd = workingDirectory.canonicalFile
        if (!isInsideWorkspace(cwd)) {
            throw SecurityException("The terminal working directory must remain inside the selected workspace")
        }
        if (!cwd.isDirectory) {
            throw IllegalArgumentException("The terminal working directory does not exist")
        }

        val startedAt = System.nanoTime()
        val temporaryDirectory = File(workspaceRoot, ".tmp")
        if (!temporaryDirectory.exists() && !temporaryDirectory.mkdirs()) {
            throw IOException("Could not create the terminal temporary directory")
        }
        if (!temporaryDirectory.isDirectory) {
            throw IOException("The terminal temporary path is not a directory")
        }
        val sessionIdFile = File.createTempFile("terminal-session-", ".pid", temporaryDirectory)
        val bootstrap = "printf '%s\\n' \"\$\$\" > \"\$ASTRACODE_SESSION_PID_FILE\" || exit 125; unset ASTRACODE_SESSION_PID_FILE; exec \"\$@\""
        val sessionLauncherCommand = sessionLauncher.joinToString(" ") { quoteForShell(it) }
        val signalLauncherCommand = signalLauncher.joinToString(" ") { quoteForShell(it) }
        val supervisor = """
            $sessionLauncherCommand "${'
            sessionProcess=${'$'}!
            wait "${'$'}sessionProcess"
            commandExitCode=${'$'}?
            sessionId=""
            if IFS= read -r sessionId < "${'$'}ASTRACODE_SESSION_PID_FILE"; then
                if [ -n "${'$'}sessionId" ]; then
                    $signalLauncherCommand -KILL -- "-${'$'}sessionId" >/dev/null 2>&1 || true
                fi
            fi
            exit "${'$'}commandExitCode"
        """.trimIndent()
        val launchCommand = listOf(
            shellPath,
            "-c",
            supervisor,
            "astracode-supervisor",
            shellPath,
            bootstrap,
            "astracode-session",
            shellPath,
            "-c",
            command,
        )

        val process = try {
            ProcessBuilder(launchCommand)
                .directory(cwd)
                .apply {
                    val env = environment()
                    env["HOME"] = workspaceRoot.absolutePath
                    env["TMPDIR"] = temporaryDirectory.absolutePath
                    env["ASTRACODE_SESSION_PID_FILE"] = sessionIdFile.absolutePath
                    val existingPath = env["PATH"].orEmpty()
                    env["PATH"] = listOf("/system/bin", "/system/xbin", "/usr/bin", "/bin", existingPath)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(File.pathSeparator)
                }
                .start()
        } catch (failure: Exception) {
            sessionIdFile.delete()
            throw failure
        }

        val readers = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "astracode-terminal-output").apply { isDaemon = true }
        }
        val outputBudget = OutputBudget(maxOutputCharacters)
        val stdoutFuture = readers.submit(Callable { capture(process.inputStream, outputBudget) })
        val stderrFuture = readers.submit(Callable { capture(process.errorStream, outputBudget) })

        var sessionId: Long? = null
        var timedOut = false
        var result: TerminalCommandResult? = null
        var failure: Exception? = null

        try {
            // This runner is non-interactive; EOF lets commands that read stdin
            // terminate instead of waiting until the timeout.
            process.outputStream.close()

            sessionId = awaitSessionId(sessionIdFile, process)
                ?: throw IOException("Could not establish an isolated terminal process session")

            val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!finished) {
                timedOut = true
                terminateProcessGroup(process, sessionId)
            }

            val outputDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(OUTPUT_DRAIN_TIMEOUT_MILLIS)
            var stdout = awaitCapture(stdoutFuture, outputDeadline, "stdout")
            var stderr = awaitCapture(stderrFuture, outputDeadline, "stderr")

            if (stdout == null || stderr == null) {
                // A child may have inherited a pipe and kept it open after the
                // shell exited. Stop the session, close both pipes, and give the
                // reader tasks one final bounded chance to finish.
                terminateProcessGroup(process, sessionId)
                closeQuietly(process.errorStream)
                val cleanupDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(OUTPUT_CLEANUP_TIMEOUT_MILLIS)
                stdout = stdout ?: awaitCapture(stdoutFuture, cleanupDeadline, "stdout")
                stderr = stderr ?: awaitCapture(stderrFuture, cleanupDeadline, "stderr")
            }

            val capturedStdout = stdout ?: CapturedOutput("[stdout capture did not stop after process cleanup]", true)
            val capturedStderr = stderr ?: CapturedOutput("[stderr capture did not stop after process cleanup]", true)

            result = TerminalCommandResult(
                command = command,
                workingDirectory = cwd.absolutePath,
                exitCode = if (timedOut) null else process.exitValue(),
                standardOutput = capturedStdout.text,
                standardError = capturedStderr.text,
                timedOut = timedOut,
                outputTruncated = capturedStdout.truncated || capturedStderr.truncated,
                durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
            )
        } catch (exception: Exception) {
            failure = exception
            sessionId = sessionId ?: readSessionId(sessionIdFile)
            if (sessionId == null && process.isAlive) {
                // Failure or interruption can race with the tiny bootstrap
                // step that records the new session ID. Give it a bounded
                // chance to publish the ID before falling back to supervisor
                // cleanup.
                sessionId = runCatching { awaitSessionId(sessionIdFile, process) }.getOrNull()
            }
            try {
                terminateProcessGroup(process, sessionId)
            } catch (cleanupFailure: Exception) {
                exception.addSuppressed(cleanupFailure)
            }
        } finally {
            closeQuietly(process.outputStream)
            closeQuietly(process.inputStream)
            closeQuietly(process.errorStream)
            stdoutFuture.cancel(true)
            stderrFuture.cancel(true)
            readers.shutdownNow()
            try {
                readers.awaitTermination(READER_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            } catch (cleanupInterrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                if (failure == null) {
                    failure = cleanupInterrupted
                } else {
                    failure.addSuppressed(cleanupInterrupted)
                }
            }
            sessionIdFile.delete()
        }

        if (failure != null) {
            if (failure is InterruptedException) Thread.currentThread().interrupt()
            throw failure
        }
        return result ?: throw IllegalStateException("Terminal command did not produce a result")
    }

    private fun readSessionId(sessionIdFile: File): Long? =
        runCatching { sessionIdFile.readText().trim().toLongOrNull() }
            .getOrNull()
            ?.takeIf { it > 1L }

    private fun awaitSessionId(sessionIdFile: File, process: Process): Long? {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SESSION_ID_WAIT_MILLIS)
        while (System.nanoTime() < deadline) {
            val value = readSessionId(sessionIdFile)
            if (value != null) return value
            if (!process.isAlive) return null
            Thread.sleep(10L)
        }
        return readSessionId(sessionIdFile)
    }

    private fun quoteForShell(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private fun terminateProcessGroup(process: Process, sessionId: Long?) {
        if (sessionId != null) {
            signalProcessGroup(sessionId, "TERM")
            try {
                process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)
            } catch (interrupted: InterruptedException) {
                signalProcessGroup(sessionId, "KILL")
                process.destroyForcibly()
                runCatching { process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS) }
                throw interrupted
            }
            // Send KILL even when the session leader has exited: descendants can
            // outlive their shell while retaining the original process group.
            signalProcessGroup(sessionId, "KILL")
        }

        if (process.isAlive) {
            process.destroyForcibly()
            process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)
        }
    }

    private fun signalProcessGroup(sessionId: Long, signal: String) {
        val signalProcess = ProcessBuilder(signalLauncher + listOf("-$signal", "--", "-$sessionId"))
            .redirectOutput(File("/dev/null"))
            .redirectError(File("/dev/null"))
            .start()

        try {
            if (!signalProcess.waitFor(SIGNAL_COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                signalProcess.destroyForcibly()
                signalProcess.waitFor(READER_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            }
        } catch (interrupted: InterruptedException) {
            signalProcess.destroyForcibly()
            runCatching { signalProcess.waitFor(READER_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS) }
            throw interrupted
        }
    }

    private fun awaitCapture(
        future: Future<CapturedOutput>,
        deadlineNanos: Long,
        streamName: String,
    ): CapturedOutput? {
        return try {
            if (!future.isDone) {
                val remaining = deadlineNanos - System.nanoTime()
                if (remaining <= 0L) return null
                future.get(remaining, TimeUnit.NANOSECONDS)
            } else {
                future.get()
            }
        } catch (_: TimeoutException) {
            null
        } catch (failure: ExecutionException) {
            CapturedOutput("[$streamName capture failed: ${failure.cause?.message ?: "unknown error"}]", true)
        } catch (_: CancellationException) {
            CapturedOutput("[$streamName capture cancelled]", true)
        }
    }

    private fun isInsideWorkspace(candidate: File): Boolean {
        val root = workspaceRoot.absolutePath
        val path = candidate.absolutePath
        return path == root || path.startsWith(root + File.separator)
    }

    private fun capture(input: InputStream, budget: OutputBudget): CapturedOutput {
        val output = StringBuilder(minOf(maxOutputCharacters, 8_192))
        var truncated = false
        InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
            val buffer = CharArray(4_096)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                val accepted = budget.take(count)
                if (accepted > 0) output.append(buffer, 0, accepted)
                if (accepted < count) truncated = true
            }
        }
        return CapturedOutput(output.toString(), truncated)
    }

    private fun closeQuietly(closeable: Closeable) {
        runCatching { closeable.close() }
    }

    private fun detectShell(): String {
        val candidates = listOf("/system/bin/sh", "/bin/sh")
        return candidates.firstOrNull { File(it).canExecute() }
            ?: throw IllegalStateException("No supported system shell was found on this device")
    }

    private fun findUtility(name: String): List<String> {
        val candidates = when (name) {
            "setsid" -> listOf(
                listOf("/system/bin/setsid"),
                listOf("/system/bin/toybox", "setsid"),
                listOf("/usr/bin/setsid"),
                listOf("/bin/setsid"),
                listOf("/usr/bin/toybox", "setsid"),
                listOf("/bin/toybox", "setsid"),
            )
            "kill" -> listOf(
                listOf("/system/bin/kill"),
                listOf("/system/bin/toybox", "kill"),
                listOf("/usr/bin/kill"),
                listOf("/bin/kill"),
                listOf("/usr/bin/toybox", "kill"),
                listOf("/bin/toybox", "kill"),
            )
            else -> emptyList()
        }
        return candidates.firstOrNull { File(it.first()).canExecute() }
            ?: throw IllegalStateException("Required terminal utility is not available: $name")
    }

    companion object {
        private const val SESSION_ID_WAIT_MILLIS = 1_000L
        private const val PROCESS_TERMINATION_GRACE_MILLIS = 250L
        private const val SIGNAL_COMMAND_TIMEOUT_MILLIS = 500L
        private const val OUTPUT_DRAIN_TIMEOUT_MILLIS = 1_000L
        private const val OUTPUT_CLEANUP_TIMEOUT_MILLIS = 500L
        private const val READER_SHUTDOWN_TIMEOUT_MILLIS = 500L
    }
}
}1" -c "${'
            sessionProcess=${'$'}!
            wait "${'$'}sessionProcess"
            commandExitCode=${'$'}?
            sessionId=""
            if IFS= read -r sessionId < "${'$'}ASTRACODE_SESSION_PID_FILE"; then
                if [ -n "${'$'}sessionId" ]; then
                    $signalLauncherCommand -KILL -- "-${'$'}sessionId" >/dev/null 2>&1 || true
                fi
            fi
            exit "${'$'}commandExitCode"
        """.trimIndent()
        val launchCommand = listOf(
            shellPath,
            "-c",
            supervisor,
            "astracode-supervisor",
            shellPath,
            bootstrap,
            "astracode-session",
            shellPath,
            "-c",
            command,
        )

        val process = try {
            ProcessBuilder(launchCommand)
                .directory(cwd)
                .apply {
                    val env = environment()
                    env["HOME"] = workspaceRoot.absolutePath
                    env["TMPDIR"] = temporaryDirectory.absolutePath
                    env["ASTRACODE_SESSION_PID_FILE"] = sessionIdFile.absolutePath
                    val existingPath = env["PATH"].orEmpty()
                    env["PATH"] = listOf("/system/bin", "/system/xbin", "/usr/bin", "/bin", existingPath)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(File.pathSeparator)
                }
                .start()
        } catch (failure: Exception) {
            sessionIdFile.delete()
            throw failure
        }

        val readers = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "astracode-terminal-output").apply { isDaemon = true }
        }
        val outputBudget = OutputBudget(maxOutputCharacters)
        val stdoutFuture = readers.submit(Callable { capture(process.inputStream, outputBudget) })
        val stderrFuture = readers.submit(Callable { capture(process.errorStream, outputBudget) })

        var sessionId: Long? = null
        var timedOut = false
        var result: TerminalCommandResult? = null
        var failure: Exception? = null

        try {
            // This runner is non-interactive; EOF lets commands that read stdin
            // terminate instead of waiting until the timeout.
            process.outputStream.close()

            sessionId = awaitSessionId(sessionIdFile, process)
                ?: throw IOException("Could not establish an isolated terminal process session")

            val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!finished) {
                timedOut = true
                terminateProcessGroup(process, sessionId)
            }

            val outputDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(OUTPUT_DRAIN_TIMEOUT_MILLIS)
            var stdout = awaitCapture(stdoutFuture, outputDeadline, "stdout")
            var stderr = awaitCapture(stderrFuture, outputDeadline, "stderr")

            if (stdout == null || stderr == null) {
                // A child may have inherited a pipe and kept it open after the
                // shell exited. Stop the session, close both pipes, and give the
                // reader tasks one final bounded chance to finish.
                terminateProcessGroup(process, sessionId)
                closeQuietly(process.errorStream)
                val cleanupDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(OUTPUT_CLEANUP_TIMEOUT_MILLIS)
                stdout = stdout ?: awaitCapture(stdoutFuture, cleanupDeadline, "stdout")
                stderr = stderr ?: awaitCapture(stderrFuture, cleanupDeadline, "stderr")
            }

            val capturedStdout = stdout ?: CapturedOutput("[stdout capture did not stop after process cleanup]", true)
            val capturedStderr = stderr ?: CapturedOutput("[stderr capture did not stop after process cleanup]", true)

            result = TerminalCommandResult(
                command = command,
                workingDirectory = cwd.absolutePath,
                exitCode = if (timedOut) null else process.exitValue(),
                standardOutput = capturedStdout.text,
                standardError = capturedStderr.text,
                timedOut = timedOut,
                outputTruncated = capturedStdout.truncated || capturedStderr.truncated,
                durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
            )
        } catch (exception: Exception) {
            failure = exception
            sessionId = sessionId ?: readSessionId(sessionIdFile)
            if (sessionId == null && process.isAlive) {
                // Failure or interruption can race with the tiny bootstrap
                // step that records the new session ID. Give it a bounded
                // chance to publish the ID before falling back to supervisor
                // cleanup.
                sessionId = runCatching { awaitSessionId(sessionIdFile, process) }.getOrNull()
            }
            try {
                terminateProcessGroup(process, sessionId)
            } catch (cleanupFailure: Exception) {
                exception.addSuppressed(cleanupFailure)
            }
        } finally {
            closeQuietly(process.outputStream)
            closeQuietly(process.inputStream)
            closeQuietly(process.errorStream)
            stdoutFuture.cancel(true)
            stderrFuture.cancel(true)
            readers.shutdownNow()
            try {
                readers.awaitTermination(READER_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            } catch (cleanupInterrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                if (failure == null) {
                    failure = cleanupInterrupted
                } else {
                    failure.addSuppressed(cleanupInterrupted)
                }
            }
            sessionIdFile.delete()
        }

        if (failure != null) {
            if (failure is InterruptedException) Thread.currentThread().interrupt()
            throw failure
        }
        return result ?: throw IllegalStateException("Terminal command did not produce a result")
    }

    private fun readSessionId(sessionIdFile: File): Long? =
        runCatching { sessionIdFile.readText().trim().toLongOrNull() }
            .getOrNull()
            ?.takeIf { it > 1L }

    private fun awaitSessionId(sessionIdFile: File, process: Process): Long? {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SESSION_ID_WAIT_MILLIS)
        while (System.nanoTime() < deadline) {
            val value = readSessionId(sessionIdFile)
            if (value != null) return value
            if (!process.isAlive) return null
            Thread.sleep(10L)
        }
        return readSessionId(sessionIdFile)
    }

    private fun quoteForShell(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private fun terminateProcessGroup(process: Process, sessionId: Long?) {
        if (sessionId != null) {
            signalProcessGroup(sessionId, "TERM")
            try {
                process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)
            } catch (interrupted: InterruptedException) {
                signalProcessGroup(sessionId, "KILL")
                process.destroyForcibly()
                runCatching { process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS) }
                throw interrupted
            }
            // Send KILL even when the session leader has exited: descendants can
            // outlive their shell while retaining the original process group.
            signalProcessGroup(sessionId, "KILL")
        }

        if (process.isAlive) {
            process.destroyForcibly()
            process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)
        }
    }

    private fun signalProcessGroup(sessionId: Long, signal: String) {
        val signalProcess = ProcessBuilder(signalLauncher + listOf("-$signal", "--", "-$sessionId"))
            .redirectOutput(File("/dev/null"))
            .redirectError(File("/dev/null"))
            .start()

        try {
            if (!signalProcess.waitFor(SIGNAL_COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                signalProcess.destroyForcibly()
                signalProcess.waitFor(READER_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            }
        } catch (interrupted: InterruptedException) {
            signalProcess.destroyForcibly()
            runCatching { signalProcess.waitFor(READER_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS) }
            throw interrupted
        }
    }

    private fun awaitCapture(
        future: Future<CapturedOutput>,
        deadlineNanos: Long,
        streamName: String,
    ): CapturedOutput? {
        return try {
            if (!future.isDone) {
                val remaining = deadlineNanos - System.nanoTime()
                if (remaining <= 0L) return null
                future.get(remaining, TimeUnit.NANOSECONDS)
            } else {
                future.get()
            }
        } catch (_: TimeoutException) {
            null
        } catch (failure: ExecutionException) {
            CapturedOutput("[$streamName capture failed: ${failure.cause?.message ?: "unknown error"}]", true)
        } catch (_: CancellationException) {
            CapturedOutput("[$streamName capture cancelled]", true)
        }
    }

    private fun isInsideWorkspace(candidate: File): Boolean {
        val root = workspaceRoot.absolutePath
        val path = candidate.absolutePath
        return path == root || path.startsWith(root + File.separator)
    }

    private fun capture(input: InputStream, budget: OutputBudget): CapturedOutput {
        val output = StringBuilder(minOf(maxOutputCharacters, 8_192))
        var truncated = false
        InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
            val buffer = CharArray(4_096)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                val accepted = budget.take(count)
                if (accepted > 0) output.append(buffer, 0, accepted)
                if (accepted < count) truncated = true
            }
        }
        return CapturedOutput(output.toString(), truncated)
    }

    private fun closeQuietly(closeable: Closeable) {
        runCatching { closeable.close() }
    }

    private fun detectShell(): String {
        val candidates = listOf("/system/bin/sh", "/bin/sh")
        return candidates.firstOrNull { File(it).canExecute() }
            ?: throw IllegalStateException("No supported system shell was found on this device")
    }

    private fun findUtility(name: String): List<String> {
        val candidates = when (name) {
            "setsid" -> listOf(
                listOf("/system/bin/setsid"),
                listOf("/system/bin/toybox", "setsid"),
                listOf("/usr/bin/setsid"),
                listOf("/bin/setsid"),
                listOf("/usr/bin/toybox", "setsid"),
                listOf("/bin/toybox", "setsid"),
            )
            "kill" -> listOf(
                listOf("/system/bin/kill"),
                listOf("/system/bin/toybox", "kill"),
                listOf("/usr/bin/kill"),
                listOf("/bin/kill"),
                listOf("/usr/bin/toybox", "kill"),
                listOf("/bin/toybox", "kill"),
            )
            else -> emptyList()
        }
        return candidates.firstOrNull { File(it.first()).canExecute() }
            ?: throw IllegalStateException("Required terminal utility is not available: $name")
    }

    companion object {
        private const val SESSION_ID_WAIT_MILLIS = 1_000L
        private const val PROCESS_TERMINATION_GRACE_MILLIS = 250L
        private const val SIGNAL_COMMAND_TIMEOUT_MILLIS = 500L
        private const val OUTPUT_DRAIN_TIMEOUT_MILLIS = 1_000L
        private const val OUTPUT_CLEANUP_TIMEOUT_MILLIS = 500L
        private const val READER_SHUTDOWN_TIMEOUT_MILLIS = 500L
    }
}
}2" "${'
            sessionProcess=${'$'}!
            wait "${'$'}sessionProcess"
            commandExitCode=${'$'}?
            sessionId=""
            if IFS= read -r sessionId < "${'$'}ASTRACODE_SESSION_PID_FILE"; then
                if [ -n "${'$'}sessionId" ]; then
                    $signalLauncherCommand -KILL -- "-${'$'}sessionId" >/dev/null 2>&1 || true
                fi
            fi
            exit "${'$'}commandExitCode"
        """.trimIndent()
        val launchCommand = listOf(
            shellPath,
            "-c",
            supervisor,
            "astracode-supervisor",
            shellPath,
            bootstrap,
            "astracode-session",
            shellPath,
            "-c",
            command,
        )

        val process = try {
            ProcessBuilder(launchCommand)
                .directory(cwd)
                .apply {
                    val env = environment()
                    env["HOME"] = workspaceRoot.absolutePath
                    env["TMPDIR"] = temporaryDirectory.absolutePath
                    env["ASTRACODE_SESSION_PID_FILE"] = sessionIdFile.absolutePath
                    val existingPath = env["PATH"].orEmpty()
                    env["PATH"] = listOf("/system/bin", "/system/xbin", "/usr/bin", "/bin", existingPath)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(File.pathSeparator)
                }
                .start()
        } catch (failure: Exception) {
            sessionIdFile.delete()
            throw failure
        }

        val readers = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "astracode-terminal-output").apply { isDaemon = true }
        }
        val outputBudget = OutputBudget(maxOutputCharacters)
        val stdoutFuture = readers.submit(Callable { capture(process.inputStream, outputBudget) })
        val stderrFuture = readers.submit(Callable { capture(process.errorStream, outputBudget) })

        var sessionId: Long? = null
        var timedOut = false
        var result: TerminalCommandResult? = null
        var failure: Exception? = null

        try {
            // This runner is non-interactive; EOF lets commands that read stdin
            // terminate instead of waiting until the timeout.
            process.outputStream.close()

            sessionId = awaitSessionId(sessionIdFile, process)
                ?: throw IOException("Could not establish an isolated terminal process session")

            val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!finished) {
                timedOut = true
                terminateProcessGroup(process, sessionId)
            }

            val outputDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(OUTPUT_DRAIN_TIMEOUT_MILLIS)
            var stdout = awaitCapture(stdoutFuture, outputDeadline, "stdout")
            var stderr = awaitCapture(stderrFuture, outputDeadline, "stderr")

            if (stdout == null || stderr == null) {
                // A child may have inherited a pipe and kept it open after the
                // shell exited. Stop the session, close both pipes, and give the
                // reader tasks one final bounded chance to finish.
                terminateProcessGroup(process, sessionId)
                closeQuietly(process.errorStream)
                val cleanupDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(OUTPUT_CLEANUP_TIMEOUT_MILLIS)
                stdout = stdout ?: awaitCapture(stdoutFuture, cleanupDeadline, "stdout")
                stderr = stderr ?: awaitCapture(stderrFuture, cleanupDeadline, "stderr")
            }

            val capturedStdout = stdout ?: CapturedOutput("[stdout capture did not stop after process cleanup]", true)
            val capturedStderr = stderr ?: CapturedOutput("[stderr capture did not stop after process cleanup]", true)

            result = TerminalCommandResult(
                command = command,
                workingDirectory = cwd.absolutePath,
                exitCode = if (timedOut) null else process.exitValue(),
                standardOutput = capturedStdout.text,
                standardError = capturedStderr.text,
                timedOut = timedOut,
                outputTruncated = capturedStdout.truncated || capturedStderr.truncated,
                durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
            )
        } catch (exception: Exception) {
            failure = exception
            sessionId = sessionId ?: readSessionId(sessionIdFile)
            if (sessionId == null && process.isAlive) {
                // Failure or interruption can race with the tiny bootstrap
                // step that records the new session ID. Give it a bounded
                // chance to publish the ID before falling back to supervisor
                // cleanup.
                sessionId = runCatching { awaitSessionId(sessionIdFile, process) }.getOrNull()
            }
            try {
                terminateProcessGroup(process, sessionId)
            } catch (cleanupFailure: Exception) {
                exception.addSuppressed(cleanupFailure)
            }
        } finally {
            closeQuietly(process.outputStream)
            closeQuietly(process.inputStream)
            closeQuietly(process.errorStream)
            stdoutFuture.cancel(true)
            stderrFuture.cancel(true)
            readers.shutdownNow()
            try {
                readers.awaitTermination(READER_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            } catch (cleanupInterrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                if (failure == null) {
                    failure = cleanupInterrupted
                } else {
                    failure.addSuppressed(cleanupInterrupted)
                }
            }
            sessionIdFile.delete()
        }

        if (failure != null) {
            if (failure is InterruptedException) Thread.currentThread().interrupt()
            throw failure
        }
        return result ?: throw IllegalStateException("Terminal command did not produce a result")
    }

    private fun readSessionId(sessionIdFile: File): Long? =
        runCatching { sessionIdFile.readText().trim().toLongOrNull() }
            .getOrNull()
            ?.takeIf { it > 1L }

    private fun awaitSessionId(sessionIdFile: File, process: Process): Long? {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SESSION_ID_WAIT_MILLIS)
        while (System.nanoTime() < deadline) {
            val value = readSessionId(sessionIdFile)
            if (value != null) return value
            if (!process.isAlive) return null
            Thread.sleep(10L)
        }
        return readSessionId(sessionIdFile)
    }

    private fun quoteForShell(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private fun terminateProcessGroup(process: Process, sessionId: Long?) {
        if (sessionId != null) {
            signalProcessGroup(sessionId, "TERM")
            try {
                process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)
            } catch (interrupted: InterruptedException) {
                signalProcessGroup(sessionId, "KILL")
                process.destroyForcibly()
                runCatching { process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS) }
                throw interrupted
            }
            // Send KILL even when the session leader has exited: descendants can
            // outlive their shell while retaining the original process group.
            signalProcessGroup(sessionId, "KILL")
        }

        if (process.isAlive) {
            process.destroyForcibly()
            process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)
        }
    }

    private fun signalProcessGroup(sessionId: Long, signal: String) {
        val signalProcess = ProcessBuilder(signalLauncher + listOf("-$signal", "--", "-$sessionId"))
            .redirectOutput(File("/dev/null"))
            .redirectError(File("/dev/null"))
            .start()

        try {
            if (!signalProcess.waitFor(SIGNAL_COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                signalProcess.destroyForcibly()
                signalProcess.waitFor(READER_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            }
        } catch (interrupted: InterruptedException) {
            signalProcess.destroyForcibly()
            runCatching { signalProcess.waitFor(READER_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS) }
            throw interrupted
        }
    }

    private fun awaitCapture(
        future: Future<CapturedOutput>,
        deadlineNanos: Long,
        streamName: String,
    ): CapturedOutput? {
        return try {
            if (!future.isDone) {
                val remaining = deadlineNanos - System.nanoTime()
                if (remaining <= 0L) return null
                future.get(remaining, TimeUnit.NANOSECONDS)
            } else {
                future.get()
            }
        } catch (_: TimeoutException) {
            null
        } catch (failure: ExecutionException) {
            CapturedOutput("[$streamName capture failed: ${failure.cause?.message ?: "unknown error"}]", true)
        } catch (_: CancellationException) {
            CapturedOutput("[$streamName capture cancelled]", true)
        }
    }

    private fun isInsideWorkspace(candidate: File): Boolean {
        val root = workspaceRoot.absolutePath
        val path = candidate.absolutePath
        return path == root || path.startsWith(root + File.separator)
    }

    private fun capture(input: InputStream, budget: OutputBudget): CapturedOutput {
        val output = StringBuilder(minOf(maxOutputCharacters, 8_192))
        var truncated = false
        InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
            val buffer = CharArray(4_096)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                val accepted = budget.take(count)
                if (accepted > 0) output.append(buffer, 0, accepted)
                if (accepted < count) truncated = true
            }
        }
        return CapturedOutput(output.toString(), truncated)
    }

    private fun closeQuietly(closeable: Closeable) {
        runCatching { closeable.close() }
    }

    private fun detectShell(): String {
        val candidates = listOf("/system/bin/sh", "/bin/sh")
        return candidates.firstOrNull { File(it).canExecute() }
            ?: throw IllegalStateException("No supported system shell was found on this device")
    }

    private fun findUtility(name: String): List<String> {
        val candidates = when (name) {
            "setsid" -> listOf(
                listOf("/system/bin/setsid"),
                listOf("/system/bin/toybox", "setsid"),
                listOf("/usr/bin/setsid"),
                listOf("/bin/setsid"),
                listOf("/usr/bin/toybox", "setsid"),
                listOf("/bin/toybox", "setsid"),
            )
            "kill" -> listOf(
                listOf("/system/bin/kill"),
                listOf("/system/bin/toybox", "kill"),
                listOf("/usr/bin/kill"),
                listOf("/bin/kill"),
                listOf("/usr/bin/toybox", "kill"),
                listOf("/bin/toybox", "kill"),
            )
            else -> emptyList()
        }
        return candidates.firstOrNull { File(it.first()).canExecute() }
            ?: throw IllegalStateException("Required terminal utility is not available: $name")
    }

    companion object {
        private const val SESSION_ID_WAIT_MILLIS = 1_000L
        private const val PROCESS_TERMINATION_GRACE_MILLIS = 250L
        private const val SIGNAL_COMMAND_TIMEOUT_MILLIS = 500L
        private const val OUTPUT_DRAIN_TIMEOUT_MILLIS = 1_000L
        private const val OUTPUT_CLEANUP_TIMEOUT_MILLIS = 500L
        private const val READER_SHUTDOWN_TIMEOUT_MILLIS = 500L
    }
}
}3" "${'
            sessionProcess=${'$'}!
            wait "${'$'}sessionProcess"
            commandExitCode=${'$'}?
            sessionId=""
            if IFS= read -r sessionId < "${'$'}ASTRACODE_SESSION_PID_FILE"; then
                if [ -n "${'$'}sessionId" ]; then
                    $signalLauncherCommand -KILL -- "-${'$'}sessionId" >/dev/null 2>&1 || true
                fi
            fi
            exit "${'$'}commandExitCode"
        """.trimIndent()
        val launchCommand = listOf(
            shellPath,
            "-c",
            supervisor,
            "astracode-supervisor",
            shellPath,
            bootstrap,
            "astracode-session",
            shellPath,
            "-c",
            command,
        )

        val process = try {
            ProcessBuilder(launchCommand)
                .directory(cwd)
                .apply {
                    val env = environment()
                    env["HOME"] = workspaceRoot.absolutePath
                    env["TMPDIR"] = temporaryDirectory.absolutePath
                    env["ASTRACODE_SESSION_PID_FILE"] = sessionIdFile.absolutePath
                    val existingPath = env["PATH"].orEmpty()
                    env["PATH"] = listOf("/system/bin", "/system/xbin", "/usr/bin", "/bin", existingPath)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(File.pathSeparator)
                }
                .start()
        } catch (failure: Exception) {
            sessionIdFile.delete()
            throw failure
        }

        val readers = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "astracode-terminal-output").apply { isDaemon = true }
        }
        val outputBudget = OutputBudget(maxOutputCharacters)
        val stdoutFuture = readers.submit(Callable { capture(process.inputStream, outputBudget) })
        val stderrFuture = readers.submit(Callable { capture(process.errorStream, outputBudget) })

        var sessionId: Long? = null
        var timedOut = false
        var result: TerminalCommandResult? = null
        var failure: Exception? = null

        try {
            // This runner is non-interactive; EOF lets commands that read stdin
            // terminate instead of waiting until the timeout.
            process.outputStream.close()

            sessionId = awaitSessionId(sessionIdFile, process)
                ?: throw IOException("Could not establish an isolated terminal process session")

            val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!finished) {
                timedOut = true
                terminateProcessGroup(process, sessionId)
            }

            val outputDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(OUTPUT_DRAIN_TIMEOUT_MILLIS)
            var stdout = awaitCapture(stdoutFuture, outputDeadline, "stdout")
            var stderr = awaitCapture(stderrFuture, outputDeadline, "stderr")

            if (stdout == null || stderr == null) {
                // A child may have inherited a pipe and kept it open after the
                // shell exited. Stop the session, close both pipes, and give the
                // reader tasks one final bounded chance to finish.
                terminateProcessGroup(process, sessionId)
                closeQuietly(process.errorStream)
                val cleanupDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(OUTPUT_CLEANUP_TIMEOUT_MILLIS)
                stdout = stdout ?: awaitCapture(stdoutFuture, cleanupDeadline, "stdout")
                stderr = stderr ?: awaitCapture(stderrFuture, cleanupDeadline, "stderr")
            }

            val capturedStdout = stdout ?: CapturedOutput("[stdout capture did not stop after process cleanup]", true)
            val capturedStderr = stderr ?: CapturedOutput("[stderr capture did not stop after process cleanup]", true)

            result = TerminalCommandResult(
                command = command,
                workingDirectory = cwd.absolutePath,
                exitCode = if (timedOut) null else process.exitValue(),
                standardOutput = capturedStdout.text,
                standardError = capturedStderr.text,
                timedOut = timedOut,
                outputTruncated = capturedStdout.truncated || capturedStderr.truncated,
                durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
            )
        } catch (exception: Exception) {
            failure = exception
            sessionId = sessionId ?: readSessionId(sessionIdFile)
            if (sessionId == null && process.isAlive) {
                // Failure or interruption can race with the tiny bootstrap
                // step that records the new session ID. Give it a bounded
                // chance to publish the ID before falling back to supervisor
                // cleanup.
                sessionId = runCatching { awaitSessionId(sessionIdFile, process) }.getOrNull()
            }
            try {
                terminateProcessGroup(process, sessionId)
            } catch (cleanupFailure: Exception) {
                exception.addSuppressed(cleanupFailure)
            }
        } finally {
            closeQuietly(process.outputStream)
            closeQuietly(process.inputStream)
            closeQuietly(process.errorStream)
            stdoutFuture.cancel(true)
            stderrFuture.cancel(true)
            readers.shutdownNow()
            try {
                readers.awaitTermination(READER_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            } catch (cleanupInterrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                if (failure == null) {
                    failure = cleanupInterrupted
                } else {
                    failure.addSuppressed(cleanupInterrupted)
                }
            }
            sessionIdFile.delete()
        }

        if (failure != null) {
            if (failure is InterruptedException) Thread.currentThread().interrupt()
            throw failure
        }
        return result ?: throw IllegalStateException("Terminal command did not produce a result")
    }

    private fun readSessionId(sessionIdFile: File): Long? =
        runCatching { sessionIdFile.readText().trim().toLongOrNull() }
            .getOrNull()
            ?.takeIf { it > 1L }

    private fun awaitSessionId(sessionIdFile: File, process: Process): Long? {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SESSION_ID_WAIT_MILLIS)
        while (System.nanoTime() < deadline) {
            val value = readSessionId(sessionIdFile)
            if (value != null) return value
            if (!process.isAlive) return null
            Thread.sleep(10L)
        }
        return readSessionId(sessionIdFile)
    }

    private fun quoteForShell(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private fun terminateProcessGroup(process: Process, sessionId: Long?) {
        if (sessionId != null) {
            signalProcessGroup(sessionId, "TERM")
            try {
                process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)
            } catch (interrupted: InterruptedException) {
                signalProcessGroup(sessionId, "KILL")
                process.destroyForcibly()
                runCatching { process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS) }
                throw interrupted
            }
            // Send KILL even when the session leader has exited: descendants can
            // outlive their shell while retaining the original process group.
            signalProcessGroup(sessionId, "KILL")
        }

        if (process.isAlive) {
            process.destroyForcibly()
            process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)
        }
    }

    private fun signalProcessGroup(sessionId: Long, signal: String) {
        val signalProcess = ProcessBuilder(signalLauncher + listOf("-$signal", "--", "-$sessionId"))
            .redirectOutput(File("/dev/null"))
            .redirectError(File("/dev/null"))
            .start()

        try {
            if (!signalProcess.waitFor(SIGNAL_COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                signalProcess.destroyForcibly()
                signalProcess.waitFor(READER_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            }
        } catch (interrupted: InterruptedException) {
            signalProcess.destroyForcibly()
            runCatching { signalProcess.waitFor(READER_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS) }
            throw interrupted
        }
    }

    private fun awaitCapture(
        future: Future<CapturedOutput>,
        deadlineNanos: Long,
        streamName: String,
    ): CapturedOutput? {
        return try {
            if (!future.isDone) {
                val remaining = deadlineNanos - System.nanoTime()
                if (remaining <= 0L) return null
                future.get(remaining, TimeUnit.NANOSECONDS)
            } else {
                future.get()
            }
        } catch (_: TimeoutException) {
            null
        } catch (failure: ExecutionException) {
            CapturedOutput("[$streamName capture failed: ${failure.cause?.message ?: "unknown error"}]", true)
        } catch (_: CancellationException) {
            CapturedOutput("[$streamName capture cancelled]", true)
        }
    }

    private fun isInsideWorkspace(candidate: File): Boolean {
        val root = workspaceRoot.absolutePath
        val path = candidate.absolutePath
        return path == root || path.startsWith(root + File.separator)
    }

    private fun capture(input: InputStream, budget: OutputBudget): CapturedOutput {
        val output = StringBuilder(minOf(maxOutputCharacters, 8_192))
        var truncated = false
        InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
            val buffer = CharArray(4_096)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                val accepted = budget.take(count)
                if (accepted > 0) output.append(buffer, 0, accepted)
                if (accepted < count) truncated = true
            }
        }
        return CapturedOutput(output.toString(), truncated)
    }

    private fun closeQuietly(closeable: Closeable) {
        runCatching { closeable.close() }
    }

    private fun detectShell(): String {
        val candidates = listOf("/system/bin/sh", "/bin/sh")
        return candidates.firstOrNull { File(it).canExecute() }
            ?: throw IllegalStateException("No supported system shell was found on this device")
    }

    private fun findUtility(name: String): List<String> {
        val candidates = when (name) {
            "setsid" -> listOf(
                listOf("/system/bin/setsid"),
                listOf("/system/bin/toybox", "setsid"),
                listOf("/usr/bin/setsid"),
                listOf("/bin/setsid"),
                listOf("/usr/bin/toybox", "setsid"),
                listOf("/bin/toybox", "setsid"),
            )
            "kill" -> listOf(
                listOf("/system/bin/kill"),
                listOf("/system/bin/toybox", "kill"),
                listOf("/usr/bin/kill"),
                listOf("/bin/kill"),
                listOf("/usr/bin/toybox", "kill"),
                listOf("/bin/toybox", "kill"),
            )
            else -> emptyList()
        }
        return candidates.firstOrNull { File(it.first()).canExecute() }
            ?: throw IllegalStateException("Required terminal utility is not available: $name")
    }

    companion object {
        private const val SESSION_ID_WAIT_MILLIS = 1_000L
        private const val PROCESS_TERMINATION_GRACE_MILLIS = 250L
        private const val SIGNAL_COMMAND_TIMEOUT_MILLIS = 500L
        private const val OUTPUT_DRAIN_TIMEOUT_MILLIS = 1_000L
        private const val OUTPUT_CLEANUP_TIMEOUT_MILLIS = 500L
        private const val READER_SHUTDOWN_TIMEOUT_MILLIS = 500L
    }
}
}4" "${'
            sessionProcess=${'$'}!
            wait "${'$'}sessionProcess"
            commandExitCode=${'$'}?
            sessionId=""
            if IFS= read -r sessionId < "${'$'}ASTRACODE_SESSION_PID_FILE"; then
                if [ -n "${'$'}sessionId" ]; then
                    $signalLauncherCommand -KILL -- "-${'$'}sessionId" >/dev/null 2>&1 || true
                fi
            fi
            exit "${'$'}commandExitCode"
        """.trimIndent()
        val launchCommand = listOf(
            shellPath,
            "-c",
            supervisor,
            "astracode-supervisor",
            shellPath,
            bootstrap,
            "astracode-session",
            shellPath,
            "-c",
            command,
        )

        val process = try {
            ProcessBuilder(launchCommand)
                .directory(cwd)
                .apply {
                    val env = environment()
                    env["HOME"] = workspaceRoot.absolutePath
                    env["TMPDIR"] = temporaryDirectory.absolutePath
                    env["ASTRACODE_SESSION_PID_FILE"] = sessionIdFile.absolutePath
                    val existingPath = env["PATH"].orEmpty()
                    env["PATH"] = listOf("/system/bin", "/system/xbin", "/usr/bin", "/bin", existingPath)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(File.pathSeparator)
                }
                .start()
        } catch (failure: Exception) {
            sessionIdFile.delete()
            throw failure
        }

        val readers = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "astracode-terminal-output").apply { isDaemon = true }
        }
        val outputBudget = OutputBudget(maxOutputCharacters)
        val stdoutFuture = readers.submit(Callable { capture(process.inputStream, outputBudget) })
        val stderrFuture = readers.submit(Callable { capture(process.errorStream, outputBudget) })

        var sessionId: Long? = null
        var timedOut = false
        var result: TerminalCommandResult? = null
        var failure: Exception? = null

        try {
            // This runner is non-interactive; EOF lets commands that read stdin
            // terminate instead of waiting until the timeout.
            process.outputStream.close()

            sessionId = awaitSessionId(sessionIdFile, process)
                ?: throw IOException("Could not establish an isolated terminal process session")

            val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!finished) {
                timedOut = true
                terminateProcessGroup(process, sessionId)
            }

            val outputDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(OUTPUT_DRAIN_TIMEOUT_MILLIS)
            var stdout = awaitCapture(stdoutFuture, outputDeadline, "stdout")
            var stderr = awaitCapture(stderrFuture, outputDeadline, "stderr")

            if (stdout == null || stderr == null) {
                // A child may have inherited a pipe and kept it open after the
                // shell exited. Stop the session, close both pipes, and give the
                // reader tasks one final bounded chance to finish.
                terminateProcessGroup(process, sessionId)
                closeQuietly(process.errorStream)
                val cleanupDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(OUTPUT_CLEANUP_TIMEOUT_MILLIS)
                stdout = stdout ?: awaitCapture(stdoutFuture, cleanupDeadline, "stdout")
                stderr = stderr ?: awaitCapture(stderrFuture, cleanupDeadline, "stderr")
            }

            val capturedStdout = stdout ?: CapturedOutput("[stdout capture did not stop after process cleanup]", true)
            val capturedStderr = stderr ?: CapturedOutput("[stderr capture did not stop after process cleanup]", true)

            result = TerminalCommandResult(
                command = command,
                workingDirectory = cwd.absolutePath,
                exitCode = if (timedOut) null else process.exitValue(),
                standardOutput = capturedStdout.text,
                standardError = capturedStderr.text,
                timedOut = timedOut,
                outputTruncated = capturedStdout.truncated || capturedStderr.truncated,
                durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
            )
        } catch (exception: Exception) {
            failure = exception
            sessionId = sessionId ?: readSessionId(sessionIdFile)
            if (sessionId == null && process.isAlive) {
                // Failure or interruption can race with the tiny bootstrap
                // step that records the new session ID. Give it a bounded
                // chance to publish the ID before falling back to supervisor
                // cleanup.
                sessionId = runCatching { awaitSessionId(sessionIdFile, process) }.getOrNull()
            }
            try {
                terminateProcessGroup(process, sessionId)
            } catch (cleanupFailure: Exception) {
                exception.addSuppressed(cleanupFailure)
            }
        } finally {
            closeQuietly(process.outputStream)
            closeQuietly(process.inputStream)
            closeQuietly(process.errorStream)
            stdoutFuture.cancel(true)
            stderrFuture.cancel(true)
            readers.shutdownNow()
            try {
                readers.awaitTermination(READER_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            } catch (cleanupInterrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                if (failure == null) {
                    failure = cleanupInterrupted
                } else {
                    failure.addSuppressed(cleanupInterrupted)
                }
            }
            sessionIdFile.delete()
        }

        if (failure != null) {
            if (failure is InterruptedException) Thread.currentThread().interrupt()
            throw failure
        }
        return result ?: throw IllegalStateException("Terminal command did not produce a result")
    }

    private fun readSessionId(sessionIdFile: File): Long? =
        runCatching { sessionIdFile.readText().trim().toLongOrNull() }
            .getOrNull()
            ?.takeIf { it > 1L }

    private fun awaitSessionId(sessionIdFile: File, process: Process): Long? {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SESSION_ID_WAIT_MILLIS)
        while (System.nanoTime() < deadline) {
            val value = readSessionId(sessionIdFile)
            if (value != null) return value
            if (!process.isAlive) return null
            Thread.sleep(10L)
        }
        return readSessionId(sessionIdFile)
    }

    private fun quoteForShell(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private fun terminateProcessGroup(process: Process, sessionId: Long?) {
        if (sessionId != null) {
            signalProcessGroup(sessionId, "TERM")
            try {
                process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)
            } catch (interrupted: InterruptedException) {
                signalProcessGroup(sessionId, "KILL")
                process.destroyForcibly()
                runCatching { process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS) }
                throw interrupted
            }
            // Send KILL even when the session leader has exited: descendants can
            // outlive their shell while retaining the original process group.
            signalProcessGroup(sessionId, "KILL")
        }

        if (process.isAlive) {
            process.destroyForcibly()
            process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)
        }
    }

    private fun signalProcessGroup(sessionId: Long, signal: String) {
        val signalProcess = ProcessBuilder(signalLauncher + listOf("-$signal", "--", "-$sessionId"))
            .redirectOutput(File("/dev/null"))
            .redirectError(File("/dev/null"))
            .start()

        try {
            if (!signalProcess.waitFor(SIGNAL_COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                signalProcess.destroyForcibly()
                signalProcess.waitFor(READER_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            }
        } catch (interrupted: InterruptedException) {
            signalProcess.destroyForcibly()
            runCatching { signalProcess.waitFor(READER_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS) }
            throw interrupted
        }
    }

    private fun awaitCapture(
        future: Future<CapturedOutput>,
        deadlineNanos: Long,
        streamName: String,
    ): CapturedOutput? {
        return try {
            if (!future.isDone) {
                val remaining = deadlineNanos - System.nanoTime()
                if (remaining <= 0L) return null
                future.get(remaining, TimeUnit.NANOSECONDS)
            } else {
                future.get()
            }
        } catch (_: TimeoutException) {
            null
        } catch (failure: ExecutionException) {
            CapturedOutput("[$streamName capture failed: ${failure.cause?.message ?: "unknown error"}]", true)
        } catch (_: CancellationException) {
            CapturedOutput("[$streamName capture cancelled]", true)
        }
    }

    private fun isInsideWorkspace(candidate: File): Boolean {
        val root = workspaceRoot.absolutePath
        val path = candidate.absolutePath
        return path == root || path.startsWith(root + File.separator)
    }

    private fun capture(input: InputStream, budget: OutputBudget): CapturedOutput {
        val output = StringBuilder(minOf(maxOutputCharacters, 8_192))
        var truncated = false
        InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
            val buffer = CharArray(4_096)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                val accepted = budget.take(count)
                if (accepted > 0) output.append(buffer, 0, accepted)
                if (accepted < count) truncated = true
            }
        }
        return CapturedOutput(output.toString(), truncated)
    }

    private fun closeQuietly(closeable: Closeable) {
        runCatching { closeable.close() }
    }

    private fun detectShell(): String {
        val candidates = listOf("/system/bin/sh", "/bin/sh")
        return candidates.firstOrNull { File(it).canExecute() }
            ?: throw IllegalStateException("No supported system shell was found on this device")
    }

    private fun findUtility(name: String): List<String> {
        val candidates = when (name) {
            "setsid" -> listOf(
                listOf("/system/bin/setsid"),
                listOf("/system/bin/toybox", "setsid"),
                listOf("/usr/bin/setsid"),
                listOf("/bin/setsid"),
                listOf("/usr/bin/toybox", "setsid"),
                listOf("/bin/toybox", "setsid"),
            )
            "kill" -> listOf(
                listOf("/system/bin/kill"),
                listOf("/system/bin/toybox", "kill"),
                listOf("/usr/bin/kill"),
                listOf("/bin/kill"),
                listOf("/usr/bin/toybox", "kill"),
                listOf("/bin/toybox", "kill"),
            )
            else -> emptyList()
        }
        return candidates.firstOrNull { File(it.first()).canExecute() }
            ?: throw IllegalStateException("Required terminal utility is not available: $name")
    }

    companion object {
        private const val SESSION_ID_WAIT_MILLIS = 1_000L
        private const val PROCESS_TERMINATION_GRACE_MILLIS = 250L
        private const val SIGNAL_COMMAND_TIMEOUT_MILLIS = 500L
        private const val OUTPUT_DRAIN_TIMEOUT_MILLIS = 1_000L
        private const val OUTPUT_CLEANUP_TIMEOUT_MILLIS = 500L
        private const val READER_SHUTDOWN_TIMEOUT_MILLIS = 500L
    }
}
}5" "${'
            sessionProcess=${'$'}!
            wait "${'$'}sessionProcess"
            commandExitCode=${'$'}?
            sessionId=""
            if IFS= read -r sessionId < "${'$'}ASTRACODE_SESSION_PID_FILE"; then
                if [ -n "${'$'}sessionId" ]; then
                    $signalLauncherCommand -KILL -- "-${'$'}sessionId" >/dev/null 2>&1 || true
                fi
            fi
            exit "${'$'}commandExitCode"
        """.trimIndent()
        val launchCommand = listOf(
            shellPath,
            "-c",
            supervisor,
            "astracode-supervisor",
            shellPath,
            bootstrap,
            "astracode-session",
            shellPath,
            "-c",
            command,
        )

        val process = try {
            ProcessBuilder(launchCommand)
                .directory(cwd)
                .apply {
                    val env = environment()
                    env["HOME"] = workspaceRoot.absolutePath
                    env["TMPDIR"] = temporaryDirectory.absolutePath
                    env["ASTRACODE_SESSION_PID_FILE"] = sessionIdFile.absolutePath
                    val existingPath = env["PATH"].orEmpty()
                    env["PATH"] = listOf("/system/bin", "/system/xbin", "/usr/bin", "/bin", existingPath)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(File.pathSeparator)
                }
                .start()
        } catch (failure: Exception) {
            sessionIdFile.delete()
            throw failure
        }

        val readers = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "astracode-terminal-output").apply { isDaemon = true }
        }
        val outputBudget = OutputBudget(maxOutputCharacters)
        val stdoutFuture = readers.submit(Callable { capture(process.inputStream, outputBudget) })
        val stderrFuture = readers.submit(Callable { capture(process.errorStream, outputBudget) })

        var sessionId: Long? = null
        var timedOut = false
        var result: TerminalCommandResult? = null
        var failure: Exception? = null

        try {
            // This runner is non-interactive; EOF lets commands that read stdin
            // terminate instead of waiting until the timeout.
            process.outputStream.close()

            sessionId = awaitSessionId(sessionIdFile, process)
                ?: throw IOException("Could not establish an isolated terminal process session")

            val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!finished) {
                timedOut = true
                terminateProcessGroup(process, sessionId)
            }

            val outputDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(OUTPUT_DRAIN_TIMEOUT_MILLIS)
            var stdout = awaitCapture(stdoutFuture, outputDeadline, "stdout")
            var stderr = awaitCapture(stderrFuture, outputDeadline, "stderr")

            if (stdout == null || stderr == null) {
                // A child may have inherited a pipe and kept it open after the
                // shell exited. Stop the session, close both pipes, and give the
                // reader tasks one final bounded chance to finish.
                terminateProcessGroup(process, sessionId)
                closeQuietly(process.errorStream)
                val cleanupDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(OUTPUT_CLEANUP_TIMEOUT_MILLIS)
                stdout = stdout ?: awaitCapture(stdoutFuture, cleanupDeadline, "stdout")
                stderr = stderr ?: awaitCapture(stderrFuture, cleanupDeadline, "stderr")
            }

            val capturedStdout = stdout ?: CapturedOutput("[stdout capture did not stop after process cleanup]", true)
            val capturedStderr = stderr ?: CapturedOutput("[stderr capture did not stop after process cleanup]", true)

            result = TerminalCommandResult(
                command = command,
                workingDirectory = cwd.absolutePath,
                exitCode = if (timedOut) null else process.exitValue(),
                standardOutput = capturedStdout.text,
                standardError = capturedStderr.text,
                timedOut = timedOut,
                outputTruncated = capturedStdout.truncated || capturedStderr.truncated,
                durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
            )
        } catch (exception: Exception) {
            failure = exception
            sessionId = sessionId ?: readSessionId(sessionIdFile)
            if (sessionId == null && process.isAlive) {
                // Failure or interruption can race with the tiny bootstrap
                // step that records the new session ID. Give it a bounded
                // chance to publish the ID before falling back to supervisor
                // cleanup.
                sessionId = runCatching { awaitSessionId(sessionIdFile, process) }.getOrNull()
            }
            try {
                terminateProcessGroup(process, sessionId)
            } catch (cleanupFailure: Exception) {
                exception.addSuppressed(cleanupFailure)
            }
        } finally {
            closeQuietly(process.outputStream)
            closeQuietly(process.inputStream)
            closeQuietly(process.errorStream)
            stdoutFuture.cancel(true)
            stderrFuture.cancel(true)
            readers.shutdownNow()
            try {
                readers.awaitTermination(READER_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            } catch (cleanupInterrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                if (failure == null) {
                    failure = cleanupInterrupted
                } else {
                    failure.addSuppressed(cleanupInterrupted)
                }
            }
            sessionIdFile.delete()
        }

        if (failure != null) {
            if (failure is InterruptedException) Thread.currentThread().interrupt()
            throw failure
        }
        return result ?: throw IllegalStateException("Terminal command did not produce a result")
    }

    private fun readSessionId(sessionIdFile: File): Long? =
        runCatching { sessionIdFile.readText().trim().toLongOrNull() }
            .getOrNull()
            ?.takeIf { it > 1L }

    private fun awaitSessionId(sessionIdFile: File, process: Process): Long? {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SESSION_ID_WAIT_MILLIS)
        while (System.nanoTime() < deadline) {
            val value = readSessionId(sessionIdFile)
            if (value != null) return value
            if (!process.isAlive) return null
            Thread.sleep(10L)
        }
        return readSessionId(sessionIdFile)
    }

    private fun quoteForShell(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private fun terminateProcessGroup(process: Process, sessionId: Long?) {
        if (sessionId != null) {
            signalProcessGroup(sessionId, "TERM")
            try {
                process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)
            } catch (interrupted: InterruptedException) {
                signalProcessGroup(sessionId, "KILL")
                process.destroyForcibly()
                runCatching { process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS) }
                throw interrupted
            }
            // Send KILL even when the session leader has exited: descendants can
            // outlive their shell while retaining the original process group.
            signalProcessGroup(sessionId, "KILL")
        }

        if (process.isAlive) {
            process.destroyForcibly()
            process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)
        }
    }

    private fun signalProcessGroup(sessionId: Long, signal: String) {
        val signalProcess = ProcessBuilder(signalLauncher + listOf("-$signal", "--", "-$sessionId"))
            .redirectOutput(File("/dev/null"))
            .redirectError(File("/dev/null"))
            .start()

        try {
            if (!signalProcess.waitFor(SIGNAL_COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                signalProcess.destroyForcibly()
                signalProcess.waitFor(READER_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            }
        } catch (interrupted: InterruptedException) {
            signalProcess.destroyForcibly()
            runCatching { signalProcess.waitFor(READER_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS) }
            throw interrupted
        }
    }

    private fun awaitCapture(
        future: Future<CapturedOutput>,
        deadlineNanos: Long,
        streamName: String,
    ): CapturedOutput? {
        return try {
            if (!future.isDone) {
                val remaining = deadlineNanos - System.nanoTime()
                if (remaining <= 0L) return null
                future.get(remaining, TimeUnit.NANOSECONDS)
            } else {
                future.get()
            }
        } catch (_: TimeoutException) {
            null
        } catch (failure: ExecutionException) {
            CapturedOutput("[$streamName capture failed: ${failure.cause?.message ?: "unknown error"}]", true)
        } catch (_: CancellationException) {
            CapturedOutput("[$streamName capture cancelled]", true)
        }
    }

    private fun isInsideWorkspace(candidate: File): Boolean {
        val root = workspaceRoot.absolutePath
        val path = candidate.absolutePath
        return path == root || path.startsWith(root + File.separator)
    }

    private fun capture(input: InputStream, budget: OutputBudget): CapturedOutput {
        val output = StringBuilder(minOf(maxOutputCharacters, 8_192))
        var truncated = false
        InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
            val buffer = CharArray(4_096)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                val accepted = budget.take(count)
                if (accepted > 0) output.append(buffer, 0, accepted)
                if (accepted < count) truncated = true
            }
        }
        return CapturedOutput(output.toString(), truncated)
    }

    private fun closeQuietly(closeable: Closeable) {
        runCatching { closeable.close() }
    }

    private fun detectShell(): String {
        val candidates = listOf("/system/bin/sh", "/bin/sh")
        return candidates.firstOrNull { File(it).canExecute() }
            ?: throw IllegalStateException("No supported system shell was found on this device")
    }

    private fun findUtility(name: String): List<String> {
        val candidates = when (name) {
            "setsid" -> listOf(
                listOf("/system/bin/setsid"),
                listOf("/system/bin/toybox", "setsid"),
                listOf("/usr/bin/setsid"),
                listOf("/bin/setsid"),
                listOf("/usr/bin/toybox", "setsid"),
                listOf("/bin/toybox", "setsid"),
            )
            "kill" -> listOf(
                listOf("/system/bin/kill"),
                listOf("/system/bin/toybox", "kill"),
                listOf("/usr/bin/kill"),
                listOf("/bin/kill"),
                listOf("/usr/bin/toybox", "kill"),
                listOf("/bin/toybox", "kill"),
            )
            else -> emptyList()
        }
        return candidates.firstOrNull { File(it.first()).canExecute() }
            ?: throw IllegalStateException("Required terminal utility is not available: $name")
    }

    companion object {
        private const val SESSION_ID_WAIT_MILLIS = 1_000L
        private const val PROCESS_TERMINATION_GRACE_MILLIS = 250L
        private const val SIGNAL_COMMAND_TIMEOUT_MILLIS = 500L
        private const val OUTPUT_DRAIN_TIMEOUT_MILLIS = 1_000L
        private const val OUTPUT_CLEANUP_TIMEOUT_MILLIS = 500L
        private const val READER_SHUTDOWN_TIMEOUT_MILLIS = 500L
    }
}
}6" &
            sessionProcess=${'$'}!
            wait "${'$'}sessionProcess"
            commandExitCode=${'$'}?
            sessionId=""
            if IFS= read -r sessionId < "${'$'}ASTRACODE_SESSION_PID_FILE"; then
                if [ -n "${'$'}sessionId" ]; then
                    $signalLauncherCommand -KILL -- "-${'$'}sessionId" >/dev/null 2>&1 || true
                fi
            fi
            exit "${'$'}commandExitCode"
        """.trimIndent()
        val launchCommand = listOf(
            shellPath,
            "-c",
            supervisor,
            "astracode-supervisor",
            shellPath,
            bootstrap,
            "astracode-session",
            shellPath,
            "-c",
            command,
        )

        val process = try {
            ProcessBuilder(launchCommand)
                .directory(cwd)
                .apply {
                    val env = environment()
                    env["HOME"] = workspaceRoot.absolutePath
                    env["TMPDIR"] = temporaryDirectory.absolutePath
                    env["ASTRACODE_SESSION_PID_FILE"] = sessionIdFile.absolutePath
                    val existingPath = env["PATH"].orEmpty()
                    env["PATH"] = listOf("/system/bin", "/system/xbin", "/usr/bin", "/bin", existingPath)
                        .filter { it.isNotBlank() }
                        .distinct()
                        .joinToString(File.pathSeparator)
                }
                .start()
        } catch (failure: Exception) {
            sessionIdFile.delete()
            throw failure
        }

        val readers = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "astracode-terminal-output").apply { isDaemon = true }
        }
        val outputBudget = OutputBudget(maxOutputCharacters)
        val stdoutFuture = readers.submit(Callable { capture(process.inputStream, outputBudget) })
        val stderrFuture = readers.submit(Callable { capture(process.errorStream, outputBudget) })

        var sessionId: Long? = null
        var timedOut = false
        var result: TerminalCommandResult? = null
        var failure: Exception? = null

        try {
            // This runner is non-interactive; EOF lets commands that read stdin
            // terminate instead of waiting until the timeout.
            process.outputStream.close()

            sessionId = awaitSessionId(sessionIdFile, process)
                ?: throw IOException("Could not establish an isolated terminal process session")

            val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!finished) {
                timedOut = true
                terminateProcessGroup(process, sessionId)
            }

            val outputDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(OUTPUT_DRAIN_TIMEOUT_MILLIS)
            var stdout = awaitCapture(stdoutFuture, outputDeadline, "stdout")
            var stderr = awaitCapture(stderrFuture, outputDeadline, "stderr")

            if (stdout == null || stderr == null) {
                // A child may have inherited a pipe and kept it open after the
                // shell exited. Stop the session, close both pipes, and give the
                // reader tasks one final bounded chance to finish.
                terminateProcessGroup(process, sessionId)
                closeQuietly(process.errorStream)
                val cleanupDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(OUTPUT_CLEANUP_TIMEOUT_MILLIS)
                stdout = stdout ?: awaitCapture(stdoutFuture, cleanupDeadline, "stdout")
                stderr = stderr ?: awaitCapture(stderrFuture, cleanupDeadline, "stderr")
            }

            val capturedStdout = stdout ?: CapturedOutput("[stdout capture did not stop after process cleanup]", true)
            val capturedStderr = stderr ?: CapturedOutput("[stderr capture did not stop after process cleanup]", true)

            result = TerminalCommandResult(
                command = command,
                workingDirectory = cwd.absolutePath,
                exitCode = if (timedOut) null else process.exitValue(),
                standardOutput = capturedStdout.text,
                standardError = capturedStderr.text,
                timedOut = timedOut,
                outputTruncated = capturedStdout.truncated || capturedStderr.truncated,
                durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
            )
        } catch (exception: Exception) {
            failure = exception
            sessionId = sessionId ?: readSessionId(sessionIdFile)
            if (sessionId == null && process.isAlive) {
                // Failure or interruption can race with the tiny bootstrap
                // step that records the new session ID. Give it a bounded
                // chance to publish the ID before falling back to supervisor
                // cleanup.
                sessionId = runCatching { awaitSessionId(sessionIdFile, process) }.getOrNull()
            }
            try {
                terminateProcessGroup(process, sessionId)
            } catch (cleanupFailure: Exception) {
                exception.addSuppressed(cleanupFailure)
            }
        } finally {
            closeQuietly(process.outputStream)
            closeQuietly(process.inputStream)
            closeQuietly(process.errorStream)
            stdoutFuture.cancel(true)
            stderrFuture.cancel(true)
            readers.shutdownNow()
            try {
                readers.awaitTermination(READER_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            } catch (cleanupInterrupted: InterruptedException) {
                Thread.currentThread().interrupt()
                if (failure == null) {
                    failure = cleanupInterrupted
                } else {
                    failure.addSuppressed(cleanupInterrupted)
                }
            }
            sessionIdFile.delete()
        }

        if (failure != null) {
            if (failure is InterruptedException) Thread.currentThread().interrupt()
            throw failure
        }
        return result ?: throw IllegalStateException("Terminal command did not produce a result")
    }

    private fun readSessionId(sessionIdFile: File): Long? =
        runCatching { sessionIdFile.readText().trim().toLongOrNull() }
            .getOrNull()
            ?.takeIf { it > 1L }

    private fun awaitSessionId(sessionIdFile: File, process: Process): Long? {
        val deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(SESSION_ID_WAIT_MILLIS)
        while (System.nanoTime() < deadline) {
            val value = readSessionId(sessionIdFile)
            if (value != null) return value
            if (!process.isAlive) return null
            Thread.sleep(10L)
        }
        return readSessionId(sessionIdFile)
    }

    private fun quoteForShell(value: String): String =
        "'" + value.replace("'", "'\\''") + "'"

    private fun terminateProcessGroup(process: Process, sessionId: Long?) {
        if (sessionId != null) {
            signalProcessGroup(sessionId, "TERM")
            try {
                process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)
            } catch (interrupted: InterruptedException) {
                signalProcessGroup(sessionId, "KILL")
                process.destroyForcibly()
                runCatching { process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS) }
                throw interrupted
            }
            // Send KILL even when the session leader has exited: descendants can
            // outlive their shell while retaining the original process group.
            signalProcessGroup(sessionId, "KILL")
        }

        if (process.isAlive) {
            process.destroyForcibly()
            process.waitFor(PROCESS_TERMINATION_GRACE_MILLIS, TimeUnit.MILLISECONDS)
        }
    }

    private fun signalProcessGroup(sessionId: Long, signal: String) {
        val signalProcess = ProcessBuilder(signalLauncher + listOf("-$signal", "--", "-$sessionId"))
            .redirectOutput(File("/dev/null"))
            .redirectError(File("/dev/null"))
            .start()

        try {
            if (!signalProcess.waitFor(SIGNAL_COMMAND_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)) {
                signalProcess.destroyForcibly()
                signalProcess.waitFor(READER_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS)
            }
        } catch (interrupted: InterruptedException) {
            signalProcess.destroyForcibly()
            runCatching { signalProcess.waitFor(READER_SHUTDOWN_TIMEOUT_MILLIS, TimeUnit.MILLISECONDS) }
            throw interrupted
        }
    }

    private fun awaitCapture(
        future: Future<CapturedOutput>,
        deadlineNanos: Long,
        streamName: String,
    ): CapturedOutput? {
        return try {
            if (!future.isDone) {
                val remaining = deadlineNanos - System.nanoTime()
                if (remaining <= 0L) return null
                future.get(remaining, TimeUnit.NANOSECONDS)
            } else {
                future.get()
            }
        } catch (_: TimeoutException) {
            null
        } catch (failure: ExecutionException) {
            CapturedOutput("[$streamName capture failed: ${failure.cause?.message ?: "unknown error"}]", true)
        } catch (_: CancellationException) {
            CapturedOutput("[$streamName capture cancelled]", true)
        }
    }

    private fun isInsideWorkspace(candidate: File): Boolean {
        val root = workspaceRoot.absolutePath
        val path = candidate.absolutePath
        return path == root || path.startsWith(root + File.separator)
    }

    private fun capture(input: InputStream, budget: OutputBudget): CapturedOutput {
        val output = StringBuilder(minOf(maxOutputCharacters, 8_192))
        var truncated = false
        InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
            val buffer = CharArray(4_096)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                val accepted = budget.take(count)
                if (accepted > 0) output.append(buffer, 0, accepted)
                if (accepted < count) truncated = true
            }
        }
        return CapturedOutput(output.toString(), truncated)
    }

    private fun closeQuietly(closeable: Closeable) {
        runCatching { closeable.close() }
    }

    private fun detectShell(): String {
        val candidates = listOf("/system/bin/sh", "/bin/sh")
        return candidates.firstOrNull { File(it).canExecute() }
            ?: throw IllegalStateException("No supported system shell was found on this device")
    }

    private fun findUtility(name: String): List<String> {
        val candidates = when (name) {
            "setsid" -> listOf(
                listOf("/system/bin/setsid"),
                listOf("/system/bin/toybox", "setsid"),
                listOf("/usr/bin/setsid"),
                listOf("/bin/setsid"),
                listOf("/usr/bin/toybox", "setsid"),
                listOf("/bin/toybox", "setsid"),
            )
            "kill" -> listOf(
                listOf("/system/bin/kill"),
                listOf("/system/bin/toybox", "kill"),
                listOf("/usr/bin/kill"),
                listOf("/bin/kill"),
                listOf("/usr/bin/toybox", "kill"),
                listOf("/bin/toybox", "kill"),
            )
            else -> emptyList()
        }
        return candidates.firstOrNull { File(it.first()).canExecute() }
            ?: throw IllegalStateException("Required terminal utility is not available: $name")
    }

    companion object {
        private const val SESSION_ID_WAIT_MILLIS = 1_000L
        private const val PROCESS_TERMINATION_GRACE_MILLIS = 250L
        private const val SIGNAL_COMMAND_TIMEOUT_MILLIS = 500L
        private const val OUTPUT_DRAIN_TIMEOUT_MILLIS = 1_000L
        private const val OUTPUT_CLEANUP_TIMEOUT_MILLIS = 500L
        private const val READER_SHUTDOWN_TIMEOUT_MILLIS = 500L
    }
}

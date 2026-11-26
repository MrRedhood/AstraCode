package com.astracode.terminal

import java.io.BufferedReader
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

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

/**
 * Executes user-entered commands in the Android app's own UID and workspace.
 *
 * On Android this uses /system/bin/sh and the OS-provided utilities (normally
 * Toybox). It is deliberately not described as a full Linux distribution:
 * PRoot, a Linux root filesystem, package managers and PTY-backed TUI programs
 * require a separate, device-compatible userland installation.
 */
class TerminalCommandRunner(
    workspaceRoot: File,
    private val maxOutputCharacters: Int = 128 * 1024,
    private val maximumTimeoutMillis: Long = 120_000L,
    shellOverride: String? = null,
) {
    val workspaceRoot: File = workspaceRoot.canonicalFile
    val shellPath: String = shellOverride ?: detectShell()

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
        val process = ProcessBuilder(shellPath, "-c", command)
            .directory(cwd)
            .apply {
                val env = environment()
                env["HOME"] = workspaceRoot.absolutePath
                env["TMPDIR"] = File(workspaceRoot, ".tmp").apply { mkdirs() }.absolutePath
                val existingPath = env["PATH"].orEmpty()
                env["PATH"] = listOf("/system/bin", "/system/xbin", "/usr/bin", "/bin", existingPath)
                    .filter { it.isNotBlank() }
                    .distinct()
                    .joinToString(File.pathSeparator)
            }
            .start()

        val readers = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "astracode-terminal-output").apply { isDaemon = true }
        }
        val stdoutFuture = readers.submit(Callable { capture(process.inputStream, maxOutputCharacters) })
        val stderrFuture = readers.submit(Callable { capture(process.errorStream, maxOutputCharacters) })

        var timedOut = false
        try {
            val finished = process.waitFor(timeoutMillis, TimeUnit.MILLISECONDS)
            if (!finished) {
                timedOut = true
                process.destroy()
                if (!process.waitFor(250L, TimeUnit.MILLISECONDS)) {
                    process.destroyForcibly()
                    process.waitFor()
                }
            }
        } catch (interrupted: InterruptedException) {
            process.destroyForcibly()
            Thread.currentThread().interrupt()
            throw interrupted
        }

        val stdout = runCatching { stdoutFuture.get(3L, TimeUnit.SECONDS) }
            .getOrElse { CapturedOutput("[stdout capture failed: " + (it.message ?: "unknown error") + "]", true) }
        val stderr = runCatching { stderrFuture.get(3L, TimeUnit.SECONDS) }
            .getOrElse { CapturedOutput("[stderr capture failed: " + (it.message ?: "unknown error") + "]", true) }
        readers.shutdownNow()

        return TerminalCommandResult(
            command = command,
            workingDirectory = cwd.absolutePath,
            exitCode = if (timedOut) null else process.exitValue(),
            standardOutput = stdout.text,
            standardError = stderr.text,
            timedOut = timedOut,
            outputTruncated = stdout.truncated || stderr.truncated,
            durationMillis = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt),
        )
    }

    private fun isInsideWorkspace(candidate: File): Boolean {
        val root = workspaceRoot.absolutePath
        val path = candidate.absolutePath
        return path == root || path.startsWith(root + File.separator)
    }

    private fun capture(input: InputStream, maxCharacters: Int): CapturedOutput {
        val output = StringBuilder(minOf(maxCharacters, 8_192))
        var truncated = false
        InputStreamReader(input, StandardCharsets.UTF_8).use { reader ->
            val buffer = CharArray(4_096)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                if (count == 0) continue
                val remaining = maxCharacters - output.length
                if (remaining > 0) {
                    output.append(buffer, 0, minOf(count, remaining))
                }
                if (count > remaining) truncated = true
            }
        }
        return CapturedOutput(output.toString(), truncated)
    }

    private fun detectShell(): String {
        val candidates = listOf("/system/bin/sh", "/bin/sh")
        return candidates.firstOrNull { File(it).canExecute() }
            ?: throw IllegalStateException("No supported system shell was found on this device")
    }
}

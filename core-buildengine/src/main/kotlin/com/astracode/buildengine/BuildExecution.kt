package com.astracode.buildengine

import java.io.BufferedReader
import java.io.Closeable
import java.io.File
import java.io.InputStream
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class BuildTaskKind {
    BUILD,
    TEST,
    CLEAN,
    CUSTOM,
}

enum class BuildOutputStream {
    STDOUT,
    STDERR,
}

sealed interface BuildExecutionEvent {
    data class Started(val taskName: String, val command: List<String>) : BuildExecutionEvent
    data class Output(val stream: BuildOutputStream, val text: String) : BuildExecutionEvent
    data class Finished(
        val exitCode: Int,
        val durationMillis: Long,
        val outputTruncated: Boolean,
    ) : BuildExecutionEvent
    data class TimedOut(val timeoutMillis: Long) : BuildExecutionEvent
    data object Cancelled : BuildExecutionEvent
    data class Failed(val message: String) : BuildExecutionEvent
}

data class BuildExecutionProfile(
    val taskName: String,
    val kind: BuildTaskKind,
    val workspaceRoot: File,
    val workingDirectory: File,
    val command: List<String>,
    val environment: Map<String, String> = emptyMap(),
    val javaHome: File? = null,
    val timeoutMillis: Long = 15 * 60 * 1_000L,
    val maximumOutputCharacters: Int = 256 * 1024,
) {
    init {
        require(taskName.isNotBlank() && taskName.length <= 120)
        require(command.isNotEmpty() && command.size <= 128)
        require(command.all { it.isNotBlank() && '\u0000' !in it })
        require(timeoutMillis in 1..(24 * 60 * 60 * 1_000L))
        require(maximumOutputCharacters in 1_024..(4 * 1024 * 1024))
        require(environment.size <= 128)
        require(environment.all { (key, value) ->
            ENVIRONMENT_KEY.matches(key) && '\u0000' !in value && value.length <= 16 * 1024
        })
    }

    private companion object {
        val ENVIRONMENT_KEY = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}

data class BuildExecutionResult(
    val taskName: String,
    val exitCode: Int?,
    val timedOut: Boolean,
    val cancelled: Boolean,
    val durationMillis: Long,
    val output: String,
    val outputTruncated: Boolean,
    val failureMessage: String? = null,
) {
    val succeeded: Boolean
        get() = exitCode == 0 && !timedOut && !cancelled && failureMessage == null
}

class BuildCancellationToken {
    private val cancelled = AtomicBoolean(false)

    val isCancelled: Boolean get() = cancelled.get()

    fun cancel(): Boolean = cancelled.compareAndSet(false, true)
}

/**
 * Runs a command in an approved workspace directory. The workspace boundary is
 * a path validation boundary, not an OS sandbox: children run with the Android
 * app's UID and platform permissions.
 */
class BuildProcessRunner {
    fun run(
        profile: BuildExecutionProfile,
        cancellation: BuildCancellationToken = BuildCancellationToken(),
        onEvent: (BuildExecutionEvent) -> Unit = {},
    ): BuildExecutionResult {
        val startedAt = System.nanoTime()
        val output = BoundedBuildOutput(profile.maximumOutputCharacters)
        var process: Process? = null
        var readers: List<Future<*>> = emptyList()
        val readerPool = Executors.newFixedThreadPool(2) { runnable ->
            Thread(runnable, "astracode-build-output").apply { isDaemon = true }
        }

        fun emit(event: BuildExecutionEvent) {
            runCatching { onEvent(event) }
        }

        try {
            val workspaceRoot = profile.workspaceRoot.canonicalFile
            require(workspaceRoot.isDirectory) { "The workspace root does not exist" }
            val workingDirectory = profile.workingDirectory.canonicalFile
            require(workingDirectory.isDirectory) { "The build working directory does not exist" }
            require(isWithin(workspaceRoot, workingDirectory)) {
                "The build working directory is outside the approved workspace"
            }

            val javaHome = profile.javaHome?.canonicalFile
            if (javaHome != null) {
                require(javaHome.isDirectory) { "The selected JDK directory does not exist" }
                val java = File(javaHome, "bin/java")
                require(java.isFile && java.canExecute()) {
                    "The selected JDK does not contain an executable bin/java"
                }
            }

            val builder = ProcessBuilder(profile.command).directory(workingDirectory)
            val environment = builder.environment()
            profile.environment.forEach { (key, value) -> environment[key] = value }
            if (javaHome != null) {
                environment["JAVA_HOME"] = javaHome.absolutePath
                environment["PATH"] = File(javaHome, "bin").absolutePath +
                    File.pathSeparator + environment["PATH"].orEmpty()
            }

            emit(BuildExecutionEvent.Started(profile.taskName, profile.command))
            process = builder.start()
            val activeProcess = process
            readers = listOf(
                readerPool.submit(Callable {
                    copyOutput(
                        activeProcess.inputStream,
                        BuildOutputStream.STDOUT,
                        output,
                        emit,
                    )
                }),
                readerPool.submit(Callable {
                    copyOutput(
                        activeProcess.errorStream,
                        BuildOutputStream.STDERR,
                        output,
                        emit,
                    )
                }),
            )

            val deadline = System.nanoTime() +
                TimeUnit.MILLISECONDS.toNanos(profile.timeoutMillis)
            var timedOut = false
            while (activeProcess.isAlive) {
                if (cancellation.isCancelled) {
                    activeProcess.destroyForcibly()
                    break
                }
                if (System.nanoTime() >= deadline) {
                    timedOut = true
                    activeProcess.destroyForcibly()
                    break
                }
                activeProcess.waitFor(100, TimeUnit.MILLISECONDS)
            }

            activeProcess.waitFor(PROCESS_EXIT_GRACE_MS, TimeUnit.MILLISECONDS)
            val cancelled = cancellation.isCancelled
            val exitCode = if (activeProcess.isAlive) {
                activeProcess.destroyForcibly()
                null
            } else {
                activeProcess.exitValue()
            }
            awaitReaders(readers)
            val duration = elapsedMillis(startedAt)
            val snapshot = output.snapshot()

            when {
                cancelled -> emit(BuildExecutionEvent.Cancelled)
                timedOut -> emit(BuildExecutionEvent.TimedOut(profile.timeoutMillis))
                exitCode != null -> emit(
                    BuildExecutionEvent.Finished(exitCode, duration, snapshot.truncated),
                )
            }

            return BuildExecutionResult(
                taskName = profile.taskName,
                exitCode = exitCode,
                timedOut = timedOut,
                cancelled = cancelled,
                durationMillis = duration,
                output = snapshot.text,
                outputTruncated = snapshot.truncated,
            )
        } catch (error: Exception) {
            process?.destroyForcibly()
            awaitReaders(readers)
            val message = error.message ?: error.javaClass.simpleName
            emit(BuildExecutionEvent.Failed(message))
            val snapshot = output.snapshot()
            return BuildExecutionResult(
                taskName = profile.taskName,
                exitCode = process?.takeIf { !it.isAlive }
                    ?.let { runCatching { it.exitValue() }.getOrNull() },
                timedOut = false,
                cancelled = cancellation.isCancelled,
                durationMillis = elapsedMillis(startedAt),
                output = snapshot.text,
                outputTruncated = snapshot.truncated,
                failureMessage = message,
            )
        } finally {
            readerPool.shutdownNow()
            process?.let {
                runCatching { it.inputStream.close() }
                runCatching { it.errorStream.close() }
            }
        }
    }

    private fun copyOutput(
        stream: InputStream,
        source: BuildOutputStream,
        output: BoundedBuildOutput,
        onEvent: (BuildExecutionEvent) -> Unit,
    ) {
        InputStreamReader(stream, StandardCharsets.UTF_8).buffered().use { reader: BufferedReader ->
            val buffer = CharArray(4_096)
            while (true) {
                val count = reader.read(buffer)
                if (count < 0) break
                val chunk = String(buffer, 0, count)
                output.append(chunk)
                runCatching { onEvent(BuildExecutionEvent.Output(source, chunk)) }
            }
        }
    }

    private fun awaitReaders(readers: List<Future<*>>) {
        readers.forEach { future ->
            runCatching { future.get(READER_JOIN_TIMEOUT_MS, TimeUnit.MILLISECONDS) }
        }
    }

    private fun isWithin(root: File, child: File): Boolean {
        val rootPath = root.toPath().normalize()
        val childPath = child.toPath().normalize()
        return childPath == rootPath || childPath.startsWith(rootPath)
    }

    private fun elapsedMillis(startedAtNanos: Long): Long =
        TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAtNanos)

    private companion object {
        const val PROCESS_EXIT_GRACE_MS = 2_000L
        const val READER_JOIN_TIMEOUT_MS = 3_000L
    }
}

data class BoundedBuildOutputSnapshot(
    val text: String,
    val truncated: Boolean,
)

/** Thread-safe tail buffer so long build logs cannot grow without limit. */
class BoundedBuildOutput(private val maximumCharacters: Int) {
    private val lock = Any()
    private val content = StringBuilder()
    private var hasTruncated = false

    init {
        require(maximumCharacters in 1..(4 * 1024 * 1024))
    }

    fun append(value: String) {
        if (value.isEmpty()) return
        synchronized(lock) {
            content.append(value)
            if (content.length > maximumCharacters) {
                val removeCount = content.length - maximumCharacters
                content.delete(0, removeCount)
                hasTruncated = true
            }
        }
    }

    fun snapshot(): BoundedBuildOutputSnapshot = synchronized(lock) {
        BoundedBuildOutputSnapshot(content.toString(), hasTruncated)
    }
}

data class BuildTaskHandle(
    val cancellation: BuildCancellationToken,
    val result: Future<BuildExecutionResult>,
) {
    fun cancel(): Boolean = cancellation.cancel()
}

/** Owns bounded worker concurrency for build and test tasks. */
class BuildTaskCoordinator(
    private val runner: BuildProcessRunner = BuildProcessRunner(),
    maximumConcurrentTasks: Int = 1,
) : Closeable {
    private val executor = Executors.newFixedThreadPool(maximumConcurrentTasks.coerceIn(1, 2)) { runnable ->
        Thread(runnable, "astracode-build-task").apply { isDaemon = true }
    }
    private val closed = AtomicBoolean(false)
    private val handles = java.util.Collections.synchronizedSet(mutableSetOf<BuildCancellationToken>())

    fun submit(
        profile: BuildExecutionProfile,
        onEvent: (BuildExecutionEvent) -> Unit = {},
    ): BuildTaskHandle {
        check(!closed.get()) { "The build task coordinator is closed" }
        val token = BuildCancellationToken()
        handles += token
        val future = executor.submit(Callable {
            try {
                runner.run(profile, token, onEvent)
            } finally {
                handles -= token
            }
        })
        return BuildTaskHandle(token, future)
    }

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(handles) { handles.forEach { it.cancel() } }
        executor.shutdownNow()
        runCatching { executor.awaitTermination(2, TimeUnit.SECONDS) }
    }
}

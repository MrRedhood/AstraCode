package com.astracode.editor

import java.io.Closeable
import java.io.File
import java.net.URI
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap

/**
 * A user-configured language-server process. The command is passed directly
 * to ProcessBuilder as an argument list; no shell is inserted automatically.
 *
 * Extensions do not include the leading dot and are matched case-insensitively.
 */
data class LanguageServerConfiguration(
    val id: String,
    val languageIds: Set<String>,
    val fileExtensions: Set<String>,
    val command: List<String>,
    val workingDirectoryRelativePath: String = ".",
    val environment: Map<String, String> = emptyMap(),
) {
    init {
        require(SERVER_ID.matches(id)) { "Language-server ID must be a short identifier" }
        require(languageIds.isNotEmpty() && languageIds.all(LANGUAGE_ID::matches)) {
            "At least one valid language ID is required"
        }
        require(fileExtensions.isNotEmpty() && fileExtensions.all(EXTENSION::matches)) {
            "At least one file extension is required (without the leading dot)"
        }
        require(command.isNotEmpty() && command.size <= MAX_COMMAND_ARGUMENTS) {
            "Language-server command must contain between 1 and 64 arguments"
        }
        require(command.all { it.isNotBlank() && '\u0000' !in it }) {
            "Language-server command arguments cannot be blank or contain NUL"
        }
        require(
            workingDirectoryRelativePath.isNotBlank() &&
                !File(workingDirectoryRelativePath).isAbsolute &&
                '\\' !in workingDirectoryRelativePath &&
                workingDirectoryRelativePath.split('/').none { it == ".." },
        ) {
            "Language-server working directory must stay inside the workspace"
        }
        require(environment.size <= MAX_ENVIRONMENT_ENTRIES)
        require(environment.all { (key, value) ->
            ENVIRONMENT_KEY.matches(key) && '\u0000' !in value && value.length <= MAX_ENVIRONMENT_VALUE_CHARS
        }) { "Language-server environment contains an invalid entry" }
    }

    private companion object {
        const val MAX_COMMAND_ARGUMENTS = 64
        const val MAX_ENVIRONMENT_ENTRIES = 64
        const val MAX_ENVIRONMENT_VALUE_CHARS = 8 * 1024
        val SERVER_ID = Regex("[A-Za-z0-9][A-Za-z0-9_.-]{0,63}")
        val LANGUAGE_ID = Regex("[A-Za-z0-9][A-Za-z0-9_.+-]{0,63}")
        val EXTENSION = Regex("[A-Za-z0-9][A-Za-z0-9_-]{0,15}")
        val ENVIRONMENT_KEY = Regex("[A-Za-z_][A-Za-z0-9_]*")
    }
}

/**
 * Starts configured language servers on demand and reuses one initialized
 * process per server ID for the lifetime of this workspace manager.
 *
 * The manager never downloads binaries. Each configuration must point to an
 * executable already available on the Android device or in the workspace.
 */
class LspWorkspaceManager(
    workspaceRoot: File,
    configurations: List<LanguageServerConfiguration>,
) : Closeable {
    private data class ServerHandle(
        val session: LspProcessSession,
        val bridge: JsonRpcLanguageServerBridge,
    ) {
        fun close() {
            bridge.close()
            if (session.state == LspProcessSessionState.READY) {
                session.shutdown().whenComplete { _, error ->
                    if (error != null) session.close()
                }
            } else {
                session.close()
            }
        }
    }

    private val lock = Any()
    private val root = workspaceRoot.canonicalFile
    private val configurationsById: Map<String, LanguageServerConfiguration>
    private val configurationsByExtension: Map<String, LanguageServerConfiguration>
    private val connections = ConcurrentHashMap<String, CompletableFuture<ServerHandle>>()
    private val activeSessions = ConcurrentHashMap<String, LspProcessSession>()

    @Volatile
    private var closed = false

    init {
        require(root.isDirectory) { "Workspace root must be an existing directory" }
        require(configurations.map { it.id }.toSet().size == configurations.size) {
            "Language-server configuration IDs must be unique"
        }
        configurationsById = configurations.associateBy { it.id }
        val extensionOwners = linkedMapOf<String, LanguageServerConfiguration>()
        configurations.forEach { config ->
            config.fileExtensions.forEach { extension ->
                val normalized = extension.lowercase()
                require(extensionOwners.putIfAbsent(normalized, config) == null) {
                    "More than one language server is configured for .$normalized"
                }
            }
        }
        configurationsByExtension = extensionOwners
    }

    fun configurationForFile(fileName: String): LanguageServerConfiguration? {
        val leaf = fileName.substringAfterLast('/').substringAfterLast('\\')
        val dot = leaf.lastIndexOf('.')
        if (dot <= 0 || dot == leaf.lastIndex) return null
        return configurationsByExtension[leaf.substring(dot + 1).lowercase()]
    }

    fun connectForFile(fileName: String): CompletableFuture<LanguageServerBridge> {
        val configuration = configurationForFile(fileName)
            ?: return failedFuture(IllegalArgumentException("No language server is configured for this file"))
        return connect(configuration.id)
    }

    fun connect(serverId: String): CompletableFuture<LanguageServerBridge> {
        val configuration = configurationsById[serverId]
            ?: return failedFuture(IllegalArgumentException("Unknown language-server configuration: " + serverId))

        synchronized(lock) {
            if (closed) return failedFuture(IllegalStateException("Language-server manager is closed"))

            val existing = connections[serverId]
            if (existing != null && !existing.isCompletedExceptionally && !existing.isCancelled) {
                val active = if (existing.isDone) runCatching { existing.getNow(null) }.getOrNull() else null
                if (active == null || active.session.state == LspProcessSessionState.READY || !existing.isDone) {
                    return existing.thenApply { it.bridge }
                }
                connections.remove(serverId, existing)
                active.close()
            } else if (existing != null) {
                connections.remove(serverId, existing)
            }

            val created = start(configuration)
            connections[serverId] = created
            created.whenComplete { _, error ->
                if (error != null) {
                    synchronized(lock) {
                        if (connections[serverId] === created) connections.remove(serverId)
                    }
                }
            }
            return created.thenApply { it.bridge }
        }
    }

    /** Builds an LSP document only for a file contained in this workspace. */
    fun createDocument(
        file: File,
        languageId: String,
        version: Int,
        text: String,
    ): EditorDocument {
        val canonicalFile = file.canonicalFile
        require(canonicalFile.toPath().startsWith(root.toPath()) && canonicalFile != root) {
            "Language-server documents must be inside the workspace"
        }
        val configuration = configurationForFile(canonicalFile.name)
            ?: throw IllegalArgumentException("No language server is configured for this file")
        require(languageId in configuration.languageIds) {
            "Language-server configuration ${configuration.id} does not support language ID ${languageId}"
        }
        return EditorDocument(
            uri = canonicalFile.toURI().toString(),
            languageId = languageId,
            version = version,
            text = text,
        )
    }

    /** Reuses the configured server for an already validated workspace document. */
    fun connectForDocument(document: EditorDocument): CompletableFuture<LanguageServerBridge> {
        val documentFile = runCatching { File(URI(document.uri)).canonicalFile }.getOrElse {
            return failedFuture(IllegalArgumentException("Language-server documents must use a valid file URI", it))
        }
        if (!documentFile.toPath().startsWith(root.toPath()) || documentFile == root) {
            return failedFuture(IllegalArgumentException("Language-server documents must be inside the workspace"))
        }
        val configuration = configurationForFile(documentFile.name)
            ?: return failedFuture(IllegalArgumentException("No language server is configured for this document"))
        if (document.languageId !in configuration.languageIds) {
            return failedFuture(IllegalArgumentException("Language server does not support ${document.languageId}"))
        }
        return connect(configuration.id)
    }

    fun closeServer(serverId: String): CompletableFuture<Unit> {
        val connection: CompletableFuture<ServerHandle>?
        val session: LspProcessSession?
        synchronized(lock) {
            connection = connections.remove(serverId)
            session = activeSessions.remove(serverId)
        }
        if (connection == null && session == null) {
            return CompletableFuture.completedFuture(Unit)
        }
        connection?.whenComplete { handle, _ ->
            if (session == null) handle?.close() else handle?.bridge?.close()
        }
        val result = CompletableFuture<Unit>()
        if (session == null) {
            result.complete(Unit)
            return result
        }
        if (session.state == LspProcessSessionState.READY) {
            session.shutdown().whenComplete { _, _ ->
                session.close()
                result.complete(Unit)
            }
        } else {
            session.close()
            result.complete(Unit)
        }
        return result
    }

    private fun start(configuration: LanguageServerConfiguration): CompletableFuture<ServerHandle> {
        val workingDirectory = File(root, configuration.workingDirectoryRelativePath).canonicalFile
        if (!workingDirectory.toPath().startsWith(root.toPath()) || !workingDirectory.isDirectory) {
            return failedFuture(IllegalArgumentException("Language-server working directory is outside the workspace or missing"))
        }

        val session = try {
            LspProcessSession.start(
                command = configuration.command,
                workspaceRoot = root,
                workingDirectory = workingDirectory,
                environment = configuration.environment,
            )
        } catch (error: Throwable) {
            return failedFuture(error)
        }

        val result = CompletableFuture<ServerHandle>()
        activeSessions[configuration.id] = session
        val initialize = LspInitializeParams(
            processId = null,
            rootUri = root.toURI().toString(),
        )
        session.initialize(initialize).whenComplete { _, error ->
            activeSessions.remove(configuration.id, session)
            if (error != null || session.state != LspProcessSessionState.READY) {
                session.close()
                result.completeExceptionally(
                    error?.let(::unwrap) ?: IllegalStateException("Language-server manager closed during initialization"),
                )
            } else {
                result.complete(ServerHandle(session, JsonRpcLanguageServerBridge(session)))
            }
        }
        return result
    }

    override fun close() {
        val active: List<CompletableFuture<ServerHandle>>
        val sessions: List<LspProcessSession>
        synchronized(lock) {
            if (closed) return
            closed = true
            active = connections.values.toList()
            connections.clear()
            sessions = activeSessions.values.toList()
            activeSessions.clear()
        }
        sessions.forEach(LspProcessSession::close)
        active.forEach { future ->
            future.whenComplete { handle, _ -> handle?.close() }
        }
    }

    private fun unwrap(error: Throwable): Throwable =
        if (error.cause != null) error.cause!! else error

    private fun <T> failedFuture(error: Throwable): CompletableFuture<T> =
        CompletableFuture<T>().also { it.completeExceptionally(error) }
}

package com.astracode.editor

/**
 * The LSP 3.17 client capability block sent in initialize.params.capabilities.
 * AstraCode intentionally offers only UTF-8 because its document offsets are
 * UTF-8 byte positions.
 */
data class LspGeneralClientCapabilities(
    val positionEncodings: List<String> = listOf(LspPositionCodec.POSITION_ENCODING),
) {
    init {
        require(positionEncodings == listOf(LspPositionCodec.POSITION_ENCODING)) {
            "AstraCode must advertise only UTF-8 because its editor coordinates are UTF-8 bytes"
        }
    }

    fun toWireMap(): Map<String, Any> = mapOf(
        "positionEncodings" to positionEncodings,
    )
}

data class LspClientCapabilities(
    val general: LspGeneralClientCapabilities = LspGeneralClientCapabilities(),
) {
    fun toWireMap(): Map<String, Any> = mapOf(
        "general" to general.toWireMap(),
        "textDocument" to mapOf(
            "synchronization" to mapOf(
                "dynamicRegistration" to false,
                "willSave" to false,
                "willSaveWaitUntil" to false,
                "didSave" to false,
            ),
            "completion" to mapOf(
                "dynamicRegistration" to false,
                "contextSupport" to true,
                "completionItem" to mapOf("snippetSupport" to false),
            ),
            "hover" to mapOf(
                "dynamicRegistration" to false,
                "contentFormat" to listOf("markdown", "plaintext"),
            ),
            "definition" to mapOf(
                "dynamicRegistration" to false,
                "linkSupport" to true,
            ),
            "references" to mapOf("dynamicRegistration" to false),
            "rename" to mapOf("dynamicRegistration" to false),
            "publishDiagnostics" to mapOf(
                "versionSupport" to true,
            ),
        ),
        "workspace" to mapOf(
            "applyEdit" to false,
            "workspaceEdit" to mapOf(
                "documentChanges" to true,
                "resourceOperations" to emptyList<String>(),
                "failureHandling" to "abort",
                "normalizesLineEndings" to true,
                "changeAnnotationSupport" to mapOf("groupsOnLabel" to true),
            ),
        ),
    )
}

/**
 * Minimal initialize parameters needed by AstraCode's language-server host.
 * The host can add workspace/textDocument capabilities before JSON encoding.
 */
data class LspInitializeParams(
    val processId: Long?,
    val rootUri: String?,
    val capabilities: LspClientCapabilities = LspClientCapabilities(),
    val clientName: String = "AstraCode",
) {
    init {
        require(processId == null || processId > 0L)
        require(rootUri == null || rootUri.startsWith("file://"))
        require(clientName.isNotBlank())
    }

    fun toWireMap(): Map<String, Any?> = mapOf(
        "processId" to processId,
        "rootUri" to rootUri,
        "clientInfo" to mapOf("name" to clientName),
        "capabilities" to capabilities.toWireMap(),
    )
}

data class LspServerCapabilities(
    val positionEncoding: String? = null,
)

/**
 * A successful result means the server explicitly selected UTF-8.
 *
 * LSP defaults to UTF-16 when the server omits positionEncoding. That default
 * is rejected here because passing UTF-16 coordinates into the UTF-8 Piece
 * Table would move edits to the wrong bytes for non-ASCII source files.
 */
data class LspNegotiatedCapabilities(
    val positionEncoding: String,
)

class LspPositionEncodingException(message: String) : IllegalStateException(message)

object LspPositionEncodingNegotiator {
    const val DEFAULT_LSP_POSITION_ENCODING = "utf-16"

    fun negotiate(server: LspServerCapabilities): LspNegotiatedCapabilities {
        val selected = server.positionEncoding
            ?.trim()
            ?.lowercase()
            ?.takeIf { it.isNotEmpty() }
            ?: DEFAULT_LSP_POSITION_ENCODING

        if (selected != LspPositionCodec.POSITION_ENCODING) {
            throw LspPositionEncodingException(
                "The language server selected $selected. AstraCode requires UTF-8 positions; " +
                    "the connection must not send UTF-8 byte offsets to this server.",
            )
        }
        return LspNegotiatedCapabilities(LspPositionCodec.POSITION_ENCODING)
    }

    fun negotiateInitializeResult(result: Any?): LspNegotiatedCapabilities {
        val response = result as? Map<*, *>
            ?: throw LspPositionEncodingException("Language server initialize response has no result object")
        val capabilities = response["capabilities"] as? Map<*, *>
            ?: throw LspPositionEncodingException("Language server initialize response has no server capabilities")
        val encoding = capabilities["positionEncoding"] as? String
        return negotiate(LspServerCapabilities(positionEncoding = encoding))
    }
}

package com.mrredhood.astracode

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.security.MessageDigest

internal data class EditorDraftSnapshot(
    val baselineFingerprint: String,
    val text: String,
)

/** Bounded, versioned draft format shared by local recovery storage and its JVM tests. */
internal object EditorDraftRecordCodec {
    const val MAX_DRAFT_BYTES = 2 * 1024 * 1024
    private const val MAGIC = 0x41534452 // ASDR
    private const val VERSION = 1
    private val fingerprintPattern = Regex("[0-9a-f]{64}")
    private val hex = "0123456789abcdef"

    fun fingerprint(text: String): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(text.toByteArray(Charsets.UTF_8))
        return buildString(digest.size * 2) {
            digest.forEach { value ->
                val unsigned = value.toInt() and 0xff
                append(hex[unsigned ushr 4])
                append(hex[unsigned and 0x0f])
            }
        }
    }

    fun encode(snapshot: EditorDraftSnapshot): ByteArray? {
        if (!fingerprintPattern.matches(snapshot.baselineFingerprint)) return null
        val payload = snapshot.text.toByteArray(Charsets.UTF_8)
        if (payload.size > MAX_DRAFT_BYTES) return null
        return ByteArrayOutputStream(payload.size + 80).also { bytes ->
            DataOutputStream(bytes).use { output ->
                output.writeInt(MAGIC)
                output.writeInt(VERSION)
                output.writeUTF(snapshot.baselineFingerprint)
                output.writeInt(payload.size)
                output.write(payload)
            }
        }.toByteArray()
    }

    fun decode(encoded: ByteArray): EditorDraftSnapshot? {
        if (encoded.size > MAX_DRAFT_BYTES + 128) return null
        return try {
            DataInputStream(ByteArrayInputStream(encoded)).use { input ->
                if (input.readInt() != MAGIC || input.readInt() != VERSION) return null
                val fingerprint = input.readUTF()
                if (!fingerprintPattern.matches(fingerprint)) return null
                val length = input.readInt()
                if (length < 0 || length > MAX_DRAFT_BYTES || length != input.available()) return null
                val payload = ByteArray(length)
                input.readFully(payload)
                val decoder = Charsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                EditorDraftSnapshot(fingerprint, decoder.decode(ByteBuffer.wrap(payload)).toString())
            }
        } catch (_: Exception) {
            null
        }
    }
}

package com.astracode.connectors

data class AiRedactionResult(
    val content: String,
    val wasRedacted: Boolean,
    val redactionCount: Int,
)

/**
 * Redacts common provider tokens and private-key material from context.
 * Callers must keep redacted files write-protected so a placeholder can never
 * be written back over the original secret-bearing file.
 */
object AiSecretRedactor {
    private val patterns = listOf(
        Regex("""(?i)\bsk-[A-Za-z0-9_-]{20,}\b"""),
        Regex("""(?i)\b(?:ghp|github_pat|gho|ghu|ghs|ghr)_[A-Za-z0-9_]{20,}\b"""),
        Regex("""(?i)\bAIza[0-9A-Za-z_-]{30,}\b"""),
        Regex("""(?i)\bAKIA[0-9A-Z]{16}\b"""),
        Regex("""(?i)\bBearer\s+[A-Za-z0-9._~-]{20,}"""),
        Regex(
            """(?im)^(\s*(?:OPENAI_API_KEY|ANTHROPIC_API_KEY|GITHUB_TOKEN|API_KEY|ACCESS_TOKEN|REFRESH_TOKEN|CLIENT_SECRET|PASSWORD)\s*[:=]\s*)["']?[^\s"']{10,}["']?""",
        ),
        Regex(
            """(?s)-----BEGIN [A-Z ]*PRIVATE KEY-----.*?-----END [A-Z ]*PRIVATE KEY-----""",
        ),
    )

    fun redact(text: String): AiRedactionResult {
        var result = text
        var count = 0
        patterns.forEachIndexed { index, pattern ->
            val replacement = when (index) {
                5 -> "$1[REDACTED]"
                6 -> "[REDACTED PRIVATE KEY]"
                else -> "[REDACTED]"
            }
            val matches = pattern.findAll(result).count()
            if (matches > 0) {
                count += matches
                result = pattern.replace(result, replacement)
            }
        }
        return AiRedactionResult(
            content = result,
            wasRedacted = count > 0,
            redactionCount = count,
        )
    }
}

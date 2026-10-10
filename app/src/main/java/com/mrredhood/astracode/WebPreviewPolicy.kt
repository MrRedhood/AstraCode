package com.mrredhood.astracode

import java.util.Locale

/**
 * Builds self-contained web previews. Network, file, content-provider and form access
 * are intentionally disallowed by both the document CSP and the WebView client.
 */
internal object WebPreviewPolicy {
    const val MAX_PREVIEW_BYTES = 2 * 1024 * 1024

    private val previewExtensions = setOf("html", "htm", "css", "js", "mjs")
    private val headPattern = Regex("(?i)<head\\b[^>]*>")
    private val htmlPattern = Regex("(?i)<html\\b[^>]*>")

    private const val CSP = "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; img-src data: blob:; style-src 'unsafe-inline' data:; script-src 'unsafe-inline'; font-src data:; media-src data: blob:; connect-src 'none'; frame-src 'none'; object-src 'none'; form-action 'none'; base-uri 'none'\">"

    fun supports(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase(Locale.ROOT) in previewExtensions

    fun buildDocument(fileName: String, source: String): String? {
        if (!supports(fileName)) return null
        if (!fitsPreviewLimit(source)) return null
        return when (fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
            "html", "htm" -> secureHtml(source)
            "css" -> buildString {
                append("<!doctype html><html><head><meta charset=\"utf-8\">")
                append(CSP)
                append("<style>")
                append(source)
                append("</style></head><body><main id=\"app\"><h1>CSS preview</h1><p>Change the stylesheet to style this sample.</p><button>Sample button</button><article>Sample card</article></main></body></html>")
            }
            "js", "mjs" -> buildString {
                append("<!doctype html><html><head><meta charset=\"utf-8\">")
                append(CSP)
                append("</head><body><main id=\"app\"><h1>JavaScript preview</h1><p id=\"message\">Edit the script to interact with this page.</p><button id=\"demo\">Click me</button></main><script>")
                append(source)
                append("</script></body></html>")
            }
            else -> null
        }
    }

    private fun secureHtml(source: String): String {
        val head = headPattern.find(source)
        if (head != null) {
            val insertion = head.range.last + 1
            return source.substring(0, insertion) + CSP + source.substring(insertion)
        }
        val html = htmlPattern.find(source)
        if (html != null) {
            val insertion = html.range.last + 1
            return source.substring(0, insertion) + "<head><meta charset=\"utf-8\">$CSP</head>" + source.substring(insertion)
        }
        return "<!doctype html><html><head><meta charset=\"utf-8\">$CSP</head><body>$source</body></html>"
    }

    private fun fitsPreviewLimit(source: String): Boolean {
        var bytes = 0
        var index = 0
        while (index < source.length) {
            val char = source[index]
            val pairedSurrogate = Character.isHighSurrogate(char) &&
                source.getOrNull(index + 1)?.let { Character.isLowSurrogate(it) } == true
            bytes += when {
                pairedSurrogate -> 4
                char.code <= 0x7F -> 1
                char.code <= 0x7FF -> 2
                Character.isSurrogate(char) -> 1
                else -> 3
            }
            if (bytes > MAX_PREVIEW_BYTES) return false
            index += if (pairedSurrogate) 2 else 1
        }
        return true
    }
}

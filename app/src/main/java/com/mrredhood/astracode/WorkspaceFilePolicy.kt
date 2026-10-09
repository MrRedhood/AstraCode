package com.mrredhood.astracode

import java.util.Locale

/** Conservative extension/MIME allowlist for read-only text previews. */
internal object WorkspaceFilePolicy {
    private val textExtensions = setOf(
        "txt", "md", "markdown", "kt", "kts", "java", "xml", "json", "jsonc",
        "yaml", "yml", "toml", "properties", "gradle", "html", "htm", "css",
        "scss", "js", "mjs", "cjs", "ts", "tsx", "jsx", "py", "sh", "bash",
        "zsh", "sql", "c", "h", "cpp", "hpp", "rs", "go", "swift", "dart",
        "ini", "conf", "env", "gitignore", "editorconfig"
    )

    fun supportsTextPreview(displayName: String, mimeType: String): Boolean {
        if (mimeType.startsWith("text/")) return true
        if (mimeType in setOf(
                "application/json",
                "application/xml",
                "application/javascript",
                "application/x-yaml"
            )
        ) return true
        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.ROOT)
        return extension in textExtensions
    }
}

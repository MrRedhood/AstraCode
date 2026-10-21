package com.astracode.app

/** Adds the default text extension only when the new file name has no extension. */
internal fun normalizeNewTextFileName(input: String): String {
    val name = input.trim().trimEnd('.')
    if (name.isBlank()) return ""
    val dot = name.lastIndexOf('.')
    return if (dot <= 0 || dot == name.lastIndex) "$name.txt" else name
}

/** Displays both an extension and a readable format description in file metadata. */
internal fun formatFileTypeLabel(name: String): String {
    if (name.equals("Makefile", ignoreCase = true) || name.equals("GNUmakefile", ignoreCase = true)) {
        return "makefile file (Makefile build instructions)"
    }
    if (name.equals("Dockerfile", ignoreCase = true)) return "dockerfile file (Docker build instructions)"

    val extension = name.substringAfterLast('.', "").lowercase()
    if (extension.isBlank()) return "File (no extension)"
    val description = when (extension) {
        "txt" -> "text file"
        "md", "markdown" -> "Markdown document"
        "kt" -> "Kotlin source file"
        "kts" -> "Kotlin script file"
        "java" -> "Java source file"
        "xml" -> "XML document"
        "json" -> "JSON document"
        "yaml", "yml" -> "YAML document"
        "html", "htm" -> "HTML document"
        "css" -> "CSS stylesheet"
        "js" -> "JavaScript source file"
        "jsx" -> "JavaScript UI component"
        "ts" -> "TypeScript source file"
        "tsx" -> "TypeScript UI component"
        "py" -> "Python source file"
        "sh" -> "shell script"
        "bash" -> "Bash script"
        "c" -> "C source file"
        "h" -> "C header file"
        "cc", "cpp", "cxx" -> "C++ source file"
        "hpp", "hh", "hxx" -> "C++ header file"
        "gradle" -> "Gradle build script"
        "properties" -> "Java properties file"
        "toml" -> "TOML configuration file"
        "ini" -> "INI configuration file"
        "cfg", "conf" -> "configuration file"
        "sql" -> "SQL script"
        "rs" -> "Rust source file"
        "go" -> "Go source file"
        "rb" -> "Ruby source file"
        "php" -> "PHP source file"
        "dart" -> "Dart source file"
        "swift" -> "Swift source file"
        "log" -> "log file"
        "csv" -> "CSV data file"
        "env" -> "environment configuration file"
        "gitignore" -> "Git ignore rules file"
        "makefile" -> "Makefile build instructions"
        "zip" -> "ZIP archive"
        "apk" -> "Android application package"
        "aab" -> "Android App Bundle"
        "aar" -> "Android library archive"
        "jar" -> "Java archive"
        "class" -> "Java class file"
        "dex" -> "Dalvik executable"
        "pdf" -> "PDF document"
        "png" -> "PNG image"
        "jpg", "jpeg" -> "JPEG image"
        "webp" -> "WebP image"
        "gif" -> "GIF image"
        "svg" -> "SVG vector image"
        "mp3" -> "MP3 audio file"
        "mp4" -> "MP4 video file"
        "db", "sqlite", "sqlite3" -> "database file"
        "bin" -> "binary data file"
        "dat" -> "data file"
        else -> "$extension format"
    }
    return "$extension file ($description)"
}

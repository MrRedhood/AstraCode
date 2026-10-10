package com.mrredhood.astracode

import java.nio.charset.StandardCharsets
import java.util.Locale

internal data class EditorCodeMetrics(
    val utf8Bytes: Int,
    val lineCount: Int,
    val longestLine: Int,
)

internal data class EditorFoldRegion(val startLine: Int, val endLine: Int)

internal data class EditorFoldingAnalysis(
    val metrics: EditorCodeMetrics,
    val syntaxSupported: Boolean,
    val limitExceeded: Boolean,
    val regions: List<EditorFoldRegion>,
)

internal data class EditorFoldDisplayLine(
    val lineNumber: Int,
    val text: String,
    val foldStartLine: Int? = null,
    val placeholder: Boolean = false,
)

internal object EditorCodeFolding {
    const val MAX_ANALYSIS_BYTES = 256 * 1024
    const val MAX_FOLD_LINES = 12_000
    const val PERFORMANCE_NOTICE_LINES = 6_000
    const val PERFORMANCE_NOTICE_LINE_LENGTH = 4_096

    private val braceLanguages = setOf(
        "kt", "kts", "java", "js", "mjs", "cjs", "ts", "tsx", "jsx",
        "c", "h", "cpp", "hpp", "go", "rs", "swift", "dart", "css", "scss",
    )

    fun supportsFolding(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase(Locale.ROOT) in braceLanguages

    fun analyze(fileName: String, text: String): EditorFoldingAnalysis {
        val byteCount = text.toByteArray(StandardCharsets.UTF_8).size
        var lineCount = 1
        var currentLineLength = 0
        var longestLine = 0
        for (char in text) {
            if (char == '\n') {
                lineCount++
                longestLine = maxOf(longestLine, currentLineLength)
                currentLineLength = 0
            } else if (char != '\r') {
                currentLineLength++
            }
        }
        longestLine = maxOf(longestLine, currentLineLength)
        val metrics = EditorCodeMetrics(byteCount, lineCount, longestLine)
        val supported = supportsFolding(fileName)
        val limited = byteCount > MAX_ANALYSIS_BYTES || lineCount > MAX_FOLD_LINES
        val regions = if (supported && !limited) scanBraceRegions(text) else emptyList()
        return EditorFoldingAnalysis(metrics, supported, limited, regions)
    }

    fun visibleLines(
        lines: List<String>,
        analysis: EditorFoldingAnalysis,
        foldedStarts: Set<Int>,
    ): List<EditorFoldDisplayLine> {
        val regionsByStart = analysis.regions.associateBy { it.startLine }
        val output = ArrayList<EditorFoldDisplayLine>(lines.size)
        var line = 0
        while (line < lines.size) {
            val region = regionsByStart[line]
            output += EditorFoldDisplayLine(line, lines[line], region?.startLine)
            if (region != null && line in foldedStarts && region.endLine > line + 1) {
                output += EditorFoldDisplayLine(
                    lineNumber = line,
                    text = "… ${region.endLine - line - 1} lines folded …",
                    foldStartLine = line,
                    placeholder = true,
                )
                line = region.endLine
            } else {
                line++
            }
        }
        return output
    }

    private fun scanBraceRegions(text: String): List<EditorFoldRegion> {
        // 0=code, 1=single quote, 2=double quote, 3=triple double quote,
        // 4=template/backtick, 5=line comment, 6=block comment.
        var state = 0
        var escaped = false
        var line = 0
        val stack = ArrayList<Int>()
        val found = ArrayList<EditorFoldRegion>()
        var index = 0
        while (index < text.length) {
            val char = text[index]
            val next = text.getOrNull(index + 1)
            when (state) {
                0 -> when {
                    char == '/' && next == '/' -> { state = 5; index++ }
                    char == '/' && next == '*' -> { state = 6; index++ }
                    char == '\'' -> state = 1
                    char == '"' && text.startsWith("\\""" , index) -> { state = 3; index += 2 }
                    char == '"' -> state = 2
                    char == '`' -> state = 4
                    char == '{' -> stack += line
                    char == '}' && stack.isNotEmpty() -> {
                        val start = stack.removeAt(stack.lastIndex)
                        if (line > start + 1) found += EditorFoldRegion(start, line)
                    }
                }
                1, 2, 4 -> {
                    if (escaped) escaped = false
                    else if (char == '\\\\') escaped = true
                    else if ((state == 1 && char == '\'') ||
                        (state == 2 && char == '"') ||
                        (state == 4 && char == '`')) state = 0
                }
                3 -> if (text.startsWith("\\\""" , index)) { state = 0; index += 2 }
                5 -> if (char == '\n') state = 0
                6 -> if (char == '*' && next == '/') { state = 0; index++ }
            }
            if (char == '\n') line++
            index++
        }
        // A line can contain more than one opening brace. Keep the widest useful
        // region for that line so a single fold control has predictable behavior.
        return found
            .sortedWith(compareBy<EditorFoldRegion> { it.startLine }.thenByDescending { it.endLine })
            .distinctBy { it.startLine }
    }
}

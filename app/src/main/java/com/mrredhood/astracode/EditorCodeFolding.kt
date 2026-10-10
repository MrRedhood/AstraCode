package com.mrredhood.astracode

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
    val lineStartOffsets: IntArray,
    val regions: List<EditorFoldRegion>,
    val regionsTruncated: Boolean,
) {
    fun lineText(text: String, line: Int): String {
        require(line in lineStartOffsets.indices)
        val start = lineStartOffsets[line]
        var end = if (line + 1 < lineStartOffsets.size) lineStartOffsets[line + 1] - 1 else text.length
        if (end > start && text[end - 1] == '\r') end--
        val length = end - start
        return if (length <= EditorCodeFolding.MAX_DISPLAY_LINE_CHARS) {
            text.substring(start, end)
        } else {
            text.substring(start, start + EditorCodeFolding.MAX_DISPLAY_LINE_CHARS) + " … [line clipped]"
        }
    }
}

internal data class EditorFoldDisplayLine(
    val lineNumber: Int,
    val text: String,
    val foldStartLine: Int? = null,
    val placeholder: Boolean = false,
)

internal class EditorFoldingRows private constructor(
    private val segments: List<Segment>,
    val rowCount: Int,
    private val regionsByStart: Map<Int, EditorFoldRegion>,
) {
    fun rowAt(index: Int, analysis: EditorFoldingAnalysis, source: String): EditorFoldDisplayLine {
        require(index in 0 until rowCount)
        var low = 0
        var high = segments.lastIndex
        var selected: Segment? = null
        while (low <= high) {
            val mid = (low + high) ushr 1
            val candidate = segments[mid]
            when {
                index < candidate.displayStart -> high = mid - 1
                index >= candidate.displayStart + candidate.rowCount -> low = mid + 1
                else -> { selected = candidate; break }
            }
        }
        val segment = requireNotNull(selected)
        if (segment.placeholderText != null) {
            return EditorFoldDisplayLine(segment.sourceStart, segment.placeholderText, segment.sourceStart, true)
        }
        val sourceLine = segment.sourceStart + (index - segment.displayStart)
        return EditorFoldDisplayLine(sourceLine, analysis.lineText(source, sourceLine), regionsByStart[sourceLine]?.startLine)
    }

    private data class Segment(
        val displayStart: Int,
        val rowCount: Int,
        val sourceStart: Int,
        val placeholderText: String? = null,
    )

    companion object {
        fun build(analysis: EditorFoldingAnalysis, foldedStarts: Set<Int>): EditorFoldingRows {
            val segments = ArrayList<Segment>()
            var displayStart = 0
            var sourceCursor = 0
            fun addSourceRange(start: Int, endExclusive: Int) {
                if (endExclusive <= start) return
                val count = endExclusive - start
                segments += Segment(displayStart, count, start)
                displayStart += count
            }
            val regionsByStart = analysis.regions.associateBy { it.startLine }
            val collapsedRegions = analysis.regions.filter { it.startLine in foldedStarts }.sortedBy { it.startLine }
            for (region in collapsedRegions) {
                if (region.startLine < sourceCursor || region.startLine >= analysis.metrics.lineCount) continue
                addSourceRange(sourceCursor, region.startLine + 1)
                val hiddenCount = region.endLine - region.startLine - 1
                segments += Segment(displayStart, 1, region.startLine, "… " + hiddenCount + " lines folded …")
                displayStart++
                sourceCursor = region.endLine
            }
            addSourceRange(sourceCursor, analysis.metrics.lineCount)
            return EditorFoldingRows(segments, displayStart, regionsByStart)
        }
    }
}

internal object EditorCodeFolding {
    const val MAX_ANALYSIS_BYTES = 2 * 1024 * 1024
    const val MAX_FOLD_LINES = 1_500_000
    const val MAX_FOLD_REGIONS = 50_000
    const val MAX_TRACKED_NESTING = 20_000
    const val MAX_DISPLAY_LINE_CHARS = 4_000
    const val PERFORMANCE_NOTICE_LINES = 100_000
    const val PERFORMANCE_NOTICE_LINE_LENGTH = 4_096

    private val braceLanguages = setOf(
        "kt", "kts", "java", "js", "mjs", "cjs", "ts", "tsx", "jsx",
        "c", "h", "cpp", "hpp", "go", "rs", "swift", "dart", "css", "scss",
    )

    fun supportsFolding(fileName: String): Boolean =
        fileName.substringAfterLast('.', "").lowercase(Locale.ROOT) in braceLanguages

    fun analyze(fileName: String, text: String): EditorFoldingAnalysis {
        val byteCount = countUtf8Bytes(text)
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
        if (limited) return EditorFoldingAnalysis(metrics, supported, true, IntArray(0), emptyList(), false)
        val lineStarts = indexLineStarts(text, lineCount)
        val scan = if (supported) scanBraceRegions(text) else ScanResult(emptyList(), false)
        return EditorFoldingAnalysis(metrics, supported, false, lineStarts, scan.regions, scan.truncated)
    }

    fun createRows(analysis: EditorFoldingAnalysis, foldedStarts: Set<Int>): EditorFoldingRows =
        EditorFoldingRows.build(analysis, foldedStarts)

    /** Small-list adapter used by JVM tests. The UI uses lazy indexed rows instead. */
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
                output += EditorFoldDisplayLine(line, "… " + (region.endLine - line - 1) + " lines folded …", line, true)
                line = region.endLine
            } else {
                line++
            }
        }
        return output
    }

    private data class ScanResult(val regions: List<EditorFoldRegion>, val truncated: Boolean)

    private fun countUtf8Bytes(text: String): Int {
        var bytes = 0
        var index = 0
        while (index < text.length) {
            val char = text[index]
            val pairedSurrogate = Character.isHighSurrogate(char) &&
                text.getOrNull(index + 1)?.let { Character.isLowSurrogate(it) } == true
            bytes += when {
                pairedSurrogate -> 4
                char.code <= 0x7F -> 1
                char.code <= 0x7FF -> 2
                Character.isSurrogate(char) -> 1
                else -> 3
            }
            if (pairedSurrogate) index++
            index++
        }
        return bytes
    }

    private fun indexLineStarts(text: String, lineCount: Int): IntArray {
        val starts = IntArray(lineCount)
        var nextSlot = 1
        for (index in text.indices) {
            if (text[index] == '\n' && nextSlot < starts.size) starts[nextSlot++] = index + 1
        }
        return starts
    }

    private fun scanBraceRegions(text: String): ScanResult {
        // 0=code, 1=single quote, 2=double quote, 3=triple double quote,
        // 4=template/backtick, 5=line comment, 6=block comment.
        var state = 0
        var escaped = false
        var line = 0
        val stack = ArrayList<Int>()
        val found = ArrayList<EditorFoldRegion>()
        var index = 0
        var truncated = false
        while (index < text.length) {
            val char = text[index]
            val next = text.getOrNull(index + 1)
            when (state) {
                0 -> when {
                    char == '/' && next == '/' -> { state = 5; index++ }
                    char == '/' && next == '*' -> { state = 6; index++ }
                    char == '\'' -> state = 1
                    char == '"' && next == '"' && text.getOrNull(index + 2) == '"' -> { state = 3; index += 2 }
                    char == '"' -> state = 2
                    char.code == 96 -> state = 4
                    char == '{' -> {
                        if (stack.size >= MAX_TRACKED_NESTING) { truncated = true; break }
                        stack += line
                    }
                    char == '}' && stack.isNotEmpty() -> {
                        val start = stack.removeAt(stack.lastIndex)
                        if (line > start + 1) {
                            if (found.size >= MAX_FOLD_REGIONS) { truncated = true; break }
                            found += EditorFoldRegion(start, line)
                        }
                    }
                }
                1, 2, 4 -> {
                    if (escaped) escaped = false
                    else if (char.code == 92) escaped = true
                    else if ((state == 1 && char == '\'') || (state == 2 && char == '"') || (state == 4 && char.code == 96)) state = 0
                }
                3 -> if (char == '"' && next == '"' && text.getOrNull(index + 2) == '"') { state = 0; index += 2 }
                5 -> if (char == '\n') state = 0
                6 -> if (char == '*' && next == '/') { state = 0; index++ }
            }
            if (char == '\n') line++
            index++
        }
        val sorted = found.sortedWith(compareBy<EditorFoldRegion> { it.startLine }.thenByDescending { it.endLine })
            .distinctBy { it.startLine }
        return ScanResult(sorted, truncated)
    }
}

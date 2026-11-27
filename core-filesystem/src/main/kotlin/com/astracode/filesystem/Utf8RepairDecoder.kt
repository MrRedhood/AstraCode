package com.astracode.filesystem

/**
 * Streaming UTF-8 decoder following the WHATWG UTF-8 decoder state machine.
 * It keeps partial sequences across windows and replaces malformed input with
 * U+FFFD. A leading UTF-8 BOM is consumed by default.
 */
class Utf8RepairDecoder(
    private val ignoreBom: Boolean = false,
    private val maxRepairSpans: Int = DEFAULT_MAX_REPAIR_SPANS,
) {
    init {
        require(maxRepairSpans >= 0) { "maxRepairSpans must be zero or greater" }
    }
    private var codePoint = 0
    private var bytesNeeded = 0
    private var bytesSeen = 0
    private var lowerBoundary = 0x80
    private var upperBoundary = 0xBF
    private var sequenceStartByteOffset = 0L
    private var totalBytesRead = 0L
    private var totalDocumentByteLength = 0L
    private var hasProducedOutput = false
    private var finished = false

    private val repairs = mutableListOf<RepairedSpan>()
    var repairedSpansTruncated: Boolean = false
        private set
    var hadLeadingBom: Boolean = false
        private set

    val bytesRead: Long get() = totalBytesRead
    val documentByteLength: Long get() = totalDocumentByteLength
    val repairedSpans: List<RepairedSpan> get() = repairs.toList()

    /** Decode one byte window and return only the text produced by that call. */
    fun decode(
        input: ByteArray,
        offset: Int = 0,
        length: Int = input.size - offset,
        endOfInput: Boolean = false,
    ): String {
        check(!finished) { "Decoder has already reached end of input" }
        require(offset >= 0 && length >= 0 && offset <= input.size - length) {
            "Invalid input window: offset=" + offset + ", length=" + length + ", size=" + input.size
        }

        val output = StringBuilder(length)
        val end = offset + length
        var index = offset
        while (index < end) {
            val value = input[index].toInt() and 0xFF
            val byteOffset = totalBytesRead
            totalBytesRead++
            index++

            var reprocess = true
            while (reprocess) {
                reprocess = false
                if (bytesNeeded == 0) {
                    when (value) {
                        in 0x00..0x7F -> emitScalar(value, output)
                        in 0xC2..0xDF -> beginSequence(value, 1, byteOffset)
                        in 0xE0..0xEF -> {
                            val lower = if (value == 0xE0) 0xA0 else 0x80
                            val upper = if (value == 0xED) 0x9F else 0xBF
                            beginSequence(value, 2, byteOffset, lower, upper)
                        }
                        in 0xF0..0xF4 -> {
                            val lower = if (value == 0xF0) 0x90 else 0x80
                            val upper = if (value == 0xF4) 0x8F else 0xBF
                            beginSequence(value, 3, byteOffset, lower, upper)
                        }
                        else -> emitReplacement(byteOffset, 1, output)
                    }
                } else if (value !in lowerBoundary..upperBoundary) {
                    emitReplacement(sequenceStartByteOffset, bytesSeen + 1, output)
                    resetSequence()
                    reprocess = true
                } else {
                    lowerBoundary = 0x80
                    upperBoundary = 0xBF
                    codePoint = (codePoint shl 6) or (value and 0x3F)
                    bytesSeen++
                    if (bytesSeen == bytesNeeded) {
                        emitScalar(codePoint, output)
                        resetSequence()
                    }
                }
            }
        }

        if (endOfInput) {
            if (bytesNeeded != 0) {
                emitReplacement(sequenceStartByteOffset, bytesSeen + 1, output)
                resetSequence()
            }
            finished = true
        }
        return output.toString()
    }

    private fun beginSequence(
        firstByte: Int,
        continuationBytesNeeded: Int,
        byteOffset: Long,
        lower: Int = 0x80,
        upper: Int = 0xBF,
    ) {
        codePoint = firstByte and (0x7F shr (continuationBytesNeeded + 1))
        bytesNeeded = continuationBytesNeeded
        bytesSeen = 0
        lowerBoundary = lower
        upperBoundary = upper
        sequenceStartByteOffset = byteOffset
    }

    private fun emitScalar(value: Int, output: StringBuilder) {
        if (!hasProducedOutput) {
            hasProducedOutput = true
            if (!ignoreBom && value == 0xFEFF) {
                hadLeadingBom = true
                return
            }
        }
        output.appendCodePoint(value)
        totalDocumentByteLength += utf8Length(value)
    }

    private fun emitReplacement(sourceOffset: Long, sourceLength: Int, output: StringBuilder) {
        if (repairs.size < maxRepairSpans) {
            repairs += RepairedSpan(
                documentByteOffset = totalDocumentByteLength,
                sourceByteOffset = sourceOffset,
                sourceByteLength = sourceLength,
            )
        } else {
            repairedSpansTruncated = true
        }
        hasProducedOutput = true
        output.append('\uFFFD')
        totalDocumentByteLength += REPLACEMENT_UTF8_LENGTH
    }

    private fun utf8Length(value: Int): Int = when {
        value <= 0x7F -> 1
        value <= 0x7FF -> 2
        value <= 0xFFFF -> 3
        else -> 4
    }

    private fun resetSequence() {
        codePoint = 0
        bytesNeeded = 0
        bytesSeen = 0
        lowerBoundary = 0x80
        upperBoundary = 0xBF
        sequenceStartByteOffset = 0L
    }

    private companion object {
        const val REPLACEMENT_UTF8_LENGTH = 3L
        const val DEFAULT_MAX_REPAIR_SPANS = 100_000
    }
}

/** Byte provenance for one U+FFFD inserted while repairing malformed UTF-8. */
data class RepairedSpan(
    val documentByteOffset: Long,
    val sourceByteOffset: Long,
    val sourceByteLength: Int,
)

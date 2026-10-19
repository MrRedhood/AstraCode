package com.astracode.filesystem

/**
 * A streaming UTF-8 decoder following the WHATWG UTF-8 decoder state machine.
 *
 * Input may be supplied in arbitrarily sized windows. The decoder keeps partial
 * sequences between calls and emits U+FFFD for malformed input. By default, a
 * single leading UTF-8 BOM is consumed, matching TextDecoder("utf-8").
 * Instances are single-use and must be finished with endOfInput = true.
 */
class Utf8RepairDecoder(
    private val ignoreBom: Boolean = false,
) {
    private var codePoint = 0
    private var bytesNeeded = 0
    private var bytesSeen = 0
    private var lowerBoundary = 0x80
    private var upperBoundary = 0xBF
    private var sequenceStartByteOffset = 0L
    private var totalBytesRead = 0L
    private var totalUtf16Units = 0
    private var hasProducedOutput = false
    private var finished = false

    private val repairs = mutableListOf<RepairedSpan>()
    var hadLeadingBom: Boolean = false
        private set

    val bytesRead: Long get() = totalBytesRead
    val utf16Length: Int get() = totalUtf16Units
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
            "Invalid input window: offset=$offset, length=$length, size=${input.size}"
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
        totalUtf16Units += if (value > 0xFFFF) 2 else 1
    }

    private fun emitReplacement(sourceOffset: Long, sourceLength: Int, output: StringBuilder) {
        repairs += RepairedSpan(
            documentUtf16Offset = totalUtf16Units,
            sourceByteOffset = sourceOffset,
            sourceByteLength = sourceLength,
        )
        hasProducedOutput = true
        output.append('\uFFFD')
        totalUtf16Units++
    }

    private fun resetSequence() {
        codePoint = 0
        bytesNeeded = 0
        bytesSeen = 0
        lowerBoundary = 0x80
        upperBoundary = 0xBF
        sequenceStartByteOffset = 0L
    }
}

/** Provenance for one U+FFFD inserted while repairing a malformed byte sequence. */
data class RepairedSpan(
    val documentUtf16Offset: Int,
    val sourceByteOffset: Long,
    val sourceByteLength: Int,
)

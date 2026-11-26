package com.astracode.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.nio.charset.StandardCharsets.UTF_8

class LspPositionCodecTest {
    private val source = "Aह😀\r\nnext".toByteArray(UTF_8)

    @Test
    fun mapsUtf8BytePositions() {
        assertEquals(8, LspPositionCodec.byteOffsetForPosition(source, LspPosition(0, 8)))
        assertEquals(LspPosition(0, 8), LspPositionCodec.positionForByteOffset(source, 8))
    }

    @Test
    fun mapsNextLineAndRejectsSplitCharacters() {
        assertEquals(10, LspPositionCodec.byteOffsetForPosition(source, LspPosition(1, 0)))
        assertFailsWith<IllegalArgumentException> {
            LspPositionCodec.byteOffsetForPosition(source, LspPosition(0, 2))
        }
    }

    @Test
    fun onlyAdvertisesUtf8PositionEncoding() {
        assertEquals("utf-8", LspPositionCodec.POSITION_ENCODING)
    }
}

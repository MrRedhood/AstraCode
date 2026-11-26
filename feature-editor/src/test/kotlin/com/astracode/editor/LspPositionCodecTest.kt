package com.astracode.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import java.nio.charset.StandardCharsets.UTF_8

class LspPositionCodecTest {
    private val source = "Aह😀\r\nnext".toByteArray(UTF_8)

    @Test
    fun usesUtf8BytesForPreferredPositions() {
        assertEquals(8, LspPositionCodec.byteOffsetForPosition(source, LspPosition(0, 8)))
        assertEquals(LspPosition(0, 8), LspPositionCodec.positionForByteOffset(source, 8))
    }

    @Test
    fun translatesUtf16OnlyForLegacyLspServers() {
        assertEquals(8, LspPositionCodec.byteOffsetForPosition(
            source, LspPosition(0, 4), LspPositionEncoding.UTF16,
        ))
        assertEquals(
            LspPosition(0, 4),
            LspPositionCodec.positionForByteOffset(source, 8, LspPositionEncoding.UTF16),
        )
    }

    @Test
    fun mapsNextLineAndRejectsSplitCharacters() {
        assertEquals(10, LspPositionCodec.byteOffsetForPosition(source, LspPosition(1, 0)))
        assertFailsWith<IllegalArgumentException> {
            LspPositionCodec.byteOffsetForPosition(source, LspPosition(0, 2))
        }
    }
}

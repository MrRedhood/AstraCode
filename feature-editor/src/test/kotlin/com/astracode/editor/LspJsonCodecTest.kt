package com.astracode.editor

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LspJsonCodecTest {
    @Test
    fun roundTripsNestedJsonAndUnicodeText() {
        val source = linkedMapOf<String, Any?>(
            "jsonrpc" to "2.0",
            "id" to 42L,
            "params" to mapOf(
                "text" to "Hindi: नमस्ते · emoji: 😀",
                "items" to listOf(true, 12L, 1.5, null),
            ),
        )

        val encoded = LspJsonCodec.encode(source)
        val decoded = LspJsonCodec.decodeObject(encoded)
        assertEquals("2.0", decoded["jsonrpc"])
        assertEquals(42L, decoded["id"])
        val params = decoded["params"] as Map<*, *>
        assertEquals("Hindi: नमस्ते · emoji: 😀", params["text"])
        assertEquals(listOf(true, 12L, 1.5, null), params["items"])
    }

    @Test
    fun parsesEscapedCharactersAndUnicodeEscapes() {
        val decoded = LspJsonCodec.decodeObject(
            """{"text":"quote: \" slash: \\ newline: \n unicode: \u0939"}""",
        )
        assertEquals("quote: \" slash: \\ newline: \n unicode: ह", decoded["text"])
    }

    @Test
    fun rejectsMalformedAndAmbiguousJson() {
        assertFailsWith<IllegalArgumentException> {
            LspJsonCodec.decodeObject("""{"id":1,"id":2}""")
        }
        assertFailsWith<IllegalArgumentException> {
            LspJsonCodec.decodeObject("""{"id":01}""")
        }
        assertFailsWith<IllegalArgumentException> {
            LspJsonCodec.decodeObject("""{"id":1} trailing""")
        }
        assertFailsWith<IllegalArgumentException> {
            LspJsonCodec.decodeObject("""{"text":"bad \q escape"}""")
        }
    }

    @Test
    fun onlyAcceptsJsonObjectAtTheProtocolRoot() {
        assertFailsWith<IllegalArgumentException> { LspJsonCodec.decodeObject("[]") }
        assertFailsWith<IllegalArgumentException> { LspJsonCodec.decodeObject("null") }
    }

    @Test
    fun encodesNullAndEscapesControlCharacters() {
        val encoded = LspJsonCodec.encode(mapOf("value" to null, "control" to "\u0001"))
        assertTrue(encoded.contains("null"))
        assertTrue(encoded.contains("\\u0001"))
        assertNull(LspJsonCodec.decodeObject(encoded)["value"])
    }
}

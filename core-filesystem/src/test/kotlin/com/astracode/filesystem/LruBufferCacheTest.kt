package com.astracode.filesystem

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LruBufferCacheTest {
    @Test
    fun `least recently used item is evicted first`() {
        val cache = LruBufferCache<String, String>(2)
        cache.put("a", "A")
        cache.put("b", "B")
        assertEquals("A", cache["a"]) // a becomes the most recently used entry
        cache.put("c", "C")
        assertNull(cache["b"])
        assertEquals(listOf("c", "a"), cache.keysMostRecentlyUsedFirst())
    }

    @Test
    fun `zero capacity disables caching`() {
        val cache = LruBufferCache<String, String>(0)
        cache.put("a", "A")
        assertNull(cache["a"])
        assertEquals(0, cache.size())
    }
}

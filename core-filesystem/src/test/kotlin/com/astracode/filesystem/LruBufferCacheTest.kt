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
    fun `weighted cache evicts least recently used entries to stay within budget`() {
        val cache = LruBufferCache<String, String>(capacity = 4, maxWeight = 5L) { it.length.toLong() }
        cache.put("a", "abc")
        cache.put("b", "de")
        assertEquals(5L, cache.currentWeight())

        assertEquals("abc", cache["a"])
        cache.put("c", "xy")

        assertNull(cache["b"])
        assertEquals("abc", cache["a"])
        assertEquals("xy", cache["c"])
        assertEquals(5L, cache.currentWeight())
    }

    @Test
    fun `oversized entries are ignored without evicting useful cached values`() {
        val cache = LruBufferCache<String, String>(capacity = 4, maxWeight = 3L) { it.length.toLong() }
        cache.put("small", "ok")
        cache.put("large", "too large")

        assertEquals("ok", cache["small"])
        assertNull(cache["large"])
        assertEquals(2L, cache.currentWeight())
    }

    @Test
    fun `replacement removal and clear keep cached weight accurate`() {
        val cache = LruBufferCache<String, String>(capacity = 4, maxWeight = 8L) { it.length.toLong() }
        cache.put("one", "abc")
        cache.put("one", "x")
        cache.put("two", "yz")
        assertEquals(3L, cache.currentWeight())

        cache.removeIf { it == "one" }
        assertEquals(2L, cache.currentWeight())
        cache.clear()
        assertEquals(0L, cache.currentWeight())
        assertEquals(0, cache.size())
    }

    @Test
    fun `weigher is evaluated once and insertion weight is retained`() {
        var currentWeight = 2L
        var weigherCalls = 0
        val cache = LruBufferCache<String, String>(capacity = 2, maxWeight = 4L) {
            weigherCalls++
            currentWeight
        }

        cache.put("a", "A")
        assertEquals(1, weigherCalls)

        currentWeight = 4L
        assertEquals("A", cache.remove("a"))
        assertEquals(0L, cache.currentWeight())
        assertEquals(1, weigherCalls)

        cache.put("b", "B")
        assertEquals(4L, cache.currentWeight())
        currentWeight = 1L
        cache.put("c", "C")

        assertNull(cache["b"])
        assertEquals("C", cache["c"])
        assertEquals(1L, cache.currentWeight())
        assertEquals(3, weigherCalls)
    }

    @Test
    fun `maximum weight and zero weight entries do not overflow accounting`() {
        val cache = LruBufferCache<String, Long>(
            capacity = 3,
            maxWeight = Long.MAX_VALUE,
            weigher = { it },
        )
        cache.put("maximum", Long.MAX_VALUE)
        cache.put("free", 0L)
        assertEquals(Long.MAX_VALUE, cache.currentWeight())

        cache.put("one", 1L)

        assertNull(cache["maximum"])
        assertEquals(0L, cache["free"])
        assertEquals(1L, cache["one"])
        assertEquals(1L, cache.currentWeight())
    }

    @Test
    fun `zero capacity disables caching`() {
        val cache = LruBufferCache<String, String>(0)
        cache.put("a", "A")
        assertNull(cache["a"])
        assertEquals(0, cache.size())
    }
}

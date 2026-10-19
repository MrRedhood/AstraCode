package com.astracode.filesystem

import java.util.LinkedHashMap

/** A small, thread-safe least-recently-used cache with a hard entry limit. */
class LruBufferCache<K : Any, V : Any>(val capacity: Int) {
    private val entries = LinkedHashMap<K, V>(16, 0.75f, true)

    init {
        require(capacity >= 0) { "capacity must be zero or greater" }
    }

    @Synchronized
    operator fun get(key: K): V? = entries[key]

    @Synchronized
    fun put(key: K, value: V) {
        if (capacity == 0) return
        entries[key] = value
        while (entries.size > capacity) {
            val eldestKey = entries.entries.iterator().next().key
            entries.remove(eldestKey)
        }
    }

    @Synchronized
    fun remove(key: K): V? = entries.remove(key)

    @Synchronized
    fun removeIf(predicate: (K) -> Boolean) {
        val iterator = entries.keys.iterator()
        while (iterator.hasNext()) {
            if (predicate(iterator.next())) iterator.remove()
        }
    }

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun size(): Int = entries.size

    @Synchronized
    fun keysMostRecentlyUsedFirst(): List<K> = entries.keys.toList().asReversed()
}

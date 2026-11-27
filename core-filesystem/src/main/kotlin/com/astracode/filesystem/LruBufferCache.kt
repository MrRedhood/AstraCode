package com.astracode.filesystem

import java.util.LinkedHashMap

/**
 * A thread-safe least-recently-used cache bounded by both entry count and weight.
 * Each value's weight is captured once when it is inserted.
 */
class LruBufferCache<K : Any, V : Any>(
    val capacity: Int,
    val maxWeight: Long = Long.MAX_VALUE,
    private val weigher: (V) -> Long = { 1L },
) {
    private data class CacheEntry<V : Any>(val value: V, val weight: Long)

    private val entries = LinkedHashMap<K, CacheEntry<V>>(16, 0.75f, true)
    private var storedWeight = 0L

    init {
        require(capacity >= 0) { "capacity must be zero or greater" }
        require(maxWeight >= 0L) { "maxWeight must be zero or greater" }
    }

    @Synchronized
    operator fun get(key: K): V? = entries[key]?.value

    @Synchronized
    fun put(key: K, value: V) {
        val weight = weigher(value)
        require(weight >= 0L) { "Cached values must have a non-negative weight" }
        entries.remove(key)?.let { previous ->
            storedWeight -= previous.weight
        }
        if (capacity == 0 || maxWeight == 0L || weight > maxWeight) return

        while (entries.isNotEmpty() &&
            (entries.size >= capacity || weight > maxWeight - storedWeight)
        ) {
            evictEldest()
        }
        entries[key] = CacheEntry(value, weight)
        storedWeight += weight
    }

    @Synchronized
    fun remove(key: K): V? {
        val removed = entries.remove(key) ?: return null
        storedWeight -= removed.weight
        return removed.value
    }

    @Synchronized
    fun removeIf(predicate: (K) -> Boolean) {
        val iterator = entries.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (predicate(entry.key)) {
                storedWeight -= entry.value.weight
                iterator.remove()
            }
        }
    }

    @Synchronized
    fun clear() {
        entries.clear()
        storedWeight = 0L
    }

    @Synchronized
    fun size(): Int = entries.size

    @Synchronized
    fun currentWeight(): Long = storedWeight

    @Synchronized
    fun keysMostRecentlyUsedFirst(): List<K> = entries.keys.toList().asReversed()

    private fun evictEldest() {
        val iterator = entries.entries.iterator()
        if (!iterator.hasNext()) return
        val eldest = iterator.next()
        storedWeight -= eldest.value.weight
        iterator.remove()
    }
}

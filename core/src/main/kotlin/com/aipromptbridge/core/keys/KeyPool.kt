package com.aipromptbridge.core.keys

/** One named API key. `name` is only a label for the UI ("Key 1", "Work account"). */
data class KeyEntry(val name: String, val value: String)

/**
 * Rotating pool of API keys for ONE provider (port of the original `key_manager.py`).
 *
 * Semantics: [rotate] marks the current key exhausted and moves to the next non-exhausted key.
 * When every key has been marked exhausted the set is cleared (so we start a fresh cycle) instead of failing.
 * Thread-safe: providers may be used from several coroutines.
 */
class KeyPool(rawEntries: List<KeyEntry>) {
    private val entries: List<KeyEntry> = rawEntries.filter { it.value.isNotBlank() }
    private val exhausted = mutableSetOf<Int>()
    private var index = 0

    val size: Int get() = entries.size
    val hasMultipleKeys: Boolean get() = entries.size > 1

    @Synchronized
    fun currentKey(): String? = entries.getOrNull(index)?.value

    @Synchronized
    fun currentName(): String? = entries.getOrNull(index)?.name

    /** Marks the current key exhausted and advances. Returns the new current key, or null when the pool is empty. */
    @Synchronized
    fun rotate(): String? {
        if (entries.isEmpty()) return null
        exhausted.add(index)
        if (exhausted.size >= entries.size) exhausted.clear()
        for (step in 1..entries.size) {
            val candidate = (index + step) % entries.size
            if (candidate !in exhausted) {
                index = candidate
                break
            }
        }
        return entries[index].value
    }

    @Synchronized
    fun reset() {
        exhausted.clear()
        index = 0
    }
}

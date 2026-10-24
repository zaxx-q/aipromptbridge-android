package com.aipromptbridge.core.keys

/**
 * Pure helpers for the API-key screens. Nothing here logs, prints or stores keys: it only reshapes lists.
 * The UI persists the result with `KeyRepository.saveKeys(pool, entries)` and then `KeyPoolRegistry.invalidate(pool)`.
 */
object KeyEditing {
    private const val MASK = "••••"

    /**
     * What the UI may show for a saved key: bullets plus at most the last 4 characters. Short keys show no characters
     * at all, so a 6-character secret is never half revealed.
     */
    fun mask(value: String): String {
        val v = value.trim()
        return if (v.length >= 12) MASK + v.takeLast(4) else MASK + MASK
    }

    /** "Key 1", "Key 2", ... — the first default label not already used. */
    fun nextName(entries: List<KeyEntry>): String {
        val used = entries.map { it.name }.toSet()
        var n = 1
        while ("Key $n" in used) n++
        return "Key $n"
    }

    /** One-line pool description for list rows: "No keys", "1 key" or "3 keys · rotation on". */
    fun summary(count: Int): String = when {
        count <= 0 -> "No keys"
        count == 1 -> "1 key"
        else -> "$count keys · rotation on"
    }

    /** True when [value] (trimmed) is already stored in [entries]. */
    fun containsValue(entries: List<KeyEntry>, value: String): Boolean {
        val v = value.trim()
        return v.isNotEmpty() && entries.any { it.value == v }
    }

    /**
     * Appends a key. A blank [value] changes nothing; a blank [name] gets the next default label.
     * Both are trimmed.
     */
    fun add(entries: List<KeyEntry>, name: String, value: String): List<KeyEntry> {
        val v = value.trim()
        if (v.isEmpty()) return entries
        return entries + KeyEntry(name.trim().ifBlank { nextName(entries) }, v)
    }

    /** Renames the entry at [index]. A blank name keeps the old one. */
    fun rename(entries: List<KeyEntry>, index: Int, name: String): List<KeyEntry> {
        val clean = name.trim()
        if (index !in entries.indices || clean.isEmpty()) return entries
        return entries.toMutableList().also { it[index] = it[index].copy(name = clean) }
    }

    /** Replaces the secret of the entry at [index]. A blank value keeps the old one. */
    fun replaceValue(entries: List<KeyEntry>, index: Int, value: String): List<KeyEntry> {
        val clean = value.trim()
        if (index !in entries.indices || clean.isEmpty()) return entries
        return entries.toMutableList().also { it[index] = it[index].copy(value = clean) }
    }

    fun remove(entries: List<KeyEntry>, index: Int): List<KeyEntry> =
        if (index in entries.indices) entries.filterIndexed { i, _ -> i != index } else entries
}

package com.aipromptbridge.core.diff

enum class DiffKind { EQUAL, INSERT, DELETE }

data class DiffSegment(val kind: DiffKind, val text: String)

/**
 * Word-level diff used to render the "Fix Grammar" result: inserted words get a highlighter background,
 * deleted words are struck through (see designsystem/DiffText.kt).
 *
 * Algorithm: tokenize into words and whitespace runs, compute the longest common subsequence (classic DP table),
 * then walk both token lists emitting EQUAL / DELETE / INSERT segments. Adjacent segments of the same kind are merged.
 * Invariants (covered by tests):  concat(EQUAL+DELETE) == old   and   concat(EQUAL+INSERT) == new.
 */
object WordDiff {
    /** Above this many DP cells (tokensOld * tokensNew) fall back to "delete all, insert all" to avoid huge allocations. */
    private const val MAX_CELLS = 4_000_000L
    private val TOKEN = Regex("\\s+|\\S+")

    fun diff(old: String, new: String): List<DiffSegment> {
        if (old == new) return if (old.isEmpty()) emptyList() else listOf(DiffSegment(DiffKind.EQUAL, old))

        val a = tokenize(old)
        val b = tokenize(new)
        if (a.size.toLong() * b.size.toLong() > MAX_CELLS) return coarse(old, new)

        val n = a.size
        val m = b.size
        // lcs[i][j] = length of the LCS of a[i..] and b[j..]
        val lcs = Array(n + 1) { IntArray(m + 1) }
        for (i in n - 1 downTo 0) {
            for (j in m - 1 downTo 0) {
                lcs[i][j] = if (a[i] == b[j]) lcs[i + 1][j + 1] + 1 else maxOf(lcs[i + 1][j], lcs[i][j + 1])
            }
        }

        val raw = mutableListOf<DiffSegment>()
        var i = 0
        var j = 0
        while (i < n && j < m) {
            when {
                a[i] == b[j] -> {
                    raw += DiffSegment(DiffKind.EQUAL, a[i]); i++; j++
                }
                // Prefer deleting first so a replaced word renders as: ~~old~~ new
                lcs[i + 1][j] >= lcs[i][j + 1] -> {
                    raw += DiffSegment(DiffKind.DELETE, a[i]); i++
                }
                else -> {
                    raw += DiffSegment(DiffKind.INSERT, b[j]); j++
                }
            }
        }
        while (i < n) { raw += DiffSegment(DiffKind.DELETE, a[i]); i++ }
        while (j < m) { raw += DiffSegment(DiffKind.INSERT, b[j]); j++ }
        return merge(raw)
    }

    /** Number of changed regions (each maximal run of INSERT/DELETE counts once). */
    fun changeCount(segments: List<DiffSegment>): Int {
        var count = 0
        var inChange = false
        for (segment in segments) {
            val changed = segment.kind != DiffKind.EQUAL
            if (changed && !inChange) count++
            inChange = changed
        }
        return count
    }

    private fun tokenize(text: String): List<String> = TOKEN.findAll(text).map { it.value }.toList()

    private fun coarse(old: String, new: String): List<DiffSegment> =
        listOfNotNull(
            if (old.isNotEmpty()) DiffSegment(DiffKind.DELETE, old) else null,
            if (new.isNotEmpty()) DiffSegment(DiffKind.INSERT, new) else null,
        )

    private fun merge(segments: List<DiffSegment>): List<DiffSegment> {
        val merged = mutableListOf<DiffSegment>()
        for (segment in segments) {
            val last = merged.lastOrNull()
            if (last != null && last.kind == segment.kind) {
                merged[merged.size - 1] = DiffSegment(last.kind, last.text + segment.text)
            } else {
                merged += segment
            }
        }
        return merged
    }
}

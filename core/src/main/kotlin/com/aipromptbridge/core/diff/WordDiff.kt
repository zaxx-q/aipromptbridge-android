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
        return cleanup(merge(raw))
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

    /**
     * Shifts equal whitespace segments out of multi-word edits towards the boundary,
     * so that multi-word replacements/insertions/deletions don't get split by inter-word spaces.
     */
    private fun cleanup(segments: List<DiffSegment>): List<DiffSegment> {
        var current = segments
        var changed = true
        var passes = 0
        while (changed && passes < 10) {
            changed = false
            passes++
            val result = mutableListOf<DiffSegment>()
            var idx = 0
            while (idx < current.size) {
                val seg = current[idx]
                if (seg.kind == DiffKind.EQUAL && seg.text.isBlank() && idx > 0 && idx + 1 < current.size) {
                    val prev = current[idx - 1]
                    val next1 = current[idx + 1]
                    val next2 = if (idx + 2 < current.size) current[idx + 2] else null

                    if (prev.kind != DiffKind.EQUAL) {
                        if (next2 != null && next1.kind != DiffKind.EQUAL && next2.kind != DiffKind.EQUAL && next1.kind != next2.kind) {
                            if ((seg.text + next1.text).endsWith(seg.text) && (seg.text + next2.text).endsWith(seg.text)) {
                                val shifted1 = DiffSegment(next1.kind, (seg.text + next1.text).dropLast(seg.text.length))
                                val shifted2 = DiffSegment(next2.kind, (seg.text + next2.text).dropLast(seg.text.length))
                                result.add(shifted1)
                                result.add(shifted2)
                                result.add(seg)
                                idx += 3
                                changed = true
                                continue
                            }
                        } else if (next1.kind != DiffKind.EQUAL) {
                            if ((seg.text + next1.text).endsWith(seg.text)) {
                                val shifted1 = DiffSegment(next1.kind, (seg.text + next1.text).dropLast(seg.text.length))
                                result.add(shifted1)
                                result.add(seg)
                                idx += 2
                                changed = true
                                continue
                            }
                        }
                    }
                }
                result.add(seg)
                idx++
            }
            current = merge(result)
        }
        return current
    }
}

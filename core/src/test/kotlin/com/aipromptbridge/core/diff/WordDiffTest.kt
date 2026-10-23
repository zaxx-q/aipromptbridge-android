package com.aipromptbridge.core.diff

import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WordDiffTest {
    private fun List<DiffSegment>.oldText() = filter { it.kind != DiffKind.INSERT }.joinToString("") { it.text }
    private fun List<DiffSegment>.newText() = filter { it.kind != DiffKind.DELETE }.joinToString("") { it.text }

    @Test
    fun identicalTextIsOneEqualSegment() {
        assertEquals(listOf(DiffSegment(DiffKind.EQUAL, "same text")), WordDiff.diff("same text", "same text"))
    }

    @Test
    fun emptyInputs() {
        assertEquals(emptyList(), WordDiff.diff("", ""))
        assertEquals(listOf(DiffSegment(DiffKind.INSERT, "new")), WordDiff.diff("", "new"))
        assertEquals(listOf(DiffSegment(DiffKind.DELETE, "old")), WordDiff.diff("old", ""))
    }

    @Test
    fun insertedWordsAreMarkedAsOneInsert() {
        val segments = WordDiff.diff("the proposal sent last Thursday", "the proposal we sent last Thursday")
        val inserts = segments.filter { it.kind == DiffKind.INSERT }
        assertEquals(1, inserts.size)
        assertEquals("we ", inserts.single().text)
        assertTrue(segments.none { it.kind == DiffKind.DELETE })
    }

    @Test
    fun replacedWordIsDeleteThenInsert() {
        val segments = WordDiff.diff("let me know if we have questions", "let me know if you have questions")
        val kinds = segments.map { it.kind }
        val deleteIndex = kinds.indexOf(DiffKind.DELETE)
        assertEquals("we", segments[deleteIndex].text)
        assertEquals(DiffKind.INSERT, kinds[deleteIndex + 1])
        assertEquals("you", segments[deleteIndex + 1].text)
    }

    @Test
    fun changeCountCountsRegionsNotWords() {
        val segments = WordDiff.diff("a b c d e", "a X Y c d Z")
        assertEquals(2, WordDiff.changeCount(segments))
        assertEquals(0, WordDiff.changeCount(WordDiff.diff("same", "same")))
    }

    @Test
    fun oversizedInputFallsBackToCoarseDiffButStaysCorrect() {
        val old = (1..2500).joinToString(" ") { "old$it" }
        val new = (1..2500).joinToString(" ") { "new$it" }
        val segments = WordDiff.diff(old, new)
        assertEquals(old, segments.oldText())
        assertEquals(new, segments.newText())
    }

    /** Property test: for random word sequences, the diff must reconstruct BOTH inputs exactly. */
    @Test
    fun reconstructsBothSidesForRandomInputs() {
        val random = Random(42)
        val vocabulary = listOf("alpha", "beta", "gamma", "delta", "epsilon", "zeta", "eta", "theta")
        repeat(200) {
            val old = List(random.nextInt(0, 25)) { vocabulary[random.nextInt(vocabulary.size)] }.joinToString(" ")
            val new = List(random.nextInt(0, 25)) { vocabulary[random.nextInt(vocabulary.size)] }.joinToString(" ")
            val segments = WordDiff.diff(old, new)
            assertEquals(old, segments.oldText(), "old mismatch for '$old' -> '$new'")
            assertEquals(new, segments.newText(), "new mismatch for '$old' -> '$new'")
        }
    }
}

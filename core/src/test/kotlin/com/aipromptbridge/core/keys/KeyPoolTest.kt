package com.aipromptbridge.core.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class KeyPoolTest {
    private fun pool(vararg values: String) = KeyPool(values.mapIndexed { i, v -> KeyEntry("k$i", v) })

    @Test
    fun blankKeysAreIgnored() {
        val p = pool("a", " ", "")
        assertEquals(1, p.size)
        assertFalse(p.hasMultipleKeys)
    }

    @Test
    fun emptyPoolHasNoKeyAndRotateReturnsNull() {
        val p = pool()
        assertNull(p.currentKey())
        assertNull(p.rotate())
    }

    @Test
    fun rotateWalksThroughKeysAndWrapsAround() {
        val p = pool("a", "b", "c")
        assertEquals("a", p.currentKey())
        assertEquals("b", p.rotate())
        assertEquals("c", p.rotate())
        assertEquals("a", p.rotate()) // all exhausted -> reset -> next key in order
        assertEquals("b", p.rotate())
    }

    @Test
    fun singleKeyRotatesToItself() {
        val p = pool("only")
        assertEquals("only", p.rotate())
        assertEquals("only", p.currentKey())
    }

    @Test
    fun resetGoesBackToFirstKey() {
        val p = pool("a", "b")
        p.rotate()
        p.reset()
        assertEquals("a", p.currentKey())
        assertTrue(p.hasMultipleKeys)
    }
}

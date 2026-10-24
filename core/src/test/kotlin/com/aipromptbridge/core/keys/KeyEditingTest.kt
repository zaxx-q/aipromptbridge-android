package com.aipromptbridge.core.keys

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class KeyEditingTest {
    private val k1 = KeyEntry("Key 1", "aaaa-1111")
    private val k2 = KeyEntry("Work", "bbbb-2222")

    @Test
    fun maskNeverRevealsMoreThanTheLastFourCharacters() {
        val masked = KeyEditing.mask("abcd1234efgh5678")
        assertEquals("••••5678", masked)
        assertFalse(masked.contains("abcd"))
    }

    @Test
    fun shortKeysRevealNothing() {
        assertEquals("••••••••", KeyEditing.mask("secret"))
        assertEquals("••••••••", KeyEditing.mask("12345678901")) // 11 chars
        assertEquals("••••8901", KeyEditing.mask("123456788901")) // 12 chars
    }

    @Test
    fun nextNameFillsGaps() {
        assertEquals("Key 1", KeyEditing.nextName(emptyList()))
        assertEquals("Key 2", KeyEditing.nextName(listOf(KeyEntry("Key 1", "x"), KeyEntry("Key 3", "y"))))
    }

    @Test
    fun summaryDescribesRotationOnlyForSeveralKeys() {
        assertEquals("No keys", KeyEditing.summary(0))
        assertEquals("1 key", KeyEditing.summary(1))
        assertEquals("3 keys · rotation on", KeyEditing.summary(3))
    }

    @Test
    fun addTrimsAndIgnoresBlankValues() {
        val added = KeyEditing.add(listOf(k1), "  ", "  new-secret  ")
        assertEquals(listOf(k1, KeyEntry("Key 2", "new-secret")), added)
        assertEquals(listOf(k1), KeyEditing.add(listOf(k1), "Name", "   "))
        assertEquals("Home", KeyEditing.add(emptyList(), " Home ", "v").single().name)
    }

    @Test
    fun renameAndReplaceKeepTheOtherFieldAndIgnoreBlanks() {
        val entries = listOf(k1, k2)
        assertEquals(listOf(k1, KeyEntry("Spare", "bbbb-2222")), KeyEditing.rename(entries, 1, " Spare "))
        assertEquals(listOf(k1, KeyEntry("Work", "cccc-3333")), KeyEditing.replaceValue(entries, 1, " cccc-3333 "))
        assertSame(entries, KeyEditing.rename(entries, 1, " "))
        assertSame(entries, KeyEditing.replaceValue(entries, 1, ""))
        assertSame(entries, KeyEditing.rename(entries, 5, "x"))
        assertSame(entries, KeyEditing.replaceValue(entries, -1, "x"))
    }

    @Test
    fun removeAndContains() {
        val entries = listOf(k1, k2)
        assertEquals(listOf(k2), KeyEditing.remove(entries, 0))
        assertSame(entries, KeyEditing.remove(entries, 9))
        assertTrue(KeyEditing.containsValue(entries, " bbbb-2222 "))
        assertFalse(KeyEditing.containsValue(entries, ""))
    }
}

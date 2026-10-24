package com.aipromptbridge.core.prompt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ModifierEditingTest {
    private fun modifier(key: String, label: String = key, injection: String = "do $key", forcesChat: Boolean = false) =
        PromptModifier(key = key, icon = "bolt", label = label, tooltip = "tip", injection = injection, forcesChat = forcesChat)

    private val defaults = listOf(modifier("short"), modifier("formal"))

    // ---- reading

    @Test
    fun statusDistinguishesBuiltInEditedAndCustom() {
        val current = listOf(modifier("short"), modifier("formal", injection = "mine"), modifier("custom_x"))
        assertNull(ModifierEditing.status(current, defaults, "short"))
        assertEquals("Edited", ModifierEditing.status(current, defaults, "formal"))
        assertEquals("Custom", ModifierEditing.status(current, defaults, "custom_x"))
        assertTrue(ModifierEditing.isBuiltIn(defaults, "short"))
        assertFalse(ModifierEditing.isBuiltIn(defaults, "custom_x"))
        assertFalse(ModifierEditing.isModified(current, defaults, "custom_x"))
    }

    // ---- validation / keys

    @Test
    fun validationMessages() {
        assertNotNull(ModifierEditing.validate(modifier("a", label = " ")))
        assertNotNull(ModifierEditing.validate(modifier("a", label = "x".repeat(ModifierEditing.MAX_LABEL_LENGTH + 1))))
        assertNotNull(ModifierEditing.validate(modifier("a", injection = "  ")))
        assertNull(ModifierEditing.validate(modifier("a")))
    }

    @Test
    fun newKeysAreUniqueAndNeverCollideWithBuiltIns() {
        assertEquals("custom_polite", ModifierEditing.newKey("Polite", defaults.map { it.key }.toSet()))
        assertEquals("custom_polite_2", ModifierEditing.newKey("Polite", setOf("custom_polite")))
    }

    // ---- create / update / delete / reset

    @Test
    fun createAppendsAndRefusesDuplicateKeys() {
        val created = ModifierEditing.create(defaults, modifier("custom_x", label = " Mine "))
        assertEquals(listOf("short", "formal", "custom_x"), created.map { it.key })
        assertEquals("Mine", created.last().label)
        assertSame(defaults, ModifierEditing.create(defaults, modifier("short")))
    }

    @Test
    fun updateReplacesInPlaceKeepingOrderAndIgnoresUnknownKeys() {
        val edited = ModifierEditing.update(defaults, modifier("short", injection = "shorter!", forcesChat = true))
        assertEquals(listOf("short", "formal"), edited.map { it.key })
        assertEquals("shorter!", edited.first().injection)
        assertTrue(edited.first().forcesChat)
        assertSame(defaults, ModifierEditing.update(defaults, modifier("nope")))
    }

    @Test
    fun onlyUserCreatedModifiersCanBeDeleted() {
        val current = defaults + modifier("custom_x")
        assertEquals(listOf("short", "formal"), ModifierEditing.delete(current, defaults, "custom_x").map { it.key })
        assertSame(current, ModifierEditing.delete(current, defaults, "short")) // built-in: would come back on merge
        assertEquals(current, ModifierEditing.delete(current, defaults, "nope"))
    }

    @Test
    fun resetRestoresTheShippedDefinition() {
        val edited = listOf(modifier("short", injection = "mine"), modifier("formal"))
        val reset = ModifierEditing.resetToDefault(edited, defaults, "short")
        assertEquals(defaults, reset)
        assertSame(edited, ModifierEditing.resetToDefault(edited, defaults, "custom_x"))
        // A built-in that went missing is re-added at the end.
        assertEquals(listOf("formal", "short"), ModifierEditing.resetToDefault(listOf(modifier("formal")), defaults, "short").map { it.key })
    }

    // ---- through the catalog + merge

    @Test
    fun editsPersistThroughTheCatalogAndSurviveAMerge() {
        val shipped = PromptsConfig(modifiers = defaults)
        val repository = InMemoryPromptsRepository()
        val catalog = PromptCatalog(repository, shipped)

        catalog.updateModifiers { ModifierEditing.update(it, modifier("short", injection = "mine")) }
        catalog.updateModifiers { ModifierEditing.create(it, modifier("custom_x")) }

        val saved = assertNotNull(repository.load())
        assertEquals("mine", saved.modifiers.first { it.key == "short" }.injection)

        // A fresh catalog (next app start) merges the saved file with the shipped defaults.
        val reloaded = PromptCatalog(repository, shipped).config().modifiers
        assertEquals(listOf("short", "formal", "custom_x"), reloaded.map { it.key })
        assertEquals("mine", reloaded.first { it.key == "short" }.injection)
    }
}

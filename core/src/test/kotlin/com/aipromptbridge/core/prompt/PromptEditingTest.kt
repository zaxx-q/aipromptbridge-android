package com.aipromptbridge.core.prompt

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PromptEditingTest {
    private fun action(id: String, system: String = "sys", isDefault: Boolean = true, label: String = id) =
        PromptAction(id = id, label = label, icon = "bolt", systemPrompt = system, task = "task", isDefault = isDefault)

    /** The shipped config the user starts from. */
    private val defaults = ToolPromptConfig(
        groups = listOf(
            PromptGroup("Edit", true, listOf("a", "b")),
            PromptGroup("Understand", true, listOf("c")),
        ),
        actions = listOf(action("a"), action("b"), action("c"), action(PromptComposer.CUSTOM_ID)).associateBy { it.id },
        barOrder = listOf("a", "c"),
    )

    private fun ToolPromptConfig.actionIds(group: String) = groups.first { it.name == group }.items

    // ---- reading

    @Test
    fun sectionsHideSpecialActionsAndAppendUngroupedOnes() {
        val tool = defaults.copy(actions = defaults.actions + ("x" to action("x", isDefault = false)))
        val sections = PromptEditing.sections(tool)
        assertEquals(listOf("Edit", "Understand", PromptEditing.OTHER_GROUP), sections.map { it.groupName })
        assertEquals(listOf("a", "b"), sections[0].actions.map { it.id })
        assertEquals(listOf("x"), sections[2].actions.map { it.id })
        assertTrue(sections.flatMap { it.actions }.none { it.isSpecial })
    }

    @Test
    fun sectionsIncludeDisabledGroupsAndSkipEmptyOnes() {
        val tool = defaults.copy(
            groups = listOf(PromptGroup("Edit", enabled = false, items = listOf("a")), PromptGroup("Empty", true, emptyList())),
        )
        assertEquals(listOf("Edit", PromptEditing.OTHER_GROUP), PromptEditing.sections(tool).map { it.groupName })
    }

    @Test
    fun groupLookupAndAssignableGroups() {
        assertEquals("Edit", PromptEditing.groupOf(defaults, "b"))
        assertNull(PromptEditing.groupOf(defaults, "zzz"))
        assertEquals(listOf("Edit", "Understand", PromptEditing.CUSTOM_GROUP), PromptEditing.assignableGroups(defaults))
        val withCustom = defaults.copy(groups = defaults.groups + PromptGroup(PromptEditing.CUSTOM_GROUP))
        assertEquals(1, PromptEditing.assignableGroups(withCustom).count { it == PromptEditing.CUSTOM_GROUP })
    }

    @Test
    fun tokenEstimateIsAboutFourCharactersPerToken() {
        assertEquals(0, PromptEditing.estimateTokens(""))
        assertEquals(1, PromptEditing.estimateTokens("abcd"))
        assertEquals(2, PromptEditing.estimateTokens("abcde"))
    }

    // ---- validation and ids

    @Test
    fun validationMessages() {
        assertNotNull(PromptEditing.validate(action("a", label = "  ")))
        assertNotNull(PromptEditing.validate(action("a", label = "x".repeat(PromptEditing.MAX_LABEL_LENGTH + 1))))
        assertNotNull(PromptEditing.validate(action("a").copy(systemPrompt = " ", task = "")))
        assertNull(PromptEditing.validate(action("a")))
        assertNull(PromptEditing.validate(action("a").copy(systemPrompt = "", task = "do it")))
    }

    @Test
    fun newIdsAreSlugsWithAPrefixAndStayUnique() {
        assertEquals("custom_tone_to_formal", PromptEditing.newId("Tone to Formal", emptySet()))
        assertEquals("custom_hello_world", PromptEditing.newId("Hello,  World!", emptySet()))
        assertEquals("custom", PromptEditing.newId("!!!", emptySet()))
        assertEquals("custom_tone_2", PromptEditing.newId("Tone", setOf("custom_tone")))
        assertEquals("custom_tone_3", PromptEditing.newId("Tone", setOf("custom_tone", "custom_tone_2")))
        assertTrue(PromptEditing.newId("y".repeat(100), emptySet()).length <= "custom_".length + 24)
    }

    // ---- create

    @Test
    fun createAddsAUserActionToANewGroupAndTheBar() {
        val created = PromptEditing.create(defaults, action("custom_x", label = " Mine "), PromptEditing.CUSTOM_GROUP, onBar = true)
        val stored = created.actions.getValue("custom_x")
        assertFalse(stored.isDefault)
        assertEquals("Mine", stored.label)
        assertEquals(listOf("custom_x"), created.actionIds(PromptEditing.CUSTOM_GROUP))
        assertEquals(listOf("a", "c", "custom_x"), created.barOrder)
    }

    @Test
    fun createCanSkipTheBarAndRefusesDuplicatesAndSpecialIds() {
        val off = PromptEditing.create(defaults, action("custom_x"), "Edit", onBar = false)
        assertFalse("custom_x" in off.barOrder)
        assertEquals(listOf("a", "b", "custom_x"), off.actionIds("Edit"))

        assertSame(defaults, PromptEditing.create(defaults, action("a"), "Edit", onBar = true))
        assertSame(defaults, PromptEditing.create(defaults, action("_ask"), "Edit", onBar = true))
    }

    // ---- update

    @Test
    fun savingWithoutChangesKeepsABuiltInAction() {
        val result = PromptEditing.update(defaults, defaults, defaults.actions.getValue("a"), "Edit", onBar = true)
        assertTrue(result.actions.getValue("a").isDefault)
        assertEquals(defaults, result)
    }

    @Test
    fun editingABuiltInActionMarksItUserModified() {
        val edited = defaults.actions.getValue("a").copy(systemPrompt = "mine")
        val result = PromptEditing.update(defaults, defaults, edited, "Edit", onBar = true)
        assertEquals("mine", result.actions.getValue("a").systemPrompt)
        assertFalse(result.actions.getValue("a").isDefault)
    }

    @Test
    fun updateCanUnpinAndMoveGroupsAndMovingBackIsRemembered() {
        val moved = PromptEditing.update(defaults, defaults, defaults.actions.getValue("a"), PromptEditing.CUSTOM_GROUP, onBar = false)
        assertEquals(PromptEditing.CUSTOM_GROUP, PromptEditing.groupOf(moved, "a"))
        assertFalse("a" in moved.barOrder)
        assertEquals(setOf("a"), moved.deletedGroupItems["Edit"])

        val back = PromptEditing.update(moved, defaults, moved.actions.getValue("a"), "Edit", onBar = true)
        assertEquals("Edit", PromptEditing.groupOf(back, "a"))
        assertNull(back.deletedGroupItems["Edit"])
        assertTrue("a" in back.barOrder)
    }

    @Test
    fun updateIgnoresUnknownAndSpecialActions() {
        assertSame(defaults, PromptEditing.update(defaults, defaults, action("nope"), "Edit", onBar = true))
        val special = defaults.actions.getValue(PromptComposer.CUSTOM_ID).copy(systemPrompt = "hacked")
        assertSame(defaults, PromptEditing.update(defaults, defaults, special, "Edit", onBar = true))
    }

    // ---- delete / reset

    @Test
    fun deletingABuiltInActionRemovesItEverywhereAndRemembersIt() {
        val result = PromptEditing.delete(defaults, defaults, "a")
        assertNull(result.actions["a"])
        assertFalse("a" in result.barOrder)
        assertFalse("a" in result.actionIds("Edit"))
        assertEquals(setOf("a"), result.deletedDefaults)
    }

    @Test
    fun deletingAnEditedBuiltInActionIsStillRemembered() {
        val edited = defaults.copy(actions = defaults.actions + ("b" to action("b", system = "mine", isDefault = false)))
        assertEquals(setOf("b"), PromptEditing.delete(edited, defaults, "b").deletedDefaults)
    }

    @Test
    fun deletingAUserActionLeavesDeletedDefaultsAlone() {
        val withUser = PromptEditing.create(defaults, action("custom_x"), "Edit", onBar = true)
        val result = PromptEditing.delete(withUser, defaults, "custom_x")
        assertTrue(result.deletedDefaults.isEmpty())
        assertNull(result.actions["custom_x"])
    }

    @Test
    fun specialAndUnknownActionsCannotBeDeleted() {
        assertSame(defaults, PromptEditing.delete(defaults, defaults, PromptComposer.CUSTOM_ID))
        assertSame(defaults, PromptEditing.delete(defaults, defaults, "nope"))
    }

    @Test
    fun resetRevertsAnEditedActionButKeepsItsBarAndGroup() {
        val edited = defaults.copy(actions = defaults.actions + ("a" to action("a", system = "mine", isDefault = false)))
        val result = PromptEditing.resetToDefault(edited, defaults, "a")
        assertEquals("sys", result.actions.getValue("a").systemPrompt)
        assertTrue(result.actions.getValue("a").isDefault)
        assertEquals(edited.barOrder, result.barOrder)
        assertEquals(edited.groups, result.groups)
    }

    @Test
    fun resetBringsADeletedActionBackIntoItsGroup() {
        val deleted = PromptEditing.delete(defaults, defaults, "a")
        val restored = PromptEditing.resetToDefault(deleted, defaults, "a")
        assertNotNull(restored.actions["a"])
        assertTrue(restored.deletedDefaults.isEmpty())
        assertEquals(listOf("b", "a"), restored.actionIds("Edit"))
        assertFalse("a" in restored.barOrder) // pinning is the user's choice, not restored automatically
        assertEquals(listOf("a"), PromptEditing.deletedDefaultActions(deleted, defaults).map { it.id })
    }

    @Test
    fun resetIgnoresIdsThatAreNotBuiltIn() {
        assertSame(defaults, PromptEditing.resetToDefault(defaults, defaults, "custom_x"))
    }

    // ---- bar

    @Test
    fun setOnBarAppendsRemovesAndIgnoresUnknownIds() {
        assertEquals(listOf("a", "c", "b"), PromptEditing.setOnBar(defaults, "b", true).barOrder)
        assertEquals(listOf("c"), PromptEditing.setOnBar(defaults, "a", false).barOrder)
        assertSame(defaults, PromptEditing.setOnBar(defaults, "a", true))
        assertSame(defaults, PromptEditing.setOnBar(defaults, "nope", true))
    }

    @Test
    fun moveOnBarSwapsAndStopsAtTheEnds() {
        assertEquals(listOf("c", "a"), PromptEditing.moveOnBar(defaults, "c", -1).barOrder)
        assertEquals(listOf("c", "a"), PromptEditing.moveOnBar(defaults, "a", 1).barOrder)
        assertSame(defaults, PromptEditing.moveOnBar(defaults, "a", -5))
        assertSame(defaults, PromptEditing.moveOnBar(defaults, "b", 1)) // not pinned
    }

    // ---- interplay with app updates (PromptMerge)

    @Test
    fun editsSurviveAnAppUpdateThatShipsNewDefaults() {
        // The user deletes "a" and edits "b"...
        val afterDelete = PromptEditing.delete(defaults, defaults, "a")
        val user = PromptEditing.update(afterDelete, defaults, defaults.actions.getValue("b").copy(systemPrompt = "mine"), "Edit", onBar = true)

        // ...then an update ships a changed "b" and a brand-new "d".
        val newDefaults = defaults.copy(
            actions = defaults.actions + ("b" to action("b", system = "new")) + ("d" to action("d")),
            groups = listOf(PromptGroup("Edit", true, listOf("a", "b", "d")), PromptGroup("Understand", true, listOf("c"))),
        )
        val merged = PromptMerge.mergeTool(user, newDefaults)

        assertNull(merged.actions["a"])
        assertEquals("mine", merged.actions.getValue("b").systemPrompt)
        assertNotNull(merged.actions["d"])
        assertEquals(listOf("b", "d"), merged.actionIds("Edit"))
    }

    @Test
    fun aMovedBuiltInActionStaysInItsNewGroupAfterAnUpdate() {
        val user = PromptEditing.update(defaults, defaults, defaults.actions.getValue("c"), PromptEditing.CUSTOM_GROUP, onBar = true)
        val merged = PromptMerge.mergeTool(user, defaults)
        assertEquals(PromptEditing.CUSTOM_GROUP, PromptEditing.groupOf(merged, "c"))
        assertTrue(merged.actionIds("Understand").isEmpty())
    }
}

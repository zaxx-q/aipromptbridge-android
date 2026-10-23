package com.aipromptbridge.core.prompt

import com.aipromptbridge.core.tool.ToolId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/** Sanity checks on the generated default prompt data (catches generator/mapping mistakes). */
class DefaultPromptsTest {
    private val config = DefaultPrompts.build()

    @Test
    fun everyToolHasActionsGroupsAndBar() {
        for (tool in ToolId.entries) {
            val cfg = config.tool(tool)
            assertTrue(cfg.actions.isNotEmpty(), "$tool has no actions")
            assertTrue(cfg.groups.isNotEmpty(), "$tool has no groups")
            assertTrue(cfg.barOrder.isNotEmpty(), "$tool has no bar")
        }
    }

    @Test
    fun barAndGroupsOnlyReferenceExistingActions() {
        for (tool in ToolId.entries) {
            val cfg = config.tool(tool)
            for (id in cfg.barOrder) assertNotNull(cfg.actions[id], "$tool bar references missing action $id")
            for (group in cfg.groups) for (id in group.items) assertNotNull(cfg.actions[id], "$tool group ${group.name} references missing $id")
        }
    }

    @Test
    fun specialActionsExist() {
        val text = config.tool(ToolId.TEXT_EDIT)
        assertNotNull(text.actions[PromptComposer.CUSTOM_ID])
        assertNotNull(text.actions[PromptComposer.ASK_ID])
        assertNotNull(config.tool(ToolId.SNIP).actions[PromptComposer.CUSTOM_ID])
    }

    @Test
    fun realActionsHaveSystemPromptsAndStableDefaults() {
        val text = config.tool(ToolId.TEXT_EDIT)
        for (action in text.actions.values) {
            assertTrue(action.systemPrompt.isNotBlank(), "${action.id} has an empty system prompt")
            assertTrue(action.isDefault)
        }
        val humanize = text.actions.getValue("humanize")
        assertEquals("Humanizer", humanize.label)
        assertEquals(PromptType.EDIT, humanize.promptType)
        assertTrue(humanize.systemPrompt.length > 5_000, "Humanize prompt should be the long original one")
    }

    @Test
    fun modifiersAreLoaded() {
        val keys = config.modifiers.map { it.key }
        assertTrue("variations" in keys && "shorter" in keys && "formal" in keys)
        assertTrue(config.modifiers.first { it.key == "variations" }.forcesChat)
    }
}

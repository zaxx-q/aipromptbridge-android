package com.aipromptbridge.core.prompt

import com.aipromptbridge.core.tool.ToolId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class PromptMergeTest {
    private fun action(id: String, system: String = "sys", isDefault: Boolean = true) =
        PromptAction(id = id, label = id, icon = "bolt", systemPrompt = system, task = "task", isDefault = isDefault)

    private fun tool(
        vararg actions: PromptAction,
        groups: List<PromptGroup> = emptyList(),
        settings: Map<String, String> = emptyMap(),
        bar: List<String> = emptyList(),
    ) = ToolPromptConfig(settings = settings, groups = groups, actions = actions.associateBy { it.id }, barOrder = bar)

    @Test
    fun nullUserConfigYieldsDefaults() {
        val defaults = tool(action("a"))
        assertSame(defaults, PromptMerge.mergeTool(null, defaults))
    }

    @Test
    fun untouchedDefaultIsReplacedByNewDefault() {
        val user = tool(action("a", system = "old"))
        val defaults = tool(action("a", system = "new"))
        assertEquals("new", PromptMerge.mergeTool(user, defaults).actions.getValue("a").systemPrompt)
    }

    @Test
    fun userEditedDefaultIsKept() {
        val user = tool(action("a", system = "mine", isDefault = false))
        val defaults = tool(action("a", system = "new"))
        assertEquals("mine", PromptMerge.mergeTool(user, defaults).actions.getValue("a").systemPrompt)
    }

    @Test
    fun deletedDefaultStaysDeletedAndNewDefaultsAreAdded() {
        val user = ToolPromptConfig(deletedDefaults = setOf("gone"), actions = mapOf("a" to action("a")))
        val defaults = tool(action("a"), action("gone"), action("fresh"))
        val merged = PromptMerge.mergeTool(user, defaults)
        assertNull(merged.actions["gone"])
        assertTrue(merged.actions.containsKey("fresh"))
    }

    @Test
    fun userCreatedActionsSurvive() {
        val user = tool(action("a"), action("mine", isDefault = false))
        val merged = PromptMerge.mergeTool(user, tool(action("a")))
        assertTrue(merged.actions.containsKey("mine"))
    }

    @Test
    fun modifiedSettingsAreProtectedOthersUpdate() {
        val user = ToolPromptConfig(
            settings = mapOf("x" to "user-x", "y" to "old-y"),
            modifiedSettings = setOf("x"),
        )
        val defaults = tool(settings = mapOf("x" to "new-x", "y" to "new-y"))
        val merged = PromptMerge.mergeTool(user, defaults)
        assertEquals("user-x", merged.settings["x"])
        assertEquals("new-y", merged.settings["y"])
    }

    @Test
    fun groupsKeepUserOrderAndGainNewDefaultItemsAndGroups() {
        val user = ToolPromptConfig(
            actions = mapOf("a" to action("a"), "b" to action("b")),
            groups = listOf(PromptGroup("G", false, listOf("b", "a"))),
        )
        val defaults = tool(
            action("a"), action("b"), action("c"), action("d"),
            groups = listOf(PromptGroup("G", true, listOf("a", "b", "c")), PromptGroup("H", true, listOf("d"))),
        )
        val merged = PromptMerge.mergeTool(user, defaults)

        val g = merged.groups.first { it.name == "G" }
        assertEquals(listOf("b", "a", "c"), g.items) // user order kept, new item appended
        assertFalse(g.enabled) // user's enabled flag kept
        assertEquals(listOf("d"), merged.groups.first { it.name == "H" }.items) // new group added
    }

    @Test
    fun deletedGroupsAndItemsAreNotResurrected() {
        val user = ToolPromptConfig(
            actions = mapOf("a" to action("a")),
            groups = listOf(PromptGroup("G", true, listOf("a"))),
            deletedGroups = setOf("H"),
            deletedGroupItems = mapOf("G" to setOf("c")),
        )
        val defaults = tool(
            action("a"), action("c"), action("d"),
            groups = listOf(PromptGroup("G", true, listOf("a", "c")), PromptGroup("H", true, listOf("d"))),
        )
        val merged = PromptMerge.mergeTool(user, defaults)
        assertEquals(listOf("a"), merged.groups.first { it.name == "G" }.items)
        assertTrue(merged.groups.none { it.name == "H" })
    }

    @Test
    fun barOrderDropsMissingActionsAndKeepsUserOrder() {
        val user = ToolPromptConfig(
            actions = mapOf("a" to action("a"), "b" to action("b")),
            barOrder = listOf("b", "ghost", "a"),
        )
        val merged = PromptMerge.mergeTool(user, tool(action("a"), action("b")))
        assertEquals(listOf("b", "a"), merged.barOrder)
    }

    @Test
    fun mergeAllPreservesUnknownToolsFromNewerBuilds() {
        val user = PromptsConfig(tools = mapOf("future_tool" to tool(action("z"))))
        val merged = PromptMerge.mergeAll(user, DefaultPrompts.build())
        assertTrue(merged.tools.containsKey("future_tool"))
        assertTrue(merged.tools.containsKey(ToolId.TEXT_EDIT.key))
    }
}

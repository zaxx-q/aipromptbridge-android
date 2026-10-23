package com.aipromptbridge.core.prompt

import com.aipromptbridge.core.tool.ToolId

/** Assembles the built-in default [PromptsConfig] from the generated data. */
object DefaultPrompts {
    fun build(): PromptsConfig = PromptsConfig(
        version = 1,
        chatWindowSystemInstruction = DefaultPromptsData.CHAT_WINDOW_SYSTEM_INSTRUCTION,
        modifiers = DefaultPromptsData.modifiers,
        tools = linkedMapOf(
            ToolId.TEXT_EDIT.key to ToolPromptConfig(
                settings = DefaultPromptsData.textEditSettings,
                groups = DefaultPromptsData.textEditGroups,
                actions = DefaultPromptsData.textEditActions.associateBy { it.id },
                barOrder = DefaultPromptsData.textEditBarOrder,
            ),
            ToolId.SNIP.key to ToolPromptConfig(
                settings = DefaultPromptsData.snipSettings,
                groups = DefaultPromptsData.snipGroups,
                actions = DefaultPromptsData.snipActions.associateBy { it.id },
                barOrder = DefaultPromptsData.snipBarOrder,
            ),
        ),
    )
}

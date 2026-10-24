package com.aipromptbridge.feature.prompteditor

import com.aipromptbridge.AppContainer
import com.aipromptbridge.core.prompt.DefaultPrompts
import com.aipromptbridge.core.prompt.PromptAction
import com.aipromptbridge.core.prompt.ToolPromptConfig
import com.aipromptbridge.core.tool.ToolId

/** What the editor screens need: the user's current config for one tool, and the shipped defaults to compare with. */
class EditorSnapshot(val tool: ToolPromptConfig, val defaults: ToolPromptConfig) {
    /** "Edited" (a built-in action the user changed), "Custom" (created by the user) or null (untouched built-in). */
    fun statusOf(action: PromptAction): String? = when {
        action.isDefault -> null
        action.id in defaults.actions -> "Edited"
        else -> "Custom"
    }
}

/** May read and merge prompts.json on first use, so call off the main thread. */
fun loadEditorSnapshot(container: AppContainer, tool: ToolId): EditorSnapshot =
    EditorSnapshot(container.promptCatalog.tool(tool), DefaultPrompts.build().tool(tool))

fun toolTitle(tool: ToolId): String = when (tool) {
    ToolId.TEXT_EDIT -> "Text"
    ToolId.SNIP -> "Snip"
}

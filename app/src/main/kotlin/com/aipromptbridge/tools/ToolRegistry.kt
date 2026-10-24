package com.aipromptbridge.tools

import com.aipromptbridge.core.tool.ToolId

/**
 * Describes a user-facing feature ("tool") for the setup screen. This is the extension point for the future:
 * Audio analysis, TTS, File processor... each becomes one AiTool here + a ToolId in :core + a feature package.
 */
interface AiTool {
    val id: ToolId
    val title: String
    val description: String
    val howToUse: String
    /** false = scaffolded but not finished (shown as "in progress" in the UI). */
    val implemented: Boolean
}

object TextEditTool : AiTool {
    override val id = ToolId.TEXT_EDIT
    override val title = "Text Edit"
    override val description = "Fix, rewrite or explain selected text with one tap, in any app."
    override val howToUse = "Select text in any app, open the selection menu (⋮ / More) and choose AIPromptBridge."
    override val implemented = true
}

object SnipTool : AiTool {
    override val id = ToolId.SNIP
    override val title = "Snip & Ask"
    override val description = "Capture the screen, select a region and run an AI action on it."
    override val howToUse = "Add the \"Snip & Ask\" Quick Settings tile, then tap it from any screen."
    // Verified on device (see README Phase 7).
    override val implemented = true
}

object ToolRegistry {
    val all: List<AiTool> = listOf(TextEditTool, SnipTool)
}

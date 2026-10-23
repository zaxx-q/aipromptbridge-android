package com.aipromptbridge.core.tool

/**
 * Every AIPromptBridge "tool" (feature) has a stable id. Prompt configuration is stored per tool,
 * exactly like the sections of the original app's prompts.json.
 *
 * HOW TO ADD A FUTURE FEATURE (e.g. Audio, TTS, File Processor):
 *  1. Add an entry here.
 *  2. Add its default prompts to DefaultPrompts (prompt/DefaultPrompts.kt).
 *  3. Add a feature package under app/.../feature/<name> and register an AiTool in app/.../tools/ToolRegistry.kt.
 * Nothing in provider/, keys/, profile/ or engine/ needs to change.
 */
enum class ToolId(val key: String, val originPrefix: String) {
    TEXT_EDIT("text_edit_tool", "textedit"),
    SNIP("snip_tool", "snip"),
    // TODO(future): AUDIO("audio_tool", "audio"), TTS("tts_tool", "tts")
}

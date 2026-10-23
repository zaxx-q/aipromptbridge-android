package com.aipromptbridge.core.prompt

import com.aipromptbridge.core.tool.ToolId

/** Persistence for the user's prompts.json. */
interface PromptsRepository {
    fun load(): PromptsConfig?
    fun save(config: PromptsConfig)
}

class InMemoryPromptsRepository(private var stored: PromptsConfig? = null) : PromptsRepository {
    override fun load(): PromptsConfig? = stored
    override fun save(config: PromptsConfig) {
        stored = config
    }
}

/**
 * Single access point for prompt configuration: defaults merged with user edits, cached in memory.
 * Methods may touch the disk (first load / save) so call them off the main thread (the engine does).
 */
class PromptCatalog(
    private val repository: PromptsRepository,
    private val defaults: PromptsConfig = DefaultPrompts.build(),
) {
    private var cached: PromptsConfig? = null

    @Synchronized
    fun config(): PromptsConfig {
        cached?.let { return it }
        val merged = PromptMerge.mergeAll(repository.load(), defaults)
        cached = merged
        repository.save(merged)
        return merged
    }

    fun tool(tool: ToolId): ToolPromptConfig = config().tool(tool)

    fun action(tool: ToolId, actionId: String): PromptAction? = tool(tool).actions[actionId]

    @Synchronized
    fun update(config: PromptsConfig) {
        cached = config
        repository.save(config)
    }

    /** Convenience for the prompt editor: transform one tool's config and persist. */
    @Synchronized
    fun updateTool(tool: ToolId, transform: (ToolPromptConfig) -> ToolPromptConfig) {
        val current = config()
        val updated = current.copy(tools = current.tools + (tool.key to transform(current.tool(tool))))
        update(updated)
    }

    /** "Reset to defaults" for the settings screen. */
    @Synchronized
    fun resetToDefaults() = update(defaults)
}

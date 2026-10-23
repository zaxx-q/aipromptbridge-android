package com.aipromptbridge.core.prompt

import com.aipromptbridge.core.tool.ToolId
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * EDIT    = strict output (only the processed text); its result can REPLACE the selection.
 * GENERAL = conversational/explanatory output; shown to the user instead of replacing text.
 */
@Serializable
enum class PromptType {
    @SerialName("edit") EDIT,
    @SerialName("general") GENERAL,
}

/**
 * One AI action = one chip in the UI ("Fix Grammar", "Humanizer", "Describe image", ...).
 * Naming note: the original Python app calls the chip row "ModifierBar" but it contains ACTIONS.
 * Real "modifiers" (Shorter, Formal...) are [PromptModifier] — a different concept.
 */
@Serializable
data class PromptAction(
    /** Stable key used for storage and merging, e.g. "proofread". Special actions start with "_". */
    val id: String,
    /** Text shown in the UI. */
    val label: String,
    /** Icon id resolved by the app's AipbIcons (e.g. "spellcheck"). */
    val icon: String,
    val systemPrompt: String,
    val task: String,
    val promptType: PromptType = PromptType.GENERAL,
    /** true = show the result to the user (sheet/chat) instead of replacing the selection. */
    val showInChat: Boolean = false,
    /** Edit actions only: apply the result without the diff review step. */
    val autoApply: Boolean = false,
    /** Optional ConnectionProfile name overriding the active profile for this action. */
    val connectionProfile: String? = null,
    /**
     * true  = untouched built-in default: app updates REPLACE it with the new default.
     * false = user created or user edited: app updates never overwrite it. The prompt editor MUST set this to false on edit.
     */
    val isDefault: Boolean = true,
) {
    val isSpecial: Boolean get() = id.startsWith("_")
}

@Serializable
data class PromptGroup(
    val name: String,
    val enabled: Boolean = true,
    /** Action ids in display order. */
    val items: List<String> = emptyList(),
)

/**
 * A toggleable instruction injected into the SYSTEM prompt (e.g. "Shorter", "Formal", "Variations").
 * Named PromptModifier to avoid clashing with Compose's `Modifier`.
 * v1 models and tests these in :core; the UI for them is a later phase (see README).
 */
@Serializable
data class PromptModifier(
    val key: String,
    val icon: String,
    val label: String,
    val tooltip: String,
    val injection: String,
    /** true = when active, the result must be shown to the user instead of replacing text. */
    val forcesChat: Boolean = false,
)

/** Per-tool prompt configuration (one section of prompts.json). */
@Serializable
data class ToolPromptConfig(
    /** String settings keyed by [SettingKeys]. A Map keeps merging generic when new settings are added. */
    val settings: Map<String, String> = emptyMap(),
    /** Setting keys the user changed; updates never overwrite these. */
    val modifiedSettings: Set<String> = emptySet(),
    val groups: List<PromptGroup> = emptyList(),
    val actions: Map<String, PromptAction> = emptyMap(),
    /** Ordered action ids pinned to the one-tap bar (user reorderable). */
    val barOrder: List<String> = emptyList(),
    /** Default action ids the user deleted; never re-added by updates. */
    val deletedDefaults: Set<String> = emptySet(),
    val deletedGroups: Set<String> = emptySet(),
    /** group name -> default item ids the user removed from that group. */
    val deletedGroupItems: Map<String, Set<String>> = emptyMap(),
) {
    fun setting(key: String): String = settings[key].orEmpty()

    /** Actions pinned to the bar, in bar order (unknown ids skipped). */
    fun barActions(): List<PromptAction> = barOrder.mapNotNull { actions[it] }

    /** Enabled groups with their resolved actions (unknown ids skipped, empty groups dropped). */
    fun resolvedGroups(): List<Pair<PromptGroup, List<PromptAction>>> =
        groups.filter { it.enabled }
            .map { group -> group to group.items.mapNotNull { actions[it] } }
            .filter { (_, list) -> list.isNotEmpty() }
}

/** Root of prompts.json. */
@Serializable
data class PromptsConfig(
    val version: Int = 1,
    /** System instruction used for follow-up chat after an action result. */
    val chatWindowSystemInstruction: String = "",
    val modifiers: List<PromptModifier> = emptyList(),
    /** Keyed by [ToolId.key]. Unknown keys (tools from newer builds) are preserved on merge. */
    val tools: Map<String, ToolPromptConfig> = emptyMap(),
) {
    fun tool(tool: ToolId): ToolPromptConfig = tools[tool.key] ?: ToolPromptConfig()
}

/** Keys of [ToolPromptConfig.settings]. */
object SettingKeys {
    const val CHAT_SYSTEM_INSTRUCTION = "chat_system_instruction"
    const val BASE_OUTPUT_RULES_EDIT = "base_output_rules_edit"
    const val BASE_OUTPUT_RULES_GENERAL = "base_output_rules_general"
    const val TEXT_DELIMITER = "text_delimiter"
    const val TEXT_DELIMITER_CLOSE = "text_delimiter_close"
    const val CUSTOM_TASK_TEMPLATE = "custom_task_template"
    const val ASK_TASK_TEMPLATE = "ask_task_template"
}

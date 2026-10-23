package com.aipromptbridge.core.prompt

/**
 * Merges the user's saved prompts with the app's built-in defaults so app updates can ship new/improved defaults
 * WITHOUT clobbering user edits (ported from the original `_is_default` / `deleted_defaults` scheme).
 *
 * Rules per tool:
 *  - Default action the user never touched (isDefault = true)  -> replaced by the latest default.
 *  - Default action the user edited (isDefault = false)        -> user's version kept.
 *  - Default action the user deleted (in deletedDefaults)      -> stays deleted.
 *  - Brand-new default action                                  -> added.
 *  - User-created action                                       -> kept.
 *  - Settings: replaced by new defaults unless listed in modifiedSettings.
 *  - Groups: user order/enabled kept; new default groups & items appended unless the user deleted them.
 */
object PromptMerge {

    fun mergeAll(user: PromptsConfig?, defaults: PromptsConfig): PromptsConfig {
        if (user == null) return defaults

        val tools = LinkedHashMap<String, ToolPromptConfig>()
        for ((key, defaultTool) in defaults.tools) {
            tools[key] = mergeTool(user.tools[key], defaultTool)
        }
        // Tools this build does not know about (written by a newer version) are preserved untouched.
        for ((key, userTool) in user.tools) {
            if (key !in tools) tools[key] = userTool
        }

        val defaultModifierKeys = defaults.modifiers.map { it.key }.toSet()
        val modifiers = defaults.modifiers.map { default -> user.modifiers.firstOrNull { it.key == default.key } ?: default } +
            user.modifiers.filter { it.key !in defaultModifierKeys }

        return PromptsConfig(
            version = maxOf(user.version, defaults.version),
            chatWindowSystemInstruction = user.chatWindowSystemInstruction.ifBlank { defaults.chatWindowSystemInstruction },
            modifiers = modifiers,
            tools = tools,
        )
    }

    fun mergeTool(user: ToolPromptConfig?, defaults: ToolPromptConfig): ToolPromptConfig {
        if (user == null) return defaults

        // ---- actions
        val actions = LinkedHashMap<String, PromptAction>()
        for ((id, defaultAction) in defaults.actions) {
            if (id in user.deletedDefaults) continue
            val existing = user.actions[id]
            actions[id] = if (existing == null || existing.isDefault) defaultAction else existing
        }
        for ((id, userAction) in user.actions) {
            if (id !in actions && id !in defaults.actions) actions[id] = userAction
        }

        // ---- settings
        val settings = LinkedHashMap<String, String>()
        for ((key, defaultValue) in defaults.settings) {
            settings[key] = if (key in user.modifiedSettings) user.settings[key] ?: defaultValue else defaultValue
        }
        for ((key, value) in user.settings) {
            if (key !in settings) settings[key] = value
        }

        // ---- groups
        val groups = mutableListOf<PromptGroup>()
        for (userGroup in user.groups) {
            val defaultGroup = defaults.groups.firstOrNull { it.name == userGroup.name }
            val keptItems = userGroup.items.filter { it in actions }
            if (defaultGroup == null) {
                groups += userGroup.copy(items = keptItems)
                continue
            }
            val deletedItems = user.deletedGroupItems[userGroup.name].orEmpty()
            val newItems = defaultGroup.items.filter { it !in userGroup.items && it !in deletedItems && it in actions }
            groups += userGroup.copy(items = keptItems + newItems)
        }
        for (defaultGroup in defaults.groups) {
            if (defaultGroup.name in user.deletedGroups) continue
            if (groups.none { it.name == defaultGroup.name }) {
                groups += defaultGroup.copy(items = defaultGroup.items.filter { it in actions })
            }
        }

        return ToolPromptConfig(
            settings = settings,
            modifiedSettings = user.modifiedSettings,
            groups = groups,
            actions = actions,
            barOrder = user.barOrder.filter { it in actions },
            deletedDefaults = user.deletedDefaults,
            deletedGroups = user.deletedGroups,
            deletedGroupItems = user.deletedGroupItems,
        )
    }
}

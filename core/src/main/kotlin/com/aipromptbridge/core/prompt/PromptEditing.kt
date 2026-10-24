package com.aipromptbridge.core.prompt

/** One section of the action list: a group heading and the actions in it. */
data class ActionSection(val groupName: String, val actions: List<PromptAction>)

/**
 * Pure operations behind the prompt editor. Every function takes a [ToolPromptConfig] and returns a new one; the UI
 * persists the result with `PromptCatalog.updateTool(tool) { PromptEditing.xyz(it, ...) }`.
 *
 * Rules that keep app updates and user edits compatible (see [PromptMerge]):
 *  - Editing a built-in action marks it `isDefault = false`, so updates stop replacing it.
 *  - Deleting a built-in id (even one the user already edited) records it in `deletedDefaults`, so updates never
 *    bring it back. Restoring removes it from there again.
 *  - Moving a built-in action out of its original group records `deletedGroupItems`, so updates do not re-add it there.
 *
 * `defaults` parameters are the built-in config of the SAME tool (`DefaultPrompts.build().tool(id)`).
 * Special actions (`_custom`, `_ask`) are internal plumbing for the instruction field: they never appear in listings
 * and cannot be edited or deleted here.
 */
object PromptEditing {
    const val CUSTOM_GROUP = "Custom"
    const val OTHER_GROUP = "Other"
    const val MAX_LABEL_LENGTH = 40

    // ------------------------------------------------------------------ reading

    /**
     * Sections for the editor list. Unlike [ToolPromptConfig.resolvedGroups] this includes disabled groups (nothing may
     * be hidden from the editor) and appends actions that belong to no group under [OTHER_GROUP]. Empty sections and
     * special actions are skipped.
     */
    fun sections(tool: ToolPromptConfig): List<ActionSection> {
        val sections = mutableListOf<ActionSection>()
        val seen = mutableSetOf<String>()
        for (group in tool.groups) {
            val actions = group.items.mapNotNull { id -> tool.actions[id]?.takeIf { !it.isSpecial && seen.add(id) } }
            if (actions.isNotEmpty()) sections += ActionSection(group.name, actions)
        }
        val ungrouped = tool.actions.values.filter { !it.isSpecial && it.id !in seen }
        if (ungrouped.isNotEmpty()) sections += ActionSection(OTHER_GROUP, ungrouped)
        return sections
    }

    /** Name of the group that lists [id], or null when it is in none. */
    fun groupOf(tool: ToolPromptConfig, id: String): String? = tool.groups.firstOrNull { id in it.items }?.name

    fun isOnBar(tool: ToolPromptConfig, id: String): Boolean = id in tool.barOrder

    /** Group names a user can assign an action to: existing groups plus [CUSTOM_GROUP] (created on demand). */
    fun assignableGroups(tool: ToolPromptConfig): List<String> {
        val names = tool.groups.map { it.name }
        return if (CUSTOM_GROUP in names) names else names + CUSTOM_GROUP
    }

    /** Built-in actions the user deleted and could bring back. */
    fun deletedDefaultActions(tool: ToolPromptConfig, defaults: ToolPromptConfig): List<PromptAction> =
        tool.deletedDefaults.mapNotNull { defaults.actions[it] }.filter { !it.isSpecial }

    /** Rough token estimate (about 4 characters per token). Good enough for a size hint; not a billing figure. */
    fun estimateTokens(text: String): Int = (text.length + 3) / 4

    // ------------------------------------------------------------------ validation / ids

    /** Returns a user-facing problem, or null when [action] can be saved. */
    fun validate(action: PromptAction): String? = when {
        action.label.isBlank() -> "Give the action a name."
        action.label.trim().length > MAX_LABEL_LENGTH -> "Use at most $MAX_LABEL_LENGTH characters for the name."
        action.systemPrompt.isBlank() && action.task.isBlank() -> "Add a system instruction or a task."
        else -> null
    }

    /** A fresh id like `custom_tone_to_formal`. The prefix guarantees it never collides with a built-in id. */
    fun newId(label: String, existing: Set<String>): String {
        val slug = buildString {
            for (ch in label.trim().lowercase()) {
                if (ch.isLetterOrDigit()) append(ch) else if (isNotEmpty() && last() != '_') append('_')
            }
        }.trim('_').take(24).trim('_')
        val base = if (slug.isEmpty()) "custom" else "custom_$slug"
        if (base !in existing) return base
        var n = 2
        while ("${base}_$n" in existing) n++
        return "${base}_$n"
    }

    // ------------------------------------------------------------------ create / update / delete

    /** Adds a user-created action to [groupName] (created if needed) and optionally pins it to the bar. */
    fun create(tool: ToolPromptConfig, action: PromptAction, groupName: String, onBar: Boolean): ToolPromptConfig {
        if (action.isSpecial || action.id in tool.actions) return tool
        val stored = action.copy(label = action.label.trim(), isDefault = false)
        val withAction = tool.copy(actions = tool.actions + (stored.id to stored))
        return setOnBar(addToGroup(withAction, stored.id, groupName), stored.id, onBar)
    }

    /**
     * Saves an edit. The action is marked user-modified ONLY when something actually changed, so opening and saving a
     * built-in action without touching it does not stop it from receiving updates.
     */
    fun update(
        tool: ToolPromptConfig,
        defaults: ToolPromptConfig,
        edited: PromptAction,
        groupName: String,
        onBar: Boolean,
    ): ToolPromptConfig {
        val original = tool.actions[edited.id] ?: return tool
        if (original.isSpecial) return tool
        val cleaned = edited.copy(label = edited.label.trim())
        val changed = cleaned.copy(isDefault = original.isDefault) != original
        val stored = if (changed) cleaned.copy(isDefault = false) else original
        val updated = tool.copy(actions = tool.actions + (stored.id to stored))
        return setOnBar(moveToGroup(updated, defaults, stored.id, groupName), stored.id, onBar)
    }

    /** Deletes an action everywhere. Built-in ids are remembered so updates do not resurrect them. */
    fun delete(tool: ToolPromptConfig, defaults: ToolPromptConfig, id: String): ToolPromptConfig {
        val action = tool.actions[id] ?: return tool
        if (action.isSpecial) return tool
        return tool.copy(
            actions = tool.actions - id,
            barOrder = tool.barOrder - id,
            groups = tool.groups.map { it.copy(items = it.items - id) },
            deletedDefaults = if (id in defaults.actions) tool.deletedDefaults + id else tool.deletedDefaults,
        )
    }

    /**
     * Puts a built-in action back to its shipped definition. Works for an edited action (content reverts, bar and group
     * stay) and for a deleted one (it returns to its original group(s)). Unknown ids change nothing.
     */
    fun resetToDefault(tool: ToolPromptConfig, defaults: ToolPromptConfig, id: String): ToolPromptConfig {
        val shipped = defaults.actions[id] ?: return tool
        val existed = id in tool.actions
        var result = tool.copy(
            actions = tool.actions + (id to shipped),
            deletedDefaults = tool.deletedDefaults - id,
        )
        if (!existed) {
            // Re-attach to the groups the built-in config lists it in; otherwise it would exist but be unreachable.
            for (group in defaults.groups.filter { id in it.items }) {
                result = addToGroup(result, id, group.name)
                result = result.copy(deletedGroupItems = result.deletedGroupItems - group.name + dropItem(result.deletedGroupItems, group.name, id))
            }
        }
        return result
    }

    // ------------------------------------------------------------------ bar

    /** Pins ([on] = true, appended at the end) or unpins an action. Unknown ids are ignored. */
    fun setOnBar(tool: ToolPromptConfig, id: String, on: Boolean): ToolPromptConfig = when {
        id !in tool.actions -> tool
        on && id !in tool.barOrder -> tool.copy(barOrder = tool.barOrder + id)
        !on && id in tool.barOrder -> tool.copy(barOrder = tool.barOrder - id)
        else -> tool
    }

    /** Moves a pinned action by [delta] positions (negative = earlier). Stops at both ends; no-op if not pinned. */
    fun moveOnBar(tool: ToolPromptConfig, id: String, delta: Int): ToolPromptConfig {
        val from = tool.barOrder.indexOf(id)
        if (from < 0) return tool
        val to = (from + delta).coerceIn(0, tool.barOrder.lastIndex)
        if (to == from) return tool
        val order = tool.barOrder.toMutableList()
        order.add(to, order.removeAt(from))
        return tool.copy(barOrder = order)
    }

    // ------------------------------------------------------------------ groups

    /** Moves [id] to [target] (created if missing). See the class comment for how built-in membership is remembered. */
    fun moveToGroup(tool: ToolPromptConfig, defaults: ToolPromptConfig, id: String, target: String): ToolPromptConfig {
        if (id !in tool.actions) return tool
        val name = target.trim()
        if (name.isEmpty()) return tool
        val current = groupOf(tool, id)
        if (current == name) return tool

        var result = tool.copy(groups = tool.groups.map { it.copy(items = it.items - id) })
        if (current != null && defaults.groups.any { it.name == current && id in it.items }) {
            result = result.copy(
                deletedGroupItems = result.deletedGroupItems + (current to (result.deletedGroupItems[current].orEmpty() + id)),
            )
        }
        result = addToGroup(result, id, name)
        // Putting a built-in action back into its original group cancels the earlier "removed from this group" mark.
        return result.copy(deletedGroupItems = result.deletedGroupItems - name + dropItem(result.deletedGroupItems, name, id))
    }

    /** Appends [id] to group [name], creating (and un-deleting) the group when needed. No duplicates. */
    private fun addToGroup(tool: ToolPromptConfig, id: String, name: String): ToolPromptConfig {
        val index = tool.groups.indexOfFirst { it.name == name }
        val groups = if (index >= 0) {
            tool.groups.toMutableList().also {
                val group = it[index]
                if (id !in group.items) it[index] = group.copy(items = group.items + id)
            }
        } else {
            tool.groups + PromptGroup(name = name, enabled = true, items = listOf(id))
        }
        return tool.copy(groups = groups, deletedGroups = tool.deletedGroups - name)
    }

    /** `deletedGroupItems[group]` without [id]; empty sets are dropped entirely. Returned as 0 or 1 map entries. */
    private fun dropItem(map: Map<String, Set<String>>, group: String, id: String): Map<String, Set<String>> {
        val remaining = map[group].orEmpty() - id
        return if (remaining.isEmpty()) emptyMap() else mapOf(group to remaining)
    }
}

package com.aipromptbridge.core.prompt

/**
 * Pure operations behind the modifier editor. Every function takes the current modifier list and returns a new one;
 * the UI persists it with `PromptCatalog.updateModifiers { ModifierEditing.xyz(it, ...) }`.
 *
 * Modifiers have no "is default" flag and `PromptMerge` re-adds every built-in key, so:
 *  - a built-in modifier can be EDITED and RESET, but not deleted (it would simply come back on the next merge);
 *  - a user-created modifier can be deleted.
 * `defaults` parameters are `DefaultPrompts.build().modifiers`.
 */
object ModifierEditing {
    const val MAX_LABEL_LENGTH = 30

    fun isBuiltIn(defaults: List<PromptModifier>, key: String): Boolean = defaults.any { it.key == key }

    /** True when a built-in modifier differs from its shipped definition. Always false for user-created ones. */
    fun isModified(current: List<PromptModifier>, defaults: List<PromptModifier>, key: String): Boolean {
        val shipped = defaults.firstOrNull { it.key == key } ?: return false
        val now = current.firstOrNull { it.key == key } ?: return false
        return now != shipped
    }

    /** "Edited" / "Custom" / null (untouched built-in), for list rows. */
    fun status(current: List<PromptModifier>, defaults: List<PromptModifier>, key: String): String? = when {
        !isBuiltIn(defaults, key) -> "Custom"
        isModified(current, defaults, key) -> "Edited"
        else -> null
    }

    /** Returns a user-facing problem, or null when [modifier] can be saved. */
    fun validate(modifier: PromptModifier): String? = when {
        modifier.label.isBlank() -> "Give the modifier a name."
        modifier.label.trim().length > MAX_LABEL_LENGTH -> "Use at most $MAX_LABEL_LENGTH characters for the name."
        modifier.injection.isBlank() -> "Write the instruction that is added to the prompt when this modifier is on."
        else -> null
    }

    /** A fresh key like `custom_polite`; the prefix guarantees it never collides with a built-in key. */
    fun newKey(label: String, existing: Set<String>): String = PromptEditing.newId(label, existing)

    /** Appends a user-created modifier. Does nothing when its key is already taken. */
    fun create(current: List<PromptModifier>, modifier: PromptModifier): List<PromptModifier> {
        if (current.any { it.key == modifier.key }) return current
        return current + modifier.copy(label = modifier.label.trim())
    }

    /** Replaces the modifier with the same key. The key itself never changes. Unknown keys change nothing. */
    fun update(current: List<PromptModifier>, edited: PromptModifier): List<PromptModifier> {
        if (current.none { it.key == edited.key }) return current
        return current.map { if (it.key == edited.key) edited.copy(label = edited.label.trim()) else it }
    }

    /** Deletes a USER-CREATED modifier. Built-in and unknown keys change nothing. */
    fun delete(current: List<PromptModifier>, defaults: List<PromptModifier>, key: String): List<PromptModifier> {
        if (isBuiltIn(defaults, key)) return current
        return current.filter { it.key != key }
    }

    /** Puts a built-in modifier back to its shipped definition (re-adding it if it is somehow missing). */
    fun resetToDefault(current: List<PromptModifier>, defaults: List<PromptModifier>, key: String): List<PromptModifier> {
        val shipped = defaults.firstOrNull { it.key == key } ?: return current
        return if (current.any { it.key == key }) {
            current.map { if (it.key == key) shipped else it }
        } else {
            current + shipped
        }
    }
}

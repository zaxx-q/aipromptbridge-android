package com.aipromptbridge.core.session

import com.aipromptbridge.core.model.ChatMessage
import com.aipromptbridge.core.model.Role
import kotlinx.serialization.Serializable

/** Text-only persisted message (images are intentionally not stored). */
@Serializable
data class StoredMessage(val role: String, val text: String)

/**
 * A chat conversation started from an action result (Chat mode / follow-ups).
 * Like the original app, a session NEVER stores provider details: only an optional profile NAME override.
 */
@Serializable
data class ChatSession(
    val id: Long,
    /** e.g. "textedit:proofread" or "snip:describe" (see PromptComposer.ComposedPrompt.sessionOrigin). */
    val origin: String,
    val title: String,
    val profileOverride: String? = null,
    val messages: List<StoredMessage> = emptyList(),
    val createdAt: Long,
    val updatedAt: Long,
) {
    fun toChatMessages(): List<ChatMessage> = messages.map {
        val role = when (it.role) {
            "assistant" -> Role.ASSISTANT
            "system" -> Role.SYSTEM
            else -> Role.USER
        }
        ChatMessage(role, listOf(com.aipromptbridge.core.model.ContentPart.Text(it.text)))
    }

    fun withMessage(role: Role, text: String, now: Long): ChatSession =
        copy(messages = messages + StoredMessage(role.name.lowercase(), text), updatedAt = now)

    /** Removes the newest message (used to regenerate the last answer). No-op for an empty session. */
    fun withoutLastMessage(now: Long): ChatSession =
        if (messages.isEmpty()) this else copy(messages = messages.dropLast(1), updatedAt = now)
}

object ContextMarker {
    /** Prepended to the first user message of a follow-up chat so the model knows what task produced the result. */
    fun forAction(label: String): String = "[Task: $label]"
}

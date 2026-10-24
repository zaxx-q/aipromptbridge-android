package com.aipromptbridge.core.session

import com.aipromptbridge.core.model.ChatMessage
import com.aipromptbridge.core.model.ContentPart
import com.aipromptbridge.core.model.Role

/**
 * A finished exchange that a chat continues from, e.g. a Snip result: the user asked something about an image and got an
 * answer, and now wants to keep talking about it.
 */
data class ChatSeed(
    /** Shown in the chat as the user's first message (just what they asked). */
    val userText: String,
    /** What the MODEL is told that first message was: the task marker plus the request. */
    val modelUserText: String,
    /** The answer already received; shown as the first assistant message. */
    val assistantText: String,
) {
    companion object {
        fun forAction(actionLabel: String, request: String, result: String) = ChatSeed(
            userText = request,
            modelUserText = "${ContextMarker.forAction(actionLabel)}\n\n$request",
            assistantText = result,
        )
    }
}

/**
 * Builds the messages sent to the model for a stored conversation. Sessions persist TEXT only, so everything the model
 * needs beyond the stored text is passed in explicitly:
 *
 *  - [firstUserText]: what the model should see in place of the FIRST user message (the task marker, the selected text,
 *    ...). It must be applied on EVERY request, not just the first one, or the model loses the context after one follow-up.
 *  - [image]: attached to that same first user message (image first, then text), so every later turn still "sees" it.
 */
object ChatHistory {
    fun build(
        systemInstruction: String,
        stored: List<StoredMessage>,
        firstUserText: String? = null,
        image: ContentPart.Image? = null,
    ): List<ChatMessage> {
        val messages = mutableListOf<ChatMessage>()
        if (systemInstruction.isNotBlank()) messages += ChatMessage.system(systemInstruction)

        var firstUserSeen = false
        for (message in stored) {
            val role = roleOf(message.role)
            if (role == Role.USER && !firstUserSeen) {
                firstUserSeen = true
                val text = ContentPart.Text(firstUserText ?: message.text)
                messages += ChatMessage(Role.USER, if (image != null) listOf(image, text) else listOf(text))
            } else {
                messages += ChatMessage(role, listOf(ContentPart.Text(message.text)))
            }
        }
        return messages
    }

    private fun roleOf(role: String): Role = when (role) {
        "assistant" -> Role.ASSISTANT
        "system" -> Role.SYSTEM
        else -> Role.USER
    }
}

package com.aipromptbridge.core.model

enum class Role { SYSTEM, USER, ASSISTANT }

/** A piece of message content. Sealed so `when` over it is exhaustive (polymorphism by type). */
sealed interface ContentPart {
    data class Text(val text: String) : ContentPart
    data class Image(val mimeType: String, val base64: String) : ContentPart
}

data class ChatMessage(val role: Role, val parts: List<ContentPart>) {
    /** All text parts joined by newlines. */
    val text: String get() = parts.filterIsInstance<ContentPart.Text>().joinToString("\n") { it.text }
    val hasImage: Boolean get() = parts.any { it is ContentPart.Image }

    companion object {
        fun system(text: String) = ChatMessage(Role.SYSTEM, listOf(ContentPart.Text(text)))
        fun user(text: String) = ChatMessage(Role.USER, listOf(ContentPart.Text(text)))
        fun assistant(text: String) = ChatMessage(Role.ASSISTANT, listOf(ContentPart.Text(text)))

        /** Image first, then the instruction text (matches the original app's snip messages). */
        fun userWithImage(text: String, mimeType: String, base64: String) =
            ChatMessage(Role.USER, listOf(ContentPart.Image(mimeType, base64), ContentPart.Text(text)))
    }
}

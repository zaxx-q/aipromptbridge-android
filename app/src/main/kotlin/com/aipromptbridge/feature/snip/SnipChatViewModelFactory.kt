package com.aipromptbridge.feature.snip

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.aipromptbridge.AppContainer
import com.aipromptbridge.core.model.ContentPart
import com.aipromptbridge.core.session.ChatSeed
import com.aipromptbridge.core.tool.ToolId
import com.aipromptbridge.feature.textedit.ChatViewModel

/**
 * "Continue in chat" for a Snip result. The conversation starts from the exchange that already happened (the request and
 * its answer) and keeps the cropped image attached to the first message, so follow-up questions can still look at it.
 * The image is only held in memory; the saved session contains text only.
 */
class SnipChatViewModelFactory(
    private val container: AppContainer,
    private val done: SnipResult.Done,
    private val pngBase64: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        ChatViewModel(
            engine = container.engine,
            catalog = container.promptCatalog,
            sessions = container.sessions,
            selectedText = "",
            isReadOnly = true, // there is no text selection to replace
            initialActionLabel = done.actionLabel,
            origin = "snip:${done.actionId}",
            title = done.request.take(40).ifBlank { done.actionLabel },
            barTool = ToolId.SNIP,
            image = ContentPart.Image("image/png", pngBase64),
            seed = ChatSeed.forAction(done.actionLabel, done.request, done.text),
        ) as T
}

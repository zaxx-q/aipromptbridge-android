package com.aipromptbridge.core.prompt

import com.aipromptbridge.core.model.ChatMessage
import com.aipromptbridge.core.tool.ToolId

/** What the user selected / captured. Sealed: the composer handles each case explicitly. */
sealed interface PromptInput {
    data class Text(val text: String) : PromptInput
    data class Image(val mimeType: String, val base64: String) : PromptInput
}

/** User-chosen override for where the result goes. DEFAULT = let the action decide. */
enum class ResponseMode { DEFAULT, REPLACE, SHOW, COPY, SHARE }

enum class ResultDestination { REPLACE, SHOW, COPY, SHARE }

data class PromptRequest(
    val action: PromptAction,
    val input: PromptInput,
    /** Text typed in the instruction field. Used by the special "_custom" and "_ask" actions. */
    val customInstruction: String? = null,
    val activeModifierKeys: Set<String> = emptySet(),
    val responseMode: ResponseMode = ResponseMode.DEFAULT,
)

data class ComposedPrompt(
    val messages: List<ChatMessage>,
    val destination: ResultDestination,
    /** e.g. "textedit:proofread"; stored on chat sessions so follow-ups know where they came from. */
    val sessionOrigin: String,
)

/**
 * Turns (action + input + modifiers) into provider-ready messages. Faithful port of the original text_edit_tool logic:
 *
 *   SYSTEM = action.systemPrompt  [+ "\n\n" + active modifier injections]
 *   USER   = join("\n\n", [task, outputRules]) + textDelimiter + selectedText + textDelimiterClose
 *            (for images: the instruction text plus the image part)
 *
 * Output rules depend on the action's promptType (EDIT = strict, GENERAL = permissive).
 */
object PromptComposer {
    const val CUSTOM_ID = "_custom"
    const val ASK_ID = "_ask"

    private const val PLACEHOLDER = "{custom_input}"
    private const val DEFAULT_DELIMITER = "\n\n<text_to_process>\n"
    private const val DEFAULT_DELIMITER_CLOSE = "\n</text_to_process>"
    private const val DEFAULT_CUSTOM_TEXT = "Apply this change to the text: {custom_input}"
    private const val DEFAULT_CUSTOM_IMAGE = "Regarding this image: {custom_input}"
    private const val DEFAULT_ASK_TEXT = "Regarding the text below, {custom_input}"

    fun compose(
        tool: ToolId,
        toolConfig: ToolPromptConfig,
        modifiers: List<PromptModifier>,
        request: PromptRequest,
    ): ComposedPrompt {
        val action = request.action
        val active = modifiers.filter { it.key in request.activeModifierKeys }

        // 1) System prompt (+ modifier injections)
        var systemPrompt = action.systemPrompt
        val injections = active.map { it.injection }.filter { it.isNotBlank() }
        if (injections.isNotEmpty()) systemPrompt += "\n\n" + injections.joinToString("\n\n")

        // 2) Task (special actions build it from a template + the user's typed instruction)
        var task = action.task
        val custom = request.customInstruction?.trim().orEmpty()
        if (custom.isNotEmpty()) {
            val isImage = request.input is PromptInput.Image
            val template = when (action.id) {
                CUSTOM_ID -> toolConfig.settings[SettingKeys.CUSTOM_TASK_TEMPLATE]
                    ?: if (isImage) DEFAULT_CUSTOM_IMAGE else DEFAULT_CUSTOM_TEXT
                ASK_ID -> toolConfig.settings[SettingKeys.ASK_TASK_TEMPLATE] ?: DEFAULT_ASK_TEXT
                else -> null
            }
            if (template != null) task = template.replace(PLACEHOLDER, custom)
        }

        // 3) Output rules by prompt type
        val rules = when (action.promptType) {
            PromptType.EDIT -> toolConfig.setting(SettingKeys.BASE_OUTPUT_RULES_EDIT)
            PromptType.GENERAL -> toolConfig.setting(SettingKeys.BASE_OUTPUT_RULES_GENERAL)
        }
        val instruction = listOf(task, rules).filter { it.isNotBlank() }.joinToString("\n\n")

        // 4) User message
        val userMessage = when (val input = request.input) {
            is PromptInput.Text -> {
                val delimiter = toolConfig.settings[SettingKeys.TEXT_DELIMITER] ?: DEFAULT_DELIMITER
                val close = toolConfig.settings[SettingKeys.TEXT_DELIMITER_CLOSE] ?: DEFAULT_DELIMITER_CLOSE
                ChatMessage.user(instruction + delimiter + input.text + close)
            }

            is PromptInput.Image -> ChatMessage.userWithImage(
                text = instruction.ifBlank { "Describe this image." },
                mimeType = input.mimeType,
                base64 = input.base64,
            )
        }

        val messages = buildList {
            if (systemPrompt.isNotBlank()) add(ChatMessage.system(systemPrompt))
            add(userMessage)
        }
        return ComposedPrompt(
            messages = messages,
            destination = resolveDestination(request.responseMode, action, active.any { it.forcesChat }),
            sessionOrigin = "${tool.originPrefix}:${action.id}",
        )
    }

    /** Hierarchy (same as the original): explicit response mode > modifier forces chat > action setting > prompt type. */
    internal fun resolveDestination(mode: ResponseMode, action: PromptAction, modifierForcesChat: Boolean): ResultDestination =
        when {
            mode == ResponseMode.SHOW -> ResultDestination.SHOW
            mode == ResponseMode.COPY -> ResultDestination.COPY
            mode == ResponseMode.SHARE -> ResultDestination.SHARE
            mode == ResponseMode.REPLACE -> ResultDestination.REPLACE
            modifierForcesChat -> ResultDestination.SHOW
            action.showInChat -> ResultDestination.SHOW
            action.promptType == PromptType.EDIT -> ResultDestination.REPLACE
            else -> ResultDestination.SHOW
        }
}

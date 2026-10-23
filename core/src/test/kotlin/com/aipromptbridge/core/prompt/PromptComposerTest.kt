package com.aipromptbridge.core.prompt

import com.aipromptbridge.core.model.ContentPart
import com.aipromptbridge.core.model.Role
import com.aipromptbridge.core.tool.ToolId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class PromptComposerTest {
    private val config = DefaultPrompts.build()
    private val textTool = config.tool(ToolId.TEXT_EDIT)
    private val snipTool = config.tool(ToolId.SNIP)

    private fun composeText(
        actionId: String,
        text: String = "teh cat sat",
        custom: String? = null,
        modifiers: Set<String> = emptySet(),
        mode: ResponseMode = ResponseMode.DEFAULT,
    ) = PromptComposer.compose(
        ToolId.TEXT_EDIT, textTool, config.modifiers,
        PromptRequest(textTool.actions.getValue(actionId), PromptInput.Text(text), custom, modifiers, mode),
    )

    @Test
    fun editActionBuildsSystemPlusUserWithRulesAndDelimiters() {
        val composed = composeText("proofread")
        val action = textTool.actions.getValue("proofread")

        assertEquals(listOf(Role.SYSTEM, Role.USER), composed.messages.map { it.role })
        assertEquals(action.systemPrompt, composed.messages[0].text)

        val user = composed.messages[1].text
        assertTrue(user.startsWith(action.task))
        assertTrue(user.contains(textTool.setting(SettingKeys.BASE_OUTPUT_RULES_EDIT)))
        assertTrue(user.endsWith("\n\n<text_to_process>\nteh cat sat\n</text_to_process>"))
        assertEquals(ResultDestination.REPLACE, composed.destination)
        assertEquals("textedit:proofread", composed.sessionOrigin)
    }

    @Test
    fun generalActionUsesGeneralRulesAndShowsResult() {
        val composed = composeText("explain")
        assertTrue(composed.messages[1].text.contains(textTool.setting(SettingKeys.BASE_OUTPUT_RULES_GENERAL)))
        assertEquals(ResultDestination.SHOW, composed.destination)
    }

    @Test
    fun modifierInjectionIsAppendedToSystemPrompt() {
        val composed = composeText("proofread", modifiers = setOf("shorter"))
        val shorter = config.modifiers.first { it.key == "shorter" }
        assertTrue(composed.messages[0].text.endsWith(shorter.injection))
        assertEquals(ResultDestination.REPLACE, composed.destination)
    }

    @Test
    fun forcingModifierRoutesResultToChat() {
        assertEquals(ResultDestination.SHOW, composeText("proofread", modifiers = setOf("variations")).destination)
    }

    @Test
    fun explicitResponseModeBeatsEverything() {
        assertEquals(ResultDestination.COPY, composeText("proofread", mode = ResponseMode.COPY).destination)
        assertEquals(ResultDestination.REPLACE, composeText("explain", mode = ResponseMode.REPLACE).destination)
    }

    @Test
    fun customActionBuildsTaskFromTemplate() {
        val composed = composeText(PromptComposer.CUSTOM_ID, custom = "make it shorter")
        assertTrue(composed.messages.last().text.startsWith("Apply this change to the text: make it shorter"))
    }

    @Test
    fun askActionBuildsTaskFromTemplate() {
        val composed = composeText(PromptComposer.ASK_ID, custom = "who is the subject?")
        assertTrue(composed.messages.last().text.startsWith("Regarding the text below, who is the subject?"))
        assertEquals(ResultDestination.SHOW, composed.destination)
    }

    @Test
    fun imageInputProducesImagePartAndInstruction() {
        val action = snipTool.actions.getValue("describe")
        val composed = PromptComposer.compose(
            ToolId.SNIP, snipTool, config.modifiers,
            PromptRequest(action, PromptInput.Image("image/png", "QUJD")),
        )
        val user = composed.messages.last()
        assertTrue(user.hasImage)
        assertEquals(ContentPart.Image("image/png", "QUJD"), user.parts.first())
        assertEquals(ResultDestination.SHOW, composed.destination)
        assertEquals("snip:describe", composed.sessionOrigin)
    }

    @Test
    fun snipCustomInstructionUsesImageTemplate() {
        val action = snipTool.actions.getValue(PromptComposer.CUSTOM_ID)
        val composed = PromptComposer.compose(
            ToolId.SNIP, snipTool, config.modifiers,
            PromptRequest(action, PromptInput.Image("image/png", "QUJD"), customInstruction = "what is the total?"),
        )
        assertEquals("Regarding this image: what is the total?", composed.messages.last().text)
    }
}

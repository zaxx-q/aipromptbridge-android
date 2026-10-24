package com.aipromptbridge.core.session

import com.aipromptbridge.core.model.ContentPart
import com.aipromptbridge.core.model.Role
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ChatHistoryTest {
    private val image = ContentPart.Image("image/png", "BASE64")

    private fun stored(vararg pairs: Pair<String, String>) = pairs.map { StoredMessage(it.first, it.second) }

    @Test
    fun systemInstructionComesFirstAndBlankOnesAreOmitted() {
        val withSystem = ChatHistory.build("be nice", stored("user" to "hi"))
        assertEquals(listOf(Role.SYSTEM, Role.USER), withSystem.map { it.role })
        assertEquals("be nice", withSystem.first().text)

        assertEquals(listOf(Role.USER), ChatHistory.build("  ", stored("user" to "hi")).map { it.role })
    }

    @Test
    fun rolesAreMappedAndUnknownOnesAreTreatedAsUser() {
        val history = ChatHistory.build("", stored("user" to "a", "assistant" to "b", "system" to "c", "weird" to "d"))
        assertEquals(listOf(Role.USER, Role.ASSISTANT, Role.SYSTEM, Role.USER), history.map { it.role })
    }

    @Test
    fun withoutOverridesTheStoredTextIsUsed() {
        val history = ChatHistory.build("", stored("user" to "q1", "assistant" to "a1", "user" to "q2"))
        assertEquals(listOf("q1", "a1", "q2"), history.map { it.text })
    }

    @Test
    fun theFirstUserOverrideSurvivesEveryFollowUp() {
        // Regression: the override used to be replaced by the NEWEST user text on turn two, so the model lost the context.
        val turnTwo = ChatHistory.build("", stored("user" to "q1", "assistant" to "a1", "user" to "q2"), firstUserText = "[Task: X]\n\nq1")
        assertEquals(listOf("[Task: X]\n\nq1", "a1", "q2"), turnTwo.map { it.text })

        val turnThree = ChatHistory.build(
            "",
            stored("user" to "q1", "assistant" to "a1", "user" to "q2", "assistant" to "a2", "user" to "q3"),
            firstUserText = "[Task: X]\n\nq1",
        )
        assertEquals(listOf("[Task: X]\n\nq1", "a1", "q2", "a2", "q3"), turnThree.map { it.text })
    }

    @Test
    fun theImageIsAttachedToTheFirstUserMessageOnlyAndComesFirst() {
        val history = ChatHistory.build("sys", stored("user" to "q1", "assistant" to "a1", "user" to "q2"), image = image)
        val users = history.filter { it.role == Role.USER }

        assertEquals(listOf(image, ContentPart.Text("q1")), users[0].parts)
        assertTrue(users[0].hasImage)
        assertFalse(users[1].hasImage)
        assertFalse(history.first { it.role == Role.SYSTEM }.hasImage)
        assertFalse(history.first { it.role == Role.ASSISTANT }.hasImage)
    }

    @Test
    fun imageAndOverrideCombine() {
        val history = ChatHistory.build("", stored("user" to "ask", "assistant" to "answer", "user" to "more"), "[Task: Describe]\n\nask", image)
        assertEquals(listOf(image, ContentPart.Text("[Task: Describe]\n\nask")), history[0].parts)
        assertEquals("more", history[2].text)
    }

    @Test
    fun anAssistantFirstConversationAttachesTheImageToTheFirstUserMessage() {
        val history = ChatHistory.build("", stored("assistant" to "hello", "user" to "q"), image = image)
        assertFalse(history[0].hasImage)
        assertTrue(history[1].hasImage)
    }

    @Test
    fun noImageMeansPlainTextParts() {
        assertEquals(listOf(ContentPart.Text("q")), ChatHistory.build("", stored("user" to "q")).single().parts)
    }

    // ---- ChatSeed

    @Test
    fun aSeedShowsTheRequestButTellsTheModelWhichTaskProducedIt() {
        val seed = ChatSeed.forAction("Describe", "Describe this image in detail.", "A red barn.")
        assertEquals("Describe this image in detail.", seed.userText)
        assertEquals("[Task: Describe]\n\nDescribe this image in detail.", seed.modelUserText)
        assertEquals("A red barn.", seed.assistantText)
    }

    @Test
    fun aSeededConversationYieldsTheOriginalExchangeThenTheFollowUp() {
        val seed = ChatSeed.forAction("Describe", "What is this?", "A barn.")
        val session = ChatSession(id = 1, origin = "snip:describe", title = "t", createdAt = 0, updatedAt = 0)
            .withMessage(Role.USER, seed.userText, 1)
            .withMessage(Role.ASSISTANT, seed.assistantText, 2)
            .withMessage(Role.USER, "What colour is the roof?", 3)

        val history = ChatHistory.build("sys", session.messages, seed.modelUserText, image)
        assertEquals(listOf(Role.SYSTEM, Role.USER, Role.ASSISTANT, Role.USER), history.map { it.role })
        assertEquals(listOf(image, ContentPart.Text("[Task: Describe]\n\nWhat is this?")), history[1].parts)
        assertEquals("A barn.", history[2].text)
        assertEquals("What colour is the roof?", history[3].text)
    }

    // ---- ChatSession.withoutLastMessage

    @Test
    fun withoutLastMessageDropsTheNewestAndTouchesTheTimestamp() {
        val session = ChatSession(id = 1, origin = "o", title = "t", createdAt = 0, updatedAt = 0)
            .withMessage(Role.USER, "q", 1)
            .withMessage(Role.ASSISTANT, "a", 2)
        val trimmed = session.withoutLastMessage(now = 9)
        assertEquals(listOf("q"), trimmed.messages.map { it.text })
        assertEquals(9L, trimmed.updatedAt)
    }

    @Test
    fun withoutLastMessageOnAnEmptySessionChangesNothing() {
        val empty = ChatSession(id = 1, origin = "o", title = "t", createdAt = 0, updatedAt = 0)
        assertSame(empty, empty.withoutLastMessage(now = 9))
    }
}

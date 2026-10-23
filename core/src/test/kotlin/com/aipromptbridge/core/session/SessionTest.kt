package com.aipromptbridge.core.session

import com.aipromptbridge.core.model.Role
import com.aipromptbridge.core.storage.FileSessionRepository
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SessionTest {
    @Test
    fun contextMarkerFormat() = assertEquals("[Task: Fix Grammar]", ContextMarker.forAction("Fix Grammar"))

    @Test
    fun inMemoryRepositoryCreatesIncrementingIds() {
        val repo = InMemorySessionRepository()
        val a = repo.create("textedit:proofread", "A", now = 1)
        val b = repo.create("snip:describe", "B", now = 2)
        assertEquals(listOf(1L, 2L), listOf(a.id, b.id))
        assertEquals(listOf(b.id, a.id), repo.all().map { it.id }) // newest first
        repo.delete(a.id)
        assertNull(repo.get(a.id))
    }

    @Test
    fun messagesConvertAndFileRepositoryRoundTrips() {
        val file = Files.createTempDirectory("aipb-sessions").toFile().resolve("sessions.json")
        val repo = FileSessionRepository(file)
        val session = repo.create("textedit:explain", "Explain", profileOverride = "Fast", now = 10)
            .withMessage(Role.USER, "hello", 11)
            .withMessage(Role.ASSISTANT, "hi there", 12)
        repo.save(session)

        val loaded = FileSessionRepository(file).get(session.id)!!
        assertEquals("Fast", loaded.profileOverride)
        assertEquals(listOf(Role.USER, Role.ASSISTANT), loaded.toChatMessages().map { it.role })
        assertEquals("hi there", loaded.toChatMessages().last().text)
        assertEquals(12L, loaded.updatedAt)
    }
}

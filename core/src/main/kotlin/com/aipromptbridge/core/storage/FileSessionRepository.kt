package com.aipromptbridge.core.storage

import com.aipromptbridge.core.session.ChatSession
import com.aipromptbridge.core.session.SessionRepository
import java.io.File
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException

@Serializable
internal data class SessionsFile(val nextId: Long = 1, val sessions: List<ChatSession> = emptyList())

/** JSON-file session store (simple and fine for a personal tool; swap for Room if history grows large). */
class FileSessionRepository(private val file: File) : SessionRepository {
    private fun read(): SessionsFile =
        try {
            if (file.exists()) CoreJson.json.decodeFromString(SessionsFile.serializer(), file.readText(Charsets.UTF_8))
            else SessionsFile()
        } catch (e: SerializationException) {
            SessionsFile()
        }

    private fun write(data: SessionsFile) {
        file.writeTextAtomically(CoreJson.json.encodeToString(SessionsFile.serializer(), data))
    }

    @Synchronized override fun all(): List<ChatSession> = read().sessions.sortedByDescending { it.updatedAt }
    @Synchronized override fun get(id: Long): ChatSession? = read().sessions.firstOrNull { it.id == id }

    @Synchronized
    override fun create(origin: String, title: String, profileOverride: String?, now: Long): ChatSession {
        val data = read()
        val session = ChatSession(data.nextId, origin, title, profileOverride, emptyList(), now, now)
        write(SessionsFile(data.nextId + 1, data.sessions + session))
        return session
    }

    @Synchronized
    override fun save(session: ChatSession) {
        val data = read()
        write(data.copy(sessions = data.sessions.filter { it.id != session.id } + session))
    }

    @Synchronized
    override fun delete(id: Long) {
        val data = read()
        write(data.copy(sessions = data.sessions.filter { it.id != id }))
    }
}

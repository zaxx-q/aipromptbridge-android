package com.aipromptbridge.core.session

interface SessionRepository {
    fun all(): List<ChatSession>
    fun get(id: Long): ChatSession?
    /** Creates a new session with a fresh id. */
    fun create(origin: String, title: String, profileOverride: String? = null, now: Long = System.currentTimeMillis()): ChatSession
    fun save(session: ChatSession)
    fun delete(id: Long)
}

class InMemorySessionRepository : SessionRepository {
    private val sessions = LinkedHashMap<Long, ChatSession>()
    private var nextId = 1L

    @Synchronized override fun all(): List<ChatSession> = sessions.values.sortedByDescending { it.updatedAt }
    @Synchronized override fun get(id: Long): ChatSession? = sessions[id]

    @Synchronized
    override fun create(origin: String, title: String, profileOverride: String?, now: Long): ChatSession {
        val session = ChatSession(nextId++, origin, title, profileOverride, emptyList(), now, now)
        sessions[session.id] = session
        return session
    }

    @Synchronized override fun save(session: ChatSession) { sessions[session.id] = session }
    @Synchronized override fun delete(id: Long) { sessions.remove(id) }
}

package com.aipromptbridge.core.profile

interface ProfileRepository {
    fun profiles(): List<ConnectionProfile>
    fun activeProfileName(): String?
    fun save(profiles: List<ConnectionProfile>, activeName: String?)
}

class InMemoryProfileRepository(
    private var stored: List<ConnectionProfile> = emptyList(),
    private var active: String? = null,
) : ProfileRepository {
    override fun profiles(): List<ConnectionProfile> = stored
    override fun activeProfileName(): String? = active
    override fun save(profiles: List<ConnectionProfile>, activeName: String?) {
        stored = profiles
        active = activeName
    }
}

/**
 * Resolution chain (same as the original app): session override -> action override -> active profile -> hard-coded fallback.
 * Unknown / blank names are skipped, so a deleted profile never breaks a request.
 */
object ProfileResolver {
    fun resolve(
        repository: ProfileRepository,
        sessionProfile: String? = null,
        actionProfile: String? = null,
    ): ConnectionProfile {
        val all = repository.profiles()
        fun find(name: String?): ConnectionProfile? =
            name?.takeIf { it.isNotBlank() }?.let { wanted -> all.firstOrNull { it.name == wanted } }

        return find(sessionProfile)
            ?: find(actionProfile)
            ?: find(repository.activeProfileName())
            ?: ConnectionProfile.FALLBACK
    }
}

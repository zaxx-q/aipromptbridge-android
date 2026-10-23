package com.aipromptbridge.core.profile

import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileResolverTest {
    private val fast = ConnectionProfile("Fast", "google", "flash")
    private val smart = ConnectionProfile("Smart", "anthropic", "opus")
    private val repo = InMemoryProfileRepository(listOf(fast, smart), active = "Fast")

    @Test
    fun sessionOverrideWins() =
        assertEquals(smart, ProfileResolver.resolve(repo, sessionProfile = "Smart", actionProfile = "Fast"))

    @Test
    fun actionOverrideBeatsActive() =
        assertEquals(smart, ProfileResolver.resolve(repo, actionProfile = "Smart"))

    @Test
    fun fallsBackToActiveProfile() = assertEquals(fast, ProfileResolver.resolve(repo))

    @Test
    fun unknownNamesAreSkipped() =
        assertEquals(fast, ProfileResolver.resolve(repo, sessionProfile = "Deleted", actionProfile = " "))

    @Test
    fun hardCodedFallbackWhenNothingConfigured() =
        assertEquals(ConnectionProfile.FALLBACK, ProfileResolver.resolve(InMemoryProfileRepository()))
}

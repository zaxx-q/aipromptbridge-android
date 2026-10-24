package com.aipromptbridge.core.profile

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class ProfileEditingTest {
    private fun profile(name: String, provider: String = "google", model: String = "m") =
        ConnectionProfile(name = name, provider = provider, model = model)

    private fun fields(draft: ConnectionProfile, others: List<ConnectionProfile> = emptyList()) =
        ProfileEditing.validate(draft, others).map { it.field }.toSet()

    // ---- validation

    @Test
    fun aNormalProfileIsValid() {
        assertTrue(ProfileEditing.validate(profile("Fast"), emptyList()).isEmpty())
    }

    @Test
    fun nameMustBePresentUniqueAndShort() {
        assertEquals(setOf(ProfileField.NAME), fields(profile("  ")))
        assertEquals(setOf(ProfileField.NAME), fields(profile("fast"), listOf(profile("Fast")))) // case-insensitive
        assertEquals(setOf(ProfileField.NAME), fields(profile("x".repeat(ProfileEditing.MAX_NAME_LENGTH + 1))))
        assertTrue(fields(profile("Fast"), listOf(profile("Smart"))).isEmpty())
    }

    @Test
    fun providerAndModelAreRequired() {
        assertEquals(setOf(ProfileField.PROVIDER, ProfileField.MODEL), fields(profile("A", provider = "nope", model = " ")))
    }

    @Test
    fun customProviderNeedsABaseUrl() {
        val custom = profile("C", provider = "custom")
        assertEquals(setOf(ProfileField.BASE_URL), fields(custom))
        assertTrue(fields(custom.copy(baseUrl = "https://my-server.example/v1")).isEmpty())
    }

    @Test
    fun baseUrlMustBeHttps() {
        assertEquals(setOf(ProfileField.BASE_URL), fields(profile("A").copy(baseUrl = "http://localhost:11434/v1")))
        assertEquals(setOf(ProfileField.BASE_URL), fields(profile("A").copy(baseUrl = "https://")))
        assertEquals(setOf(ProfileField.BASE_URL), fields(profile("A").copy(baseUrl = "https://a b.example")))
        assertTrue(fields(profile("A").copy(baseUrl = "HTTPS://api.example.com/v1/")).isEmpty())
    }

    @Test
    fun numericFieldsAreRangeChecked() {
        assertEquals(setOf(ProfileField.TEMPERATURE), fields(profile("A").copy(temperature = 2.5)))
        assertEquals(setOf(ProfileField.TEMPERATURE), fields(profile("A").copy(temperature = -0.1)))
        assertTrue(fields(profile("A").copy(temperature = 0.0)).isEmpty())
        assertTrue(fields(profile("A").copy(temperature = 2.0)).isEmpty())

        assertEquals(setOf(ProfileField.MAX_TOKENS), fields(profile("A").copy(maxOutputTokens = 0)))
        assertTrue(fields(profile("A").copy(maxOutputTokens = 1)).isEmpty())

        assertEquals(setOf(ProfileField.TIMEOUT), fields(profile("A").copy(requestTimeoutSeconds = 4)))
        assertEquals(setOf(ProfileField.TIMEOUT), fields(profile("A").copy(requestTimeoutSeconds = 301)))
        assertTrue(fields(profile("A").copy(requestTimeoutSeconds = 300)).isEmpty())
    }

    // ---- upsert / delete / active

    @Test
    fun firstProfileBecomesActiveAndIsTrimmed() {
        val book = ProfileEditing.upsert(ProfileBook(emptyList(), null), null, profile("  Fast  ", model = " flash "))
        assertEquals(listOf("Fast"), book.profiles.map { it.name })
        assertEquals("flash", book.profiles.single().model)
        assertEquals("Fast", book.activeName)
    }

    @Test
    fun addingKeepsTheActiveProfile() {
        val start = ProfileBook(listOf(profile("A"), profile("B")), "A")
        val book = ProfileEditing.upsert(start, null, profile("C"))
        assertEquals(listOf("A", "B", "C"), book.profiles.map { it.name })
        assertEquals("A", book.activeName)
    }

    @Test
    fun renamingTheActiveProfileKeepsItActive() {
        val start = ProfileBook(listOf(profile("A"), profile("B")), "A")
        val book = ProfileEditing.upsert(start, "A", profile("A2"))
        assertEquals(listOf("A2", "B"), book.profiles.map { it.name })
        assertEquals("A2", book.activeName)
    }

    @Test
    fun renamingAnInactiveProfileDoesNotChangeTheActiveOne() {
        val start = ProfileBook(listOf(profile("A"), profile("B")), "A")
        val book = ProfileEditing.upsert(start, "B", profile("B2"))
        assertEquals("A", book.activeName)
        assertEquals(listOf("A", "B2"), book.profiles.map { it.name })
    }

    @Test
    fun deletingTheActiveProfileActivatesTheFirstRemaining() {
        val start = ProfileBook(listOf(profile("A"), profile("B"), profile("C")), "B")
        val book = ProfileEditing.delete(start, "B")
        assertEquals(listOf("A", "C"), book.profiles.map { it.name })
        assertEquals("A", book.activeName)
    }

    @Test
    fun deletingAnInactiveProfileKeepsTheActiveOne() {
        val start = ProfileBook(listOf(profile("A"), profile("B")), "B")
        assertEquals("B", ProfileEditing.delete(start, "A").activeName)
    }

    @Test
    fun theLastProfileCannotBeDeleted() {
        val single = ProfileBook(listOf(profile("A")), "A")
        assertTrue(!ProfileEditing.canDelete(single, "A"))
        assertSame(single, ProfileEditing.delete(single, "A"))
    }

    @Test
    fun setActiveIgnoresUnknownNames() {
        val start = ProfileBook(listOf(profile("A"), profile("B")), "A")
        assertEquals("B", ProfileEditing.setActive(start, "B").activeName)
        assertEquals("A", ProfileEditing.setActive(start, "Nope").activeName)
    }

    @Test
    fun activeReturnsNullWhenNothingMatches() {
        assertNull(ProfileBook(listOf(profile("A")), "Gone").active())
    }

    // ---- names and pools

    @Test
    fun uniqueNameCountsUp() {
        val existing = listOf(profile("New profile"), profile("new profile 2"))
        assertEquals("New profile 3", ProfileEditing.uniqueName("New profile", existing))
        assertEquals("New profile", ProfileEditing.uniqueName("New profile", emptyList()))
    }

    @Test
    fun effectivePoolPrefersTheOverride() {
        assertEquals("google", ProfileEditing.effectivePool(profile("A")))
        assertEquals("work", ProfileEditing.effectivePool(profile("A").copy(apiKeyPool = " work ")))
        assertEquals("zzz", ProfileEditing.effectivePool(profile("A", provider = "zzz")))
    }

    @Test
    fun poolNamesListProvidersFirstThenCustomPoolsWithoutDuplicates() {
        val names = ProfileEditing.poolNames(
            listOf(profile("A").copy(apiKeyPool = "work"), profile("B").copy(apiKeyPool = "work"), profile("C")),
        )
        assertEquals("google", names.first())
        assertEquals("work", names.last())
        assertEquals(names.size, names.toSet().size)
    }
}

package com.aipromptbridge.core.profile

import com.aipromptbridge.core.provider.ProviderRegistry

/** profiles.json as the editor sees it: the list plus which one is active. */
data class ProfileBook(
    val profiles: List<ConnectionProfile>,
    val activeName: String?,
) {
    /** The profile requests really use right now (null = nothing configured, the built-in fallback applies). */
    fun active(): ConnectionProfile? = profiles.firstOrNull { it.name == activeName }
}

enum class ProfileField { NAME, PROVIDER, MODEL, BASE_URL, TEMPERATURE, MAX_TOKENS, TIMEOUT }

data class ProfileIssue(val field: ProfileField, val message: String)

/**
 * Pure rules for creating / editing / deleting connection profiles. The UI calls these and persists the result with
 * `ProfileRepository.save(book.profiles, book.activeName)`.
 *
 * Invariant kept by every operation: when at least one profile exists, [ProfileBook.activeName] names one of them.
 * (`ProfileResolver` falls back to the hard-coded profile when the active name is unknown, which would silently ignore
 * the user's profiles.)
 */
object ProfileEditing {
    const val MAX_NAME_LENGTH = 40
    const val MIN_TIMEOUT_SECONDS = 5
    const val MAX_TIMEOUT_SECONDS = 300
    const val MAX_TEMPERATURE = 2.0

    /** The key pool a profile reads its keys from: its own override, else the provider's default pool. */
    fun effectivePool(profile: ConnectionProfile): String =
        profile.apiKeyPool.trim().ifBlank { ProviderRegistry.find(profile.provider)?.keyPool ?: profile.provider }

    /** Every pool the key screen should offer: one per known provider, plus custom pools profiles point at. */
    fun poolNames(profiles: List<ConnectionProfile>): List<String> =
        (ProviderRegistry.all.map { it.keyPool } + profiles.map { effectivePool(it) }).distinct()

    /**
     * Checks a draft. [others] are all profiles EXCEPT the one being edited (so renaming to the same name is fine).
     * Returns an empty list when the draft can be saved.
     */
    fun validate(draft: ConnectionProfile, others: List<ConnectionProfile>): List<ProfileIssue> {
        val issues = mutableListOf<ProfileIssue>()
        val name = draft.name.trim()
        when {
            name.isEmpty() -> issues += ProfileIssue(ProfileField.NAME, "Give the profile a name.")
            name.length > MAX_NAME_LENGTH -> issues += ProfileIssue(ProfileField.NAME, "Use at most $MAX_NAME_LENGTH characters.")
            others.any { it.name.equals(name, ignoreCase = true) } ->
                issues += ProfileIssue(ProfileField.NAME, "A profile named \"$name\" already exists.")
        }

        val definition = ProviderRegistry.find(draft.provider)
        if (definition == null) issues += ProfileIssue(ProfileField.PROVIDER, "Choose a provider.")

        if (draft.model.isBlank()) issues += ProfileIssue(ProfileField.MODEL, "Enter a model id.")

        val url = draft.baseUrl.trim()
        if (url.isEmpty()) {
            if (definition?.requiresBaseUrl == true) {
                issues += ProfileIssue(ProfileField.BASE_URL, "A custom provider needs a base URL, for example https://my-server/v1.")
            }
        } else if (!isHttpsUrl(url)) {
            // The app manifest sets usesCleartextTraffic=false, so plain http:// would fail at request time.
            issues += ProfileIssue(ProfileField.BASE_URL, "Use an https:// URL (plain http is blocked on Android).")
        }

        draft.temperature?.let {
            if (it.isNaN() || it < 0.0 || it > MAX_TEMPERATURE) {
                issues += ProfileIssue(ProfileField.TEMPERATURE, "Temperature must be between 0 and 2.")
            }
        }
        draft.maxOutputTokens?.let {
            if (it < 1) issues += ProfileIssue(ProfileField.MAX_TOKENS, "Max output tokens must be at least 1.")
        }
        if (draft.requestTimeoutSeconds !in MIN_TIMEOUT_SECONDS..MAX_TIMEOUT_SECONDS) {
            issues += ProfileIssue(ProfileField.TIMEOUT, "Timeout must be $MIN_TIMEOUT_SECONDS to $MAX_TIMEOUT_SECONDS seconds.")
        }
        return issues
    }

    private fun isHttpsUrl(url: String): Boolean =
        url.length > "https://".length &&
            url.startsWith("https://", ignoreCase = true) &&
            url.none { it.isWhitespace() }

    /** Trims user-typed text fields. Call after [validate] succeeded. */
    fun normalize(draft: ConnectionProfile): ConnectionProfile =
        draft.copy(
            name = draft.name.trim(),
            model = draft.model.trim(),
            baseUrl = draft.baseUrl.trim(),
            apiKeyPool = draft.apiKeyPool.trim(),
        )

    /**
     * Adds a profile ([originalName] == null) or replaces the one called [originalName].
     * A rename keeps the active selection; the first profile ever added becomes active.
     * The edited profile is normalized (trimmed) before it is stored.
     */
    fun upsert(book: ProfileBook, originalName: String?, edited: ConnectionProfile): ProfileBook {
        val clean = normalize(edited)
        val index = if (originalName == null) -1 else book.profiles.indexOfFirst { it.name == originalName }
        val profiles = if (index >= 0) {
            book.profiles.toMutableList().also { it[index] = clean }
        } else {
            book.profiles + clean
        }
        val active = when {
            index >= 0 && book.activeName == originalName -> clean.name // renamed (or just edited) active profile
            book.active() != null -> book.activeName
            else -> clean.name // nothing valid was active before: the profile we just saved becomes active
        }
        return ProfileBook(profiles, active)
    }

    /** The last remaining profile cannot be deleted: the app always keeps at least one to resolve requests with. */
    fun canDelete(book: ProfileBook, name: String): Boolean =
        book.profiles.size > 1 && book.profiles.any { it.name == name }

    /** Removes [name]. If it was active, the first remaining profile becomes active. No-op when [canDelete] is false. */
    fun delete(book: ProfileBook, name: String): ProfileBook {
        if (!canDelete(book, name)) return book
        val remaining = book.profiles.filter { it.name != name }
        val active = if (book.activeName == name || remaining.none { it.name == book.activeName }) {
            remaining.first().name
        } else {
            book.activeName
        }
        return ProfileBook(remaining, active)
    }

    /** Makes [name] active if such a profile exists. */
    fun setActive(book: ProfileBook, name: String): ProfileBook =
        if (book.profiles.any { it.name == name }) book.copy(activeName = name) else book

    /** "New profile", "New profile 2", ... — the first name not used (case-insensitively) by [profiles]. */
    fun uniqueName(base: String, profiles: List<ConnectionProfile>): String {
        fun taken(candidate: String) = profiles.any { it.name.equals(candidate, ignoreCase = true) }
        if (!taken(base)) return base
        var number = 2
        while (taken("$base $number")) number++
        return "$base $number"
    }
}

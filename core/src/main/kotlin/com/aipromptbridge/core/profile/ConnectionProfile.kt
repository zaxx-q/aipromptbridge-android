package com.aipromptbridge.core.profile

import com.aipromptbridge.core.provider.ProviderDefinition
import kotlinx.serialization.Serializable

/**
 * A complete, self-contained way to talk to a model (port of the original connection profiles).
 * Sessions and actions refer to profiles BY NAME; the profile holds provider + model + tuning.
 */
@Serializable
data class ConnectionProfile(
    val name: String,
    /** ProviderDefinition id, e.g. "google", "openai". */
    val provider: String,
    val model: String,
    /** Empty = use the provider's default base URL. */
    val baseUrl: String = "",
    /** Empty = use the provider's default key pool. */
    val apiKeyPool: String = "",
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
    val requestTimeoutSeconds: Int = 60,
    // Reserved for future work (see README TODOs): streaming responses and thinking budgets.
    val streaming: Boolean = false,
    val thinking: Boolean = false,
) {
    companion object {
        const val DEFAULT_NAME = "Default"

        /** Hard-coded last resort used when nothing is configured. */
        val FALLBACK = ConnectionProfile(name = DEFAULT_NAME, provider = "google", model = "gemini-3.5-flash")

        fun forProvider(definition: ProviderDefinition, name: String = DEFAULT_NAME) =
            ConnectionProfile(name = name, provider = definition.id, model = definition.defaultModel)
    }
}

/** On-disk shape of profiles.json. */
@Serializable
data class ProfilesFile(
    val active: String? = null,
    val profiles: List<ConnectionProfile> = emptyList(),
)

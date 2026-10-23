package com.aipromptbridge.core.provider

import com.aipromptbridge.core.http.HttpTransport
import com.aipromptbridge.core.keys.KeyPool

/** Which wire format / provider class handles a definition. */
enum class ProviderKind { GEMINI_NATIVE, OPENAI_COMPATIBLE, ANTHROPIC }

/** Static description of a provider. Adding a provider = adding ONE entry to [ProviderRegistry.all]. */
data class ProviderDefinition(
    val id: String,
    val displayName: String,
    val kind: ProviderKind,
    val defaultBaseUrl: String,
    val defaultModel: String,
    /** Name of the key pool in the KeyRepository. Defaults to the provider id. */
    val keyPool: String = id,
    /** True when the user must type a base URL (custom OpenAI-compatible servers). */
    val requiresBaseUrl: Boolean = false,
)

/** Registry-driven provider construction (port of the original `providers/registry.py`). */
object ProviderRegistry {
    // Default models come from the original app's FALLBACK_MODELS; users can change the model in their profile.
    val all: List<ProviderDefinition> = listOf(
        ProviderDefinition("google", "Google Gemini", ProviderKind.GEMINI_NATIVE,
            "https://generativelanguage.googleapis.com/v1beta", "gemini-3.5-flash"),
        ProviderDefinition("openai", "OpenAI", ProviderKind.OPENAI_COMPATIBLE,
            "https://api.openai.com/v1", "gpt-5-mini"),
        ProviderDefinition("anthropic", "Anthropic", ProviderKind.ANTHROPIC,
            "https://api.anthropic.com/v1", "claude-haiku-4-5"),
        ProviderDefinition("openrouter", "OpenRouter", ProviderKind.OPENAI_COMPATIBLE,
            "https://openrouter.ai/api/v1", "google/gemini-2.5-flash"),
        ProviderDefinition("xai", "xAI", ProviderKind.OPENAI_COMPATIBLE,
            "https://api.x.ai/v1", "grok-4-fast"),
        ProviderDefinition("mistral", "Mistral", ProviderKind.OPENAI_COMPATIBLE,
            "https://api.mistral.ai/v1", "mistral-small-latest"),
        ProviderDefinition("cohere", "Cohere (compat)", ProviderKind.OPENAI_COMPATIBLE,
            "https://api.cohere.ai/compatibility/v1", "command-a-03-2025"),
        ProviderDefinition("custom", "Custom (OpenAI-compatible)", ProviderKind.OPENAI_COMPATIBLE,
            "", "", requiresBaseUrl = true),
    )

    fun find(id: String): ProviderDefinition? = all.firstOrNull { it.id == id }

    fun create(
        definition: ProviderDefinition,
        baseUrlOverride: String?,
        keyPool: KeyPool,
        transport: HttpTransport,
        retryPolicy: RetryPolicy = RetryPolicy(),
    ): AiProvider {
        val baseUrl = baseUrlOverride?.takeIf { it.isNotBlank() } ?: definition.defaultBaseUrl
        return when (definition.kind) {
            ProviderKind.GEMINI_NATIVE -> GeminiProvider(baseUrl, keyPool, transport, retryPolicy)
            ProviderKind.OPENAI_COMPATIBLE -> OpenAiCompatibleProvider(baseUrl, keyPool, transport, retryPolicy)
            ProviderKind.ANTHROPIC -> AnthropicProvider(baseUrl, keyPool, transport, retryPolicy)
        }
    }
}

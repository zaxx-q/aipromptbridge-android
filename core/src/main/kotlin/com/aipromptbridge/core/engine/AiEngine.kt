package com.aipromptbridge.core.engine

import com.aipromptbridge.core.http.HttpTransport
import com.aipromptbridge.core.keys.KeyPoolRegistry
import com.aipromptbridge.core.model.ChatMessage
import com.aipromptbridge.core.profile.ConnectionProfile
import com.aipromptbridge.core.profile.ProfileRepository
import com.aipromptbridge.core.profile.ProfileResolver
import com.aipromptbridge.core.prompt.PromptCatalog
import com.aipromptbridge.core.prompt.PromptComposer
import com.aipromptbridge.core.prompt.PromptRequest
import com.aipromptbridge.core.prompt.ResultDestination
import com.aipromptbridge.core.provider.FailureKind
import com.aipromptbridge.core.provider.GenerateRequest
import com.aipromptbridge.core.provider.GenerateResult
import com.aipromptbridge.core.provider.GenerationParams
import com.aipromptbridge.core.provider.ProviderRegistry
import com.aipromptbridge.core.provider.RetryPolicy
import com.aipromptbridge.core.tool.ToolId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class EngineOutcome(
    val result: GenerateResult,
    val destination: ResultDestination,
    val profile: ConnectionProfile,
    val sessionOrigin: String,
)

/**
 * The single facade features talk to (replaces the original `request_pipeline.py`):
 *   action + input  ->  compose prompt  ->  resolve profile  ->  build provider  ->  generate.
 *
 * Features (TextEdit, Snip, future tools) never touch providers, keys or HTTP directly.
 * Cancelling the calling coroutine cancels the in-flight request.
 */
class AiEngine(
    private val profiles: ProfileRepository,
    private val keyPools: KeyPoolRegistry,
    private val prompts: PromptCatalog,
    private val transport: HttpTransport,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
) {
    /** Blocking (reads a file). Call from a background dispatcher. */
    fun resolveProfile(sessionProfile: String? = null, actionProfile: String? = null): ConnectionProfile =
        ProfileResolver.resolve(profiles, sessionProfile, actionProfile)

    suspend fun runAction(tool: ToolId, request: PromptRequest, sessionProfile: String? = null): EngineOutcome {
        // Prompt + profile loading touch the disk, so do it on the IO dispatcher.
        val (composed, profile) = withContext(Dispatchers.IO) {
            val config = prompts.config()
            val composed = PromptComposer.compose(tool, config.tool(tool), config.modifiers, request)
            composed to ProfileResolver.resolve(profiles, sessionProfile, request.action.connectionProfile)
        }
        val result = generate(profile, composed.messages)
        return EngineOutcome(result, composed.destination, profile, composed.sessionOrigin)
    }

    /** Plain multi-turn chat (follow-ups). `messages` should already include any system message. */
    suspend fun chat(messages: List<ChatMessage>, sessionProfile: String? = null): GenerateResult {
        val profile = withContext(Dispatchers.IO) { ProfileResolver.resolve(profiles, sessionProfile, null) }
        return generate(profile, messages)
    }

    private suspend fun generate(profile: ConnectionProfile, messages: List<ChatMessage>): GenerateResult {
        val definition = ProviderRegistry.find(profile.provider)
            ?: return GenerateResult.Failure(
                "Unknown provider '${profile.provider}' in profile '${profile.name}'.",
                FailureKind.REQUEST,
            )
        val pool = withContext(Dispatchers.IO) { keyPools.poolFor(profile.apiKeyPool.ifBlank { definition.keyPool }) }
        val provider = ProviderRegistry.create(definition, profile.baseUrl, pool, transport, retryPolicy)
        val request = GenerateRequest(
            model = profile.model,
            messages = messages,
            params = GenerationParams(profile.temperature, profile.maxOutputTokens, profile.requestTimeoutSeconds),
        )
        return provider.generate(request)
    }
}

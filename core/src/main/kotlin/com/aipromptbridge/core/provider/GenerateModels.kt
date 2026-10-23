package com.aipromptbridge.core.provider

import com.aipromptbridge.core.model.ChatMessage

data class GenerationParams(
    val temperature: Double? = null,
    val maxOutputTokens: Int? = null,
    val timeoutSeconds: Int = 60,
)

/** Provider-independent request. System prompts are simply messages with Role.SYSTEM; each provider maps them. */
data class GenerateRequest(
    val model: String,
    val messages: List<ChatMessage>,
    val params: GenerationParams = GenerationParams(),
)

data class UsageData(
    val promptTokens: Int = 0,
    val completionTokens: Int = 0,
    val totalTokens: Int = promptTokens + completionTokens,
)

/** What a provider subclass extracts from a successful HTTP body. */
data class ParsedResponse(val content: String, val usage: UsageData? = null)

enum class FailureKind { NO_API_KEY, AUTH_OR_QUOTA, SERVER, NETWORK, REQUEST, EMPTY, PARSE }

sealed interface GenerateResult {
    data class Success(
        val content: String,
        val usage: UsageData? = null,
        val retryCount: Int = 0,
    ) : GenerateResult

    data class Failure(
        val message: String,
        val kind: FailureKind,
        val statusCode: Int? = null,
        val retryCount: Int = 0,
    ) : GenerateResult
}

/** Thrown by [AiProvider.parseResponse] for blocked prompts or malformed bodies. Becomes a non-retryable failure. */
class ProviderParseException(message: String) : Exception(message)

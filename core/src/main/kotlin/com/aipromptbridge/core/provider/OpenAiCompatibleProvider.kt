package com.aipromptbridge.core.provider

import com.aipromptbridge.core.http.HttpRequest
import com.aipromptbridge.core.http.HttpTransport
import com.aipromptbridge.core.keys.KeyPool
import com.aipromptbridge.core.model.ChatMessage
import com.aipromptbridge.core.model.ContentPart
import com.aipromptbridge.core.model.Role
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * OpenAI "chat completions" wire format: POST {base}/chat/completions with `Authorization: Bearer`.
 * Used for OpenAI, OpenRouter, xAI, Mistral, Cohere (compat endpoint) and any custom OpenAI-compatible server.
 *
 * TODO(openai-params): newer OpenAI models reject `max_tokens` and want `max_completion_tokens`
 *  (and may reject a custom temperature). Params are only sent when set on the ConnectionProfile, so the
 *  defaults are safe; add a per-provider switch if you need token limits on OpenAI proper.
 */
class OpenAiCompatibleProvider(
    baseUrl: String,
    keyPool: KeyPool,
    transport: HttpTransport,
    retryPolicy: RetryPolicy = RetryPolicy(),
) : AiProvider(baseUrl, keyPool, transport, retryPolicy) {

    override fun buildHttpRequest(request: GenerateRequest, apiKey: String): HttpRequest {
        val url = "${baseUrl.trimEnd('/')}/chat/completions"
        val body = buildJsonObject {
            put("model", request.model)
            put("messages", buildJsonArray {
                for (message in request.messages) {
                    add(buildJsonObject {
                        put("role", roleName(message.role))
                        put("content", contentFor(message))
                    })
                }
            })
            request.params.temperature?.let { put("temperature", it) }
            request.params.maxOutputTokens?.let { put("max_tokens", it) }
        }
        return HttpRequest(
            url = url,
            headers = jsonHeaders("Authorization" to "Bearer $apiKey"),
            body = body.toString(),
        )
    }

    override fun parseResponse(body: String): ParsedResponse {
        val root = parseJsonObject(body)
        val message = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject?.get("message")?.jsonObject
        val rawContent = when (val content = message?.get("content")) {
            null, is JsonNull -> ""
            is JsonPrimitive -> content.content
            is JsonArray -> content.joinToString("") { it.jsonObject["text"]?.jsonPrimitive?.contentOrNull.orEmpty() }
            else -> ""
        }
        val usage = root["usage"]?.jsonObject?.let {
            UsageData(it.intOrZero("prompt_tokens"), it.intOrZero("completion_tokens"), it.intOrZero("total_tokens"))
        }
        return ParsedResponse(InlineThinking.strip(rawContent), usage)
    }

    private fun roleName(role: Role): String = when (role) {
        Role.SYSTEM -> "system"
        Role.USER -> "user"
        Role.ASSISTANT -> "assistant"
    }

    /** Plain string for text-only messages; typed content array when an image is present. */
    private fun contentFor(message: ChatMessage): JsonElement {
        if (!message.hasImage) return JsonPrimitive(message.text)
        return buildJsonArray {
            for (part in message.parts) {
                when (part) {
                    is ContentPart.Text -> add(buildJsonObject {
                        put("type", "text")
                        put("text", part.text)
                    })

                    is ContentPart.Image -> add(buildJsonObject {
                        put("type", "image_url")
                        put("image_url", buildJsonObject {
                            put("url", "data:${part.mimeType};base64,${part.base64}")
                        })
                    })
                }
            }
        }
    }
}

package com.aipromptbridge.core.provider

import com.aipromptbridge.core.http.HttpRequest
import com.aipromptbridge.core.http.HttpTransport
import com.aipromptbridge.core.keys.KeyPool
import com.aipromptbridge.core.model.ContentPart
import com.aipromptbridge.core.model.Role
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Anthropic Messages API: POST {base}/messages with `x-api-key` + `anthropic-version` headers.
 * `max_tokens` is REQUIRED by this API, so a default is applied when the profile has none.
 * TODO(verify): this provider has not been checked against current Anthropic docs in this scaffold
 *  (Gemini was). Before relying on it, confirm field names and the `anthropic-version` value.
 */
class AnthropicProvider(
    baseUrl: String,
    keyPool: KeyPool,
    transport: HttpTransport,
    retryPolicy: RetryPolicy = RetryPolicy(),
) : AiProvider(baseUrl, keyPool, transport, retryPolicy) {

    override fun buildHttpRequest(request: GenerateRequest, apiKey: String): HttpRequest {
        val url = "${baseUrl.trimEnd('/')}/messages"
        val systemText = request.messages
            .filter { it.role == Role.SYSTEM }
            .joinToString("\n\n") { it.text }
            .trim()
        val conversation = request.messages.filter { it.role != Role.SYSTEM }

        val body = buildJsonObject {
            put("model", request.model)
            put("max_tokens", request.params.maxOutputTokens ?: DEFAULT_MAX_TOKENS)
            if (systemText.isNotEmpty()) put("system", systemText)
            request.params.temperature?.let { put("temperature", it) }
            put("messages", buildJsonArray {
                for (message in conversation) {
                    add(buildJsonObject {
                        put("role", if (message.role == Role.ASSISTANT) "assistant" else "user")
                        put("content", buildJsonArray {
                            for (part in message.parts) {
                                when (part) {
                                    is ContentPart.Text -> add(buildJsonObject {
                                        put("type", "text")
                                        put("text", part.text)
                                    })

                                    is ContentPart.Image -> add(buildJsonObject {
                                        put("type", "image")
                                        put("source", buildJsonObject {
                                            put("type", "base64")
                                            put("media_type", part.mimeType)
                                            put("data", part.base64)
                                        })
                                    })
                                }
                            }
                        })
                    })
                }
            })
        }

        return HttpRequest(
            url = url,
            headers = jsonHeaders("x-api-key" to apiKey, "anthropic-version" to ANTHROPIC_VERSION),
            body = body.toString(),
        )
    }

    override fun parseResponse(body: String): ParsedResponse {
        val root = parseJsonObject(body)
        val text = root["content"]?.jsonArray.orEmpty().mapNotNull { element ->
            val block = element.jsonObject
            if (block["type"]?.jsonPrimitive?.contentOrNull == "text") block["text"]?.jsonPrimitive?.contentOrNull else null
        }.joinToString("")
        val usage = root["usage"]?.jsonObject?.let {
            val input = it.intOrZero("input_tokens")
            val output = it.intOrZero("output_tokens")
            UsageData(input, output, input + output)
        }
        return ParsedResponse(text, usage)
    }

    private companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
        const val DEFAULT_MAX_TOKENS = 4096
    }
}

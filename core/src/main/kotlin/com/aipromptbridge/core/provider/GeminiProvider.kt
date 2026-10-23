package com.aipromptbridge.core.provider

import com.aipromptbridge.core.http.HttpRequest
import com.aipromptbridge.core.http.HttpTransport
import com.aipromptbridge.core.keys.KeyPool
import com.aipromptbridge.core.model.ContentPart
import com.aipromptbridge.core.model.Role
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.add
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/**
 * Google Gemini native REST API: POST {base}/models/{model}:generateContent with the `x-goog-api-key` header.
 * Verified against the current Gemini API docs (contents[].parts[], inlineData{mimeType,data}, systemInstruction).
 *
 * Mapping decisions (carried over from the original gemini_native.py):
 *  - Role.SYSTEM messages -> top-level `systemInstruction`; ASSISTANT -> role "model".
 *  - Legacy Gemma 1/2/3 models do not support systemInstruction: the system text is prepended to the first user turn.
 *  - Response parts flagged `thought: true` are internal reasoning and are skipped.
 */
class GeminiProvider(
    baseUrl: String,
    keyPool: KeyPool,
    transport: HttpTransport,
    retryPolicy: RetryPolicy = RetryPolicy(),
) : AiProvider(baseUrl, keyPool, transport, retryPolicy) {

    override fun buildHttpRequest(request: GenerateRequest, apiKey: String): HttpRequest {
        val modelId = request.model.removePrefix("models/")
        val url = "${baseUrl.trimEnd('/')}/models/$modelId:generateContent"

        val systemText = request.messages
            .filter { it.role == Role.SYSTEM }
            .joinToString("\n\n") { it.text }
            .trim()
        val conversation = request.messages.filter { it.role != Role.SYSTEM }
        val systemGoesInUserTurn = isLegacyGemma(modelId) && systemText.isNotEmpty()
        var pendingPrefix = if (systemGoesInUserTurn) "$systemText\n\n" else ""

        val body = buildJsonObject {
            put("contents", buildJsonArray {
                for (message in conversation) {
                    add(buildJsonObject {
                        put("role", if (message.role == Role.ASSISTANT) "model" else "user")
                        put("parts", buildJsonArray {
                            for (part in message.parts) {
                                when (part) {
                                    is ContentPart.Text -> {
                                        val text = if (pendingPrefix.isNotEmpty() && message.role == Role.USER) {
                                            val prefixed = pendingPrefix + part.text
                                            pendingPrefix = ""
                                            prefixed
                                        } else {
                                            part.text
                                        }
                                        add(textPart(text))
                                    }

                                    is ContentPart.Image -> add(
                                        buildJsonObject {
                                            put("inlineData", buildJsonObject {
                                                put("mimeType", part.mimeType)
                                                put("data", part.base64)
                                            })
                                        },
                                    )
                                }
                            }
                        })
                    })
                }
            })

            if (systemText.isNotEmpty() && !systemGoesInUserTurn) {
                put("systemInstruction", buildJsonObject {
                    put("parts", buildJsonArray { add(textPart(systemText)) })
                })
            }

            val generationConfig = buildJsonObject {
                request.params.temperature?.let { put("temperature", it) }
                request.params.maxOutputTokens?.let { put("maxOutputTokens", it) }
                // TODO(thinking): map ConnectionProfile.thinking to generationConfig.thinkingConfig once the
                //  exact field names for the chosen model family are confirmed in the Gemini docs.
            }
            if (generationConfig.isNotEmpty()) put("generationConfig", generationConfig)
        }

        return HttpRequest(
            url = url,
            headers = jsonHeaders("x-goog-api-key" to apiKey),
            body = body.toString(),
        )
    }

    override fun parseResponse(body: String): ParsedResponse {
        val root = parseJsonObject(body)

        val blockReason = root["promptFeedback"]?.jsonObject?.get("blockReason")?.jsonPrimitive?.contentOrNull
        if (blockReason != null) throw ProviderParseException("Prompt was blocked by Gemini ($blockReason)")

        val parts = root["candidates"]?.jsonArray?.firstOrNull()
            ?.jsonObject?.get("content")?.jsonObject?.get("parts")?.jsonArray

        val text = parts.orEmpty().mapNotNull { element ->
            val part = element.jsonObject
            val isThought = part["thought"]?.jsonPrimitive?.booleanOrNull == true
            if (isThought) null else part["text"]?.jsonPrimitive?.contentOrNull
        }.joinToString("")

        val usage = root["usageMetadata"]?.jsonObject?.let {
            UsageData(it.intOrZero("promptTokenCount"), it.intOrZero("candidatesTokenCount"), it.intOrZero("totalTokenCount"))
        }
        return ParsedResponse(text, usage)
    }

    private fun textPart(text: String): JsonObject = buildJsonObject { put("text", text) }

    private fun isLegacyGemma(modelId: String): Boolean = LEGACY_GEMMA.containsMatchIn(modelId.lowercase())

    private companion object {
        val LEGACY_GEMMA = Regex("^gemma-?[123]")
    }
}

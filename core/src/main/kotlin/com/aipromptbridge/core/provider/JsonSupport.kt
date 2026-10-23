package com.aipromptbridge.core.provider

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

internal fun parseJsonObject(body: String): JsonObject =
    try {
        Json.parseToJsonElement(body).jsonObject
    } catch (e: Exception) {
        throw ProviderParseException("Provider returned a response that is not a JSON object")
    }

internal fun JsonObject.intOrZero(key: String): Int = this[key]?.jsonPrimitive?.intOrNull ?: 0

package com.aipromptbridge.core.provider

import com.aipromptbridge.core.http.HttpRequest
import com.aipromptbridge.core.http.HttpResponse
import com.aipromptbridge.core.http.HttpTransport
import com.aipromptbridge.core.http.NetworkException
import com.aipromptbridge.core.keys.KeyEntry
import com.aipromptbridge.core.keys.KeyPool
import com.aipromptbridge.core.model.ChatMessage
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * Scripted fake transport: each call to execute() consumes the next scripted item.
 * Items are HttpResponse (returned) or NetworkException (thrown). All requests are recorded in [requests].
 */
class FakeTransport(vararg script: Any) : HttpTransport {
    private val queue = ArrayDeque(script.toList())
    val requests = mutableListOf<HttpRequest>()

    override suspend fun execute(request: HttpRequest): HttpResponse {
        requests += request
        val next = queue.removeFirstOrNull() ?: error("FakeTransport script exhausted")
        if (next is NetworkException) throw next
        return next as HttpResponse
    }
}

fun ok(body: String) = HttpResponse(200, body)
fun httpStatus(code: Int, body: String = """{"error":{"message":"boom"}}""") = HttpResponse(code, body)
fun keyPoolOf(vararg keys: String) = KeyPool(keys.mapIndexed { i, k -> KeyEntry("Key ${i + 1}", k) })

fun simpleRequest(text: String = "hi", system: String? = null, model: String = "test-model", params: GenerationParams = GenerationParams()) =
    GenerateRequest(
        model,
        buildList {
            if (system != null) add(ChatMessage.system(system))
            add(ChatMessage.user(text))
        },
        params,
    )

fun parseJson(text: String): JsonObject = Json.parseToJsonElement(text).jsonObject
fun JsonObject.obj(key: String): JsonObject = getValue(key).jsonObject
fun JsonObject.arr(key: String): JsonArray = getValue(key).jsonArray
fun JsonObject.str(key: String): String = getValue(key).jsonPrimitive.content

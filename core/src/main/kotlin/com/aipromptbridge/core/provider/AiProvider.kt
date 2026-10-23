package com.aipromptbridge.core.provider

import com.aipromptbridge.core.http.HttpRequest
import com.aipromptbridge.core.http.HttpTransport
import com.aipromptbridge.core.http.NetworkException
import com.aipromptbridge.core.keys.KeyPool
import kotlinx.coroutines.delay
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * TEMPLATE METHOD pattern (ported from the original `providers/base.py`).
 *
 * The base class owns ALL orchestration: key selection, rotation, retry classification and delays.
 * Subclasses only describe how to BUILD the HTTP request and how to PARSE the response body:
 *   - [buildHttpRequest]
 *   - [parseResponse]
 * Adding a new provider never requires touching retry logic.
 *
 * Cancellation: [generate] is a suspend function; cancelling the calling coroutine cancels the request
 * (CancellationException is never swallowed here).
 *
 * Retry table (deliberately close to the original app):
 *   HTTP 429                  -> rotate key immediately (single key: wait [RetryPolicy.singleKeyRateLimitDelayMs])
 *   HTTP 401 / 402 / 403      -> rotate key immediately; with a single key there is nothing to rotate to -> fail fast
 *   HTTP 5xx                  -> wait [RetryPolicy.serverErrorDelayMs], same key
 *   empty / blank content     -> rotate key, wait [RetryPolicy.emptyResponseDelayMs]
 *   network error / timeout   -> wait [RetryPolicy.networkErrorDelayMs], same key
 *   other 4xx, parse errors   -> fail immediately
 */
abstract class AiProvider(
    protected val baseUrl: String,
    private val keyPool: KeyPool,
    private val transport: HttpTransport,
    private val retryPolicy: RetryPolicy = RetryPolicy(),
) {
    /** Build the provider-specific HTTP request for [request] using [apiKey]. */
    protected abstract fun buildHttpRequest(request: GenerateRequest, apiKey: String): HttpRequest

    /** Extract text (+ optional usage) from a 2xx body. Throw [ProviderParseException] for blocked/invalid bodies. */
    protected abstract fun parseResponse(body: String): ParsedResponse

    /** Best-effort extraction of a human-readable error from a non-2xx body. Override for odd providers. */
    protected open fun extractErrorMessage(body: String): String? =
        runCatching {
            Json.parseToJsonElement(body).jsonObject["error"]?.jsonObject?.get("message")?.jsonPrimitive?.contentOrNull
        }.getOrNull()

    protected fun jsonHeaders(vararg extra: Pair<String, String>): Map<String, String> =
        mapOf("Content-Type" to "application/json") + extra

    suspend fun generate(request: GenerateRequest): GenerateResult {
        var lastFailure = GenerateResult.Failure("Request failed", FailureKind.SERVER)
        for (attempt in 0 until retryPolicy.maxAttempts) {
            val isLastAttempt = attempt == retryPolicy.maxAttempts - 1
            val apiKey = keyPool.currentKey()
                ?: return GenerateResult.Failure(NO_KEY_MESSAGE, FailureKind.NO_API_KEY, null, attempt)

            when (val outcome = attemptOnce(request, apiKey)) {
                is Attempt.Done ->
                    return GenerateResult.Success(outcome.parsed.content, outcome.parsed.usage, attempt)

                is Attempt.Fatal ->
                    return GenerateResult.Failure(outcome.message, outcome.kind, outcome.status, attempt)

                is Attempt.RotateKey -> {
                    lastFailure = GenerateResult.Failure(outcome.message, outcome.kind, outcome.status, attempt)
                    keyPool.rotate()
                    if (outcome.delayMs > 0 && !isLastAttempt) delay(outcome.delayMs)
                }

                is Attempt.RetrySameKey -> {
                    lastFailure = GenerateResult.Failure(outcome.message, outcome.kind, outcome.status, attempt)
                    if (outcome.delayMs > 0 && !isLastAttempt) delay(outcome.delayMs)
                }
            }
        }
        return lastFailure.copy(retryCount = retryPolicy.maxAttempts)
    }

    private suspend fun attemptOnce(request: GenerateRequest, apiKey: String): Attempt {
        val http = buildHttpRequest(request, apiKey).copy(timeoutMs = request.params.timeoutSeconds * 1000)
        val response = try {
            transport.execute(http)
        } catch (e: NetworkException) {
            // NOTE: CancellationException is intentionally not caught, so cancelling aborts the whole call.
            return Attempt.RetrySameKey("Network error: ${e.message}", null, FailureKind.NETWORK, retryPolicy.networkErrorDelayMs)
        }

        val status = response.statusCode
        return when {
            response.isSuccessful -> classifySuccess(response.body)

            status == 429 -> Attempt.RotateKey(
                describeError(status, response.body), status, FailureKind.AUTH_OR_QUOTA,
                if (keyPool.hasMultipleKeys) 0L else retryPolicy.singleKeyRateLimitDelayMs,
            )

            status == 401 || status == 402 || status == 403 ->
                if (keyPool.hasMultipleKeys) {
                    Attempt.RotateKey(describeError(status, response.body), status, FailureKind.AUTH_OR_QUOTA, 0L)
                } else {
                    Attempt.Fatal(describeError(status, response.body), status, FailureKind.AUTH_OR_QUOTA)
                }

            status >= 500 -> Attempt.RetrySameKey(
                describeError(status, response.body), status, FailureKind.SERVER, retryPolicy.serverErrorDelayMs,
            )

            else -> Attempt.Fatal(describeError(status, response.body), status, FailureKind.REQUEST)
        }
    }

    private fun classifySuccess(body: String): Attempt {
        val parsed = try {
            parseResponse(body)
        } catch (e: ProviderParseException) {
            return Attempt.Fatal(e.message ?: "Blocked or unreadable response", null, FailureKind.PARSE)
        } catch (e: Exception) {
            return Attempt.Fatal("Could not parse provider response: ${e.message}", null, FailureKind.PARSE)
        }
        return if (parsed.content.isBlank()) {
            Attempt.RotateKey("The model returned an empty response", 200, FailureKind.EMPTY, retryPolicy.emptyResponseDelayMs)
        } else {
            Attempt.Done(parsed)
        }
    }

    private fun describeError(status: Int, body: String): String =
        "HTTP $status: ${extractErrorMessage(body) ?: body.take(300)}"

    companion object {
        const val NO_KEY_MESSAGE = "No API key configured. Add one in Settings."
    }
}

/** Internal result of one HTTP attempt; drives the retry loop in [AiProvider.generate]. */
internal sealed interface Attempt {
    data class Done(val parsed: ParsedResponse) : Attempt
    data class RotateKey(val message: String, val status: Int?, val kind: FailureKind, val delayMs: Long) : Attempt
    data class RetrySameKey(val message: String, val status: Int?, val kind: FailureKind, val delayMs: Long) : Attempt
    data class Fatal(val message: String, val status: Int?, val kind: FailureKind) : Attempt
}

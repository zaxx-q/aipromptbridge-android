package com.aipromptbridge.core.http

/** A transport-agnostic HTTP request. The app supplies a real implementation; tests supply a fake. */
data class HttpRequest(
    val url: String,
    val headers: Map<String, String> = emptyMap(),
    val body: String? = null,
    val method: String = "POST",
    val timeoutMs: Int = 60_000,
)

data class HttpResponse(val statusCode: Int, val body: String) {
    val isSuccessful: Boolean get() = statusCode in 200..299
}

/** Thrown for connectivity failures and timeouts. Non-2xx statuses are NOT exceptions; they come back as HttpResponse. */
class NetworkException(message: String, cause: Throwable? = null) : Exception(message, cause)

interface HttpTransport {
    /**
     * Executes the request. Must be cancellable: when the calling coroutine is cancelled the in-flight
     * connection should be aborted (this replaces the original Python app's `abort_event`).
     */
    suspend fun execute(request: HttpRequest): HttpResponse
}

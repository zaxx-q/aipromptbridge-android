package com.aipromptbridge.data

import com.aipromptbridge.core.http.HttpRequest
import com.aipromptbridge.core.http.HttpResponse
import com.aipromptbridge.core.http.HttpTransport
import com.aipromptbridge.core.http.NetworkException
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * HttpTransport on top of java.net.HttpURLConnection (no extra dependency).
 *
 * Cancellation: HttpURLConnection blocks and ignores thread interrupts, so each request runs on its own short-lived
 * thread and cancelling the coroutine calls connection.disconnect(), which unblocks that thread with an IOException.
 *
 * TODO(streaming): to support ConnectionProfile.streaming (SSE), add a second method to HttpTransport that exposes
 *  the response body as a Flow<String> and read lines incrementally here (or switch to OkHttp).
 */
class UrlConnectionTransport : HttpTransport {

    override suspend fun execute(request: HttpRequest): HttpResponse =
        suspendCancellableCoroutine { continuation ->
            val connection = try {
                URL(request.url).openConnection() as HttpURLConnection
            } catch (e: IOException) {
                continuation.resumeWithException(NetworkException("Invalid URL: ${request.url}", e))
                return@suspendCancellableCoroutine
            } catch (e: ClassCastException) {
                continuation.resumeWithException(NetworkException("Only http(s) URLs are supported: ${request.url}", e))
                return@suspendCancellableCoroutine
            }

            continuation.invokeOnCancellation { connection.disconnect() }

            val worker = Thread {
                try {
                    val response = perform(connection, request)
                    if (continuation.isActive) continuation.resume(response)
                } catch (e: NetworkException) {
                    if (continuation.isActive) continuation.resumeWithException(e)
                } catch (e: RuntimeException) {
                    if (continuation.isActive) continuation.resumeWithException(NetworkException(e.message ?: "Unexpected error", e))
                }
            }
            worker.name = "aipb-http"
            worker.isDaemon = true
            worker.start()
        }

    private fun perform(connection: HttpURLConnection, request: HttpRequest): HttpResponse {
        try {
            connection.requestMethod = request.method
            connection.connectTimeout = CONNECT_TIMEOUT_MS
            connection.readTimeout = request.timeoutMs
            for ((name, value) in request.headers) connection.setRequestProperty(name, value)

            val body = request.body
            if (body != null) {
                connection.doOutput = true
                connection.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }

            val status = connection.responseCode
            val stream = if (status in 200..299) connection.inputStream else connection.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            return HttpResponse(status, text)
        } catch (e: IOException) {
            throw NetworkException(e.message ?: "I/O error", e)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val CONNECT_TIMEOUT_MS = 15_000
    }
}

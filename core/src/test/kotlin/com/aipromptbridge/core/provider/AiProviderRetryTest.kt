package com.aipromptbridge.core.provider

import com.aipromptbridge.core.http.NetworkException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.currentTime
import kotlinx.coroutines.test.runTest

/** Exercises the template-method retry loop in AiProvider via OpenAiCompatibleProvider and a scripted transport. */
class AiProviderRetryTest {
    private val okBody = """{"choices":[{"message":{"content":"Hello"}}],"usage":{"prompt_tokens":3,"completion_tokens":2,"total_tokens":5}}"""
    private val emptyBody = """{"choices":[{"message":{"content":""}}]}"""

    @Test
    fun rotatesKeyOn429AndSucceeds() = runTest {
        val transport = FakeTransport(httpStatus(429), ok(okBody))
        val provider = OpenAiCompatibleProvider("https://x/v1", keyPoolOf("k1", "k2"), transport)

        val result = provider.generate(simpleRequest())

        assertIs<GenerateResult.Success>(result)
        assertEquals("Hello", result.content)
        assertEquals(5, result.usage?.totalTokens)
        assertEquals("Bearer k1", transport.requests[0].headers["Authorization"])
        assertEquals("Bearer k2", transport.requests[1].headers["Authorization"])
        assertEquals(0L, currentTime) // multiple keys: rotate immediately, no waiting
    }

    @Test
    fun singleKey429WaitsBeforeRetrying() = runTest {
        val transport = FakeTransport(httpStatus(429), ok(okBody))
        val provider = OpenAiCompatibleProvider("https://x/v1", keyPoolOf("only"), transport)

        val result = provider.generate(simpleRequest())

        assertIs<GenerateResult.Success>(result)
        assertEquals(2_000L, currentTime)
    }

    @Test
    fun singleKey401FailsFast() = runTest {
        val transport = FakeTransport(httpStatus(401))
        val provider = OpenAiCompatibleProvider("https://x/v1", keyPoolOf("only"), transport)

        val result = provider.generate(simpleRequest())

        assertIs<GenerateResult.Failure>(result)
        assertEquals(FailureKind.AUTH_OR_QUOTA, result.kind)
        assertEquals(401, result.statusCode)
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun serverErrorRetriesSameKeyAfterDelay() = runTest {
        val transport = FakeTransport(httpStatus(500), ok(okBody))
        val provider = OpenAiCompatibleProvider("https://x/v1", keyPoolOf("k1", "k2"), transport)

        val result = provider.generate(simpleRequest())

        assertIs<GenerateResult.Success>(result)
        assertEquals("Bearer k1", transport.requests[1].headers["Authorization"]) // same key
        assertEquals(5_000L, currentTime)
    }

    @Test
    fun networkErrorRetriesAfterShortDelay() = runTest {
        val transport = FakeTransport(NetworkException("offline"), ok(okBody))
        val provider = OpenAiCompatibleProvider("https://x/v1", keyPoolOf("k1"), transport)

        val result = provider.generate(simpleRequest())

        assertIs<GenerateResult.Success>(result)
        assertEquals(1_000L, currentTime)
    }

    @Test
    fun emptyResponseRotatesKeyAndWaits() = runTest {
        val transport = FakeTransport(ok(emptyBody), ok(okBody))
        val provider = OpenAiCompatibleProvider("https://x/v1", keyPoolOf("k1", "k2"), transport)

        val result = provider.generate(simpleRequest())

        assertIs<GenerateResult.Success>(result)
        assertEquals("Bearer k2", transport.requests[1].headers["Authorization"])
        assertEquals(2_000L, currentTime)
    }

    @Test
    fun otherClientErrorsFailImmediatelyWithProviderMessage() = runTest {
        val transport = FakeTransport(httpStatus(400))
        val provider = OpenAiCompatibleProvider("https://x/v1", keyPoolOf("k1", "k2"), transport)

        val result = provider.generate(simpleRequest())

        assertIs<GenerateResult.Failure>(result)
        assertEquals(FailureKind.REQUEST, result.kind)
        assertTrue(result.message.contains("boom"))
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun missingKeyFailsWithoutAnyRequest() = runTest {
        val transport = FakeTransport()
        val provider = OpenAiCompatibleProvider("https://x/v1", keyPoolOf(), transport)

        val result = provider.generate(simpleRequest())

        assertIs<GenerateResult.Failure>(result)
        assertEquals(FailureKind.NO_API_KEY, result.kind)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun givesUpAfterMaxAttempts() = runTest {
        val transport = FakeTransport(httpStatus(500), httpStatus(500))
        val provider = OpenAiCompatibleProvider("https://x/v1", keyPoolOf("k1"), transport, RetryPolicy(maxAttempts = 2))

        val result = provider.generate(simpleRequest())

        assertIs<GenerateResult.Failure>(result)
        assertEquals(FailureKind.SERVER, result.kind)
        assertEquals(2, transport.requests.size)
        assertEquals(5_000L, currentTime) // no pointless delay after the final attempt
    }
}

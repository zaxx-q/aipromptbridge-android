package com.aipromptbridge.core.provider

import com.aipromptbridge.core.model.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

class GeminiProviderTest {
    private val base = "https://generativelanguage.googleapis.com/v1beta"
    private val okBody =
        """{"candidates":[{"content":{"parts":[{"text":"private thoughts","thought":true},{"text":"Answer"}]}}],""" +
            """"usageMetadata":{"promptTokenCount":4,"candidatesTokenCount":1,"totalTokenCount":5}}"""

    @Test
    fun buildsRequestWithSystemInstructionRolesAndConfig() = runTest {
        val transport = FakeTransport(ok(okBody))
        val provider = GeminiProvider(base, keyPoolOf("secret"), transport)
        val request = GenerateRequest(
            model = "gemini-x",
            messages = listOf(ChatMessage.system("SYS"), ChatMessage.user("Q1"), ChatMessage.assistant("A1"), ChatMessage.user("Q2")),
            params = GenerationParams(temperature = 0.5, maxOutputTokens = 100),
        )

        val result = provider.generate(request)

        assertIs<GenerateResult.Success>(result)
        val http = transport.requests.single()
        assertEquals("$base/models/gemini-x:generateContent", http.url)
        assertEquals("secret", http.headers["x-goog-api-key"])

        val body = parseJson(http.body!!)
        assertEquals("SYS", body.obj("systemInstruction").arr("parts")[0].let { (it as kotlinx.serialization.json.JsonObject).str("text") })
        val contents = body.arr("contents")
        assertEquals(3, contents.size) // system is NOT part of contents
        assertEquals(listOf("user", "model", "user"), contents.map { (it as kotlinx.serialization.json.JsonObject).str("role") })
        assertEquals(100, body.obj("generationConfig").getValue("maxOutputTokens").toString().toInt())
    }

    @Test
    fun imagesBecomeInlineData() = runTest {
        val transport = FakeTransport(ok(okBody))
        val provider = GeminiProvider(base, keyPoolOf("k"), transport)
        val message = ChatMessage.userWithImage("What is this?", "image/png", "QUJD")

        provider.generate(GenerateRequest("gemini-x", listOf(message)))

        val parts = parseJson(transport.requests.single().body!!).arr("contents")[0]
            .let { (it as kotlinx.serialization.json.JsonObject).arr("parts") }
        val inline = (parts[0] as kotlinx.serialization.json.JsonObject).obj("inlineData")
        assertEquals("image/png", inline.str("mimeType"))
        assertEquals("QUJD", inline.str("data"))
        assertEquals("What is this?", (parts[1] as kotlinx.serialization.json.JsonObject).str("text"))
    }

    @Test
    fun parsesAnswerSkippingThoughtParts() = runTest {
        val provider = GeminiProvider(base, keyPoolOf("k"), FakeTransport(ok(okBody)))

        val result = provider.generate(simpleRequest())

        assertIs<GenerateResult.Success>(result)
        assertEquals("Answer", result.content)
        assertEquals(5, result.usage?.totalTokens)
    }

    @Test
    fun blockedPromptIsANonRetryableFailure() = runTest {
        val transport = FakeTransport(ok("""{"promptFeedback":{"blockReason":"SAFETY"}}"""))
        val provider = GeminiProvider(base, keyPoolOf("k"), transport)

        val result = provider.generate(simpleRequest())

        assertIs<GenerateResult.Failure>(result)
        assertEquals(FailureKind.PARSE, result.kind)
        assertTrue(result.message.contains("SAFETY"))
        assertEquals(1, transport.requests.size)
    }

    @Test
    fun legacyGemmaGetsSystemTextInsideFirstUserTurn() = runTest {
        val transport = FakeTransport(ok(okBody))
        val provider = GeminiProvider(base, keyPoolOf("k"), transport)

        provider.generate(simpleRequest(text = "hello", system = "SYS", model = "gemma-3-27b-it"))

        val body = parseJson(transport.requests.single().body!!)
        assertFalse(body.containsKey("systemInstruction"))
        val firstText = body.arr("contents")[0].let { (it as kotlinx.serialization.json.JsonObject).arr("parts") }[0]
            .let { (it as kotlinx.serialization.json.JsonObject).str("text") }
        assertTrue(firstText.startsWith("SYS"))
        assertTrue(firstText.endsWith("hello"))
    }
}

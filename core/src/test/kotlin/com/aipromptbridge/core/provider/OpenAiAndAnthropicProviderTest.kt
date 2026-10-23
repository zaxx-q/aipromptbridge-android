package com.aipromptbridge.core.provider

import com.aipromptbridge.core.model.ChatMessage
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonPrimitive

class OpenAiCompatibleProviderTest {
    @Test
    fun buildsChatCompletionsRequest() = runTest {
        val body = """{"choices":[{"message":{"content":"ok"}}]}"""
        val transport = FakeTransport(ok(body))
        val provider = OpenAiCompatibleProvider("https://api.example.com/v1/", keyPoolOf("sk-1"), transport)

        provider.generate(simpleRequest("hi", system = "SYS", params = GenerationParams(temperature = 0.2, maxOutputTokens = 50)))

        val http = transport.requests.single()
        assertEquals("https://api.example.com/v1/chat/completions", http.url) // trailing slash handled
        assertEquals("Bearer sk-1", http.headers["Authorization"])
        val json = parseJson(http.body!!)
        assertEquals("test-model", json.str("model"))
        val messages = json.arr("messages").map { it as JsonObject }
        assertEquals(listOf("system", "user"), messages.map { it.str("role") })
        assertEquals("SYS", messages[0].str("content"))
        assertEquals(50, json.getValue("max_tokens").jsonPrimitive.content.toInt())
    }

    @Test
    fun imagesBecomeImageUrlDataUris() = runTest {
        val transport = FakeTransport(ok("""{"choices":[{"message":{"content":"ok"}}]}"""))
        val provider = OpenAiCompatibleProvider("https://x/v1", keyPoolOf("k"), transport)

        provider.generate(GenerateRequest("m", listOf(ChatMessage.userWithImage("look", "image/png", "QUJD"))))

        val content = (parseJson(transport.requests.single().body!!).arr("messages")[0] as JsonObject).arr("content")
        val image = content[0] as JsonObject
        assertEquals("image_url", image.str("type"))
        assertEquals("data:image/png;base64,QUJD", image.obj("image_url").str("url"))
    }

    @Test
    fun stripsInlineThinkingFromContent() = runTest {
        val body = """{"choices":[{"message":{"content":"<think>hmm</think>Final answer"}}]}"""
        val provider = OpenAiCompatibleProvider("https://x/v1", keyPoolOf("k"), FakeTransport(ok(body)))

        val result = provider.generate(simpleRequest())

        assertIs<GenerateResult.Success>(result)
        assertEquals("Final answer", result.content)
    }
}

class AnthropicProviderTest {
    @Test
    fun buildsMessagesRequestAndParsesTextBlocks() = runTest {
        val body = """{"content":[{"type":"text","text":"Hi "},{"type":"text","text":"there"}],"usage":{"input_tokens":7,"output_tokens":3}}"""
        val transport = FakeTransport(ok(body))
        val provider = AnthropicProvider("https://api.anthropic.com/v1", keyPoolOf("ak"), transport)

        val result = provider.generate(simpleRequest("hello", system = "SYS"))

        assertIs<GenerateResult.Success>(result)
        assertEquals("Hi there", result.content)
        assertEquals(10, result.usage?.totalTokens)

        val http = transport.requests.single()
        assertEquals("https://api.anthropic.com/v1/messages", http.url)
        assertEquals("ak", http.headers["x-api-key"])
        assertEquals("2023-06-01", http.headers["anthropic-version"])
        val json = parseJson(http.body!!)
        assertEquals("SYS", json.str("system"))
        assertEquals(4096, json.getValue("max_tokens").jsonPrimitive.content.toInt()) // required field gets a default
    }
}

package com.aipromptbridge.core.engine

import com.aipromptbridge.core.keys.InMemoryKeyRepository
import com.aipromptbridge.core.keys.KeyEntry
import com.aipromptbridge.core.keys.KeyPoolRegistry
import com.aipromptbridge.core.profile.ConnectionProfile
import com.aipromptbridge.core.profile.InMemoryProfileRepository
import com.aipromptbridge.core.prompt.DefaultPrompts
import com.aipromptbridge.core.prompt.InMemoryPromptsRepository
import com.aipromptbridge.core.prompt.PromptCatalog
import com.aipromptbridge.core.prompt.PromptInput
import com.aipromptbridge.core.prompt.PromptRequest
import com.aipromptbridge.core.prompt.ResultDestination
import com.aipromptbridge.core.provider.FailureKind
import com.aipromptbridge.core.provider.FakeTransport
import com.aipromptbridge.core.provider.GenerateResult
import com.aipromptbridge.core.provider.ok
import com.aipromptbridge.core.tool.ToolId
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest

/** End-to-end through the facade with fakes: prompt composition -> profile -> provider -> parsed result. */
class AiEngineTest {
    private val geminiOk = """{"candidates":[{"content":{"parts":[{"text":"the cat sat"}]}}]}"""
    private val catalog = PromptCatalog(InMemoryPromptsRepository())

    private fun buildEngine(transport: FakeTransport, keys: Map<String, List<KeyEntry>>, profiles: InMemoryProfileRepository) =
        AiEngine(profiles, KeyPoolRegistry(InMemoryKeyRepository(keys)), catalog, transport)

    private val proofread get() = catalog.tool(ToolId.TEXT_EDIT).actions.getValue("proofread")

    @Test
    fun runsAnEditActionThroughGemini() = runTest {
        val transport = FakeTransport(ok(geminiOk))
        val profiles = InMemoryProfileRepository(listOf(ConnectionProfile("Fast", "google", "gemini-test")), "Fast")
        val engine = buildEngine(transport, mapOf("google" to listOf(KeyEntry("k", "secret"))), profiles)

        val outcome = engine.runAction(ToolId.TEXT_EDIT, PromptRequest(proofread, PromptInput.Text("teh cat sat")))

        val result = outcome.result
        assertIs<GenerateResult.Success>(result)
        assertEquals("the cat sat", result.content)
        assertEquals(ResultDestination.REPLACE, outcome.destination)
        assertEquals("Fast", outcome.profile.name)
        assertTrue(transport.requests.single().url.endsWith("/models/gemini-test:generateContent"))
        assertEquals("secret", transport.requests.single().headers["x-goog-api-key"])
    }

    @Test
    fun reportsMissingKeyAsNoApiKeyFailure() = runTest {
        val transport = FakeTransport()
        val engine = buildEngine(transport, emptyMap(), InMemoryProfileRepository())

        val outcome = engine.runAction(ToolId.TEXT_EDIT, PromptRequest(proofread, PromptInput.Text("x")))

        val result = outcome.result
        assertIs<GenerateResult.Failure>(result)
        assertEquals(FailureKind.NO_API_KEY, result.kind)
        assertEquals(0, transport.requests.size)
    }

    @Test
    fun unknownProviderInProfileIsAReadableFailure() = runTest {
        val profiles = InMemoryProfileRepository(listOf(ConnectionProfile("Bad", "nope", "m")), "Bad")
        val engine = buildEngine(FakeTransport(), emptyMap(), profiles)

        val result = engine.runAction(ToolId.TEXT_EDIT, PromptRequest(proofread, PromptInput.Text("x"))).result

        assertIs<GenerateResult.Failure>(result)
        assertTrue(result.message.contains("nope"))
    }

    @Test
    fun actionLevelProfileOverrideIsUsed() = runTest {
        val transport = FakeTransport(ok("""{"choices":[{"message":{"content":"done"}}]}"""))
        val profiles = InMemoryProfileRepository(
            listOf(ConnectionProfile("Fast", "google", "flash"), ConnectionProfile("GPT", "openai", "gpt-test")),
            active = "Fast",
        )
        val engine = buildEngine(transport, mapOf("openai" to listOf(KeyEntry("k", "sk-1"))), profiles)

        val action = proofread.copy(connectionProfile = "GPT")
        val result = engine.runAction(ToolId.TEXT_EDIT, PromptRequest(action, PromptInput.Text("x"))).result

        assertIs<GenerateResult.Success>(result)
        assertTrue(transport.requests.single().url.endsWith("/chat/completions"))
        assertEquals("Bearer sk-1", transport.requests.single().headers["Authorization"])
    }

    @Test
    fun defaultCatalogBuildsWithoutPersistedFile() {
        assertTrue(DefaultPrompts.build().tools.size >= 2)
    }
}

package com.aipromptbridge.core.storage

import com.aipromptbridge.core.keys.KeyEntry
import com.aipromptbridge.core.profile.ConnectionProfile
import com.aipromptbridge.core.prompt.DefaultPrompts
import com.aipromptbridge.core.prompt.PromptCatalog
import com.aipromptbridge.core.tool.ToolId
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class StorageTest {
    private fun tempDir(): File = Files.createTempDirectory("aipb-test").toFile().also { it.deleteOnExit() }

    /** Not secure, just reversible: lets us test the repository without the Android Keystore. */
    private class XorCipher(private val key: Int) : SecretCipher {
        override fun encrypt(plain: ByteArray) = ByteArray(plain.size) { (plain[it].toInt() xor key).toByte() }
        override fun decrypt(encrypted: ByteArray) = encrypt(encrypted)
    }

    @Test
    fun profilesRoundTripAndMissingFileIsEmpty() {
        val file = File(tempDir(), "profiles.json")
        val repo = FileProfileRepository(file)
        assertTrue(repo.profiles().isEmpty())
        assertNull(repo.activeProfileName())

        val profile = ConnectionProfile("Fast", "google", "flash", temperature = 0.3)
        repo.save(listOf(profile), "Fast")

        val reloaded = FileProfileRepository(file)
        assertEquals(listOf(profile), reloaded.profiles())
        assertEquals("Fast", reloaded.activeProfileName())
    }

    @Test
    fun corruptProfilesFileBehavesAsEmpty() {
        val file = File(tempDir(), "profiles.json").apply { writeText("{ this is not json") }
        assertTrue(FileProfileRepository(file).profiles().isEmpty())
    }

    @Test
    fun fullDefaultPromptsConfigSurvivesJsonRoundTrip() {
        val file = File(tempDir(), "prompts.json")
        val repo = FilePromptsRepository(file)
        assertNull(repo.load())

        val defaults = DefaultPrompts.build()
        repo.save(defaults)

        assertEquals(defaults, FilePromptsRepository(file).load())
    }

    @Test
    fun catalogPersistsMergedConfigOnFirstLoad() {
        val file = File(tempDir(), "prompts.json")
        val catalog = PromptCatalog(FilePromptsRepository(file))
        assertTrue(catalog.tool(ToolId.TEXT_EDIT).actions.isNotEmpty())
        assertTrue(file.exists())
    }

    @Test
    fun encryptedKeysRoundTripAndAreNotStoredInPlainText() {
        val file = File(tempDir(), "keys.enc")
        val repo = EncryptedFileKeyRepository(file, XorCipher(0x5A))
        repo.saveKeys("google", listOf(KeyEntry("Key 1", "AIza-secret-1"), KeyEntry("Key 2", "AIza-secret-2")))
        repo.saveKeys("openai", listOf(KeyEntry("Main", "sk-secret")))

        assertTrue(!file.readText().contains("AIza-secret-1"))
        val reloaded = EncryptedFileKeyRepository(file, XorCipher(0x5A))
        assertEquals(listOf(KeyEntry("Key 1", "AIza-secret-1"), KeyEntry("Key 2", "AIza-secret-2")), reloaded.loadKeys("google"))
        assertEquals(listOf(KeyEntry("Main", "sk-secret")), reloaded.loadKeys("openai"))
        assertTrue(reloaded.loadKeys("unknown").isEmpty())
    }

    @Test
    fun wrongCipherOrCorruptKeyFileYieldsNoKeysInsteadOfCrashing() {
        val file = File(tempDir(), "keys.enc")
        EncryptedFileKeyRepository(file, XorCipher(0x5A)).saveKeys("google", listOf(KeyEntry("k", "v")))

        assertTrue(EncryptedFileKeyRepository(file, XorCipher(0x11)).loadKeys("google").isEmpty())

        file.writeText("%%% not base64 %%%")
        assertTrue(EncryptedFileKeyRepository(file, XorCipher(0x5A)).loadKeys("google").isEmpty())
    }

    @Test
    fun fileContentIsActuallyEncoded() {
        val file = File(tempDir(), "keys.enc")
        EncryptedFileKeyRepository(file, XorCipher(0x5A)).saveKeys("google", listOf(KeyEntry("k", "v")))
        assertNotNull(file.readText())
        assertNotEquals("", file.readText().trim())
    }
}

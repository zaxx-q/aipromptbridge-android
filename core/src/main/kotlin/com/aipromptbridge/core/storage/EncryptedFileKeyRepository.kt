package com.aipromptbridge.core.storage

import com.aipromptbridge.core.keys.KeyEntry
import com.aipromptbridge.core.keys.KeyRepository
import java.io.File
import java.util.Base64
import kotlinx.serialization.SerializationException
import kotlinx.serialization.Serializable

/** Encrypts/decrypts opaque bytes. The Android app implements this with the Android Keystore (AES-GCM). */
interface SecretCipher {
    fun encrypt(plain: ByteArray): ByteArray
    fun decrypt(encrypted: ByteArray): ByteArray
}

@Serializable
internal data class StoredKey(val name: String, val value: String)

@Serializable
internal data class KeysFile(val pools: Map<String, List<StoredKey>> = emptyMap())

/**
 * Stores all key pools in ONE file: Base64(cipher.encrypt(json)).
 * If decryption fails (e.g. the Keystore key was wiped) the repository behaves as empty rather than crashing.
 */
class EncryptedFileKeyRepository(
    private val file: File,
    private val cipher: SecretCipher,
) : KeyRepository {

    private fun readAll(): MutableMap<String, List<KeyEntry>> {
        if (!file.exists()) return mutableMapOf()
        return try {
            val encrypted = Base64.getDecoder().decode(file.readText(Charsets.UTF_8).trim())
            val json = String(cipher.decrypt(encrypted), Charsets.UTF_8)
            val parsed = CoreJson.json.decodeFromString(KeysFile.serializer(), json)
            parsed.pools.mapValuesTo(mutableMapOf<String, List<KeyEntry>>()) { entry -> entry.value.map { KeyEntry(it.name, it.value) } }
        } catch (e: IllegalArgumentException) {
            mutableMapOf()
        } catch (e: SerializationException) {
            mutableMapOf()
        } catch (e: java.security.GeneralSecurityException) {
            mutableMapOf()
        }
    }

    @Synchronized
    override fun loadKeys(poolName: String): List<KeyEntry> = readAll()[poolName].orEmpty()

    @Synchronized
    override fun saveKeys(poolName: String, keys: List<KeyEntry>) {
        val all = readAll()
        all[poolName] = keys
        val file0 = KeysFile(all.mapValues { (_, list) -> list.map { StoredKey(it.name, it.value) } })
        val json = CoreJson.json.encodeToString(KeysFile.serializer(), file0)
        val encrypted = cipher.encrypt(json.toByteArray(Charsets.UTF_8))
        file.writeTextAtomically(Base64.getEncoder().encodeToString(encrypted))
    }
}

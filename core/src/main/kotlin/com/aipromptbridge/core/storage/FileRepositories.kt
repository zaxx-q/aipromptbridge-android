package com.aipromptbridge.core.storage

import com.aipromptbridge.core.profile.ConnectionProfile
import com.aipromptbridge.core.profile.ProfileRepository
import com.aipromptbridge.core.profile.ProfilesFile
import com.aipromptbridge.core.prompt.PromptsConfig
import com.aipromptbridge.core.prompt.PromptsRepository
import java.io.File
import kotlinx.serialization.SerializationException

/**
 * File-backed repositories written against plain java.io.File, so they live in :core and are unit-tested on the JVM.
 * The Android app just passes `context.filesDir`-based files.
 * Corrupt/unreadable files are treated as "no data" (TODO: move the corrupt file aside as <name>.corrupt before overwriting).
 */
class FileProfileRepository(private val file: File) : ProfileRepository {
    private fun read(): ProfilesFile =
        try {
            if (file.exists()) CoreJson.json.decodeFromString(ProfilesFile.serializer(), file.readText(Charsets.UTF_8))
            else ProfilesFile()
        } catch (e: SerializationException) {
            ProfilesFile()
        }

    @Synchronized
    override fun profiles(): List<ConnectionProfile> = read().profiles

    @Synchronized
    override fun activeProfileName(): String? = read().active

    @Synchronized
    override fun save(profiles: List<ConnectionProfile>, activeName: String?) {
        val text = CoreJson.json.encodeToString(ProfilesFile.serializer(), ProfilesFile(activeName, profiles))
        file.writeTextAtomically(text)
    }
}

class FilePromptsRepository(private val file: File) : PromptsRepository {
    @Synchronized
    override fun load(): PromptsConfig? =
        try {
            if (file.exists()) CoreJson.json.decodeFromString(PromptsConfig.serializer(), file.readText(Charsets.UTF_8))
            else null
        } catch (e: SerializationException) {
            null
        }

    @Synchronized
    override fun save(config: PromptsConfig) {
        file.writeTextAtomically(CoreJson.json.encodeToString(PromptsConfig.serializer(), config))
    }
}

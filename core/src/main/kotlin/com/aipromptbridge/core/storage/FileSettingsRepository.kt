package com.aipromptbridge.core.storage

import com.aipromptbridge.core.settings.AppSettings
import com.aipromptbridge.core.settings.SettingsRepository
import java.io.File
import kotlinx.serialization.SerializationException

/** settings.json. A missing or unreadable file simply means "all defaults". */
class FileSettingsRepository(private val file: File) : SettingsRepository {
    @Synchronized
    override fun load(): AppSettings =
        try {
            if (file.exists()) CoreJson.json.decodeFromString(AppSettings.serializer(), file.readText(Charsets.UTF_8))
            else AppSettings()
        } catch (e: SerializationException) {
            AppSettings()
        }

    @Synchronized
    override fun save(settings: AppSettings) {
        file.writeTextAtomically(CoreJson.json.encodeToString(AppSettings.serializer(), settings))
    }
}

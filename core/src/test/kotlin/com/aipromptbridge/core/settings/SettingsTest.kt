package com.aipromptbridge.core.settings

import com.aipromptbridge.core.prompt.ResponseMode
import com.aipromptbridge.core.storage.FileSettingsRepository
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals

class SettingsTest {
    private fun tempFile(): File =
        File(Files.createTempDirectory("aipb-settings").toFile().also { it.deleteOnExit() }, "settings.json")

    private class CountingRepository : SettingsRepository {
        var saves = 0
        var stored = AppSettings()
        override fun load(): AppSettings = stored
        override fun save(settings: AppSettings) {
            saves++
            stored = settings
        }
    }

    @Test
    fun missingFileMeansDefaults() {
        assertEquals(AppSettings(), FileSettingsRepository(tempFile()).load())
    }

    @Test
    fun roundTripsThroughTheFile() {
        val file = tempFile()
        val settings = AppSettings(themeMode = ThemeMode.DARK, defaultResponse = DefaultResponse.COPY, snipTileAdded = true)
        FileSettingsRepository(file).save(settings)
        assertEquals(settings, FileSettingsRepository(file).load())
    }

    @Test
    fun corruptFileAndUnknownEnumValuesFallBackToDefaults() {
        val corrupt = tempFile().apply { writeText("{ not json") }
        assertEquals(AppSettings(), FileSettingsRepository(corrupt).load())

        val unknownTheme = tempFile().apply { writeText("""{"themeMode":"sepia"}""") }
        assertEquals(AppSettings(), FileSettingsRepository(unknownTheme).load())
    }

    @Test
    fun storePublishesChangesAndOnlyWritesRealChanges() {
        val repository = CountingRepository()
        val store = SettingsStore(repository)
        assertEquals(ThemeMode.SYSTEM, store.current.themeMode)

        store.update { it.copy(themeMode = ThemeMode.LIGHT) }
        assertEquals(ThemeMode.LIGHT, store.settings.value.themeMode)
        assertEquals(1, repository.saves)

        store.update { it.copy(themeMode = ThemeMode.LIGHT) } // same value: nothing to save
        assertEquals(1, repository.saves)
    }

    @Test
    fun defaultResponseMapsToTheRequestOverride() {
        assertEquals(ResponseMode.SHOW, DefaultResponse.SHOW.toResponseMode())
        assertEquals(ResponseMode.COPY, DefaultResponse.COPY.toResponseMode())
        assertEquals(ResponseMode.SHARE, DefaultResponse.SHARE.toResponseMode())
    }
}

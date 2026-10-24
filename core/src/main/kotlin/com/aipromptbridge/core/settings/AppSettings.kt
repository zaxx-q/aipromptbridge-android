package com.aipromptbridge.core.settings

import com.aipromptbridge.core.prompt.ResponseMode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Light/dark preference. SYSTEM follows the device setting. */
@Serializable
enum class ThemeMode {
    @SerialName("system") SYSTEM,
    @SerialName("light") LIGHT,
    @SerialName("dark") DARK,
}

/**
 * Where a finished result goes by default.
 *
 * Deliberately NOT the same type as [ResponseMode]: that one also has DEFAULT and REPLACE, which make no sense as a
 * standing preference. A dedicated enum makes invalid settings unrepresentable.
 */
@Serializable
enum class DefaultResponse {
    @SerialName("show") SHOW,
    @SerialName("copy") COPY,
    @SerialName("share") SHARE,
}

/** Maps the stored preference to the request-level override understood by `PromptComposer`. */
fun DefaultResponse.toResponseMode(): ResponseMode = when (this) {
    DefaultResponse.SHOW -> ResponseMode.SHOW
    DefaultResponse.COPY -> ResponseMode.COPY
    DefaultResponse.SHARE -> ResponseMode.SHARE
}

/** Small app-level preferences (settings.json). Everything has a default so a missing file is a valid state. */
@Serializable
data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    /** Initial "Send result to" choice in Snip & Ask. Text Edit actions decide for themselves (replace / show). */
    val defaultResponse: DefaultResponse = DefaultResponse.SHOW,
    /**
     * Whether the Quick Settings tile is currently added. Android has no API to query this, so the tile service
     * records it from onTileAdded / onTileRemoved (and from the result of the "add tile" request).
     */
    val snipTileAdded: Boolean = false,
)

interface SettingsRepository {
    fun load(): AppSettings
    fun save(settings: AppSettings)
}

class InMemorySettingsRepository(private var stored: AppSettings = AppSettings()) : SettingsRepository {
    override fun load(): AppSettings = stored
    override fun save(settings: AppSettings) {
        stored = settings
    }
}

/**
 * Observable holder for [AppSettings]. Screens collect [settings]; changes go through [update].
 *
 * The initial value is read synchronously in the constructor (one tiny JSON file) so the very first frame already uses
 * the right theme instead of flashing the wrong one. [update] publishes the new value immediately and then writes the
 * file, so call it from a background dispatcher.
 */
class SettingsStore(private val repository: SettingsRepository) {
    private val state = MutableStateFlow(repository.load())

    val settings: StateFlow<AppSettings> = state.asStateFlow()

    val current: AppSettings get() = state.value

    @Synchronized
    fun update(transform: (AppSettings) -> AppSettings): AppSettings {
        val before = state.value
        val after = transform(before)
        if (after != before) {
            state.value = after
            repository.save(after)
        }
        return after
    }
}

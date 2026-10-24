package com.aipromptbridge

import android.content.Context
import com.aipromptbridge.core.engine.AiEngine
import com.aipromptbridge.core.http.HttpTransport
import com.aipromptbridge.core.keys.KeyPoolRegistry
import com.aipromptbridge.core.keys.KeyRepository
import com.aipromptbridge.core.profile.ProfileRepository
import com.aipromptbridge.core.prompt.PromptCatalog
import com.aipromptbridge.core.session.SessionRepository
import com.aipromptbridge.core.settings.SettingsStore
import com.aipromptbridge.core.storage.EncryptedFileKeyRepository
import com.aipromptbridge.core.storage.FileProfileRepository
import com.aipromptbridge.core.storage.FilePromptsRepository
import com.aipromptbridge.core.storage.FileSessionRepository
import com.aipromptbridge.core.storage.FileSettingsRepository
import com.aipromptbridge.data.KeystoreSecretCipher
import com.aipromptbridge.data.UrlConnectionTransport
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * Wires :core to Android. Everything here is lazy so process start stays fast and nothing touches the disk
 * until a feature needs it. Features obtain it via `(application as AipbApp).container`.
 *
 * Adding a feature that needs new shared services? Add them here.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext
    private val dir: File get() = appContext.filesDir

    /** Outlives any screen, so a save started just before the user navigates away is never cancelled half way. */
    val appScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    val transport: HttpTransport by lazy { UrlConnectionTransport() }

    val keyRepository: KeyRepository by lazy { EncryptedFileKeyRepository(File(dir, "keys.enc"), KeystoreSecretCipher()) }
    val keyPools: KeyPoolRegistry by lazy { KeyPoolRegistry(keyRepository) }

    val profileRepository: ProfileRepository by lazy { FileProfileRepository(File(dir, "profiles.json")) }

    val promptCatalog: PromptCatalog by lazy { PromptCatalog(FilePromptsRepository(File(dir, "prompts.json"))) }

    val sessions: SessionRepository by lazy { FileSessionRepository(File(dir, "sessions.json")) }

    /** App preferences (theme, default response mode...). Reads settings.json once, on first access. */
    val settingsStore: SettingsStore by lazy { SettingsStore(FileSettingsRepository(File(dir, "settings.json"))) }

    val engine: AiEngine by lazy { AiEngine(profileRepository, keyPools, promptCatalog, transport) }
}

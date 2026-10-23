# Kotlin concepts → where they live in this project

Assignment checklist mapped to real code (file paths relative to the project root). Each line names the function/class so you can
find it with *Find in Files*. Short code excerpts are in the third column for quick reference.

| Concept | Where | Example |
|---|---|---|
| **Conditional statements** (`if/else`, `when`) | `core/.../provider/AiProvider.kt` → `attemptOnce()` (a `when` that classifies HTTP status codes); `core/.../prompt/PromptComposer.kt` → `resolveDestination()`; `core/.../diff/WordDiff.kt` → `diff()` | `status == 429 -> Attempt.RotateKey(...)` |
| **Arrays** | `core/.../diff/WordDiff.kt` → `Array(n + 1) { IntArray(m + 1) }` (2-D LCS table); `app/.../data/KeystoreSecretCipher.kt` (`ByteArray`); `core/.../storage/EncryptedFileKeyRepository.kt` (`ByteArray` encrypt/decrypt) | `val lcs = Array(n + 1) { IntArray(m + 1) }` |
| **Loops** (`for`, `while`) | `AiProvider.generate()` (`for (attempt in 0 until maxAttempts)`); `KeyPool.rotate()` (`for (step in 1..entries.size)`); `WordDiff.diff()` (`for` + `while`) | `while (i < n && j < m) { ... }` |
| **Nullable types** | `KeyPool.currentKey(): String?`; `ConnectionProfile.temperature: Double?`; `PromptAction.connectionProfile: String?`; `GenerationParams.maxOutputTokens: Int?` | `fun currentKey(): String?` |
| **Safe calls & Elvis operator** | `AiProvider.generate()` (`keyPool.currentKey() ?: return ...`); `ProfileResolver.resolve()` (`find(a) ?: find(b) ?: find(c) ?: FALLBACK`); `GeminiProvider.parseResponse()` (long `?.` chain through the JSON) | `root["candidates"]?.jsonArray?.firstOrNull()?.jsonObject` |
| **Functions** | top-level `File.writeTextAtomically` (`core/.../storage/CoreJson.kt`); extension `Context.copyToClipboard` (`app/.../feature/common/SystemActions.kt`); `CropMath.viewToBitmap`; `WordDiff.changeCount` | `fun Context.copyToClipboard(text: String, label: String = "AIPromptBridge")` |
| **Named & default arguments** | `RetryPolicy(maxAttempts = 2)` (`AiProviderRetryTest`); `PromptRequest(action, input, customInstruction = "…")`; `GenerationParams(temperature = 0.5, maxOutputTokens = 100)`; defaults in `ConnectionProfile`, `RetryPolicy`, `AiProvider` constructor | `data class RetryPolicy(val maxAttempts: Int = 6, ...)` |
| **Classes** (≥1 property, ≥1 method) | `core/.../keys/KeyPool.kt` — property `size`, methods `currentKey()`, `rotate()`, `reset()` | `class KeyPool(rawEntries: List<KeyEntry>)` |
| **Primary constructors** | `KeyPool(rawEntries)`, `AiProvider(baseUrl, keyPool, transport, retryPolicy)`, every `data class` (e.g. `ConnectionProfile`) | `abstract class AiProvider(protected val baseUrl: String, private val keyPool: KeyPool, ...)` |
| **Encapsulation** (private members) | `KeyPool`: `private val entries`, `private val exhausted`, `private var index` are only changed via `rotate()`/`reset()`; `AiProvider`: `private suspend fun attemptOnce`, `private val keyPool`; `KeystoreSecretCipher.getOrCreateKey()` is private | `private val exhausted = mutableSetOf<Int>()` |
| **Abstraction** (interface / abstract class) | `abstract class AiProvider` (`core/.../provider/AiProvider.kt`); `interface HttpTransport`; `interface KeyRepository`; `interface SecretCipher`; `interface AiTool` | `protected abstract fun buildHttpRequest(...)` |
| **Inheritance** | `GeminiProvider`, `OpenAiCompatibleProvider`, `AnthropicProvider` all `: AiProvider(...)` | `class GeminiProvider(...) : AiProvider(baseUrl, keyPool, transport, retryPolicy)` |
| **Polymorphism** (override / overload) | Each provider **overrides** `buildHttpRequest()` and `parseResponse()`; `ProviderRegistry.create()` returns an `AiProvider` and callers use it without knowing the subclass; `sealed interface ContentPart` handled with an exhaustive `when` | `override fun parseResponse(body: String): ParsedResponse` |

## Extra Kotlin features worth pointing out
- **Sealed interfaces** + exhaustive `when`: `GenerateResult`, `ContentPart`, `PromptInput`, `EditUiState`, `SnipStage`.
- **Data classes** and `copy()`: `PromptAction.copy(isDefault = false)`.
- **Coroutines**: `suspend fun generate()`, `delay()` retries, cancellation == aborting the HTTP call (`UrlConnectionTransport`).
- **Extension functions**, **object** singletons (`ProviderRegistry`, `PromptComposer`, `WordDiff`), **companion objects**, **enum classes** (`ToolId`).
- **Template Method pattern** (`AiProvider.generate` orchestrates; subclasses fill in two steps).

> Method names are stable; line numbers are not, so use *Navigate > Symbol* (Ctrl+Alt+Shift+N) for the names above.

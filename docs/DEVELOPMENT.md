# Developer Guide & Technical Notes

This document contains architectural decisions, implementation history, verification procedures, and developer notes for **AIPromptBridge for Android**.

---

## 1. Architecture Overview

The project is structured into two Gradle modules to ensure clean boundaries and rapid JVM unit testing:

```
core/   (pure Kotlin/JVM — NO android.* imports)              app/   (Android: Compose UI + OS integration)
  http/      HttpTransport interface (fakeable)                 AipbApp, AppContainer (manual DI, lazy singletons)
  model/     ChatMessage, Role, ContentPart (sealed)            data/        UrlConnectionTransport, KeystoreSecretCipher
  provider/  AiProvider (ABSTRACT, template-method retry)       designsystem/ Color/Type/Shape/Theme, AipbIcons, Components
             GeminiProvider, OpenAiCompatibleProvider,          feature/textedit/  ProcessTextActivity, TextEditSheet, ViewModel
             AnthropicProvider, ProviderRegistry                feature/snip/      Tile, CaptureService, SnipActivity, selector, sheet
  keys/      KeyPool (rotation), KeyRepository, KeyPoolRegistry feature/settings/  Settings, Profiles, Keys screens
  profile/   ConnectionProfile, ProfileResolver                 feature/prompteditor/ Actions, ActionEdit, Modifiers screens
  prompt/    PromptAction/Group/Modifier/Config, PromptComposer,feature/common/    ErrorPanel, clipboard/share helpers
             PromptMerge, PromptCatalog, DefaultPrompts(+Data)  nav/         Screen, Destination, NavViewModel
  session/   ChatSession, SessionRepository                     tools/       AiTool + ToolRegistry
  diff/      WordDiff (LCS word diff for the result view)
  snip/      CropMath (view -> bitmap pixel mapping)
  storage/   File*Repository, EncryptedFileKeyRepository
  engine/    AiEngine  <— the ONLY entry point features call
  tool/      ToolId (Text Edit, Snip)
```

### Request Flow (Text Edit)
1. `ProcessTextActivity` receives `android.intent.action.PROCESS_TEXT`.
2. `TextEditViewModel.run(action)` calls `AiEngine.runAction(ToolId.TEXT_EDIT, PromptRequest)`.
3. `PromptComposer.compose` builds the message payload (system instruction + active modifiers + action task + output rules + `<text_to_process>`).
4. `ProfileResolver.resolve` determines the connection profile (action override → tool setting → global active profile → built-in fallback).
5. `ProviderRegistry.create` instantiates the corresponding `AiProvider`.
6. `AiProvider.generate` runs the request loop with automatic key rotation and retry delays on rate limits.
7. `GenerateResult` returns to `TextEditViewModel`, which computes the LCS word diff using `WordDiff`.
8. `TextEditSheet` renders the diff with additions highlighted and deletions struck.

### Dependency Rules
- `:core` depends on nothing Android. All persistence uses `java.io.File` and is testable on the desktop JVM.
- `:app` depends on `:core`. Features talk to AI models **only** through `AiEngine`, never directly to providers or HTTP transports.

---

## 2. Key Technical Decisions & Rationale

| Decision | Rationale |
|---|---|
| **Two Gradle modules** (`:core` pure JVM + `:app`) | Fast JVM unit tests for all domain and data logic without emulator overhead. |
| **Jetpack Compose + Material 3** | Declarative UI matching the Stitch design system tokens directly. |
| **Text trigger = `ACTION_PROCESS_TEXT`** | Native Android text selection integration; requires zero special background permissions and supports in-place replacement. |
| **Snip trigger = Quick Settings Tile → Single Activity** | Starts `SnipActivity` in the foreground to conform with Android 14+ background launch restrictions; handles system consent, frame capture, region crop, and action sheet in a single flow. |
| **Template-method `AiProvider`** | Retries and key pool rotation live once in `AiProvider.generate`; provider subclasses only format request JSON and parse response JSON. Coroutine cancellation aborts the active HTTP request cleanly. |
| **`HttpTransport` interface + `HttpURLConnection`** | Pure JVM implementation with zero external HTTP dependencies; easily faked in unit tests. |
| **AES-GCM Keystore Encryption** | API keys are stored encrypted on-device via Android Keystore authenticated encryption (`EncryptedFileKeyRepository`). |
| **Deterministic Prompt Generation** | Default prompts (~65 KB of prompt engineering, including Humanizer and Code Review) are mechanically generated from the desktop app via `tools/generate_default_prompts.py`. Never hand-edit `DefaultPromptsData.kt`. |
| **3-Way Prompt Merge (`PromptMerge`)** | Preserves user customizations while allowing app updates to ship improved default prompts. Default actions edited by the user are marked `isDefault = false`. |

---

## 3. Implementation History & Milestones

- **Phase 0 (Compile & Core Verification):** Resolved compiler issues across Compose 1.12.0 / `compileSdk = 37`, MediaProjection nullability guards, and KDoc comment syntax. Fixed `WordDiff` equal whitespace tokens sliding in LCS.
- **Phase 1 (Text Edit):** In-place text processing with word diff rendering, copy/replace actions, and read-only field handling.
- **Phase 2 (Design System):** Stitch editorial design tokens (color schemes, control corner radii, typography, custom icons, dark & light themes).
- **Phase 3 & 5 (Settings, Profiles, Multi-key):** Connection profiles CRUD, multiple API keys per provider with rotation, triggers management, and theme settings.
- **Phase 4 (Chat Mode):** Multi-turn chat seeded with selection context, collapsible text preview pill, and inline replacement.
- **Phase 6 (Prompt Editor):** In-app prompt catalog management: action list, reorderable bar order (accessible up/down controls), group assignments, system prompt and task editors.
- **Phase 7 (Snip & Ask):** MediaProjection foreground service capture, zero-flicker frame grab, interactive touch crop with live dimensions, action sheet with cropped preview, and "Continue in chat" image context passing. Verified on Android 15 (API 35).
- **Phase 8 (Prompt Modifiers):** Real-time modifiers (Shorter, Formal, Variations, Language) injected dynamically into requests from the sheet tune toggle or dedicated editor.

---

## 4. Verification Checklist

To verify the codebase:

```bash
# 1. Run all core JVM unit tests (152 tests)
./gradlew :core:test

# 2. Build debug APK
./gradlew :app:assembleDebug

# 3. Clean full build
./gradlew clean :core:test :app:assembleDebug
```

Key verifications:
- **Core logic**: Retry/rotation virtual timing, request serialization for Gemini/OpenAI/Anthropic, prompt composition, word diff input reconstruction, JSON serialization round-trips, key masking, and chat history.
- **Keys at rest**: Run `adb shell run-as com.aipromptbridge cat files/keys.enc` — encrypted ciphertext only, no plaintext keys.
- **Prompt generator**: `python3 tools/generate_default_prompts.py && git diff --stat` produces zero diff.

---

## 5. Future Work / Stretch Enhancements

The following stretch enhancements are parked for future releases:

1. **Interactive Selection Handles** (`feature/snip/RegionSelector.kt`): Add corner and edge touch handles to resize the crop box after the initial drag gesture.
2. **Downscale Large Snip Crops** (`feature/snip/SnipViewModel.kt`): Automatically downscale high-resolution crops before Base64 encoding to optimize memory and API latency.
3. **Rich Markdown Rendering** (`feature/snip/SnipSheet.kt`): Render formatted Markdown for Snip action responses instead of plain text.
4. **Bundled Font Files** (`designsystem/Type.kt`): Drop standalone Roboto Flex and JetBrains Mono `.ttf` files into `res/font/` (the app currently falls back cleanly to system Roboto and Monospace).
5. **Streaming Responses (SSE)** (`data/UrlConnectionTransport.kt`): Add Server-Sent Events parsing to `HttpTransport` for token-by-token real-time streaming in Chat mode.
6. **Provider Parameter Updates**: Support newer OpenAI `max_completion_tokens` parameter and Gemini `thinkingConfig`.
7. **Biometric Gate** (`data/KeystoreSecretCipher.kt`): Option to enforce biometric authentication (`setUserAuthenticationRequired(true)`) before decrypting stored API keys.
8. **Saved Session History UI**: Dedicated screen to browse, search, and resume past multi-turn chat sessions.

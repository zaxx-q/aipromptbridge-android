# AIPromptBridge for Android — starter project

A native Android port of the desktop app **AIPromptBridge** (Python). It brings two features to the phone:

1. **Text Edit** — select text in *any* app, choose **AIPromptBridge** in the selection menu, and a bottom sheet slides up
   over that app with one-tap AI actions ("Fix Grammar", "Humanizer", "Rewrite"…), a diff-style result, and *Replace selection*.
2. **Snip & Ask** — tap a Quick Settings tile, the screen is captured, you drag a region, pick an action, and the AI answers about that region.

It is also a Kotlin learning assignment (see `CONCEPTS.md` for where each required concept lives), so the code is written to be
**readable and well-commented first**, clever second.

> **Honest status: this scaffold was written without being able to compile or run anything** (the authoring sandbox had no Kotlin
> compiler, Gradle or network). Everything is complete and consistent as far as static checks can tell, but **the first thing to do is
> build it and fix whatever the compiler finds** (see "Setup" and "Known risks"). Library versions were looked up in current docs.

---------------------------------------------------------------------------------------------------

## 1. Status at a glance

| Area | State |
|---|---|
| `:core` (providers, key rotation, profiles, prompts, diff, storage, engine) | **Fully implemented**, 78/78 unit tests passing |
| Design system (tokens, theme, components) | Implemented from the Stitch design |
| Setup screen (provider + model + 1 API key) | Implemented (minimal) |
| **Text Edit** — Edit mode (chips, loading, diff result, Replace/Copy/Retry, read-only handling) | Implemented, end to end |
| Text Edit — **Chat mode** | **TODO** (Phase 4) — placeholder + exact plan |
| Full **Settings** (profiles, multi-key, triggers, behaviour, theme) | **TODO** (Phase 5) |
| **Prompt editor** (action list + edit action) | **TODO** (Phase 6) — data layer done (`PromptCatalog.updateTool`) |
| **Snip & Ask** (tile → capture → region → action sheet) | Implemented but **never run on a device** (Phase 7 = verify/fix) |
| Prompt **modifiers** (Shorter/Formal/Variations…) | Modelled + tested in `:core`; **no UI** (Phase 8) |

Search for remaining work: `grep -rn "TODO" --include=*.kt --include=*.xml .` (every TODO is tagged, e.g. `TODO(chat)`).

---------------------------------------------------------------------------------------------------

## 2. Setup (Android Studio, Linux)

Requirements: **Android Studio Quail (2026.1.x)**, JDK 17+ (the JBR bundled with Studio is fine), Android SDK Platform **37**
(needed for `compileSdk = 37` due to Compose 1.12.0 in the BOM; `targetSdk` is 36, `minSdk` is 30). Pinned versions are in `gradle/libs.versions.toml`:
AGP 9.0.0 (needs Gradle ≥ 9.1.0 — set in `gradle/wrapper/gradle-wrapper.properties`), Kotlin 2.3.21, Compose BOM 2026.08.00,
activity-compose 1.13.0, lifecycle 2.10.0, coroutines 1.11.0, kotlinx-serialization 1.12.0.

1. **Open** this folder in Android Studio (`File > Open`). Let Gradle sync. If Studio says the AGP version is not supported by
   your IDE, bump/lower `agp` in `gradle/libs.versions.toml` (Studio's *AGP Upgrade Assistant* can do it).
2. **Generate the Gradle wrapper scripts once** (the zip cannot contain the binary `gradle-wrapper.jar`):
   Gradle tool window → *Tasks > build setup > wrapper* (double-click). After that `./gradlew` works from a terminal.
3. **Run the core tests first** (fast, no emulator): `./gradlew :core:test`.
   Expected: 78 tests, all green. Fix any compile errors/failures here before touching the UI.
4. **Run the app** on an emulator or device (API 30+): `./gradlew :app:installDebug` or the ▶ button.
5. In the app: pick a provider, enter a model (defaults are filled in) and paste an **API key**, press *Save*.
   Keys are stored encrypted (Android Keystore AES-GCM) — **never commit or zip real keys**.
6. **Test Text Edit:** open any app with selectable text (Chrome, Keep, Messages…), long-press to select, open the overflow
   (⋮ / More) in the selection toolbar, choose **AIPromptBridge**. Tap *Fix Grammar*.
   *(Only apps that implement `PROCESS_TEXT` show the entry; if an app does not, try another.)*
7. **Test Snip:** in the setup screen tap *Add Quick Settings tile* (Android 13+; otherwise edit tiles manually), then tap the tile.

---------------------------------------------------------------------------------------------------

## 3. Architecture

```
core/   (pure Kotlin/JVM — NO android.* imports, ever)           app/   (Android: Compose UI + OS integration)
  http/      HttpTransport interface (fakeable)                    AipbApp, AppContainer (manual DI, lazy)
  model/     ChatMessage, Role, ContentPart (sealed)               data/        UrlConnectionTransport, KeystoreSecretCipher
  provider/  AiProvider (ABSTRACT, template-method retry)          designsystem/ Color/Type/Shape/Theme, AipbIcons, Components
             GeminiProvider, OpenAiCompatibleProvider,             feature/textedit/  ProcessTextActivity, TextEditSheet, ViewModel
             AnthropicProvider, ProviderRegistry                   feature/snip/      Tile, CaptureService, SnipActivity, selector, sheet
  keys/      KeyPool (rotation), KeyRepository, KeyPoolRegistry    feature/settings/  SetupScreen (minimal)
  profile/   ConnectionProfile, ProfileResolver                    feature/common/    ErrorPanel, clipboard/share helpers
  prompt/    PromptAction/Group/Modifier/Config, PromptComposer,   tools/            AiTool + ToolRegistry (extension point)
             PromptMerge, PromptCatalog, DefaultPrompts(+Data)
  session/   ChatSession, SessionRepository
  diff/      WordDiff (LCS word diff for the result view)
  snip/      CropMath (view -> bitmap pixel mapping)
  storage/   File*Repository, EncryptedFileKeyRepository
  engine/    AiEngine  <— the ONLY thing features talk to
  tool/      ToolId (Text Edit, Snip; future: Audio, TTS…)
```

**Request flow (Text Edit):**
`ProcessTextActivity` → `TextEditViewModel.run(action)` → `AiEngine.runAction(ToolId.TEXT_EDIT, PromptRequest)` →
`PromptComposer.compose` (system prompt + modifiers + task + output rules + `<text_to_process>`) →
`ProfileResolver.resolve` (session → action → active → fallback) → `ProviderRegistry.create` → `AiProvider.generate`
(retry/rotation loop) → `GenerateResult` → ViewModel builds a word diff → `TextEditSheet` renders it.

**Dependency rule:** `app` depends on `core`; `core` depends on nothing Android. Persistence is plain `java.io.File` in `core`
(`FileProfileRepository`…) so it is unit-tested on the JVM; the app only supplies `filesDir` and the Keystore cipher.

**Naming gotcha:** the original app calls the chip row "ModifierBar" but it contains **actions**. Real *modifiers* (Shorter, Formal,
Variations… toggles that inject instructions) are `PromptModifier` (not `Modifier`, which is a Compose type).

### How to add a future feature (Audio, TTS, File Processor…)
1. Add an entry to `core/.../tool/ToolId.kt`.
2. Add its default prompts: extend `tools/generate_default_prompts.py` (or hand-write) and `core/.../prompt/DefaultPrompts.kt`.
3. Add `app/.../feature/<name>/` (+ manifest entries) and register an `AiTool` in `app/.../tools/ToolRegistry.kt`.
4. Talk to the AI only through `AiEngine`. Providers/keys/profiles/retries need no changes.

---------------------------------------------------------------------------------------------------

## 4. Key decisions and why

| Decision | Rationale |
|---|---|
| **Two Gradle modules** (`:core` pure JVM + `:app`) | Fast JVM tests for all logic, clean boundaries, still simple Gradle. Full multi-module-per-feature was rejected as premature and risky to set up without a compiler. |
| **Jetpack Compose + Material 3** | Stitch tokens map directly onto a `ColorScheme`; `ModalBottomSheet` exists. |
| **Text trigger = `ACTION_PROCESS_TEXT`** activity with a translucent theme | No special permissions, shows over the host app, can return replacement text. Limitation: only apps that support it show the entry; not a global hotkey. |
| **Snip trigger = Quick Settings tile → ONE transparent activity** hosting consent → capture → select → sheet | An already-foreground activity can show UI freely (avoids background-activity-start limits). Capture uses MediaProjection in a `mediaProjection` foreground service (verified in docs: FGS type + `FOREGROUND_SERVICE_MEDIA_PROJECTION` permission, one-time token, register a callback). The system consent prompt appears every time. |
| **Template-method providers** | Retry/key rotation lives once in `AiProvider.generate`; subclasses only build the request and parse the body (as in the original `base.py`). Cancelling the coroutine cancels the HTTP call (replaces the original `abort_event`). |
| **`HttpTransport` interface + `HttpURLConnection`** | No third-party dependency risk; trivially fakeable in tests. Swap for OkHttp later if streaming is added. |
| **JSON files** (`profiles.json`, `prompts.json`, `sessions.json`) | Mirrors the original app; trivial to inspect; fine at this scale. |
| **Keys: AES-GCM with Android Keystore** | Standard approach; Jetpack security-crypto was deliberately not used (deprecated). Keystore code is unverified on-device. |
| **Prompt defaults are GENERATED** from the original `prompts.py` | The prompts (esp. Humanize, ~19 KB) are real assets; mechanical porting avoids retyping errors. Re-run `python3 tools/generate_default_prompts.py` after changing its mapping tables. Never hand-edit `DefaultPromptsData.kt`. |
| **`isDefault` + `deletedDefaults` merge** (`PromptMerge`) | App updates can ship better default prompts without overwriting user edits. **The prompt editor must set `isDefault = false` whenever the user edits a default action.** |
| **Result shown as a word diff** | Matches the Stitch design (insert = highlighter yellow, delete = struck red); computed client-side by `WordDiff` (LCS, with a size guard). |
| **Modifiers modelled in core, UI later** | Chosen by the user: keeps v1 small without losing the concept. |
| **Single key + 401/402/403 ⇒ fail fast** (deviation from original) | Rotating a single key to itself would just hammer the API. Single key + 429 waits 2 s. |
| **Defaults for models** come from the original app's fallback lists (e.g. `gemini-3.5-flash`) | Users can change the model in setup; verify names against provider docs. |
| **minSdk 30**, compile/target 36 | Simplifies `WindowMetrics` and MediaProjection paths. |
| **Fonts**: `FontFamily.Default` (Roboto) + monospace | No font files were available offline; swap in Roboto Flex later (`Type.kt` TODO). |

---------------------------------------------------------------------------------------------------

## 5. Remaining work (concrete)

Work in this order. After each step: `./gradlew :core:test`, then run the app.

### Phase 0 — make it build (do this first)
- Run `:core:test`, then `:app:assembleDebug`. Initial compilation identified the exact list of 4 fixes needed:
  1. **`:core:test`**: 77 of 78 tests pass. Single failure: `WordDiffTest.changeCountCountsRegionsNotWords` (expected 2 regions, got 3).
     - *Cause:* In `core/.../diff/WordDiff.kt`, whitespace tokens (`" "`) match inter-word spaces in old text, splitting multi-word edits (e.g. `"b"` -> `"X Y"`) with an `EQUAL " "` segment.
     - *Fix:* Slide/shift equal whitespace segments out of multi-word insertions/deletions before counting regions.
  2. **`compileSdk = 37`**: Compose 1.12.0 dependencies (from Compose BOM 2026.08.00) require `compileSdk = 37` in `app/build.gradle.kts`.
  3. **`Previews.kt` syntax error**: Line 20 has `design/stitch/*/screen.png`; in Kotlin, `/*` inside KDoc `/** ... */` is parsed as a nested block comment, causing an `Unclosed comment` syntax error at EOF. Rephrase to avoid `/*` (e.g. `design/stitch/<screen>/screen.png`).
  4. **`ScreenCaptureService.kt` nullability**: `MediaProjectionManager.getMediaProjection(resultCode, data)` returns `MediaProjection?`. Add a null-check guard before `registerCallback` and `createVirtualDisplay`.
- Do **not** "fix" errors by adding `android.*` imports to `:core`.

### Phase 1 — verify Text Edit on a device
- Run through "Setup" steps 5-6. Check: sheet appears over the host app; chips load (they come from `PromptCatalog` on an IO thread, so a short delay is normal);
  *Fix Grammar* returns a diff; *Replace selection* actually replaces text in an **editable** field; in a **read-only** field only *Copy* is offered.
- Known things to check: IME overlap (`imePadding()` + `navigationBarsPadding()` in `AipbBottomSheet`), dark mode contrast, long text scrolling.

### Phase 4 — Chat mode (`feature/textedit/`)
Replace `ChatModePlaceholder` in `TextEditSheet.kt`. Plan (APIs already exist):
```kotlin
// ChatViewModel (new). `catalog`, `engine`, `sessions` come from AppContainer.
val config = catalog.config()                                   // blocking -> call on Dispatchers.IO
val tool = config.tool(ToolId.TEXT_EDIT)
val system = tool.setting(SettingKeys.CHAT_SYSTEM_INSTRUCTION).ifBlank { config.chatWindowSystemInstruction }
val session = sessions.create(origin = "textedit:chat", title = selectedText.take(40))   // or reuse the action's origin
// first user message gets the context marker:
val first = ChatMessage.user("${ContextMarker.forAction(actionLabel)}\n\n$selectedText")
val messages = listOf(ChatMessage.system(system)) + session.toChatMessages() + ChatMessage.user(input)
when (val r = engine.chat(messages, sessionProfile = session.profileOverride)) {
    is GenerateResult.Success -> sessions.save(session.withMessage(Role.USER, input, now).withMessage(Role.ASSISTANT, r.content, now))
    is GenerateResult.Failure -> /* show ErrorPanel */
}
```
UI (see `design/stitch/aipromptbridge_textedit_chat_mode_expanded/screen.png`): sheet expanded ~85 %, collapsed "N characters selected" pill,
user messages right-aligned in a `Panel`, AI replies as plain text (render Markdown), small icon row (copy / replace / retry), `ActionBar` above the composer.
Also wire the Snip "Continue in chat" TODO (`SnipSheet.kt`). Add unit tests for any new pure logic in `:core`.

### Phase 5 — Settings (`feature/settings/`)
Replace the minimal `SetupScreen` with the Stitch **Settings** screen (`design/stitch/aipromptbridge_settings/`). Sections (list rows with dividers, **no cards**):
Connection (profiles, API keys), Triggers (switches: text selection menu, Quick Settings tile — use `requestAddSnipTile`, floating button = future),
Behaviour (default response mode, stream responses = future), Appearance (System/Light/Dark → pass `darkTheme` into `AipbTheme`; persist the choice in a small JSON/DataStore).
- Profiles: CRUD over `ProfileRepository` (`save(profiles, activeName)`), `ConnectionProfile.forProvider(def)` for defaults.
- Multi-key: `container.keyRepository.saveKeys(pool, listOf(KeyEntry("Key 1", "…"), KeyEntry("Key 2", "…")))` then `container.keyPools.invalidate(pool)`. `KeyPool` already rotates.
- Navigation: a simple `sealed interface Screen` + `remember` state in `MainActivity` is enough; add Navigation Compose only if needed (look up its current version in the docs).
- **Do not render invented status text** from the mockup ("Engine Ready · v2.4.12", "Android 14 IPC Binder…"). See `docs/DESIGN_NOTES.md`.

### Phase 6 — Prompt editor (`feature/prompteditor/`)
Screens: action list (`design/stitch/aipromptbridge_actions_prompt_editor/`) and edit action (`…/aipromptbridge_edit_action_fix_grammar/`).
Everything goes through `PromptCatalog`:
```kotlin
val tool = catalog.tool(ToolId.TEXT_EDIT)
tool.resolvedGroups()                       // groups -> actions for the list; tool.barActions() = what is on the one-tap bar

// edit an action (MUST mark it as user-modified so updates don't overwrite it):
catalog.updateTool(ToolId.TEXT_EDIT) { t -> t.copy(actions = t.actions + (id to action.copy(systemPrompt = newText, isDefault = false))) }
// toggle "Show in bar":
catalog.updateTool(tool) { t -> t.copy(barOrder = if (id in t.barOrder) t.barOrder - id else t.barOrder + id) }
// reorder: t.copy(barOrder = reorderedList)
// delete: t.copy(actions = t.actions - id, barOrder = t.barOrder - id,
//               deletedDefaults = if (action.isDefault) t.deletedDefaults + id else t.deletedDefaults)
// when a *setting* string is edited: settings + (key to value), modifiedSettings + key
```
Add a "Test on sample text" button that runs `engine.runAction` with a fixed sample. Replace the raw icon-id text from the mockup with a real icon picker (ids in `AipbIcons.byId`).
Make the chip-row "Edit actions" button (`TextEditSheet.kt`, `TODO(prompt-editor)`) open this screen.

### Phase 7 — Snip: verify on a real device (`feature/snip/`)
The code exists but has never run. Checklist:
1. Tile appears (`requestAddSnipTile` or manual) and tapping collapses the shade and opens `SnipActivity`.
2. System consent dialog appears; accepting starts `ScreenCaptureService` (notification "Capturing your screen…"); no crash about foreground-service type/permission.
3. A frame arrives (`CaptureBus`) and `RegionSelector` shows the screenshot **at the right size/orientation, not blank, and without the consent dialog/shade in it** (tune `CAPTURE_DELAY_MS`).
4. Drag → crop marks + readout; *Use selection* → action sheet shows the **real** cropped thumbnail.
5. Tapping *Describe*/*Smart Extract* returns text; Copy/Share modes work.
6. Rotation: `SnipActivity` handles `configChanges`; confirm nothing relaunches the consent flow.
Then set `SnipTool.implemented = true` (`tools/ToolRegistry.kt`). Optional improvements: resize handles on the selection (`TODO(handles)`), downscale big crops (`TODO(size)`), capture on a background `HandlerThread`.

### Phase 8 — Modifiers UI
`PromptRequest.activeModifierKeys` and `PromptComposer` already support modifiers (injection into the system prompt, `forcesChat` ⇒ show result instead of replace).
Add a second row of toggle chips (or open it from the "tune" button), pass the selected keys through `TextEditViewModel.run`. `PromptModifier.icon` ids are already mapped.

### Phase 9 — Polish & submission
- Dark theme pass on every screen; verify 48 dp touch targets and TalkBack labels (`SegmentedToggle` needs selected-state semantics).
- Roboto Flex / mono font files (`Type.kt` TODO); real launcher icon (`ic_launcher_foreground.xml`).
- Optional features parked for later: streaming (`UrlConnectionTransport` TODO + `ConnectionProfile.streaming`), Gemini `thinkingConfig`, OpenAI `max_completion_tokens`, compare actions (need two inputs; skipped by the generator), Audio/TTS tools, source-app label, floating-button trigger.
- **Assignment submission:** `File > Export > Project to ZIP File`, name it `MP_<Mentee Name>`. Before exporting: delete `keys.enc` if it ever appeared in the project dir, make sure no API keys are in any file, and run a clean build. Fill `CONCEPTS.md` line numbers if the mentor wants exact locations.

---------------------------------------------------------------------------------------------------

## 6. How to verify

| Check | How | Expected |
|---|---|---|
| Core logic | `./gradlew :core:test` | 78 tests pass. Notable: retry/rotation timing (virtual time), Gemini/OpenAI/Anthropic request shapes, prompt composition & merge, word diff reconstructs both inputs (property test), JSON round-trip of the full default prompt config, encrypted key file never contains plaintext keys. |
| App builds | `./gradlew :app:assembleDebug` | Success (after Phase 0 fixes). |
| Text Edit | Setup step 6 | Sheet over host app → diff result → *Replace selection* replaces text. |
| Keys at rest | `adb shell run-as com.aipromptbridge cat files/keys.enc` (debug build) | Base64 gibberish, no key text. |
| Prompt defaults | `python3 tools/generate_default_prompts.py && git diff --stat` | No changes (generator is deterministic). |

---------------------------------------------------------------------------------------------------

## 7. Known risks / unverified items (be skeptical of these first)

1. **Compilation status**: Initial compilation check completed; Gradle sync succeeds on AGP 9.0.0. The 4 specific compile/test issues were pinpointed and catalogued in Phase 0.
2. `KeystoreSecretCipher`, `ScreenCaptureService`, `SnipTileService` and `requestAddSnipTile` follow the documented API shapes but were **never run**.
3. `AnthropicProvider` was **not** checked against current Anthropic docs (Gemini was; OpenAI-compatible follows the long-stable chat-completions format). Marked `TODO(verify)`.
4. `material-icons-extended` availability in the pinned BOM was not confirmed; AGP 9.0.0 chosen because Quail supports it, newer patch versions may exist.
5. Default model names were copied from the original app's fallback lists; confirm they exist for your account.
6. Android 14+ shows the screen-capture consent every time; Snip is inherently "tile → consent → select", slower than desktop.

---------------------------------------------------------------------------------------------------

## 8. Rules for whoever continues this project

1. Keep `:core` free of `android.*`. New logic that can be pure **must** live in `:core` with tests.
2. Features talk to `AiEngine` only; never call providers/HTTP from UI code.
3. Never hand-edit `DefaultPromptsData.kt` (generated).
4. UI follows `design/stitch/editorial_precision/DESIGN.md` and `docs/DESIGN_NOTES.md`: one cobalt accent for primary actions/selected states only,
   12 dp control corners, 28 dp sheet corners, Material icons (no emoji), 48 dp touch targets, no gradients/glow/blur, no cards inside cards.
   Monospace only for measurements/ids. **Never render invented mock text.**
5. Never put real API keys in code, tests, logs or the exported zip.
6. Prefer small, tested steps; run `:core:test` after every change; search `TODO` for the next task.

Agent playbook: **`AGENTS.md`** (first-session checklist for an in-IDE coding agent). Design previews: `app/.../designsystem/Previews.kt`.

See also: `CONCEPTS.md` (assignment rubric map), `docs/DESIGN_NOTES.md`, `docs/ORIGINAL_NOTES.md`, `reference/original-python/` (the original source for behaviour questions).

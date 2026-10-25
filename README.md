# AIPromptBridge for Android

[![Kotlin](https://img.shields.io/badge/Kotlin-2.3-blue.svg)](https://kotlinlang.org)
[![Android](https://img.shields.io/badge/Platform-Android%2011%2B%20(API%2030%2B)-green.svg)](https://android.com)
[![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose%20%2F%20Material%203-4285F4.svg)](https://developer.android.com/jetpack/compose)
[![Tests](https://img.shields.io/badge/Tests-152%20passed-success.svg)](https://github.com)
[![License](https://img.shields.io/badge/License-MIT-lightgrey.svg)](LICENSE)

An Android port of the desktop app [AIPromptBridge](https://github.com/zaxx-q/AIPromptBridge). It brings AI text editing and screen snipping into any mobile app using Android's native text selection menu (`ACTION_PROCESS_TEXT`) and a Quick Settings tile.

---

## Features

### Text editing in any app

Select text in Chrome, Gmail, Keep, WhatsApp, or any other app, open the selection toolbar, and choose **AIPromptBridge**:

<p align="center">
  <img src="docs/images/feature_text_edit.png" width="460" alt="Text Edit bottom sheet" />
</p>

A bottom sheet slides up over your current app with quick actions like Fix Grammar, Humanizer, Rewrite, Casual, and Summarize.

Changes appear as an inline word diff, with inserted text highlighted and removed text struck through. If you selected text inside an editable text box, tap **Replace selection** to paste the changes back in place. If the text was read-only, you can copy the result to your clipboard instead.

You can also tap the Tune button to toggle prompt modifiers (like Shorter, More Formal, or Indonesian translation) or type custom instructions in the prompt field.

---

### Interactive chat mode

If you want to iterate or ask questions about the text you highlighted, switch from Edit to Chat on the same sheet:

<p align="center">
  <img src="docs/images/feature_chat.png" width="460" alt="Chat mode" />
</p>

The original text stays visible in a collapsible pill at the top and is attached to the conversation context automatically. You can ask for variations, request specific tone adjustments, and copy or replace text directly from individual assistant messages.

---

### Snip and ask

Capture part of your screen and analyze it with multimodal models using a Quick Settings tile:

<p align="center">
  <img src="docs/images/feature_snip_selection.png" width="360" alt="Region crop selector" />
  &nbsp;&nbsp;&nbsp;&nbsp;
  <img src="docs/images/feature_snip.png" width="360" alt="Snip action sheet" />
</p>

1. Tap the **Snip & Ask** tile in your notification shade from any screen.
2. The foreground capture service grabs the display without capturing the notification shade or consent prompt.
3. Drag a box over what you want to inspect (live pixel dimensions update as you drag) or tap **Full screen**.
4. In the action sheet, pick an action like Quick Extract (OCR), Smart Extract, or To Markdown, or type a custom question about the image.
5. Choose where to send the answer: show it in a chat sheet, copy it to the clipboard, or share it to another app.

If you continue in chat, the cropped screenshot remains attached to the conversation so you can ask follow-up questions about the image.

---

### Prompt and modifier editor

The app includes an editor to customize prompts and organize your action bar:

<p align="center">
  <img src="docs/images/feature_prompt_editor.png" width="380" alt="Actions and prompt editor" />
</p>

- Reorder actions on the one-tap bar using accessible up and down controls.
- Edit system prompts, task instructions, and icons for built-in or custom actions.
- Adjust modifier instructions (such as target languages for translation).
- When the app updates its default prompt library, a three-way merge keeps your edits intact while bringing in new defaults.

---

### Settings and security

<p align="center">
  <img src="docs/images/settings_screen.png" width="380" alt="Settings screen" />
</p>

- **Supported providers**: Google Gemini, OpenAI, Anthropic Claude, OpenRouter, xAI (Grok), Mistral, Cohere, and custom OpenAI-compatible endpoints.
- **Key rotation**: Add multiple API keys for any provider. If a key hits a 429, 401, or 403 error, the app rotates to the next key automatically.
- **Encrypted storage**: Keys are encrypted at rest using Android Keystore AES-GCM authenticated encryption. They are excluded from cloud backups (`allowBackup="false"`) and never printed to logcat.
- **Theming**: Supports system, light, and dark modes based on Material 3.

---

## Architecture

The project is split into two modules:

- **`:core`** (Pure Kotlin / JVM): Providers, retry and rotation loops, key pools, profile resolution, prompt composition, LCS word diffing, crop math, and storage. It has no Android dependencies and runs 152 unit tests on the desktop JVM.
- **`:app`** (Android / Jetpack Compose): Compose UI, system integrations (`ACTION_PROCESS_TEXT`, Quick Settings tile, `MediaProjection` foreground service), navigation, and Keystore encryption.

```
core/ (Pure Kotlin/JVM)               app/ (Android Compose)
  ├── provider/ (Gemini, OpenAI...)     ├── feature/textedit/ (ProcessTextActivity, sheet)
  ├── prompt/   (Catalog, Composer)     ├── feature/snip/     (Tile, SnipActivity, crop)
  ├── keys/     (KeyPool, rotation)     ├── feature/settings/ (Settings, profiles, keys)
  ├── profile/  (Profiles, resolver)    ├── feature/prompteditor/ (Actions, modifiers)
  ├── diff/     (WordDiff LCS)          ├── designsystem/     (Theme, tokens, components)
  └── engine/   (AiEngine facade)       └── data/             (Keystore cipher, transport)
```

See [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md) for architectural notes, request lifecycles, and future stretch items.

---

## Getting started

### Requirements
- Android Studio Quail (2026.1+) or newer
- JDK 17 or higher
- Android SDK Platform 37 (`compileSdk = 37`, `minSdk = 30`, `targetSdk = 36`)
- Android device or emulator running Android 11+ (API 30+)

### Building and running

```bash
# Run JVM unit tests
./gradlew :core:test

# Build the debug APK
./gradlew :app:assembleDebug

# Build the signed release APK
./gradlew :app:assembleRelease

# Install on a connected device
./gradlew :app:installDebug
```

Release builds automatically sign the output APK using credentials from `keystore.properties` (or environment variables) if present, or the debug keystore as a fallback.

### Initial setup

1. Open **AIPromptBridge** from the launcher.
2. Tap **API keys**, select your provider (for example Google Gemini or OpenAI), and paste your key.
3. In **Connection profiles**, verify your active model.
4. Highlight text in any app, tap **AIPromptBridge** in the context menu, and test an action like **Fix Grammar**.
5. In Settings, tap **Add** next to Quick Settings tile, then pull down your notification shade to test **Snip & Ask**.

---

## Technical documentation

- [docs/DEVELOPMENT.md](docs/DEVELOPMENT.md): Design decisions, request pipelines, verification checklists, and stretch tasks.
- [docs/DESIGN_NOTES.md](docs/DESIGN_NOTES.md): Design system tokens, color palettes, and contrast checks.
- [docs/ORIGINAL_NOTES.md](docs/ORIGINAL_NOTES.md): Background notes from the original Python desktop application.

---

## License

This project is licensed under the [MIT License](LICENSE).

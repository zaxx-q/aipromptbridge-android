# Decisions carried over from the original Python app

Decisions ported from the upstream desktop application architecture (https://github.com/zaxx-q/AIPromptBridge).

1. **Template-method providers** — `BaseProvider.generate()` owns retry/key rotation; subclasses implement only the request/response mapping.
   Retry table: 429/401/402/403 rotate the key immediately; 5xx waits 5 s on the same key; empty response rotates + 2 s; network error 1 s.
   (Ported in `AiProvider.kt`; deviations: single key + 401/402/403 fails fast; single key + 429 waits 2 s.)
2. **Registry-driven providers** — adding a provider = one `ProviderDefinition` entry (`ProviderRegistry.all`).
3. **Self-contained connection profiles** resolved as: session → action → active profile → hard-coded fallback (`ProfileResolver`).
4. **Prompt architecture** — an *action* has `systemPrompt`, `task`, `promptType` (edit/general) and `showInChat`. Message assembly:
   `SYSTEM = systemPrompt [+ modifier injections]`; `USER = join("\n\n", [task, outputRules]) + delimiter + text + closeDelimiter`.
   Output rules differ for edit vs general. Special actions `_custom` (free-text change) and `_ask` (question) use task templates with `{custom_input}`.
5. **Defaults merge** — default actions carry `isDefault`; user edits flip it; `deletedDefaults` / `deletedGroups` / `deletedGroupItems` stop resurrection;
   user settings are protected by `modifiedSettings` (`PromptMerge`).
6. **Modifiers are a separate concept from actions** (toggle injections like Shorter/Formal/Variations; some force the result to be shown instead of replacing text).
7. **Sessions** record their origin (`textedit:proofread`), may carry a profile *name* override, never provider details; the first message gets a `[Task: …]` marker.
8. **Response-mode hierarchy** — explicit mode > modifier forces chat > action's `showInChat` > prompt type (`PromptComposer.resolveDestination`).
9. **Gemma 1/2/3** lack `systemInstruction`: system text is prepended to the first user turn. Gemini `thought: true` parts are skipped.
10. **Abort** — Python `abort_event` ⇒ coroutine cancellation (cancelling disconnects the HTTP connection).
11. **Inline thinking** blocks (`<think>…</think>`) are stripped from OpenAI-compatible responses (`InlineThinking`).

## Intentionally not ported (yet)
Global hotkeys, tray, clipboard hooks, desktop screen-snip tooling, loopback audio, Flask server, self-updater, "compare" actions (need two inputs),
Audio and TTS tools, file processor, 9 desktop themes. See README "How to add a future feature".

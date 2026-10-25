# Design notes (Design system and implementation)

The design system implementation is centralized in `app/src/main/kotlin/com/aipromptbridge/designsystem/` (`Color.kt`, `Type.kt`, `Shape.kt`, `Theme.kt`, `AipbIcons.kt`, `Components.kt`, `ListComponents.kt`).

Direction: **"copy editor's blue pencil"**: calm, precise, quick. Cool paper-tinted neutrals, near-navy ink (never pure black), ONE cobalt accent (`#2447E0` light / `#8EA3FF` dark) used only for primary actions, selected states, and focus. Insertions = highlighter yellow, deletions = struck muted red.

## Screen implementation status
| Feature area | Implemented in | Status |
|---|---|---|
| Text Edit (idle selection) | `feature/textedit/TextEditSheet.kt` (`SelectionPreview`, `ActionBar`) | done, verified on device |
| Text Edit (loading & result) | `LoadingBlock`, `ResultBlock` (with read-only copy fallback) | done, verified on device |
| Chat mode (expanded conversation) | `feature/textedit/ChatViewModel.kt`, `TextEditSheet.kt` | done, verified on device |
| Dark theme | `DarkColors` in `designsystem/Color.kt` | done, verified on device |
| Screen snip (region selector) | `feature/snip/RegionSelector.kt`, `SnipActivity.kt` | done, verified on device |
| Snip action sheet | `feature/snip/SnipSheet.kt` | done, verified on device |
| Settings & security | `feature/settings/SettingsScreen.kt`, `ProfilesScreen.kt`, `KeysScreen.kt` | done, verified on device |
| Actions & prompt editor | `feature/prompteditor/ActionsScreen.kt` | done, verified on device |
| Action editor | `feature/prompteditor/ActionEditScreen.kt` (custom icon picker, system prompt) | done, verified on device |
| Modifiers editor | `feature/prompteditor/ModifiersScreen.kt` | done, verified on device |

## Specific non-standard elements omitted from implementation
- Fake mock status text ("Region #04", "Buffered", "Selection area: 68% of screen", "Jinja2 Ready", "Engine Ready", "ANDROID 14 IPC BINDER", "Tone Calibration" timestamp).
- Raw icon id shown as a text label (`auto_fix_high`): replaced with a real icon picker.
- Account avatar in app bars (the app does not use accounts).
- Generic AI sparkle icons in the composer: neutral or action-specific icons are used instead.
- The snip thumbnail displays the real cropped bitmap rather than a static placeholder illustration.
- Resolved layout wrapping bugs from mockups (profile chip labels and action button layouts fit single rows without awkward truncation).

## Completed design extensions
- Read-only selection result handling (primary action switches to "Copy" when the target text field is non-editable).
- Error state feedback (implemented via `ResultViews.kt` `ErrorPanel`).
- API keys management, connection profile CRUD, Quick Settings tile flow, and system screen capture consent handling.
- Snip "Continue in chat" transition (passes the cropped screenshot bitmap into a multi-turn chat session).

## Rules
12 dp corners on controls, 28 dp top corners on sheets, 48 dp touch targets, type scale 24/20/16/14, Roboto (monospace only for pixel sizes / model ids),
Material icons (no emoji — the original desktop app used emoji icons; they are mapped to icon ids by `tools/generate_default_prompts.py`),
no gradients/glow/blur, no cards inside cards.

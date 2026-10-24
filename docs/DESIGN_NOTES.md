# Design notes (Stitch export → implementation)

Source of truth: `design/stitch/editorial_precision/DESIGN.md` (tokens) and the screenshots in `design/stitch/*/screen.png`
(`code.html` next to each is Tailwind HTML for reference only — it cannot be converted to Compose automatically).

Direction: **"copy editor's blue pencil"** — calm, precise, quick. Cool paper-tinted neutrals, near-navy ink (never pure black), ONE cobalt accent
(`#2447E0` light / `#8EA3FF` dark) used only for primary actions, selected states and focus. Insertions = highlighter yellow, deletions = struck muted red.

## Screen ↔ implementation status
| Stitch screen (folder) | Implemented in | Status |
|---|---|---|
| `aipromptbridge_textedit_bottom_sheet` (idle) | `feature/textedit/TextEditSheet.kt` (`SelectionPreview`, `ActionBar`) | done |
| `…_textedit_state_a_loading` / `…_state_b_done` | `LoadingBlock`, `ResultBlock` (+ read-only variant) | done |
| `…_textedit_chat_mode_expanded` | `ChatModePlaceholder` | **TODO Phase 4** |
| `…_textedit_bottom_sheet_dark` | `DarkColors` in `designsystem/Color.kt` | tokens done, needs device check |
| `aipromptbridge_screen_snip_region_selection` | `feature/snip/RegionSelector.kt` | done, unverified on device |
| `aipromptbridge_snip_action_bottom_sheet` | `feature/snip/SnipSheet.kt` | done, unverified |
| `aipromptbridge_settings` | `feature/settings/SettingsScreen.kt` (+ `ProfilesScreen`, `KeysScreen`) | written, uncompiled; floating-button & streaming rows intentionally omitted |
| `aipromptbridge_actions_prompt_editor` | `feature/prompteditor/ActionsScreen.kt` | written, uncompiled; arrows instead of drag handles |
| `aipromptbridge_edit_action_fix_grammar` | `feature/prompteditor/ActionEditScreen.kt` | written, uncompiled; real icon picker, shows the real action id |
| *(no Stitch screen)* modifiers list + edit | `feature/prompteditor/ModifiersScreen.kt` | written, uncompiled; reuses the Actions list / Edit Action layout language |

## Things in the mockups that must NOT be built (invented / wrong)
- Fake status text: "Region #04", "Buffered", "Selection area: 68% of screen", "Jinja2 Ready", "Engine Ready · v2.4.12 · 42m…",
  "ANDROID 14 IPC BINDER · ENFORCED", "ID: act_fg_09a", "Delivered", "Tone Calibration" timestamp in chat.
- Raw icon id shown as a label (`auto_fix_high`) → build a real icon picker.
- Account avatar in app bars (the app has no accounts).
- Sparkle icon in the composer (generic "AI" cliché) → use a neutral icon or none.
- The snip thumbnail in the mockup is a placeholder illustration → the implementation shows the **real cropped bitmap**.
- Layout bugs: profile chip wrapping ("flash-8b" breaking onto a second line → single line + ellipsis), snip buttons wrapping to two lines.

## Design gaps still to design (when you reach them)
- Read-only selection result (implemented as "Copy" primary; no mockup).
- Error states (no API key, rate limited, offline) — implemented minimally as `ErrorPanel`.
- API keys screen, connection profile list/edit, first-run explainer for screen-capture consent, Quick Settings tile onboarding.
- Snip "Continue in chat" hand-off: implemented without a mockup as a second body of the same sheet (image thumbnail + "The image is sent with every message." above the shared chat list); needs a design review.

## Rules
12 dp corners on controls, 28 dp top corners on sheets, 48 dp touch targets, type scale 24/20/16/14, Roboto (monospace only for pixel sizes / model ids),
Material icons (no emoji — the original desktop app used emoji icons; they are mapped to icon ids by `tools/generate_default_prompts.py`),
no gradients/glow/blur, no cards inside cards.

---
name: Editorial Precision
colors:
  surface: '#faf8ff'
  surface-dim: '#d4d9f4'
  surface-bright: '#faf8ff'
  surface-container-lowest: '#ffffff'
  surface-container-low: '#f3f3ff'
  surface-container: '#ebedff'
  surface-container-high: '#e3e7ff'
  surface-container-highest: '#dce1fd'
  on-surface: '#151b2f'
  on-surface-variant: '#444655'
  inverse-surface: '#2a3045'
  inverse-on-surface: '#eff0ff'
  outline: '#747687'
  outline-variant: '#c4c5d8'
  surface-tint: '#2a4ce4'
  primary: '#002dbd'
  on-primary: '#ffffff'
  primary-container: '#2447e0'
  on-primary-container: '#c9cfff'
  inverse-primary: '#bac3ff'
  secondary: '#545e7a'
  on-secondary: '#ffffff'
  secondary-container: '#d2dcfe'
  on-secondary-container: '#56607d'
  tertiary: '#7e2300'
  on-tertiary: '#ffffff'
  tertiary-container: '#a63100'
  on-tertiary-container: '#ffc5b4'
  error: '#ba1a1a'
  on-error: '#ffffff'
  error-container: '#ffdad6'
  on-error-container: '#93000a'
  primary-fixed: '#dee0ff'
  primary-fixed-dim: '#bac3ff'
  on-primary-fixed: '#00115a'
  on-primary-fixed-variant: '#0030c7'
  secondary-fixed: '#dae2ff'
  secondary-fixed-dim: '#bcc6e7'
  on-secondary-fixed: '#101b34'
  on-secondary-fixed-variant: '#3c4661'
  tertiary-fixed: '#ffdbd0'
  tertiary-fixed-dim: '#ffb59e'
  on-tertiary-fixed: '#390b00'
  on-tertiary-fixed-variant: '#842500'
  background: '#faf8ff'
  on-background: '#151b2f'
  surface-variant: '#dce1fd'
typography:
  headline-lg:
    fontFamily: Roboto Flex
    fontSize: 24px
    fontWeight: '600'
    lineHeight: 32px
    letterSpacing: -0.01em
  headline-md:
    fontFamily: Roboto Flex
    fontSize: 20px
    fontWeight: '600'
    lineHeight: 28px
    letterSpacing: -0.005em
  body-md:
    fontFamily: Roboto Flex
    fontSize: 16px
    fontWeight: '400'
    lineHeight: 24px
    letterSpacing: 0em
  body-sm:
    fontFamily: Roboto Flex
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0.01em
  label-md:
    fontFamily: Roboto Flex
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
    letterSpacing: 0.015em
  label-sm:
    fontFamily: Roboto Flex
    fontSize: 14px
    fontWeight: '500'
    lineHeight: 20px
    letterSpacing: 0.02em
  mono-readout:
    fontFamily: JetBrains Mono
    fontSize: 14px
    fontWeight: '400'
    lineHeight: 20px
    letterSpacing: 0em
rounded:
  sm: 0.25rem
  DEFAULT: 0.5rem
  md: 0.75rem
  lg: 1rem
  xl: 1.5rem
  full: 9999px
spacing:
  gutter: 1rem
  margin: 1rem
  space-xs: 0.25rem
  space-sm: 0.5rem
  space-md: 0.75rem
  space-lg: 1rem
  space-xl: 1.5rem
---

## Brand & Style

This design system delivers the disciplined authority of an expert editor's desk directly into the Android OS layer. It is built strictly for utility, speed, and cognitive clarity, operating as an ambient text assistant overlay that appears, rectifies, and withdraws without friction. 

The aesthetic is anchored in Utilitarian Precision and native Android Material 3 mechanics. It avoids decorative tech tropes: no iridescent gradients, no pulsing or glowing states, and zero anthropomorphic chatbot tropes. Visual hierarchy is established through meticulous typographic contrast, structural 1px borders, and deliberate, sparing accents. The interaction evokes handling high-grade archival paper marked with an ultra-precise cobalt drafting pen.

## Colors

The palette is engineered around clean paper tones and dense ink contrasts to maximize rapid scanability under variable lighting conditions.

- **Background Canvas (`#F4F6FA`):** A cool, tinted paper neutral that recedes underneath the active system interface.
- **Sheet Surface (`#FFFFFF`):** High-clarity white reserved for the active bottom sheet overlay, delivering immediate separation from the underlying app.
- **Secondary Surfaces & Chips (`#E8ECF5`):** A muted slate tint for non-interactive structural panels, unselected action chips, and nested containers.
- **Structural Borders (`#CBD3E3`):** Constant 1px architectural lines defining container edges and element delineations.
- **Primary Ink (`#141A2E`):** Saturated ink navy used for all primary reading content and titles; pure `#000000` is strictly prohibited.
- **Secondary Ink (`#4A5470`):** Low-strain slate for structural labels, supporting meta-information, and secondary controls.
- **Cobalt Accent (`#2447E0`):** Reserved exclusively for functional states: primary commit buttons, selected chips, radio/checkbox fills, focus indicators, and text caret cursors. It is never used decoratively.
- **Editorial Proofing Marks:**
  - Inserted/Proposed Text: Pale highlighter yellow fill (`#FFF1A8`) with ink navy text (`#141A2E`).
  - Deleted Text: Struck through in muted editorial red (`#B3261E`).
  - Confirmation / Success: Controlled botanical green (`#1E7B4F`).

## Typography

The typographic hierarchy strictly conforms to four scale steps: **24px, 20px, 16px, and 14px**. No intermediary or fractional sizes are permitted.

- **Primary Stack:** `Roboto Flex` is deployed across all primary titles, narrative prose, and operational labels. Body copy resides exclusively at `16px/24px` for optimal parsing speed during active typing sessions. Labels and interactive controls utilize `14px Medium`.
- **Monospace Readouts:** Numeric values, parameter counters, token budgets, and model identifiers route to `JetBrains Mono` or `Roboto Mono` at `14px`. This prevents layout shifts during rapid numerical updates.
- **Editorial Glyphs:** Inline editorial differences (diffs) maintain standard body line-heights without introducing vertical metrics imbalance. Strikethrough bars must match the red mark token `#B3261E` with a constant stroke thickness of 1.5px.

## Layout & Spacing

Layout geometry follows an 8dp grid rhythm, compressed to 4dp increments for dense operational panels and chip groups.

- **Overlay Bounds:** The system executes as a bottom sheet overlay adhering strictly to Android safe insets. Horizontal margin on mobile portrait is fixed to `16px` (`margin: 1rem`) with the bottom sheet expanding to `100%` width docked at the screen floor. On tablets and foldables (viewport width > 600dp), the overlay settles into a centered modal anchor with a strict maximum width of `560px`.
- **Touch Target Integrity:** Regardless of visual component boundaries (e.g., small 32px chips or icon buttons), the interactive touch target area is physically enforced at a minimum of `48px × 48px` using internal touch delegate padding.
- **Rhythm Rules:** Vertical stacking within content groups uses `space-sm` (8px). Spacing between decoupled logical groups (e.g., diff pane to chip suggestions) relies on `space-lg` (16px).

## Elevation & Depth

This system intentionally eliminates artificial skeuomorphic depth, glow states, and blurred backdrop filters. Hierarchy is created through crisp surface transitions and low-opacity ambient boundaries.

- **Surface Layering:** The primary canvas (`#F4F6FA`) supports the sheet overlay (`#FFFFFF`), bounded by a singular 1px structural stroke (`#CBD3E3`). Secondary functional surfaces (`#E8ECF5`) sit flat inside the main sheet; nesting cards within cards is prohibited.
- **Sheet Elevation:** The bottom sheet is the sole element permitted a cast shadow, rendered as an understated ambient dispersion: `box-shadow: 0px -4px 24px rgba(20, 26, 46, 0.06), 0px -1px 2px rgba(20, 26, 46, 0.04)`.
- **Overlay Scrim:** When invoked over third-party applications, the backdrop is dampened using a flat neutral tint: `rgba(20, 26, 46, 0.32)`. No background blur or saturation shifts are applied to underlying host interfaces.

## Shapes

The geometric taxonomy is strictly divided between the master container and internal interactive components:

- **Bottom Sheet Container:** The top-left and top-right vertices feature a `28px` corner radius (`rounded-t-[28px]`). The bottom corners remain unrounded (`0px`) when docked to the system navigation bar.
- **Interactive Units:** Input containers, action chips, buttons, and state notification badges strictly share a uniform `12px` radius (`rounded-[12px]`).
- **Internal Structural Panels:** Inner diff comparison blocks and metadata zones adopt an identical `12px` radius.
- **Borders:** A non-negotiable `1px solid #CBD3E3` stroke outlines all interactive nodes, input states, and unselected chips.

## Components

### Buttons
- **Primary Action:** Background `#2447E0`, text `#FFFFFF`, radius `12px`, height `48px`, horizontal padding `20px`. Typography: `14px Medium`. No drop shadows; hover/pressed states map to a dark blue overlay (`#1B36A8`).
- **Secondary Action:** Background `#E8ECF5`, border `1px solid #CBD3E3`, text `#141A2E`, radius `12px`, height `48px`, horizontal padding `20px`.
- **Text / Tertiary Button:** Transparent fill, zero border, text `#4A5470`, hover/focus text `#141A2E`, height `48px`, minimum horizontal target `48px`.

### Suggestion & Filter Chips
- **Unselected:** Fill `#E8ECF5`, border `1px solid #CBD3E3`, text `#141A2E`, height `36px`, radius `12px`, padding `8px 14px`. Bound within a `48px` minimum touch target.
- **Selected:** Fill `#2447E0`, border `1px solid #2447E0`, text `#FFFFFF`. Leading icons rendered in matching white.

### Diff Display Pane (Proofing Module)
- Contained within a flat panel with fill `#FFFFFF`, border `1px solid #CBD3E3`, radius `12px`, padding `16px`.
- Inserted text wraps in inline badge fills: `#FFF1A8`, ink `#141A2E`, radius `2px`.
- Deleted text is displayed inline with text color `#B3261E` and a persistent `1.5px` strikethrough.
- Text selection handles, carets, and bounding indicators strictly use `#2447E0`.

### Input Fields
- Fill `#FFFFFF`, border `1px solid #CBD3E3`, radius `12px`, min-height `48px`, padding `12px 16px`.
- Text: `16px Regular` (`#141A2E`). Placeholder: `16px Regular` (`#4A5470`).
- Focused State: Border changes to `1px solid #2447E0` with a co-centric `1px` inner rule (total `2px` visual blue boundary). No soft glow or ambient drop shadow.

### Checkboxes & Selection Controls
- Unselected: Fill `#FFFFFF`, border `1px solid #CBD3E3`, radius `4px`, dimensions `20px × 20px`.
- Selected: Fill `#2447E0`, border `1px solid #2447E0`, white checkmark (`2px` stroke).
- Overall target area matches the mandatory `48px × 48px` bounding footprint.

### Icons
- Strictly Material Symbols Outlined, weight `400`, size `24px × 24px`, tint `#4A5470` for utility and `#2447E0` when bound to active selection. Emoji glyphs are prohibited in UI labels and controls.
# Changelog

All notable changes to this project will be documented in this file.
The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [0.1.1] - 2026-10-25

### Performance

- **APK Size Reduction (R8 Shrinking)**: Enabled R8 minification and resource shrinking for release builds, reducing the release APK size from ~45 MB to ~2.9 MB (~93.5% reduction) by stripping unused classes from `material-icons-extended` and unused Compose/Kotlin runtime code.
- **R8 ProGuard Rules**: Added rules preserving Kotlin Serialization reflection metadata for `@Serializable` entities.

## [0.1.0] - 2026-10-25

### Initial Release

- **System Text Processing**: Native Android `ACTION_PROCESS_TEXT` integration opening a bottom sheet to run prompt actions on selected text in any app.
- **Screen Snip & Vision**: Quick Settings tile and floating capture workflow with interactive cropping for OCR, visual question answering, and image analysis.
- **Multi-Provider AI Engine**: Built-in support for Google Gemini, OpenAI, and Anthropic Claude APIs with key rotation on rate limits and errors.
- **Prompt Catalog & Customization**: Customizable actions and modifiers with 3-way merge on app updates and local JSON import/export.
- **Encrypted Local Storage**: Encrypted API key storage backed by Android Keystore (AES-GCM).
- **Design System**: Material 3 UI with WCAG-audited contrast ratios, dark and light mode support.
- **Automated CI/CD**: GitHub Actions workflow for building and publishing signed release APKs.

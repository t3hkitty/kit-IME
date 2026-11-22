# Changelog 🐾

All notable changes to **Kit-IME** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to Semantic Versioning.

## [1.0.0-plump] - 2026-11-21

### Added ✨
- **Tri-Split Candidate Ribbon (`TriSplitRibbonView.kt`)**: High-Density Rounded Plumpitude candidate shell featuring:
  - Left Slot: Typo Rescue candidate (`[1]`)
  - Center Slot: Primary Prediction candidate (`[2]`) with 2-letter confidence greenlight styling
  - Right Slot: Punctuated / Contraction Variant candidate (`[3]`)
- **Zero Post-Commit Rewriting Core (`KitImeService.kt`)**: Strict non-destructive design guarantee. Never retroactively mutates, autocorrects, or flips already-committed words (`its` vs `it's`, plurals, or verbatim user inputs).
- **Physical Keyboard Router (`PhysicalKeyRouter.kt`)**:
  - Full hardware keyboard input handling without dropping ribbon visibility or breaking focus.
  - **Spacebar + Number (`1`–`5`) candidate selection**: Holding Spacebar + pressing `1`..`5` commits the targeted candidate and suppresses duplicate spaces. Normal typing is preserved when Spacebar is released.
- **Double-Check Before Send Guard**: Intercepts `EditorInfo.IME_ACTION_SEND` and presents an explicit confirmation bar (`Confirm 🐾` or `Cancel ✕`, bound to hardware Enter/Escape).
- **Soft Spelling "Bzzt" Haptic (`SoftBzztHaptic.kt`)**: Subtle 18ms pulse at amplitude 45 (`VibrationEffect.createOneShot(18L, 45)`) on word boundary typos without changing text.
- **Prediction / Personalization DB Bridge (`PredictionDbBridge.kt`)**: Explicit **TBD** modular interfaces (`CandidateProvider`, `ConfirmationSink`, `SnapshotBridge`) ready for dual-DB `corpus.db` + `usage.db` + typo confusion integration.
- **Sticky Preferences & Settings UI (`KitImePreferences.kt`, `KitImeSettingsActivity.kt`)**: Real-time preference toggles for chording, greenlighting, soft bzzt, and pre-send confirmation.
- **Unit Test Suite (`KitImeCoreTest.kt`)**: Verification of hardware chording, normal number typing, 2-letter greenlight hooks, double-check actions, and typo detection.
- **Local / VPS Deployment Sync Script (`scripts/deploy_sync.ps1`)**: Automated build, lint, and packaging pipeline.

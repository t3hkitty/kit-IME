# Changelog 🐾

All notable changes to **Kit-IME** will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to Semantic Versioning.

## [1.3.0-plump] - 2026-11-21

### Added ✨
- **Gboard-Style Soft Keyboard & Quick Actions Bar (`view_ime_panel.xml`)**:
  - Quick action toolbar with buttons for **Arrows ⤹**, **🌸 Emoji**, and **📋 Shelf**.
  - **Themes (`ThemeManager.kt`)**: Instant selectable themes persisted in preferences:
    - 🌸 Kawaiian Plumpitude (Default Deep Indigo & Soft Lavender)
    - 🌙 Crisp Dark Slate
    - ☀️ Soft Light Lavender
    - ⚡ High Contrast Amber
  - **Emoji & Symbol Bar (`EmojiSymbolDrawer.kt`)**: Drawer with quick favorite emojis (`🐾`, `🌸`, `✨`, `💜`, `🐱`...) and secondary symbols (`@`, `#`, `$`, `%`...) with verbatim commit.
  - **Long-Press / Hold-Press Popups (`LongPressPopupView.kt`)**: Hold-press on keys to pop alternate character/diacritic selectors (`3`, `é`, `è`, `ê` for `e`, numbers for top row, punctuation for periods/commas) with zero text mutation.
- **Pop-Up Arrow Keys Pad (`ArrowPadPopup.kt`)**:
  - Compact directional arrow pad (`Up`, `Down`, `Left`, `Right`, `Home`, `End`, `Backspace`, `Enter`) sending `KeyEvent.KEYCODE_DPAD_*` for rapid cursor stepping through long texts and drafts.
- **Clipboard History Shelf & Pinned Clips (`ClipboardShelfManager.kt`, `ClipboardShelfPopup.kt`)**:
  - Local clipboard history ring with **Pin / Unpin (★/☆)** support, keeping pinned clips permanently at top for quick verbatim paste.
- **Google Keep Vault Importer (`KeepVaultImporter.kt`)**:
  - Safe Keep import gated strictly on user-configured notes folder / SAF vault (`meow-vault`). Gated with user prompt if no vault is selected. Strips HTML tags cleanly from `.html`, `.txt`, and `.md` Keep exports and ingests vocabulary into the local database with pre-sync snapshots.
- **Power User Control Hub (`KitImeSettingsActivity.kt`)**:
  - Added theme selection spinner and Keep Vault Import action button.

## [1.2.0-plump] - 2026-11-21

### Added ✨
- **Remote Google Docs & Apps Script Corpus Fetcher (`GoogleDocsCorpusFetcher.kt`)**:
  - Direct HTTPS streaming ingestion for Google Doc URLs (normalized to `/export?format=txt`) and Google Apps Script Web App manifests (`?token=...` & `?token=...&part=N`).
  - Follows HTTP redirects (e.g. `googleusercontent.com` download nodes) line-by-line using buffered readers to prevent mobile memory spikes.
  - Per-source ETag and MD5 content hashing tracked in `sync_cursors` to skip unchanged documents.
  - Pre-sync `VACUUM INTO` snapshot automatic triggers before fetching remote documents.
- **Settings UI Google Docs Integration (`KitImeSettingsActivity.kt`, `activity_settings.xml`)**:
  - Multi-line text field in Settings backed by `KitImePreferences.googleDocUrls` for persistent URL lists.
  - **"Sync Google Docs Corpus Now 📄"** button triggering instant snapshot creation, crawl, WAL checkpoint, and status feedback.
- **Enhanced Document Scanner**:
  - Expanded SAF scanner to ingest `.epub` files alongside `.md` and `.txt`.

## [1.1.0-plump] - 2026-11-21

### Added ✨
- **Dual-DB & Typo Schema (`PredictorDatabase.kt`)**:
  - SQLite WAL mode (`journal_mode = WAL`, `synchronous = NORMAL`).
  - Tables: `corpus_words`, `usage_words`, `bigrams`, `typo_confusion`, `pending_firebase_deltas`, `sync_cursors`.
  - Rolling 5-day snapshot retention with `VACUUM INTO` and integrity verification.
- **Log-Compressed Scoring Engine (`KitPredictionEngine.kt`)**:
  - Formula: `w_corpus * ln(1 + corpus_freq) + w_usage * ln(1 + confirmed_freq) + w_ctx * ln(1 + bigram_freq) + w_pos * pos_match + w_custom * is_custom + w_agree * agreement_bonus`.
  - QWERTY neighbor slip scoring matrix (0.4 vs 1.0 penalty).
- **Local Device Data Gatherer (`LocalCorpusGatherer.kt`, `NightlyCorpusWorker.kt`)**:
  - Sent SMS crawler (`type = 2`) with URL and 2FA/OTP code stripping.
  - SAF notes directory reader with gentle corpus decay (`0.985`) and boost clamping (`<= 25`).
- **Android UserDictionary Sync (`UserDictionarySyncer.kt`)**:
  - Synchronizes vocabulary into `android.provider.UserDictionary.Words`.

## [1.0.0-plump] - 2026-11-21

### Added ✨
- **Tri-Split Candidate Ribbon (`TriSplitRibbonView.kt`)**: Left Typo Rescue (`[1]`), Center Primary (`[2]`) with 2-letter greenlight, Right Variant (`[3]`).
- **Zero Post-Commit Rewriting Core (`KitImeService.kt`)**: Strict non-destructive design guarantee.
- **Physical Key Router (`PhysicalKeyRouter.kt`)**: Spacebar + Number (`1`–`5`) chording.
- **Double-Check Before Send Guard**: Pre-send preview confirmation bar.
- **Soft Spelling "Bzzt" Haptic (`SoftBzztHaptic.kt`)**: 18ms pulse on word boundary typo.

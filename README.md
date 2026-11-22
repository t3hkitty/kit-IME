```
   /\_/\  
  ( o.o )  KIT-IME 🐾 [Plumpitude Engine v3.1]
   > ^ <   Full Daily Driver • OnePlus 11 5G
```

# Kit-IME (Power-User Gboard-Class Input Method)

**Kit-IME** is an Android input method engineered for high-speed physical and virtual typing on your phone, prioritizing tactile predictability, **zero post-commit text rewriting**, and 100% on-device corpus building.

It combines power-user Gboard essentials (Themes, Arrow Keys Pad, Emojis, Clipboard Shelf, Diacritics) with the safety of non-destructive typing and Google Docs/Keep personal vault synchronization.

---

## 🌸 Key Architectural Pillars

### 1. Zero Post-Commit Rewriting Guarantee
- Words committed at a word boundary or selected from ribbon slots are **never** autocorrected, retroactively replaced, or mutated (`its` vs `it's`, jargon, or abbreviations remain verbatim). Commits are verbatim strings.

### 2. Gboard-Style Power-User Features
- **Themes (`ThemeManager.kt`):** Selectable themes (🌸 Kawaiian Plumpitude, 🌙 Crisp Dark Slate, ☀️ Soft Light Lavender, ⚡ High Contrast Amber) applied instantly across ribbon, keys, and popups.
- **Directional Arrow Keys Pad (`ArrowPadPopup.kt`):** Floating arrow pad (`Up`, `Down`, `Left`, `Right`, `Home`, `End`, `⌫`, `⏎`) sending `KeyEvent.KEYCODE_DPAD_*` for rapid navigation and cursor stepping through drafts.
- **Emoji & Symbol Bar (`EmojiSymbolDrawer.kt`):** Quick categorized favorite emojis (`🐾`, `🌸`, `✨`, `💜`, `🐱`...) and secondary symbols (`@`, `#`, `$`, `%`...) with verbatim commit.
- **Hold-Press Diacritics & Numbers (`LongPressPopupView.kt`):** Long-press on letters to pop alternate characters (`3`, `é`, `è`, `ê` for `e`, numbers for top row, punctuation for periods/commas) without losing focus.
- **Clipboard History Shelf (`ClipboardShelfManager.kt`, `ClipboardShelfPopup.kt`):** Local ring of recent clips with **Pin / Unpin (★/☆)** support so pinned snippets remain permanently at top.
- **Reliable `onEvaluateInputViewShown()`:** Ensures external or virtual input tools (such as KDE Connect Remote Input) never unexpectedly hide the soft keyboard.

### 3. Google Keep Vault Importer (`KeepVaultImporter.kt`)
- **Gated Safety:** Gated on a configured notes folder / SAF tree URI (`meow-vault` or exported Keep folder). Prompts user if no vault is selected.
- **Clean Scraping:** Parses `.html`, `.txt`, and `.md` Keep note exports, cleanly stripping HTML tags and entities, and ingesting vocabulary into the local database with pre-sync snapshots.

### 4. Dual-DB SQLite WAL Predictor (`PredictorDatabase.kt`, `KitPredictionEngine.kt`)
- Log-compressed scoring:
  $$\text{Score} = w_{\text{corpus}} \ln(1 + \text{corpus\_freq}) + w_{\text{usage}} \ln(1 + \text{confirmed\_freq}) + w_{\text{ctx}} \ln(1 + \text{bigram\_freq}) + w_{\text{pos}} \cdot \text{pos\_match} + w_{\text{custom}} \cdot \text{is\_custom} + w_{\text{agree}} \cdot \text{agreement\_bonus}$$
- QWERTY neighbor slip scoring matrix (0.4 vs 1.0 penalty).
- Pre-sync `VACUUM INTO` snapshots with 5-day rolling retention and one-tap restore.

### 5. Remote Google Docs & Apps Script Fetcher (`GoogleDocsCorpusFetcher.kt`)
- Normalizes Google Doc links to `/export?format=txt`, streams lines line-by-line via `HttpURLConnection`, and tracks ETags/MD5 content hashes to skip unchanged docs.

---

## 📲 In-App Control Hub (Kit-IME Settings)

- **Keyboard Theme Palette 🎨:** Instant theme switcher (Kawaii Purple, Dark Slate, Light Lavender, High Contrast).
- **Import Keep Notes Vault 📝:** One-tap Keep note import gated on SAF vault folder.
- **Remote Google Docs Sources:** Multi-line field for Google Doc URLs and Apps Script endpoints.
- **Sync Google Docs Corpus Now 📄:** Instant crawl with pre-sync snapshot and WAL checkpoint.
- **Grant Sent SMS Permission:** One-tap permission request to parse sent messages (`type = 2`).
- **Pick SAF Notes Folder:** Select any folder on storage (e.g., Markdown notes or `.epub` books).
- **Snapshot DB & Restore 📦:** Instant backup or recovery from one of the last 5 daily rolling snapshots.

```
   /\_/\  
  ( o.o )  KIT-IME 🐾 [Plumpitude Engine v3.1]
   > ^ <   100% On-Phone Standalone • OnePlus 11 5G
```

# Kit-IME (Pure On-Phone Personalization)

**Kit-IME** is an Android input method engineered for high-speed typing on your phone, prioritizing tactile predictability, **zero post-commit text rewriting**, and 100% on-device corpus building.

It requires **no desktop environment, python, or external server**. It operates natively on the phone and can ingest your personal vocabulary from **Google Docs / Google Drive** generated via Google Apps Script.

---

## 🌸 Native Phone Architecture

### 1. Zero Post-Commit Rewriting Guarantee
- Words committed at a word boundary or selected from ribbon slots are **never** autocorrected, retroactively replaced, or mutated (`its` vs `it's`, jargon, or abbreviations remain verbatim).

### 2. Dual-DB SQLite WAL Predictor (`PredictorDatabase.kt`, `KitPredictionEngine.kt`)
- Operates inside the app sandbox on SQLite WAL mode (`PRAGMA journal_mode=WAL; PRAGMA synchronous=NORMAL;`).
- Stores `corpus_words`, `usage_words`, `bigrams`, and `typo_confusion`.
- Log-compressed scoring:
  $$\text{Score} = w_{\text{corpus}} \ln(1 + \text{corpus\_freq}) + w_{\text{usage}} \ln(1 + \text{confirmed\_freq}) + w_{\text{ctx}} \ln(1 + \text{bigram\_freq}) + w_{\text{pos}} \cdot \text{pos\_match} + w_{\text{custom}} \cdot \text{is\_custom} + w_{\text{agree}} \cdot \text{agreement\_bonus}$$
- QWERTY neighbor slip scoring matrix (0.4 vs 1.0 penalty).

### 3. Remote Google Docs & Google Apps Script Corpus Fetcher (`GoogleDocsCorpusFetcher.kt`)
- **Direct Ingestion:** Fetches Google Doc URLs (`https://docs.google.com/document/d/<id>/...`) automatically normalized to `/export?format=txt`.
- **Apps Script Web App Support:** Fetches manifest URLs (`?token=...` returning `{ "parts": [...] }` and `?token=...&part=N` returning plain text).
- **Streaming & OOM Safety:** Streams lines with `HttpURLConnection` and buffered readers, following redirects (including `googleusercontent.com`).
- **ETag & Hash Skip:** Checks per-document ETag and MD5 content hashes against `sync_cursors` so unchanged documents are skipped.
- **Pre-Sync Snapshots:** Runs `VACUUM INTO` snapshots automatically before pulling remote documents.

### 4. Phone Data Gatherer & Syncer (`LocalCorpusGatherer.kt`, `UserDictionarySyncer.kt`)
- **Sent SMS Reader (`type = 2`):** Strips URLs and OTP codes. Never reads incoming texts.
- **SAF Directory Scanner:** Ingests `.md`, `.txt`, and `.epub` notes from local/synced storage.
- **Android UserDictionary Sync:** Injects your high-frequency & custom vocabulary into `android.provider.UserDictionary.Words` so stock apps recognize your words.
- **WorkManager Nightly Loop:** Automatically applies gentle corpus decay ($0.985$) and creates rolling 5-day WAL snapshots.

---

## 📲 In-App Control Dashboard (Kit-IME Settings)

- **Remote Google Docs / Apps Script Sources:** Multi-line field to paste doc URLs or web app endpoints.
- **Sync Google Docs Corpus Now 📄:** Instant pre-sync snapshot, fetch, hash check, and ingest.
- **Grant Sent SMS Permission:** One-tap permission request to parse sent messages.
- **Pick SAF Notes Folder:** Select any folder on storage (e.g., Markdown notes or `.epub` books).
- **Import Google Doc / File from Drive 📄:** Single-tap file selection from Google Drive or local storage.
- **Sync to Android UserDictionary:** Synchronize learned dictionary into Android system settings.
- **Run Gather Now:** Immediate manual crawl of all configured sources.
- **Restore DB Snapshot:** Instant recovery from one of the last 5 daily rolling backups.

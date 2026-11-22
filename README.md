```
   /\_/\  
  ( o.o )  KIT-IME 🐾 [Plumpitude Engine v3.1]
   > ^ <   Target: OnePlus 11 5G (NA Build)
```

# Kit-IME (Replacing kvmeowboard)

**Kit-IME** is an Android input method engineered for high-speed physical and virtual typing, prioritizing tactile predictability, zero accidental text corruption, and high-density plumpitude aesthetics.

---

## 🌸 Key Architectural Pillars

### 1. Tri-Split Ribbon Shell (`TriSplitRibbonView.kt`)
- **Left Slot (`[1]`):** Typo Rescue candidate.
- **Center Slot (`[2]`):** Primary Prediction candidate (with **2-letter greenlight** state hook when prefix confidence is achieved).
- **Right Slot (`[3]`):** Punctuated / Contraction Variant candidate (`it's`, `cats!`, etc.).
- **Extended Slots (`[4]`, `[5]`):** Quick punctuation & contextual expansions.

### 2. Zero Post-Commit Rewriting
- **Strict Guarantee:** Once text is committed at a word boundary or by candidate tap, it is **never** retroactively rewritten, autocorrected, or flipped (e.g., preserving exact user intent for `its` vs `it's`, plurals, or jargon). Commits are verbatim strings.

### 3. Physical Keyboard & Spacebar + Number Chording (`PhysicalKeyRouter.kt`)
- Intercepts hardware keyboard events without hiding candidate view or dropping input focus.
- **Spacebar + Number (`1`–`5`):**
  - Hold **Spacebar** and press number **`1`–`5`** to commit candidate slot 1–5 immediately.
  - Releases cleanly without extraneous spaces.
  - Normal number typing (`1`, `2`, `3`...) passes straight through when Spacebar is not held.

### 4. Soft Spelling "Bzzt" Haptic Feedback (`SoftBzztHaptic.kt`)
- Subtle tactile pulse (`VibrationEffect.createOneShot(18L, 45)`) when finishing a word boundary on an unrecognized token.
- Gives instant subconscious feedback **without** mutating the text.

### 5. Double-Check Before Send Guard
- Intercepts `EditorInfo.IME_ACTION_SEND` (or hardware Enter on send action) and surfaces an interactive preview confirmation bar before sending.
- Confirm via tap / hardware `Enter`, or dismiss via `Escape`.

### 6. Prediction & Personalization DB Layer (**TBD**)
- Clean Kotlin interfaces defined in `PredictionDbBridge.kt`:
  - `CandidateProvider`
  - `ConfirmationSink`
  - `SnapshotBridge`
- Marked with `TODO("TBD: Wire dual-DB corpus.db + usage.db + typo_confusion predictor in next phase")` for modular integration in the next milestone.

---

## 🛠️ Project Structure

```
kit-ime/
├── app/
│   ├── src/
│   │   ├── main/
│   │   │   ├── AndroidManifest.xml
│   │   │   ├── java/com/kitime/ime/
│   │   │   │   ├── bridge/
│   │   │   │   │   └── PredictionDbBridge.kt
│   │   │   │   ├── core/
│   │   │   │   │   ├── KitImeService.kt
│   │   │   │   │   └── PhysicalKeyRouter.kt
│   │   │   │   ├── haptic/
│   │   │   │   │   └── SoftBzztHaptic.kt
│   │   │   │   ├── pref/
│   │   │   │   │   └── KitImePreferences.kt
│   │   │   │   └── ui/
│   │   │   │       ├── KitImeSettingsActivity.kt
│   │   │   │       └── TriSplitRibbonView.kt
│   │   │   └── res/
│   │   └── test/java/com/kitime/ime/
│   │       └── KitImeCoreTest.kt
├── gradle/
│   ├── libs.versions.toml
│   └── wrapper/gradle-wrapper.properties
├── scripts/
│   └── deploy_sync.ps1
├── CHANGELOG.md
└── README.md
```

---

## 🚀 Building & Testing

```powershell
# Run unit tests
.\gradlew test

# Build debug APK
.\gradlew assembleDebug

# Deploy and install to connected device via ADB
.\scripts\deploy_sync.ps1 -InstallAdb
```

---

## ⚖️ License
Apache-2.0 / FOSS Permissive.

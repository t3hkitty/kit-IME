/**
 * 🐾 Kit-IME Google Apps Script: Corpus Generator for Google Drive & Docs 🐾
 *
 * How it works:
 * 1. Open Google Apps Script (https://script.google.com).
 * 2. Paste this code into your script editor (Code.gs).
 * 3. Set FOLDER_NAME or TARGET_DOC_IDS to specify which Docs to scan.
 * 4. Run `buildAndExportCorpusDoc()` manually or set an automated time-driven trigger (e.g. daily/weekly).
 * 5. This script scans Docs, sanitizes sensitive tokens (URLs, numbers, 2FA/OTP codes),
 *    calculates word frequencies and bigrams, and writes out a clean summary Doc / plain text export
 *    directly inside Google Drive: "Kit-IME-Corpus-Export.txt".
 * 6. On your Android phone:
 *    - Open Google Drive app -> mark "Kit-IME-Corpus-Export.txt" for offline or select folder via SAF in Kit-IME.
 *    - Or simply "Share" / "Send a copy" of the Doc/Text into Kit-IME via the Android Share Sheet!
 */

// Configuration: specify a Drive folder or specific Doc IDs to scan
const CONFIG = {
  // Folder containing your Google Docs to scan (leave empty to scan specific doc IDs)
  SOURCE_FOLDER_NAME: "Kit-IME-Docs",
  // Target output file in Google Drive
  OUTPUT_FILE_NAME: "Kit-IME-Corpus-Export.txt",
  // Minimum word frequency threshold to include
  MIN_WORD_FREQ: 2,
  // Per-word frequency boost clamp
  MAX_WORD_BOOST: 25
};

/**
 * Main entry point: Scans Docs in source folder and generates output file.
 */
function buildAndExportCorpusDoc() {
  Logger.log("🐾 [1/4] Starting Kit-IME Google Drive Corpus Harvest...");

  const wordMap = {}; // word -> { display, count, posStart, posMid, posEnd }
  const bigramMap = {}; // "prev_word->next_word" -> count

  const docs = getSourceDocs();
  Logger.log(`✨ Found ${docs.length} Google Doc(s) to process.`);

  docs.forEach((doc, idx) => {
    Logger.log(`🐾 Processing [${idx + 1}/${docs.length}]: ${doc.getName()}`);
    const text = doc.getBody().getText();
    processDocText(text, wordMap, bigramMap);
  });

  Logger.log("💜 [2/4] Formatting Corpus Payload for Android Kit-IME...");
  const exportPayload = generateExportPayload(wordMap, bigramMap);

  Logger.log("✨ [3/4] Writing output file to Google Drive...");
  savePayloadToDrive(exportPayload);

  Logger.log("🐾 [4/4] Done! Corpus file ready on Google Drive for Android ingestion.");
}

/**
 * Retrieves Docs from the designated folder or root.
 */
function getSourceDocs() {
  const docs = [];
  const folders = DriveApp.getFoldersByName(CONFIG.SOURCE_FOLDER_NAME);

  if (folders.hasNext()) {
    const folder = folders.next();
    const files = folder.getFilesByType(MimeType.GOOGLE_DOCS);
    while (files.hasNext()) {
      const file = files.next();
      docs.push(DocumentApp.openById(file.getId()));
    }
  } else {
    Logger.log(`⚠️ Folder "${CONFIG.SOURCE_FOLDER_NAME}" not found. Scanning root files...`);
    const files = DriveApp.getFilesByType(MimeType.GOOGLE_DOCS);
    let count = 0;
    while (files.hasNext() && count < 10) {
      const file = files.next();
      docs.push(DocumentApp.openById(file.getId()));
      count++;
    }
  }
  return docs;
}

/**
 * Sanitizes and extracts tokens and bigrams from document text.
 */
function processDocText(rawText, wordMap, bigramMap) {
  if (!rawText) return;

  // 1. Strip URLs and emails
  let text = rawText.replace(/https?:\/\/\S+|www\.\S+/gi, " ");
  text = text.replace(/[\w\.-]+@[\w\.-]+\.\w+/g, " ");

  // 2. Strip standalone 2FA/OTP numeric codes (4-8 digits)
  text = text.replace(/\b\d{4,8}\b/g, " ");

  // 3. Sentence split by punctuation (. ! ? \n)
  const sentences = text.split(/[.!?\n]+/);

  sentences.forEach(sentence => {
    // Delimit by non-word and non-apostrophe characters
    const rawTokens = sentence.split(/[^\p{L}\p{N}']+/u).filter(t => t.trim().length > 0);
    const tokens = rawTokens.filter(t => isNaN(t) && t.length <= 35);

    if (tokens.length === 0) return;

    for (let i = 0; i < tokens.length; i++) {
      const token = tokens[i];
      const lower = token.toLowerCase();

      if (!wordMap[lower]) {
        wordMap[lower] = {
          word: lower,
          display: token,
          count: 0,
          posStart: 0,
          posMid: 0,
          posEnd: 0
        };
      }

      const entry = wordMap[lower];
      entry.count += 1;
      if (entry.display === entry.display.toLowerCase() && token !== lower) {
        entry.display = token;
      }

      if (i === 0) entry.posStart += 1;
      else if (i === tokens.length - 1) entry.posEnd += 1;
      else entry.posMid += 1;

      if (i > 0) {
        const prevLower = tokens[i - 1].toLowerCase();
        const key = `${prevLower}->${lower}`;
        bigramMap[key] = (bigramMap[key] || 0) + 1;
      }
    }
  });
}

/**
 * Creates clean pipe-delimited flat-file payload readable by Kit-IME on Android.
 */
function generateExportPayload(wordMap, bigramMap) {
  const lines = [];
  lines.push("# KIT-IME CORPUS EXPORT 🐾");
  lines.push("# VERSION: 1.0");
  lines.push(`# GENERATED: ${new Date().toISOString()}`);
  lines.push("---WORDS---");

  for (const lower in wordMap) {
    const item = wordMap[lower];
    if (item.count >= CONFIG.MIN_WORD_FREQ) {
      const clamped = Math.min(item.count, CONFIG.MAX_WORD_BOOST);
      // format: word|display|freq|posStart|posMid|posEnd
      lines.push(`${item.word}|${item.display}|${clamped}|${item.posStart}|${item.posMid}|${item.posEnd}`);
    }
  }

  lines.push("---BIGRAMS---");
  for (const pairKey in bigramMap) {
    const freq = bigramMap[pairKey];
    if (freq >= CONFIG.MIN_WORD_FREQ) {
      const parts = pairKey.split("->");
      const clamped = Math.min(freq, CONFIG.MAX_WORD_BOOST);
      // format: prevWord|nextWord|freq
      lines.push(`${parts[0]}|${parts[1]}|${clamped}`);
    }
  }

  return lines.join("\n");
}

/**
 * Writes the plain text file into Google Drive.
 */
function savePayloadToDrive(content) {
  const folders = DriveApp.getFoldersByName(CONFIG.SOURCE_FOLDER_NAME);
  const targetFolder = folders.hasNext() ? folders.next() : DriveApp.getRootFolder();

  const existingFiles = targetFolder.getFilesByName(CONFIG.OUTPUT_FILE_NAME);
  if (existingFiles.hasNext()) {
    const existingFile = existingFiles.next();
    existingFile.setContent(content);
    Logger.log(`✨ Updated existing Drive file: ${existingFile.getUrl()}`);
  } else {
    const newFile = targetFolder.createFile(CONFIG.OUTPUT_FILE_NAME, content, MimeType.PLAIN_TEXT);
    Logger.log(`✨ Created new Drive file: ${newFile.getUrl()}`);
  }
}

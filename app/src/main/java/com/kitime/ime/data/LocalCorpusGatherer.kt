package com.kitime.ime.data

import android.content.Context
import android.net.Uri
import android.provider.Telephony
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Result metrics from a corpus gathering cycle.
 */
data class GatherStats(
    val smsCount: Int = 0,
    val safFilesScanned: Int = 0,
    val wordsIngested: Int = 0,
    val bigramsIngested: Int = 0,
    val durationMs: Long = 0L
)

/**
 * Local App & Device Data Gatherer.
 *
 * Harvests personalized text from:
 * 1. Sent SMS (Telephony.Sms.CONTENT_URI, type = 2) stripping URLs & numeric 2FA/OTP codes.
 * 2. User-Selected Local Folders via SAF (ACTION_OPEN_DOCUMENT_TREE), scanning .md & .txt files.
 * 3. Share Sheet / Text Intake strings.
 *
 * Applies gentle corpus decay (0.985) and per-run word boost clamping (<= 25)
 * to ensure stability without radical day-to-day prediction swings.
 */
class LocalCorpusGatherer(
    private val context: Context,
    private val dbHelper: PredictorDatabase
) {

    companion object {
        const val CURSOR_SMS = "last_sms_date"
        const val CURSOR_SAF = "last_saf_scan"
        const val CORPUS_DECAY_RATE = 0.985
        const val MAX_PER_RUN_WORD_BOOST = 25.0

        // Regex patterns for sanitation
        private val URL_PATTERN = Regex("""https?://\S+|www\.\S+""", RegexOption.IGNORE_CASE)
        private val OTP_2FA_PATTERN = Regex("""\b(?:\d{4,8}|[A-Z0-9]{6,8})\b""")
        private val TOKEN_DELIMITER = Regex("""[^\p{L}\p{N}']+""")
    }

    /**
     * Executes full gathering cycle across SMS, SAF folder, and applies gentle decay.
     */
    fun runFullGather(safFolderUri: Uri? = null, remoteDocUrls: List<String> = emptyList()): GatherStats {
        val startTime = System.currentTimeMillis()
        var smsCount = 0
        var safCount = 0

        // 0. Pre-sync snapshot creation to guarantee recovery
        dbHelper.createDailySnapshot()

        // 1. Gentle corpus decay (multiplies all corpus frequencies by 0.985)
        applyCorpusDecay()

        // 2. Gather from Sent SMS (if permission granted)
        val smsWords = mutableMapOf<String, WordAcc>()
        val smsBigrams = mutableMapOf<Pair<String, String>, Double>()
        try {
            smsCount = gatherSentSms(smsWords, smsBigrams)
        } catch (e: SecurityException) {
            // READ_SMS permission not granted
        }

        // 3. Gather from SAF Folder (if uri provided)
        val safWords = mutableMapOf<String, WordAcc>()
        val safBigrams = mutableMapOf<Pair<String, String>, Double>()
        if (safFolderUri != null) {
            safCount = gatherFromSafFolder(safFolderUri, safWords, safBigrams)
        }

        // 4. Merge collected tokens with per-run clamp (<= 25 boost)
        val mergedWords = mutableMapOf<String, WordAcc>()
        val mergedBigrams = mutableMapOf<Pair<String, String>, Double>()

        mergeWordMaps(mergedWords, smsWords)
        mergeWordMaps(mergedWords, safWords)

        mergeBigramMaps(mergedBigrams, smsBigrams)
        mergeBigramMaps(mergedBigrams, safBigrams)

        // 5. Ingest into corpus_words and bigrams tables
        val (wordsIngested, bigramsIngested) = commitCorpusBatch(mergedWords, mergedBigrams)

        // 6. Ingest remote Google Docs / Apps Script sources if provided
        var docWords = 0
        var docBigrams = 0
        if (remoteDocUrls.isNotEmpty()) {
            val docFetcher = GoogleDocsCorpusFetcher(dbHelper)
            val docStats = docFetcher.fetchAndIngest(remoteDocUrls, this)
            docWords = docStats.wordsIngested
            docBigrams = docStats.bigramsIngested
        }

        // 7. Checkpoint WAL
        dbHelper.checkpointWal()

        return GatherStats(
            smsCount = smsCount,
            safFilesScanned = safCount,
            wordsIngested = wordsIngested + docWords,
            bigramsIngested = bigramsIngested + docBigrams,
            durationMs = System.currentTimeMillis() - startTime
        )
    }

    /**
     * Ingest a raw single text snippet (e.g. from ACTION_SEND Share Sheet or Google Doc export).
     */
    fun ingestTextSnippet(rawText: String): Pair<Int, Int> {
        if (rawText.isBlank()) return Pair(0, 0)

        // Check if payload is a structured Google Apps Script / Drive corpus export
        if (rawText.contains("# KIT-IME CORPUS EXPORT") || rawText.contains("---WORDS---")) {
            return ingestStructuredExport(rawText)
        }

        val words = mutableMapOf<String, WordAcc>()
        val bigrams = mutableMapOf<Pair<String, String>, Double>()

        processCleanText(rawText, words, bigrams)
        val result = commitCorpusBatch(words, bigrams)
        dbHelper.checkpointWal()
        return result
    }

    /**
     * Parses structured pipe-delimited payload from Google Apps Script export.
     */
    fun ingestStructuredExport(content: String): Pair<Int, Int> {
        val lines = content.lines()
        var mode = 0 // 1 = words, 2 = bigrams
        val words = mutableMapOf<String, WordAcc>()
        val bigrams = mutableMapOf<Pair<String, String>, Double>()

        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isEmpty() || trimmed.startsWith("#")) continue
            if (trimmed == "---WORDS---") {
                mode = 1
                continue
            }
            if (trimmed == "---BIGRAMS---") {
                mode = 2
                continue
            }

            if (mode == 1) {
                // word|display|freq|posStart|posMid|posEnd
                val parts = trimmed.split("|")
                if (parts.size >= 3) {
                    val w = parts[0].lowercase()
                    val disp = parts[1]
                    val freq = parts[2].toDoubleOrNull() ?: 1.0
                    val posStart = parts.getOrNull(3)?.toIntOrNull() ?: 0
                    val posMid = parts.getOrNull(4)?.toIntOrNull() ?: 0
                    val posEnd = parts.getOrNull(5)?.toIntOrNull() ?: 0

                    val acc = words.getOrPut(w) { WordAcc(w, disp) }
                    acc.count += freq
                    acc.posStart += posStart
                    acc.posMid += posMid
                    acc.posEnd += posEnd
                }
            } else if (mode == 2) {
                // prevWord|nextWord|freq
                val parts = trimmed.split("|")
                if (parts.size >= 3) {
                    val prev = parts[0].lowercase()
                    val next = parts[1].lowercase()
                    val freq = parts[2].toDoubleOrNull() ?: 1.0
                    val pair = Pair(prev, next)
                    bigrams[pair] = (bigrams[pair] ?: 0.0) + freq
                }
            }
        }

        val result = commitCorpusBatch(words, bigrams)
        dbHelper.checkpointWal()
        return result
    }

    /**
     * Gathers sent SMS messages (type = 2) newer than last_sms_date.
     */
    fun gatherSentSms(
        wordsAcc: MutableMap<String, WordAcc>,
        bigramsAcc: MutableMap<Pair<String, String>, Double>
    ): Int {
        val lastDateStr = dbHelper.getSyncCursor(CURSOR_SMS) ?: "0"
        val lastDate = lastDateStr.toLongOrNull() ?: 0L

        var processedCount = 0
        var latestDate = lastDate

        val uri = Telephony.Sms.CONTENT_URI
        val projection = arrayOf(Telephony.Sms._ID, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.TYPE)
        val selection = "${Telephony.Sms.TYPE} = 2 AND ${Telephony.Sms.DATE} > ?"
        val selectionArgs = arrayOf(lastDate.toString())
        val sortOrder = "${Telephony.Sms.DATE} ASC"

        val cursor = context.contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)
            ?: return 0

        try {
            while (cursor.moveToNext()) {
                val body = cursor.getString(1) ?: continue
                val msgDate = cursor.getLong(2)
                if (msgDate > latestDate) {
                    latestDate = msgDate
                }

                // Sanitize SMS body: strip URLs and 2FA/OTP codes
                val sanitized = sanitizeSmsBody(body)
                if (sanitized.isNotBlank()) {
                    processCleanText(sanitized, wordsAcc, bigramsAcc)
                    processedCount++
                }
            }
        } finally {
            cursor.close()
        }

        if (latestDate > lastDate) {
            dbHelper.setSyncCursor(CURSOR_SMS, latestDate.toString())
        }

        return processedCount
    }

    private fun sanitizeSmsBody(body: String): String {
        // Strip URLs
        var text = URL_PATTERN.replace(body, " ")

        // If message appears to be a 2FA/verification code, reject whole message
        if (body.contains("code", ignoreCase = true) ||
            body.contains("verification", ignoreCase = true) ||
            body.contains("one-time", ignoreCase = true) ||
            body.contains("otp", ignoreCase = true)
        ) {
            return ""
        }

        // Strip standalone numeric codes
        text = OTP_2FA_PATTERN.replace(text, " ")
        return text
    }

    /**
     * Gathers Markdown and TXT files from user-selected SAF folder tree.
     */
    fun gatherFromSafFolder(
        treeUri: Uri,
        wordsAcc: MutableMap<String, WordAcc>,
        bigramsAcc: MutableMap<Pair<String, String>, Double>
    ): Int {
        val lastScanStr = dbHelper.getSyncCursor(CURSOR_SAF) ?: "0"
        val lastScanTime = lastScanStr.toLongOrNull() ?: 0L

        val rootDoc = DocumentFile.fromTreeUri(context, treeUri) ?: return 0
        var scannedFiles = 0
        var maxFileModified = lastScanTime

        fun scanDirectory(dir: DocumentFile) {
            val files = dir.listFiles()
            for (file in files) {
                val name = file.name ?: continue

                // Skip non-corpus paths and artifacts
                if (name.equals("build", ignoreCase = true) ||
                    name.startsWith(".") ||
                    name.endsWith(".json", ignoreCase = true) ||
                    name.endsWith(".html", ignoreCase = true) ||
                    name.endsWith(".apk", ignoreCase = true) ||
                    name.endsWith(".dex", ignoreCase = true) ||
                    name.endsWith(".arsc", ignoreCase = true)
                ) {
                    continue
                }

                if (file.isDirectory) {
                    scanDirectory(file)
                } else if (file.isFile) {
                    val isSupportedDoc = name.endsWith(".md", ignoreCase = true) ||
                            name.endsWith(".txt", ignoreCase = true) ||
                            name.endsWith(".epub", ignoreCase = true)

                    if (isSupportedDoc && file.lastModified() > lastScanTime) {
                        if (file.lastModified() > maxFileModified) {
                            maxFileModified = file.lastModified()
                        }
                        readDocFile(file, wordsAcc, bigramsAcc)
                        scannedFiles++
                    }
                }
            }
        }

        scanDirectory(rootDoc)

        if (maxFileModified > lastScanTime) {
            dbHelper.setSyncCursor(CURSOR_SAF, maxFileModified.toString())
        }

        return scannedFiles
    }

    private fun readDocFile(
        file: DocumentFile,
        wordsAcc: MutableMap<String, WordAcc>,
        bigramsAcc: MutableMap<Pair<String, String>, Double>
    ) {
        try {
            context.contentResolver.openInputStream(file.uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream)).use { reader ->
                    val sb = StringBuilder()
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        val trimmed = line?.trim() ?: continue
                        // Skip YAML frontmatter markers or markdown code blocks
                        if (trimmed.startsWith("```") || trimmed == "---") continue
                        sb.append(trimmed).append(" ")
                    }
                    processCleanText(sb.toString(), wordsAcc, bigramsAcc)
                }
            }
        } catch (e: Exception) {
            // Skip unreadable files safely
        }
    }

    /**
     * Parses sentences, extracts tokens, updates position counts and bigrams.
     */
    private fun processCleanText(
        text: String,
        wordsAcc: MutableMap<String, WordAcc>,
        bigramsAcc: MutableMap<Pair<String, String>, Double>
    ) {
        // Sentence split by punctuation (. ! ? \n)
        val sentences = text.split(Regex("""[.!?\n]+"""))
        for (sentence in sentences) {
            val rawTokens = sentence.split(TOKEN_DELIMITER).filter { it.isNotBlank() }
            if (rawTokens.isEmpty()) continue

            for (i in rawTokens.indices) {
                val token = rawTokens[i]
                if (token.length > 40 || token.all { it.isDigit() }) continue

                val lower = token.lowercase()
                val acc = wordsAcc.getOrPut(lower) { WordAcc(lower, token) }
                acc.count += 1.0

                when {
                    i == 0 -> acc.posStart++
                    i == rawTokens.lastIndex -> acc.posEnd++
                    else -> acc.posMid++
                }

                // Bigrams
                if (i > 0) {
                    val prevLower = rawTokens[i - 1].lowercase()
                    val pair = Pair(prevLower, lower)
                    bigramsAcc[pair] = (bigramsAcc[pair] ?: 0.0) + 1.0
                }
            }
        }
    }

    /**
     * Applies gentle decay to corpus_words and bigrams tables.
     */
    fun applyCorpusDecay() {
        val db = dbHelper.writableDatabase
        db.execSQL("UPDATE corpus_words SET freq = freq * $CORPUS_DECAY_RATE WHERE is_custom = 0;")
        db.execSQL("UPDATE bigrams SET freq = freq * $CORPUS_DECAY_RATE;")
        // Prune negligible corpus entries
        db.execSQL("DELETE FROM corpus_words WHERE freq < 0.1 AND is_custom = 0;")
        db.execSQL("DELETE FROM bigrams WHERE freq < 0.1;")
    }

    private fun commitCorpusBatch(
        words: Map<String, WordAcc>,
        bigrams: Map<Pair<String, String>, Double>
    ): Pair<Int, Int> {
        val db = dbHelper.writableDatabase
        val now = System.currentTimeMillis()
        var wordsCommitted = 0
        var bigramsCommitted = 0

        db.beginTransaction()
        try {
            // Upsert corpus_words with clamped frequency boost
            for ((_, acc) in words) {
                val clampedBoost = acc.count.coerceAtMost(MAX_PER_RUN_WORD_BOOST)
                db.execSQL(
                    """
                    INSERT INTO corpus_words (word, display_word, freq, pos_start, pos_mid, pos_end, is_custom, last_updated)
                    VALUES (?, ?, ?, ?, ?, ?, 0, ?)
                    ON CONFLICT(word) DO UPDATE SET 
                        freq = freq + excluded.freq,
                        pos_start = pos_start + excluded.pos_start,
                        pos_mid = pos_mid + excluded.pos_mid,
                        pos_end = pos_end + excluded.pos_end,
                        last_updated = excluded.last_updated,
                        display_word = excluded.display_word;
                    """.trimIndent(),
                    arrayOf(acc.word, acc.displayWord, clampedBoost, acc.posStart, acc.posMid, acc.posEnd, now)
                )
                wordsCommitted++
            }

            // Upsert bigrams
            for ((pair, count) in bigrams) {
                val clampedBigram = count.coerceAtMost(MAX_PER_RUN_WORD_BOOST)
                db.execSQL(
                    """
                    INSERT INTO bigrams (prev_word, next_word, freq)
                    VALUES (?, ?, ?)
                    ON CONFLICT(prev_word, next_word) DO UPDATE SET 
                        freq = freq + excluded.freq;
                    """.trimIndent(),
                    arrayOf(pair.first, pair.second, clampedBigram)
                )
                bigramsCommitted++
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        return Pair(wordsCommitted, bigramsCommitted)
    }

    private fun mergeWordMaps(target: MutableMap<String, WordAcc>, source: Map<String, WordAcc>) {
        for ((k, v) in source) {
            val existing = target.getOrPut(k) { WordAcc(v.word, v.displayWord) }
            existing.count += v.count
            existing.posStart += v.posStart
            existing.posMid += v.posMid
            existing.posEnd += v.posEnd
        }
    }

    private fun mergeBigramMaps(
        target: MutableMap<Pair<String, String>, Double>,
        source: Map<Pair<String, String>, Double>
    ) {
        for ((k, v) in source) {
            target[k] = (target[k] ?: 0.0) + v
        }
    }

    class WordAcc(
        val word: String,
        val displayWord: String
    ) {
        var count: Double = 0.0
        var posStart: Int = 0
        var posMid: Int = 0
        var posEnd: Int = 0
    }
}

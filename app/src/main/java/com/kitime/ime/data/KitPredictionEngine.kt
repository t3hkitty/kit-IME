package com.kitime.ime.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.kitime.ime.bridge.Candidate
import com.kitime.ime.bridge.RibbonSlot
import com.kitime.ime.bridge.TriSplitCandidates
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max

/**
 * Weights and scoring parameters for log-compressed prediction.
 *
 * score = w_corpus * ln(1 + corpus_freq)
 *       + w_usage  * ln(1 + confirmed_freq)
 *       + w_ctx    * ln(1 + bigram_freq)
 *       + w_pos    * pos_match
 *       + w_custom * is_custom
 *       + w_agree  * agreement_bonus
 */
data class PredictorWeights(
    val wCorpus: Double = 1.0,
    val wUsage: Double = 2.5,
    val wCtx: Double = 1.8,
    val wPos: Double = 0.5,
    val wCustom: Double = 1.5,
    val wAgree: Double = 1.2
)

/**
 * Internal scored candidate tuple.
 */
data class ScoredWord(
    val word: String,
    val displayWord: String,
    val score: Double,
    val isCustom: Boolean,
    val isFromUsage: Boolean,
    val isFromCorpus: Boolean
)

/**
 * Log-compressed dual-DB prediction and scoring engine.
 *
 * Strict constraint:
 * - ZERO post-commit rewriting. Candidates are for tri-split ribbon ranking and typo rescue only.
 * - Commits are verbatim strings.
 */
class KitPredictionEngine(
    private val dbHelper: PredictorDatabase,
    private val weights: PredictorWeights = PredictorWeights()
) {

    companion object {
        // QWERTY keyboard adjacency mapping for typo distance and neighbor slip scoring
        val QWERTY_NEIGHBORS: Map<Char, Set<Char>> = mapOf(
            'q' to setOf('w', 'a', 's', '1', '2'),
            'w' to setOf('q', 'e', 'a', 's', 'd', '2', '3'),
            'e' to setOf('w', 'r', 's', 'd', 'f', '3', '4'),
            'r' to setOf('e', 't', 'd', 'f', 'g', '4', '5'),
            't' to setOf('r', 'y', 'f', 'g', 'h', '5', '6'),
            'y' to setOf('t', 'u', 'g', 'h', 'j', '6', '7'),
            'u' to setOf('y', 'i', 'h', 'j', 'k', '7', '8'),
            'i' to setOf('u', 'o', 'j', 'k', 'l', '8', '9'),
            'o' to setOf('i', 'p', 'k', 'l', '9', '0'),
            'p' to setOf('o', 'l', '0'),
            'a' to setOf('q', 'w', 's', 'z'),
            's' to setOf('a', 'w', 'e', 'd', 'z', 'x'),
            'd' to setOf('s', 'e', 'r', 'f', 'x', 'c'),
            'f' to setOf('d', 'r', 't', 'g', 'c', 'v'),
            'g' to setOf('f', 't', 'y', 'h', 'v', 'b'),
            'h' to setOf('g', 'y', 'u', 'j', 'b', 'n'),
            'j' to setOf('h', 'u', 'i', 'k', 'n', 'm'),
            'k' to setOf('j', 'i', 'o', 'l', 'm'),
            'l' to setOf('k', 'o', 'p'),
            'z' to setOf('a', 's', 'x'),
            'x' to setOf('z', 's', 'd', 'c'),
            'c' to setOf('x', 'd', 'f', 'v'),
            'v' to setOf('c', 'f', 'g', 'b'),
            'b' to setOf('v', 'g', 'h', 'n'),
            'n' to setOf('b', 'h', 'j', 'm'),
            'm' to setOf('n', 'j', 'k')
        )
    }

    /**
     * Compute candidates for prefix given optional previous word context and sentence position.
     */
    fun predict(
        prefix: String,
        prevWord: String? = null,
        sentencePos: SentencePos = SentencePos.MID
    ): TriSplitCandidates {
        val cleanPrefix = prefix.trim()
        if (cleanPrefix.isEmpty()) {
            // Return context-based next word predictions if prefix is empty but context exists
            if (!prevWord.isNullOrBlank()) {
                return predictNextWords(prevWord.trim())
            }
            return TriSplitCandidates()
        }

        val lowerPrefix = cleanPrefix.lowercase()
        val scoredList = scoreCandidatesForPrefix(lowerPrefix, cleanPrefix, prevWord, sentencePos)

        // Determine typo rescue candidate
        val typoRescueWord = findTypoRescue(cleanPrefix)

        // Center / primary candidate
        val primaryScored = scoredList.firstOrNull()
        val primaryText = primaryScored?.displayWord ?: cleanPrefix
        val isGreenlit = primaryScored != null && cleanPrefix.length >= 2 &&
                primaryScored.word.startsWith(lowerPrefix) &&
                (primaryScored.isFromUsage || primaryScored.score > 2.0)

        // Right variant candidate (punctuation, contraction, plural, or second best)
        val variantCandidate = generateVariant(cleanPrefix, primaryText, scoredList.drop(1))

        // Extended slots
        val slot4Word = scoredList.getOrNull(1)?.displayWord ?: "$cleanPrefix?"
        val slot5Word = scoredList.getOrNull(2)?.displayWord ?: "$cleanPrefix!"

        val rescueText = typoRescueWord ?: (if (primaryText.equals(cleanPrefix, ignoreCase = true) && scoredList.size > 1) {
            scoredList[1].displayWord
        } else {
            cleanPrefix.lowercase()
        })

        return TriSplitCandidates(
            typoRescue = Candidate(
                text = rescueText,
                slot = RibbonSlot.LEFT_TYPO_RESCUE,
                isGreenlit = false,
                confidence = 0.7f
            ),
            primary = Candidate(
                text = primaryText,
                slot = RibbonSlot.CENTER_PRIMARY,
                isGreenlit = isGreenlit,
                confidence = (primaryScored?.score?.toFloat()?.coerceIn(0.1f, 1.0f) ?: 0.95f)
            ),
            variant = Candidate(
                text = variantCandidate,
                slot = RibbonSlot.RIGHT_VARIANT,
                isGreenlit = false,
                confidence = 0.75f
            ),
            slot4 = Candidate(slot4Word, RibbonSlot.EXTENDED_FOUR, isGreenlit = false, confidence = 0.5f),
            slot5 = Candidate(slot5Word, RibbonSlot.EXTENDED_FIVE, isGreenlit = false, confidence = 0.4f)
        )
    }

    private fun predictNextWords(prevWord: String): TriSplitCandidates {
        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT next_word, freq FROM bigrams 
            WHERE prev_word = ? COLLATE NOCASE 
            ORDER BY freq DESC LIMIT 5
            """.trimIndent(),
            arrayOf(prevWord)
        )

        val words = mutableListOf<String>()
        while (cursor.moveToNext()) {
            words.add(cursor.getString(0))
        }
        cursor.close()

        if (words.isEmpty()) return TriSplitCandidates()

        val primary = words.getOrNull(0) ?: return TriSplitCandidates()
        val rescue = words.getOrNull(1)
        val variant = words.getOrNull(2)

        return TriSplitCandidates(
            typoRescue = rescue?.let { Candidate(it, RibbonSlot.LEFT_TYPO_RESCUE, false, 0.7f) },
            primary = Candidate(primary, RibbonSlot.CENTER_PRIMARY, true, 0.9f),
            variant = variant?.let { Candidate(it, RibbonSlot.RIGHT_VARIANT, false, 0.6f) },
            slot4 = words.getOrNull(3)?.let { Candidate(it, RibbonSlot.EXTENDED_FOUR, false, 0.4f) },
            slot5 = words.getOrNull(4)?.let { Candidate(it, RibbonSlot.EXTENDED_FIVE, false, 0.3f) }
        )
    }

    private fun scoreCandidatesForPrefix(
        lowerPrefix: String,
        originalPrefix: String,
        prevWord: String?,
        sentencePos: SentencePos
    ): List<ScoredWord> {
        val db = dbHelper.readableDatabase
        val map = mutableMapOf<String, CandidateAcc>()

        // 1. Query corpus_words with prefix LIKE
        val corpusCursor = db.rawQuery(
            """
            SELECT word, display_word, freq, pos_start, pos_mid, pos_end, is_custom 
            FROM corpus_words 
            WHERE word LIKE ? || '%' 
            ORDER BY freq DESC LIMIT 40
            """.trimIndent(),
            arrayOf(lowerPrefix)
        )
        while (corpusCursor.moveToNext()) {
            val w = corpusCursor.getString(0)
            val dw = corpusCursor.getString(1) ?: w
            val freq = corpusCursor.getDouble(2)
            val posStart = corpusCursor.getInt(3)
            val posMid = corpusCursor.getInt(4)
            val posEnd = corpusCursor.getInt(5)
            val isCustom = corpusCursor.getInt(6) == 1

            val acc = map.getOrPut(w.lowercase()) { CandidateAcc(w, dw) }
            acc.corpusFreq = freq
            acc.posStart += posStart
            acc.posMid += posMid
            acc.posEnd += posEnd
            acc.isCustom = acc.isCustom || isCustom
            acc.inCorpus = true
        }
        corpusCursor.close()

        // 2. Query usage_words with prefix LIKE
        val usageCursor = db.rawQuery(
            """
            SELECT word, display_word, confirmed_freq, pos_start, pos_mid, pos_end, is_custom 
            FROM usage_words 
            WHERE word LIKE ? || '%' 
            ORDER BY confirmed_freq DESC LIMIT 40
            """.trimIndent(),
            arrayOf(lowerPrefix)
        )
        while (usageCursor.moveToNext()) {
            val w = usageCursor.getString(0)
            val dw = usageCursor.getString(1) ?: w
            val confirmedFreq = usageCursor.getInt(2)
            val posStart = usageCursor.getInt(3)
            val posMid = usageCursor.getInt(4)
            val posEnd = usageCursor.getInt(5)
            val isCustom = usageCursor.getInt(6) == 1

            val acc = map.getOrPut(w.lowercase()) { CandidateAcc(w, dw) }
            acc.confirmedFreq = confirmedFreq
            acc.posStart += posStart
            acc.posMid += posMid
            acc.posEnd += posEnd
            acc.isCustom = acc.isCustom || isCustom
            acc.inUsage = true
        }
        usageCursor.close()

        // 3. Query bigram frequencies if prevWord exists
        if (!prevWord.isNullOrBlank()) {
            val bigramCursor = db.rawQuery(
                """
                SELECT next_word, freq FROM bigrams 
                WHERE prev_word = ? COLLATE NOCASE AND next_word LIKE ? || '%'
                """.trimIndent(),
                arrayOf(prevWord.trim(), lowerPrefix)
            )
            while (bigramCursor.moveToNext()) {
                val nw = bigramCursor.getString(0)
                val bFreq = bigramCursor.getDouble(1)
                map[nw.lowercase()]?.let {
                    it.bigramFreq = bFreq
                }
            }
            bigramCursor.close()
        }

        // 4. Calculate log-compressed score for each candidate
        return map.values.map { acc ->
            val posCount = when (sentencePos) {
                SentencePos.START -> acc.posStart
                SentencePos.MID -> acc.posMid
                SentencePos.END -> acc.posEnd
            }
            val posMatch = if (posCount > 0) 1.0 else 0.0
            val agreementBonus = if (acc.inCorpus && acc.inUsage) 1.0 else 0.0

            val score = weights.wCorpus * ln(1.0 + acc.corpusFreq) +
                    weights.wUsage * ln(1.0 + acc.confirmedFreq) +
                    weights.wCtx * ln(1.0 + acc.bigramFreq) +
                    weights.wPos * posMatch +
                    weights.wCustom * (if (acc.isCustom) 1.0 else 0.0) +
                    weights.wAgree * agreementBonus

            // Case preservation matching original prefix capitalization
            val finalDisplay = matchCase(acc.displayWord, originalPrefix)

            ScoredWord(
                word = acc.word,
                displayWord = finalDisplay,
                score = score,
                isCustom = acc.isCustom,
                isFromUsage = acc.inUsage,
                isFromCorpus = acc.inCorpus
            )
        }.sortedByDescending { it.score }
    }

    private fun matchCase(candidate: String, prefix: String): String {
        if (prefix.isEmpty() || candidate.isEmpty()) return candidate
        return when {
            prefix.all { it.isUpperCase() } && prefix.length > 1 -> candidate.uppercase()
            prefix.first().isUpperCase() -> candidate.replaceFirstChar { it.uppercase() }
            else -> candidate
        }
    }

    /**
     * Typo rescue search: checks typo_confusion table first, then calculates neighbor slips.
     */
    fun findTypoRescue(token: String): String? {
        val lower = token.lowercase()
        val db = dbHelper.readableDatabase

        // 1. Check learned typo_confusion table
        val cursor = db.rawQuery(
            """
            SELECT intended_token FROM typo_confusion 
            WHERE bad_token = ? COLLATE NOCASE 
            ORDER BY count DESC LIMIT 1
            """.trimIndent(),
            arrayOf(lower)
        )
        if (cursor.moveToFirst()) {
            val intended = cursor.getString(0)
            cursor.close()
            return matchCase(intended, token)
        }
        cursor.close()

        // 2. Neighbor slip / edit distance 1 search across usage and corpus words
        if (token.length >= 3) {
            val neighborCandidates = db.rawQuery(
                """
                SELECT word, display_word FROM usage_words WHERE length(word) BETWEEN ? AND ? 
                UNION 
                SELECT word, display_word FROM corpus_words WHERE length(word) BETWEEN ? AND ? 
                LIMIT 50
                """.trimIndent(),
                arrayOf(
                    (token.length - 1).toString(),
                    (token.length + 1).toString(),
                    (token.length - 1).toString(),
                    (token.length + 1).toString()
                )
            )

            var bestCandidate: String? = null
            var bestCost = Double.MAX_VALUE

            while (neighborCandidates.moveToNext()) {
                val candidate = neighborCandidates.getString(0)
                val display = neighborCandidates.getString(1) ?: candidate
                val cost = calculateKeyboardSlipDistance(lower, candidate.lowercase())
                if (cost < 1.5 && cost < bestCost) {
                    bestCost = cost
                    bestCandidate = display
                }
            }
            neighborCandidates.close()

            if (bestCandidate != null) {
                return matchCase(bestCandidate, token)
            }
        }

        return null
    }

    /**
     * Custom keyboard-aware Levenshtein distance where QWERTY neighbors have lower penalty (0.4 vs 1.0).
     */
    private fun calculateKeyboardSlipDistance(s1: String, s2: String): Double {
        if (abs(s1.length - s2.length) > 2) return 99.0
        val m = s1.length
        val n = s2.length
        val dp = Array(m + 1) { DoubleArray(n + 1) }

        for (i in 0..m) dp[i][0] = i.toDouble()
        for (j in 0..n) dp[0][j] = j.toDouble()

        for (i in 1..m) {
            val c1 = s1[i - 1]
            for (j in 1..n) {
                val c2 = s2[j - 1]
                val subCost = if (c1 == c2) {
                    0.0
                } else if (QWERTY_NEIGHBORS[c1]?.contains(c2) == true) {
                    0.4 // Neighbor slip cost
                } else {
                    1.0 // Standard substitution
                }

                dp[i][j] = minOf(
                    dp[i - 1][j] + 1.0,      // Deletion
                    dp[i][j - 1] + 1.0,      // Insertion
                    dp[i - 1][j - 1] + subCost // Substitution
                )
            }
        }
        return dp[m][n]
    }

    private fun generateVariant(prefix: String, primary: String, otherCandidates: List<ScoredWord>): String {
        // If second scored word is strong and distinct, use it
        val alt = otherCandidates.firstOrNull { !it.displayWord.equals(primary, ignoreCase = true) }
        if (alt != null && alt.score > 2.0) {
            return alt.displayWord
        }

        // Synthesize grammatical variant (contraction, plural, or apostrophe-s)
        val target = if (primary.isNotEmpty()) primary else prefix
        return when {
            target.endsWith("s", ignoreCase = true) -> "${target}'"
            target.endsWith("t", ignoreCase = true) -> "${target}'s"
            target.endsWith("e", ignoreCase = true) -> "${target}d"
            else -> "${target}s"
        }
    }

    /**
     * Check if token is unrecognized across both corpus and usage databases.
     */
    fun isUnrecognizedWord(token: String): Boolean {
        if (token.isBlank() || token.length < 2) return false
        val clean = token.trim().lowercase()

        val db = dbHelper.readableDatabase
        val cursor = db.rawQuery(
            """
            SELECT 1 FROM usage_words WHERE word = ? COLLATE NOCASE 
            UNION 
            SELECT 1 FROM corpus_words WHERE word = ? COLLATE NOCASE 
            LIMIT 1
            """.trimIndent(),
            arrayOf(clean, clean)
        )
        val recognized = cursor.moveToFirst()
        cursor.close()
        return !recognized
    }

    /**
     * Word confirmed callback: increments confirmed_freq, updates bigrams locally,
     * and enqueues atomic counter deltas in pending_firebase_deltas.
     */
    fun onWordConfirmed(
        word: String,
        prevWord: String?,
        sentencePos: SentencePos,
        wasCandidateTap: Boolean
    ) {
        val clean = word.trim()
        if (clean.isBlank()) return

        val lower = clean.lowercase()
        val now = System.currentTimeMillis()
        val db = dbHelper.writableDatabase

        db.beginTransaction()
        try {
            // 1. Upsert usage_words
            val posStartInc = if (sentencePos == SentencePos.START) 1 else 0
            val posMidInc = if (sentencePos == SentencePos.MID) 1 else 0
            val posEndInc = if (sentencePos == SentencePos.END) 1 else 0

            db.execSQL(
                """
                INSERT INTO usage_words (word, display_word, confirmed_freq, pos_start, pos_mid, pos_end, is_custom, last_used)
                VALUES (?, ?, 1, ?, ?, ?, 0, ?)
                ON CONFLICT(word) DO UPDATE SET 
                    confirmed_freq = confirmed_freq + 1,
                    pos_start = pos_start + excluded.pos_start,
                    pos_mid = pos_mid + excluded.pos_mid,
                    pos_end = pos_end + excluded.pos_end,
                    last_used = excluded.last_used,
                    display_word = excluded.display_word;
                """.trimIndent(),
                arrayOf(lower, clean, posStartInc, posMidInc, posEndInc, now)
            )

            // 2. Upsert bigram if previous word exists
            if (!prevWord.isNullOrBlank()) {
                val cleanPrev = prevWord.trim().lowercase()
                db.execSQL(
                    """
                    INSERT INTO bigrams (prev_word, next_word, freq)
                    VALUES (?, ?, 1.0)
                    ON CONFLICT(prev_word, next_word) DO UPDATE SET 
                        freq = freq + 1.0;
                    """.trimIndent(),
                    arrayOf(cleanPrev, lower)
                )
            }

            // 3. Enqueue batched atomic delta for Firebase synchronization
            dbHelper.recordFirebaseDelta("word:$lower", "word_confirmed", 1)
            if (!prevWord.isNullOrBlank()) {
                val cleanPrev = prevWord.trim().lowercase()
                dbHelper.recordFirebaseDelta("bigram:$cleanPrev->$lower", "bigram_confirmed", 1)
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Typo slip corrected callback: increments typo_confusion count.
     */
    fun onSlipCorrected(badToken: String, intendedToken: String, slipType: String) {
        val bad = badToken.trim().lowercase()
        val intended = intendedToken.trim().lowercase()
        if (bad.isBlank() || intended.isBlank() || bad == intended) return

        val now = System.currentTimeMillis()
        val db = dbHelper.writableDatabase

        db.execSQL(
            """
            INSERT INTO typo_confusion (bad_token, intended_token, slip_type, count, last_seen)
            VALUES (?, ?, ?, 1, ?)
            ON CONFLICT(bad_token, intended_token, slip_type) DO UPDATE SET 
                count = count + 1,
                last_seen = excluded.last_seen;
            """.trimIndent(),
            arrayOf(bad, intended, slipType, now)
        )

        dbHelper.recordFirebaseDelta("typo:$bad->$intended", "typo_slip", 1)
    }

    private class CandidateAcc(
        val word: String,
        val displayWord: String
    ) {
        var corpusFreq: Double = 0.0
        var confirmedFreq: Int = 0
        var bigramFreq: Double = 0.0
        var posStart: Int = 0
        var posMid: Int = 0
        var posEnd: Int = 0
        var isCustom: Boolean = false
        var inCorpus: Boolean = false
        var inUsage: Boolean = false
    }
}

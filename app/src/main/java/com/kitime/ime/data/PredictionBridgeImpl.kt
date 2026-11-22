package com.kitime.ime.data

import android.content.Context
import com.kitime.ime.bridge.CandidateProvider
import com.kitime.ime.bridge.ConfirmationSink
import com.kitime.ime.bridge.RibbonSlot
import com.kitime.ime.bridge.SnapshotBridge
import com.kitime.ime.bridge.TriSplitCandidates
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

/**
 * Production bridge implementing CandidateProvider, ConfirmationSink, and SnapshotBridge.
 * Plugs directly into SQLite WAL and KitPredictionEngine.
 */
class PredictionDbBridge(
    private val context: Context? = null,
    val predictionEngine: KitPredictionEngine? = null
) : CandidateProvider, ConfirmationSink, SnapshotBridge {

    private val dbHelper: PredictorDatabase? = if (context != null) PredictorDatabase.getInstance(context) else null
    private val engine: KitPredictionEngine? = predictionEngine ?: dbHelper?.let { KitPredictionEngine(it) }

    private var previousCommittedWord: String? = null

    override fun getCandidates(prefix: String, contextBefore: String): TriSplitCandidates {
        if (prefix.isBlank()) return TriSplitCandidates()

        val eng = engine
        if (eng != null) {
            try {
                val prevWord = extractPreviousWord(contextBefore)
                val sentencePos = determineSentencePos(contextBefore)
                return eng.predict(prefix, prevWord, sentencePos)
            } catch (e: Throwable) {
                android.util.Log.e("PredictionBridge", "Database error during candidate query, falling back to heuristic", e)
            }
        }

        // Lightweight fallback when running without Android Context or on SQLite error
        val cleanPrefix = prefix.trim()
        val greenlit = cleanPrefix.length >= 2
        val primaryText = cleanPrefix
        val typoRescueText = if (cleanPrefix.length > 2) cleanPrefix.lowercase() else cleanPrefix
        val variantText = when {
            cleanPrefix.endsWith("s", ignoreCase = true) -> "${cleanPrefix}'"
            cleanPrefix.endsWith("t", ignoreCase = true) -> "${cleanPrefix}'s"
            else -> "${cleanPrefix}s"
        }

        return TriSplitCandidates(
            typoRescue = com.kitime.ime.bridge.Candidate(typoRescueText, RibbonSlot.LEFT_TYPO_RESCUE, isGreenlit = false, confidence = 0.6f),
            primary = com.kitime.ime.bridge.Candidate(primaryText, RibbonSlot.CENTER_PRIMARY, isGreenlit = greenlit, confidence = 0.95f),
            variant = com.kitime.ime.bridge.Candidate(variantText, RibbonSlot.RIGHT_VARIANT, isGreenlit = false, confidence = 0.7f),
            slot4 = com.kitime.ime.bridge.Candidate("$cleanPrefix?", RibbonSlot.EXTENDED_FOUR, isGreenlit = false, confidence = 0.4f),
            slot5 = com.kitime.ime.bridge.Candidate("$cleanPrefix!", RibbonSlot.EXTENDED_FIVE, isGreenlit = false, confidence = 0.3f)
        )
    }

    override fun isUnrecognizedWord(token: String): Boolean {
        if (engine != null) {
            try {
                return engine.isUnrecognizedWord(token)
            } catch (e: Throwable) {
                android.util.Log.e("PredictionBridge", "Error in isUnrecognizedWord", e)
            }
        }
        if (token.isBlank() || token.length < 2) return false
        return Regex("(.)\\1\\1").containsMatchIn(token)
    }

    override fun recordCommit(token: String, candidateSelected: Boolean, slot: RibbonSlot?) {
        try {
            val clean = token.trim()
            if (clean.isBlank()) return

            val prevWord = previousCommittedWord
            val pos = if (prevWord == null) SentencePos.START else SentencePos.MID

            engine?.onWordConfirmed(
                word = clean,
                prevWord = prevWord,
                sentencePos = pos,
                wasCandidateTap = candidateSelected
            )

            if (candidateSelected && slot == RibbonSlot.LEFT_TYPO_RESCUE && prevWord != null) {
                engine?.onSlipCorrected(
                    badToken = prevWord,
                    intendedToken = clean,
                    slipType = "ribbon_rescue"
                )
            }

            previousCommittedWord = clean
        } catch (e: Throwable) {
            android.util.Log.e("PredictionBridge", "Error recording commit", e)
        }
    }

    override fun exportSnapshot(): ByteArray {
        val helper = dbHelper ?: return ByteArray(0)
        val snapshotFile = helper.createDailySnapshot() ?: return ByteArray(0)
        return try {
            FileInputStream(snapshotFile).use { it.readBytes() }
        } catch (e: Exception) {
            ByteArray(0)
        }
    }

    override fun importSnapshot(data: ByteArray): Boolean {
        val helper = dbHelper ?: return false
        val ctx = context ?: return false
        if (data.isEmpty()) return false
        val tempFile = File(ctx.cacheDir, "import_snapshot_${System.currentTimeMillis()}.db")
        return try {
            FileOutputStream(tempFile).use { it.write(data) }
            val restored = helper.restoreSnapshot(tempFile)
            tempFile.delete()
            restored
        } catch (e: Exception) {
            tempFile.delete()
            false
        }
    }

    private fun extractPreviousWord(context: String): String? {
        if (context.isBlank()) return null
        val trimmed = context.trimEnd()
        val tokens = trimmed.split(Regex("""\s+"""))
        return tokens.lastOrNull()?.trim { !it.isLetterOrDigit() && it != '\'' }
    }

    private fun determineSentencePos(context: String): SentencePos {
        val trimmed = context.trimEnd()
        if (trimmed.isEmpty()) return SentencePos.START
        val lastChar = trimmed.last()
        return if (lastChar == '.' || lastChar == '?' || lastChar == '!' || lastChar == '\n') {
            SentencePos.START
        } else {
            SentencePos.MID
        }
    }
}

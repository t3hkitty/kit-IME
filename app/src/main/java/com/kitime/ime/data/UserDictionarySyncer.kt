package com.kitime.ime.data

import android.content.ContentValues
import android.content.Context
import android.provider.UserDictionary
import java.util.Locale

/**
 * Result metrics for Android UserDictionary sync.
 */
data class UserDictSyncStats(
    val wordsScanned: Int = 0,
    val wordsInserted: Int = 0,
    val wordsUpdated: Int = 0
)

/**
 * Android UserDictionary Sync Helper.
 *
 * When WRITE_USER_DICTIONARY is granted, syncs high-value words (frequency/confirmation above
 * threshold or marked is_custom = 1) into android.provider.UserDictionary.Words with proper
 * locale and frequency fields so system spellcheckers and external IME consumers remain aligned.
 */
class UserDictionarySyncer(
    private val context: Context,
    private val dbHelper: PredictorDatabase
) {

    companion object {
        const val MIN_CORPUS_FREQ_THRESHOLD = 5.0
        const val MIN_USAGE_FREQ_THRESHOLD = 3
    }

    /**
     * Executes sync into Android UserDictionary.Words.
     */
    fun syncToUserDictionary(): UserDictSyncStats {
        val resolver = context.contentResolver
        val currentLocale = Locale.getDefault().toString()

        val wordsToSync = mutableMapOf<String, Int>() // word -> frequency (1..250)

        val db = dbHelper.readableDatabase

        // 1. Gather high-usage words
        val usageCursor = db.rawQuery(
            """
            SELECT word, display_word, confirmed_freq, is_custom 
            FROM usage_words 
            WHERE confirmed_freq >= ? OR is_custom = 1
            """.trimIndent(),
            arrayOf(MIN_USAGE_FREQ_THRESHOLD.toString())
        )
        while (usageCursor.moveToNext()) {
            val display = usageCursor.getString(1) ?: usageCursor.getString(0)
            val freq = usageCursor.getInt(2)
            val isCustom = usageCursor.getInt(3) == 1

            // Scale to Android UserDictionary frequency range (1..250)
            val scaledFreq = if (isCustom) 250 else (50 + (freq * 10)).coerceIn(50, 240)
            wordsToSync[display] = scaledFreq
        }
        usageCursor.close()

        // 2. Gather high-corpus words
        val corpusCursor = db.rawQuery(
            """
            SELECT word, display_word, freq, is_custom 
            FROM corpus_words 
            WHERE freq >= ? OR is_custom = 1
            """.trimIndent(),
            arrayOf(MIN_CORPUS_FREQ_THRESHOLD.toString())
        )
        while (corpusCursor.moveToNext()) {
            val display = corpusCursor.getString(1) ?: corpusCursor.getString(0)
            val freq = corpusCursor.getDouble(2)
            val isCustom = corpusCursor.getInt(3) == 1

            val scaledFreq = if (isCustom) 250 else (40 + (freq * 5).toInt()).coerceIn(40, 220)
            if (!wordsToSync.containsKey(display) || (wordsToSync[display] ?: 0) < scaledFreq) {
                wordsToSync[display] = scaledFreq
            }
        }
        corpusCursor.close()

        // 3. Query existing UserDictionary words to avoid duplicates
        val existingWords = mutableSetOf<String>()
        try {
            val dictCursor = resolver.query(
                UserDictionary.Words.CONTENT_URI,
                arrayOf(UserDictionary.Words.WORD),
                null,
                null,
                null
            )
            dictCursor?.use { cursor ->
                while (cursor.moveToNext()) {
                    existingWords.add(cursor.getString(0).lowercase())
                }
            }
        } catch (e: SecurityException) {
            // Permission missing
            return UserDictSyncStats(wordsScanned = wordsToSync.size)
        }

        var inserted = 0
        var updated = 0

        for ((word, freq) in wordsToSync) {
            if (word.isBlank() || word.length < 2) continue

            try {
                if (existingWords.contains(word.lowercase())) {
                    val values = ContentValues().apply {
                        put(UserDictionary.Words.FREQUENCY, freq)
                    }
                    val rows = resolver.update(
                        UserDictionary.Words.CONTENT_URI,
                        values,
                        "${UserDictionary.Words.WORD} = ? COLLATE NOCASE",
                        arrayOf(word)
                    )
                    if (rows > 0) updated++
                } else {
                    UserDictionary.Words.addWord(
                        context,
                        word,
                        freq,
                        UserDictionary.Words.LOCALE_TYPE_CURRENT
                    )
                    inserted++
                }
            } catch (e: Exception) {
                // Ignore single insert error
            }
        }

        return UserDictSyncStats(
            wordsScanned = wordsToSync.size,
            wordsInserted = inserted,
            wordsUpdated = updated
        )
    }
}

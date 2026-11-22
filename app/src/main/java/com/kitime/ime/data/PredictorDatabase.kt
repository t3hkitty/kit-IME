package com.kitime.ime.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.math.ln

/**
 * Sentence position enum for corpus & usage statistics.
 */
enum class SentencePos {
    START,
    MID,
    END
}

/**
 * Corpus word record representation.
 */
data class CorpusWord(
    val word: String,
    val displayWord: String,
    val freq: Double,
    val posStart: Int = 0,
    val posMid: Int = 0,
    val posEnd: Int = 0,
    val isCustom: Boolean = false,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Usage word record representation.
 */
data class UsageWord(
    val word: String,
    val displayWord: String,
    val confirmedFreq: Int,
    val posStart: Int = 0,
    val posMid: Int = 0,
    val posEnd: Int = 0,
    val isCustom: Boolean = false,
    val lastUsed: Long = System.currentTimeMillis()
)

/**
 * Bigram transition entry.
 */
data class BigramRecord(
    val prevWord: String,
    val nextWord: String,
    val freq: Double
)

/**
 * Typo confusion table entry.
 */
data class TypoConfusion(
    val badToken: String,
    val intendedToken: String,
    val slipType: String,
    val count: Int,
    val lastSeen: Long = System.currentTimeMillis()
)

/**
 * Pending Firebase delta record for counter synchronization.
 */
data class PendingFirebaseDelta(
    val deltaKey: String,
    val kind: String,
    val delta: Int
)

/**
 * SQLite Database open helper for Kit-IME prediction and usage data.
 *
 * Configured in WAL mode with synchronous=NORMAL.
 * Enforces Zero Post-Commit Rewriting and supports snapshotting & integrity checks.
 */
class PredictorDatabase(
    private val context: Context,
    dbName: String = DB_NAME
) : SQLiteOpenHelper(context, dbName, null, DB_VERSION) {

    companion object {
        const val DB_NAME = "kit_predictor.db"
        const val DB_VERSION = 1

        @Volatile
        private var instance: PredictorDatabase? = null

        fun getInstance(context: Context): PredictorDatabase {
            return instance ?: synchronized(this) {
                instance ?: PredictorDatabase(context.applicationContext).also { instance = it }
            }
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.enableWriteAheadLogging()
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("PRAGMA synchronous = NORMAL;")

        // 1. corpus_words table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS corpus_words (
                word TEXT PRIMARY KEY COLLATE NOCASE,
                display_word TEXT,
                freq REAL,
                pos_start INTEGER DEFAULT 0,
                pos_mid INTEGER DEFAULT 0,
                pos_end INTEGER DEFAULT 0,
                is_custom INTEGER DEFAULT 0,
                last_updated INTEGER
            );
            """.trimIndent()
        )

        // 2. usage_words table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS usage_words (
                word TEXT PRIMARY KEY COLLATE NOCASE,
                display_word TEXT,
                confirmed_freq INTEGER DEFAULT 0,
                pos_start INTEGER DEFAULT 0,
                pos_mid INTEGER DEFAULT 0,
                pos_end INTEGER DEFAULT 0,
                is_custom INTEGER DEFAULT 0,
                last_used INTEGER
            );
            """.trimIndent()
        )

        // 3. bigrams table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS bigrams (
                prev_word TEXT COLLATE NOCASE,
                next_word TEXT COLLATE NOCASE,
                freq REAL,
                PRIMARY KEY (prev_word, next_word)
            );
            """.trimIndent()
        )

        // 4. typo_confusion table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS typo_confusion (
                bad_token TEXT COLLATE NOCASE,
                intended_token TEXT COLLATE NOCASE,
                slip_type TEXT,
                count INTEGER DEFAULT 1,
                last_seen INTEGER,
                PRIMARY KEY (bad_token, intended_token, slip_type)
            );
            """.trimIndent()
        )

        // 5. pending_firebase_deltas table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS pending_firebase_deltas (
                delta_key TEXT PRIMARY KEY,
                kind TEXT,
                delta INTEGER
            );
            """.trimIndent()
        )

        // 6. sync_cursors table
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS sync_cursors (
                source_key TEXT PRIMARY KEY,
                cursor_val TEXT
            );
            """.trimIndent()
        )

        // Indices for fast prefix lookup and ranking
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_corpus_freq ON corpus_words (freq DESC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_usage_freq ON usage_words (confirmed_freq DESC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_bigrams_prev ON bigrams (prev_word, freq DESC);")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_typo_bad ON typo_confusion (bad_token, count DESC);")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migration schema updates handled here
    }

    override fun onOpen(db: SQLiteDatabase) {
        super.onOpen(db)
        if (!db.isReadOnly) {
            db.execSQL("PRAGMA synchronous = NORMAL;")
        }
    }

    /**
     * Checkpoints the Write-Ahead Log with TRUNCATE.
     */
    fun checkpointWal(): Boolean {
        return try {
            val db = writableDatabase
            val cursor = db.rawQuery("PRAGMA wal_checkpoint(TRUNCATE);", null)
            val success = cursor.moveToFirst()
            cursor.close()
            success
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Runs PRAGMA integrity_check and returns true if 'ok'.
     */
    fun checkIntegrity(): Boolean {
        return try {
            val db = readableDatabase
            val cursor = db.rawQuery("PRAGMA integrity_check;", null)
            var isOk = false
            if (cursor.moveToFirst()) {
                val res = cursor.getString(0)
                isOk = "ok".equals(res, ignoreCase = true)
            }
            cursor.close()
            isOk
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Creates a daily VACUUM INTO snapshot and retains only the last 5 snapshots.
     */
    fun createDailySnapshot(): File? {
        val snapshotDir = File(context.filesDir, "snapshots")
        if (!snapshotDir.exists()) {
            snapshotDir.mkdirs()
        }

        checkpointWal()

        val timestamp = System.currentTimeMillis()
        val snapshotFile = File(snapshotDir, "kit_predictor_snapshot_$timestamp.db")

        try {
            val db = writableDatabase
            // VACUUM INTO creates a consistent online snapshot
            db.execSQL("VACUUM INTO ?", arrayOf(snapshotFile.absolutePath))

            // Prune old snapshots, retaining only last 5
            val allSnapshots = snapshotDir.listFiles { file ->
                file.name.startsWith("kit_predictor_snapshot_") && file.name.endsWith(".db")
            }?.sortedByDescending { it.lastModified() } ?: emptyList()

            if (allSnapshots.size > 5) {
                allSnapshots.drop(5).forEach { oldFile ->
                    oldFile.delete()
                }
            }

            return snapshotFile
        } catch (e: Exception) {
            // Fallback: direct file copy if VACUUM INTO not supported in current SQLite build
            return fallbackFileCopySnapshot(snapshotFile, snapshotDir)
        }
    }

    private fun fallbackFileCopySnapshot(targetFile: File, snapshotDir: File): File? {
        try {
            checkpointWal()
            val dbPath = context.getDatabasePath(databaseName)
            if (!dbPath.exists()) return null

            FileInputStream(dbPath).use { input ->
                FileOutputStream(targetFile).use { output ->
                    input.copyTo(output)
                }
            }

            val allSnapshots = snapshotDir.listFiles { file ->
                file.name.startsWith("kit_predictor_snapshot_") && file.name.endsWith(".db")
            }?.sortedByDescending { it.lastModified() } ?: emptyList()

            if (allSnapshots.size > 5) {
                allSnapshots.drop(5).forEach { it.delete() }
            }
            return targetFile
        } catch (e: Exception) {
            return null
        }
    }

    /**
     * Lists existing snapshot files.
     */
    fun listSnapshots(): List<File> {
        val snapshotDir = File(context.filesDir, "snapshots")
        if (!snapshotDir.exists()) return emptyList()
        return snapshotDir.listFiles { file ->
            file.name.startsWith("kit_predictor_snapshot_") && file.name.endsWith(".db")
        }?.sortedByDescending { it.lastModified() } ?: emptyList()
    }

    /**
     * Restores database from a given snapshot file.
     */
    @Synchronized
    fun restoreSnapshot(snapshotFile: File): Boolean {
        if (!snapshotFile.exists() || snapshotFile.length() == 0L) return false

        try {
            close()
            val dbFile = context.getDatabasePath(databaseName)
            val walFile = File(dbFile.parentFile, "${dbFile.name}-wal")
            val shmFile = File(dbFile.parentFile, "${dbFile.name}-shm")

            if (walFile.exists()) walFile.delete()
            if (shmFile.exists()) shmFile.delete()

            FileInputStream(snapshotFile).use { input ->
                FileOutputStream(dbFile).use { output ->
                    input.copyTo(output)
                }
            }

            // Verify integrity
            val isOk = checkIntegrity()
            return isOk
        } catch (e: Exception) {
            return false
        }
    }

    // --- Sync Cursors ---

    fun getSyncCursor(sourceKey: String): String? {
        val db = readableDatabase
        val cursor = db.rawQuery(
            "SELECT cursor_val FROM sync_cursors WHERE source_key = ? LIMIT 1",
            arrayOf(sourceKey)
        )
        var result: String? = null
        if (cursor.moveToFirst()) {
            result = cursor.getString(0)
        }
        cursor.close()
        return result
    }

    fun setSyncCursor(sourceKey: String, cursorVal: String) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("source_key", sourceKey)
            put("cursor_val", cursorVal)
        }
        db.insertWithOnConflict(
            "sync_cursors",
            null,
            values,
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    // --- Pending Firebase Deltas ---

    fun recordFirebaseDelta(key: String, kind: String, delta: Int = 1) {
        val db = writableDatabase
        db.execSQL(
            """
            INSERT INTO pending_firebase_deltas (delta_key, kind, delta)
            VALUES (?, ?, ?)
            ON CONFLICT(delta_key) DO UPDATE SET delta = delta + excluded.delta;
            """.trimIndent(),
            arrayOf(key, kind, delta)
        )
    }

    fun getPendingDeltas(): List<PendingFirebaseDelta> {
        val list = mutableListOf<PendingFirebaseDelta>()
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT delta_key, kind, delta FROM pending_firebase_deltas", null)
        while (cursor.moveToNext()) {
            list.add(
                PendingFirebaseDelta(
                    deltaKey = cursor.getString(0),
                    kind = cursor.getString(1),
                    delta = cursor.getInt(2)
                )
            )
        }
        cursor.close()
        return list
    }

    fun clearPendingDeltas() {
        val db = writableDatabase
        db.delete("pending_firebase_deltas", null, null)
    }
}

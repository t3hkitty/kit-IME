package com.kitime.ime.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * Result statistics from a Keep Notes import run.
 */
data class KeepImportStats(
    val filesScanned: Int = 0,
    val notesImported: Int = 0,
    val wordsIngested: Int = 0,
    val bigramsIngested: Int = 0,
    val errorMessage: String? = null
)

/**
 * Google Keep Notes Importer.
 *
 * Gated strictly on a configured notes folder / SAF vault URI (e.g. `meow-vault` or Google Keep export folder).
 * Parses imported Keep `.html`, `.txt`, and `.md` files, cleanly strips HTML formatting tags,
 * and seeds vocabulary into the local corpus without altering already-committed words.
 */
class KeepVaultImporter(
    private val context: Context,
    private val dbHelper: PredictorDatabase,
    private val gatherer: LocalCorpusGatherer
) {

    companion object {
        private val HTML_TAG_REGEX = Regex("""<[^>]*>""")
        private val HTML_ENTITY_REGEX = Regex("""&(?:nbsp|amp|quot|lt|gt);""")
    }

    /**
     * Executes Keep Vault Import.
     * Returns error message if no vault folder is configured.
     */
    fun importKeepVault(vaultTreeUri: Uri?): KeepImportStats {
        if (vaultTreeUri == null) {
            return KeepImportStats(
                errorMessage = "No notes folder or vault configured! Please pick a vault folder first 🌸"
            )
        }

        val rootDoc = DocumentFile.fromTreeUri(context, vaultTreeUri)
            ?: return KeepImportStats(errorMessage = "Cannot access configured notes folder!")

        var filesScanned = 0
        var notesImported = 0
        var wordsCount = 0
        var bigramsCount = 0

        // Take safety snapshot before ingesting Keep notes
        dbHelper.createDailySnapshot()

        fun scanKeepDir(dir: DocumentFile) {
            for (file in dir.listFiles()) {
                val name = file.name ?: continue

                // Skip non-note files & build artifacts
                if (name.startsWith(".") ||
                    name.equals("build", ignoreCase = true) ||
                    name.endsWith(".json", ignoreCase = true) ||
                    name.endsWith(".apk", ignoreCase = true)
                ) {
                    continue
                }

                if (file.isDirectory) {
                    scanKeepDir(file)
                } else if (file.isFile) {
                    val isKeepNote = name.endsWith(".html", ignoreCase = true) ||
                            name.endsWith(".txt", ignoreCase = true) ||
                            name.endsWith(".md", ignoreCase = true)

                    if (isKeepNote) {
                        filesScanned++
                        val noteText = readAndCleanKeepNote(file)
                        if (noteText.isNotBlank()) {
                            val (w, b) = gatherer.ingestTextSnippet(noteText)
                            wordsCount += w
                            bigramsCount += b
                            notesImported++
                        }
                    }
                }
            }
        }

        scanKeepDir(rootDoc)
        dbHelper.checkpointWal()

        return KeepImportStats(
            filesScanned = filesScanned,
            notesImported = notesImported,
            wordsIngested = wordsCount,
            bigramsIngested = bigramsCount
        )
    }

    private fun readAndCleanKeepNote(file: DocumentFile): String {
        return try {
            val raw = context.contentResolver.openInputStream(file.uri)?.use { stream ->
                BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
            } ?: ""

            // Clean HTML if .html
            var text = raw
            if (file.name?.endsWith(".html", ignoreCase = true) == true) {
                text = HTML_TAG_REGEX.replace(text, " ")
                text = HTML_ENTITY_REGEX.replace(text, " ")
            }
            text.trim()
        } catch (e: Exception) {
            ""
        }
    }
}

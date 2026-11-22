package com.kitime.ime.data

import android.util.Log
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

/**
 * Remote Google Docs / Google Apps Script Corpus Source Fetcher.
 *
 * Capabilities:
 * 1. Automatically normalizes standard Google Doc URLs (https://docs.google.com/document/d/<id>/...)
 *    to direct text export URLs: https://docs.google.com/document/d/<id>/export?format=txt
 * 2. Follows HTTP 302/303/307 redirects (standard for googleusercontent.com download endpoints).
 * 3. Supports Google Apps Script Web App manifest URLs (?token=... returning { "parts": [...] }
 *    and ?token=...&part=N returning plain text parts).
 * 4. Streams lines line-by-line using buffered readers to prevent Out-Of-Memory (OOM) on mobile.
 * 5. Computes and checks per-source ETag / MD5 content hashes against `sync_cursors` so unchanged
 *    documents are skipped with zero processing overhead.
 */
class GoogleDocsCorpusFetcher(
    private val dbHelper: PredictorDatabase? = null
) {

    companion object {
        private const val TAG = "KitImeDocFetcher"
        private const val TIMEOUT_MS = 15000
        private const val MAX_REDIRECTS = 5

        // Regex to extract Doc ID from standard URLs
        private val DOC_ID_REGEX = Regex("""/document/d/([a-zA-Z0-9_-]+)""")
    }

    /**
     * Normalized URL representation.
     */
    data class FetchTarget(
        val originalUrl: String,
        val downloadUrl: String,
        val isAppsScriptWebApp: Boolean
    )

    /**
     * Result metrics from fetching remote Google Docs.
     */
    data class DocFetchStats(
        val docsScanned: Int = 0,
        val docsSkippedUnchanged: Int = 0,
        val wordsIngested: Int = 0,
        val bigramsIngested: Int = 0
    )

    /**
     * Normalizes a Google Doc URL or Apps Script URL.
     */
    fun normalizeUrl(rawUrl: String): FetchTarget {
        val trimmed = rawUrl.trim()
        val docMatch = DOC_ID_REGEX.find(trimmed)

        if (docMatch != null) {
            val docId = docMatch.groupValues[1]
            val exportUrl = "https://docs.google.com/document/d/$docId/export?format=txt"
            return FetchTarget(trimmed, exportUrl, isAppsScriptWebApp = false)
        }

        val isWebApp = trimmed.contains("script.google.com") && trimmed.contains("exec")
        return FetchTarget(trimmed, trimmed, isAppsScriptWebApp = isWebApp)
    }

    /**
     * Fetches all configured doc URLs and ingests them into the corpus via the gatherer.
     */
    fun fetchAndIngest(
        urls: List<String>,
        gatherer: LocalCorpusGatherer,
        onLineProcessed: ((String) -> Unit)? = null
    ): DocFetchStats {
        var scanned = 0
        var skipped = 0
        var totalWords = 0
        var totalBigrams = 0

        for (rawUrl in urls) {
            if (rawUrl.isBlank()) continue
            val target = normalizeUrl(rawUrl)
            scanned++

            try {
                if (target.isAppsScriptWebApp) {
                    val stats = fetchAppsScriptManifestAndParts(target, gatherer)
                    totalWords += stats.first
                    totalBigrams += stats.second
                    if (stats.first == 0 && stats.second == 0) skipped++
                } else {
                    val stats = fetchDirectDocExport(target, gatherer)
                    if (stats == null) {
                        skipped++
                    } else {
                        totalWords += stats.first
                        totalBigrams += stats.second
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error fetching document: ${target.downloadUrl}", e)
            }
        }

        return DocFetchStats(
            docsScanned = scanned,
            docsSkippedUnchanged = skipped,
            wordsIngested = totalWords,
            bigramsIngested = totalBigrams
        )
    }

    /**
     * Fetches a Google Doc via /export?format=txt with redirect handling and streaming hash check.
     */
    private fun fetchDirectDocExport(
        target: FetchTarget,
        gatherer: LocalCorpusGatherer
    ): Pair<Int, Int>? {
        val cursorKey = "doc_hash_${target.downloadUrl.hashCode()}"
        val lastHash = dbHelper?.getSyncCursor(cursorKey)

        val connection = openConnectionWithRedirects(target.downloadUrl)
        val etag = connection.getHeaderField("ETag")

        // If ETag exists and matches cursor, skip immediately without reading body
        if (!etag.isNullOrBlank() && etag == lastHash) {
            connection.disconnect()
            return null
        }

        val md5 = MessageDigest.getInstance("MD5")
        val lines = mutableListOf<String>()

        connection.inputStream.use { inputStream ->
            BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    val current = line ?: continue
                    md5.update(current.toByteArray(Charsets.UTF_8))
                    lines.add(current)
                }
            }
        }
        connection.disconnect()

        val contentHash = bytesToHex(md5.digest())

        // Check if content hash matches previous sync cursor
        if (contentHash == lastHash) {
            return null
        }

        // Ingest streaming lines into corpus
        val fullText = lines.joinToString("\n")
        val result = gatherer.ingestTextSnippet(fullText)

        // Update cursor with latest hash
        val finalCursorVal = if (!etag.isNullOrBlank()) etag else contentHash
        dbHelper?.setSyncCursor(cursorKey, finalCursorVal)

        return result
    }

    /**
     * Fetches Google Apps Script Web App returning JSON parts manifest or raw text.
     */
    private fun fetchAppsScriptManifestAndParts(
        target: FetchTarget,
        gatherer: LocalCorpusGatherer
    ): Pair<Int, Int> {
        val cursorKey = "gas_hash_${target.downloadUrl.hashCode()}"
        val lastCursor = dbHelper?.getSyncCursor(cursorKey)

        val conn = openConnectionWithRedirects(target.downloadUrl)
        val bodyText = conn.inputStream.use { stream ->
            BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { it.readText() }
        }
        conn.disconnect()

        if (bodyText.isBlank()) return Pair(0, 0)

        // Try JSON manifest parsing { "updated": "...", "parts": [1, 2, ...] }
        val json = try {
            JSONObject(bodyText)
        } catch (e: Exception) {
            null
        }

        if (json != null && json.has("parts")) {
            val updated = json.optString("updated", "")
            if (updated.isNotBlank() && updated == lastCursor) {
                return Pair(0, 0) // Unchanged
            }

            val partsArray = json.getJSONArray("parts")
            var totalWords = 0
            var totalBigrams = 0

            for (i in 0 until partsArray.length()) {
                val partNum = partsArray.getInt(i)
                val partUrl = if (target.downloadUrl.contains("?")) {
                    "${target.downloadUrl}&part=$partNum"
                } else {
                    "${target.downloadUrl}?part=$partNum"
                }
                val partConn = openConnectionWithRedirects(partUrl)
                val partContent = partConn.inputStream.use { it.bufferedReader().readText() }
                partConn.disconnect()

                val (w, b) = gatherer.ingestTextSnippet(partContent)
                totalWords += w
                totalBigrams += b
            }

            if (updated.isNotBlank()) {
                dbHelper?.setSyncCursor(cursorKey, updated)
            }
            return Pair(totalWords, totalBigrams)
        } else {
            // Direct plain text response from Apps Script
            val md5 = MessageDigest.getInstance("MD5")
            val hash = bytesToHex(md5.digest(bodyText.toByteArray(Charsets.UTF_8)))
            if (hash == lastCursor) {
                return Pair(0, 0)
            }

            val result = gatherer.ingestTextSnippet(bodyText)
            dbHelper?.setSyncCursor(cursorKey, hash)
            return result
        }
    }

    /**
     * Follows HTTP/HTTPS redirects up to MAX_REDIRECTS to handle Google Drive & googleusercontent.
     */
    private fun openConnectionWithRedirects(initialUrl: String): HttpURLConnection {
        var currentUrl = initialUrl
        var redirects = 0

        while (redirects < MAX_REDIRECTS) {
            val url = URL(currentUrl)
            val conn = url.openConnection() as HttpURLConnection
            conn.instanceFollowRedirects = false
            conn.connectTimeout = TIMEOUT_MS
            conn.readTimeout = TIMEOUT_MS
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android; Kit-IME CorpusFetcher/3.1)")

            val status = conn.responseCode
            if (status == HttpURLConnection.HTTP_MOVED_TEMP ||
                status == HttpURLConnection.HTTP_MOVED_PERM ||
                status == HttpURLConnection.HTTP_SEE_OTHER ||
                status == 307 || status == 308
            ) {
                val redirectUrl = conn.getHeaderField("Location")
                conn.disconnect()
                if (redirectUrl != null) {
                    currentUrl = if (redirectUrl.startsWith("/")) {
                        val base = URL(initialUrl)
                        "${base.protocol}://${base.host}$redirectUrl"
                    } else {
                        redirectUrl
                    }
                    redirects++
                    continue
                }
            }

            if (status == HttpURLConnection.HTTP_OK) {
                return conn
            } else {
                conn.disconnect()
                throw java.io.IOException("HTTP error response: $status for $currentUrl")
            }
        }

        throw java.io.IOException("Too many redirects: $redirects for $initialUrl")
    }

    private fun bytesToHex(bytes: ByteArray): String {
        val sb = StringBuilder()
        for (b in bytes) {
            sb.append(String.format("%02x", b))
        }
        return sb.toString()
    }
}

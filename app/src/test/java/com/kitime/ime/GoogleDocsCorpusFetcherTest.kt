package com.kitime.ime

import com.kitime.ime.data.GoogleDocsCorpusFetcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Unit tests for GoogleDocsCorpusFetcher URL normalization and streaming hash detection.
 */
class GoogleDocsCorpusFetcherTest {

    private val fetcher = GoogleDocsCorpusFetcher(dbHelper = null)

    @Test
    fun testGoogleDocUrlNormalization() {
        val standardUrl = "https://docs.google.com/document/d/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms/edit?usp=sharing"
        val normalized = fetcher.normalizeUrl(standardUrl)

        assertFalse("Should not be flagged as web app", normalized.isAppsScriptWebApp)
        assertEquals(
            "https://docs.google.com/document/d/1BxiMVs0XRA5nFMdKvBdBZjgmUUqptlbs74OgvE2upms/export?format=txt",
            normalized.downloadUrl
        )
    }

    @Test
    fun testGoogleDocUrlWithTrailingSlashNormalization() {
        val rawUrl = "https://docs.google.com/document/d/2AbCdEfGhIjKlMnOpQrStUvWxYz_1234567890/"
        val normalized = fetcher.normalizeUrl(rawUrl)

        assertEquals(
            "https://docs.google.com/document/d/2AbCdEfGhIjKlMnOpQrStUvWxYz_1234567890/export?format=txt",
            normalized.downloadUrl
        )
    }

    @Test
    fun testAppsScriptWebAppUrlDetection() {
        val webAppUrl = "https://script.google.com/macros/s/AKfycbx_placeholder/exec?token=mysecrettoken"
        val normalized = fetcher.normalizeUrl(webAppUrl)

        assertTrue("Should detect Google Apps Script Web App", normalized.isAppsScriptWebApp)
        assertEquals(webAppUrl, normalized.downloadUrl)
    }
}

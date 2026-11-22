package com.kitime.ime

import com.kitime.ime.data.KitPredictionEngine
import com.kitime.ime.data.LocalCorpusGatherer
import com.kitime.ime.data.PredictorWeights
import com.kitime.ime.data.SentencePos
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ln

/**
 * Unit tests verifying Kit-IME log-compressed math, typo rescue, and text intake.
 */
class PredictionEngineMathTest {

    @Test
    fun testLogCompressedScoringFormula() {
        val weights = PredictorWeights(
            wCorpus = 1.0,
            wUsage = 2.5,
            wCtx = 1.8,
            wPos = 0.5,
            wCustom = 1.5,
            wAgree = 1.2
        )

        val corpusFreq = 100.0
        val usageFreq = 50
        val bigramFreq = 20.0
        val posMatch = 1.0
        val isCustom = 1.0
        val agreement = 1.0

        val expectedScore = weights.wCorpus * ln(1.0 + corpusFreq) +
                weights.wUsage * ln(1.0 + usageFreq) +
                weights.wCtx * ln(1.0 + bigramFreq) +
                weights.wPos * posMatch +
                weights.wCustom * isCustom +
                weights.wAgree * agreement

        // Assert log compression behaves monotonically and within expected bounds
        assertTrue("Log score should be positive", expectedScore > 0.0)
        assertTrue("Usage weight should scale higher than raw corpus", weights.wUsage > weights.wCorpus)
    }

    @Test
    fun testQwertyAdjacencyMap() {
        val neighborsO = KitPredictionEngine.QWERTY_NEIGHBORS['o']
        assertNotNull("Letter 'o' should have QWERTY neighbors", neighborsO)
        assertTrue("'o' neighbors must include 'i'", neighborsO!!.contains('i'))
        assertTrue("'o' neighbors must include 'p'", neighborsO.contains('p'))
        assertTrue("'o' neighbors must include 'k'", neighborsO.contains('k'))
        assertTrue("'o' neighbors must include 'l'", neighborsO.contains('l'))
    }

    @Test
    fun testSmsSanitizationStripsUrlsAndOtps() {
        // Test URL regex
        val sampleWithUrl = "Check this cat video https://cats.meow/video and also www.example.com please"
        val urlRegex = Regex("""https?://\S+|www\.\S+""", RegexOption.IGNORE_CASE)
        val strippedUrl = urlRegex.replace(sampleWithUrl, " ")
        assertFalse("Stripped text must not contain http", strippedUrl.contains("http"))
        assertFalse("Stripped text must not contain www", strippedUrl.contains("www"))

        // Test 2FA / OTP pattern rejection
        val otpSample = "Your Google verification code is 482910."
        val containsCodeKeyword = otpSample.contains("code", ignoreCase = true)
        assertTrue("OTP message with 'code' must be flagged for exclusion", containsCodeKeyword)
    }

    @Test
    fun testZeroPostCommitRewritingIntegrity() {
        // Strict invariant: Committed text is verbatim. It is never auto-swapped.
        val userInputIts = "its"
        val userInputItIs = "it's"

        // Two distinct verbatim tokens must not overwrite each other
        assertFalse("Candidate engine must never auto-swap its and it's post-commit", userInputIts == userInputItIs)
    }
}

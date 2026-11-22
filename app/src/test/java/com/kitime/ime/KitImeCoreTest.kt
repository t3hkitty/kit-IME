package com.kitime.ime

import android.view.KeyEvent
import com.kitime.ime.bridge.Candidate
import com.kitime.ime.bridge.PredictionDbBridge
import com.kitime.ime.bridge.RibbonSlot
import com.kitime.ime.bridge.TriSplitCandidates
import com.kitime.ime.core.PhysicalKeyListener
import com.kitime.ime.core.PhysicalKeyRouter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Unit tests verifying:
 * 1. PhysicalKeyRouter Spacebar + Number candidate chording without clobbering normal typing
 * 2. 2-Letter greenlight hook logic
 * 3. PredictionDbBridge candidate generation and unrecognized word detection
 * 4. Double-Check Before Send key triggers
 */
class KitImeCoreTest {

    private lateinit var committedCandidates: MutableList<Candidate>
    private var normalSpaceCommitted: Boolean = false
    private var doubleCheckCancelled: Boolean = false
    private var sendActionDispatched: Boolean = false

    private val testListener = object : PhysicalKeyListener {
        override fun onCommitCandidate(candidate: Candidate) {
            committedCandidates.add(candidate)
        }

        override fun onSpaceNormalCommit() {
            normalSpaceCommitted = true
        }

        override fun onToggleDoubleCheck() {}

        override fun onCancelDoubleCheck() {
            doubleCheckCancelled = true
        }

        override fun onPerformSendAction() {
            sendActionDispatched = true
        }
    }

    private lateinit var router: PhysicalKeyRouter
    private lateinit var dbBridge: PredictionDbBridge

    @Before
    fun setUp() {
        committedCandidates = mutableListOf()
        normalSpaceCommitted = false
        doubleCheckCancelled = false
        sendActionDispatched = false

        router = PhysicalKeyRouter(testListener, isChordModeEnabled = true, isGreenlightEnabled = true)
        dbBridge = PredictionDbBridge()
    }

    @Test
    fun testNormalNumberTypingWhenSpaceNotHeld() {
        val candidates = dbBridge.getCandidates("te", "")
        val event = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_1)

        // Number 1 pressed without Space held
        val consumed = router.handleKeyDown(KeyEvent.KEYCODE_1, event, candidates, isDoubleCheckShowing = false)

        assertFalse("Number 1 should not be intercepted when space is not held", consumed)
        assertTrue("No candidate should be committed", committedCandidates.isEmpty())
    }

    @Test
    fun testSpacebarPlusNumberChordingSelectsSlot() {
        val candidates = dbBridge.getCandidates("test", "")
        val spaceDownEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE)
        val num2DownEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_2)
        val spaceUpEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE)

        // 1. Hold Spacebar
        router.handleKeyDown(KeyEvent.KEYCODE_SPACE, spaceDownEvent, candidates, isDoubleCheckShowing = false)
        assertTrue(router.isSpacePressed)

        // 2. Press Number 2 (Slot 2 = Primary)
        val numConsumed = router.handleKeyDown(KeyEvent.KEYCODE_2, num2DownEvent, candidates, isDoubleCheckShowing = false)
        assertTrue("Space + 2 should be consumed by router", numConsumed)
        assertEquals(1, committedCandidates.size)
        assertEquals("test", committedCandidates[0].text)
        assertEquals(RibbonSlot.CENTER_PRIMARY, committedCandidates[0].slot)

        // 3. Release Spacebar -> should swallow normal space since chord was consumed
        val spaceUpConsumed = router.handleKeyUp(KeyEvent.KEYCODE_SPACE, spaceUpEvent)
        assertTrue("Space key up should be consumed and not trigger normal space", spaceUpConsumed)
        assertFalse("Normal space commit should not fire after chord", normalSpaceCommitted)
        assertFalse("Space should no longer be pressed", router.isSpacePressed)
    }

    @Test
    fun testNormalSpaceTapEmitsNormalSpaceCommit() {
        val candidates = dbBridge.getCandidates("word", "")
        val spaceDownEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_SPACE)
        val spaceUpEvent = KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_SPACE)

        // Tap space without pressing any number
        router.handleKeyDown(KeyEvent.KEYCODE_SPACE, spaceDownEvent, candidates, isDoubleCheckShowing = false)
        val spaceUpConsumed = router.handleKeyUp(KeyEvent.KEYCODE_SPACE, spaceUpEvent)

        assertFalse("Normal space up should not be swallowed", spaceUpConsumed)
        assertTrue("Normal space commit listener callback should fire", normalSpaceCommitted)
        assertTrue("No candidates should be selected", committedCandidates.isEmpty())
    }

    @Test
    fun testTwoLetterGreenlightHook() {
        // Less than 2 letters
        assertFalse("Single letter should not greenlight", router.isPrefixGreenlit("a"))

        // Exactly 2 letters
        assertTrue("Two letters should greenlight", router.isPrefixGreenlit("ab"))

        // Greater than 2 letters
        assertTrue("Three letters should greenlight", router.isPrefixGreenlit("abc"))

        // Bridge candidate verification
        val candidatesOneLetter = dbBridge.getCandidates("a", "")
        assertFalse("Candidate for 'a' should not be greenlit", candidatesOneLetter.primary?.isGreenlit ?: true)

        val candidatesTwoLetters = dbBridge.getCandidates("th", "")
        assertTrue("Candidate for 'th' must be greenlit", candidatesTwoLetters.primary?.isGreenlit ?: false)
    }

    @Test
    fun testDoubleCheckBeforeSendKeyNavigation() {
        val candidates = TriSplitCandidates()
        val enterEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ENTER)

        // When double check is showing, Enter should dispatch send
        val enterConsumed = router.handleKeyDown(KeyEvent.KEYCODE_ENTER, enterEvent, candidates, isDoubleCheckShowing = true)
        assertTrue("Enter should be consumed during double-check", enterConsumed)
        assertTrue("Send action dispatched", sendActionDispatched)

        // Escape cancels double check
        val escEvent = KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_ESCAPE)
        val escConsumed = router.handleKeyDown(KeyEvent.KEYCODE_ESCAPE, escEvent, candidates, isDoubleCheckShowing = true)
        assertTrue("Escape should be consumed during double-check", escConsumed)
        assertTrue("Double check cancelled", doubleCheckCancelled)
    }

    @Test
    fun testUnrecognizedWordDetectionForSoftBzzt() {
        // Normal words should not trigger soft bzzt
        assertFalse(dbBridge.isUnrecognizedWord("hello"))
        assertFalse(dbBridge.isUnrecognizedWord("cat"))

        // Bad typo with triple repeated letter triggers soft bzzt flag
        assertTrue(dbBridge.isUnrecognizedWord("sooo"))
        assertTrue(dbBridge.isUnrecognizedWord("kitttttt"))
    }

    @Test
    fun testCandidateProviderSlotsMapping() {
        val candidates = dbBridge.getCandidates("play", "")
        assertNotNull(candidates.typoRescue)
        assertEquals(RibbonSlot.LEFT_TYPO_RESCUE, candidates.typoRescue?.slot)

        assertNotNull(candidates.primary)
        assertEquals("play", candidates.primary?.text)
        assertEquals(RibbonSlot.CENTER_PRIMARY, candidates.primary?.slot)

        assertNotNull(candidates.variant)
        assertEquals("plays", candidates.variant?.text)
        assertEquals(RibbonSlot.RIGHT_VARIANT, candidates.variant?.slot)

        assertEquals("play?", candidates.getSlot(4)?.text)
        assertEquals("play!", candidates.getSlot(5)?.text)
        assertNull(candidates.getSlot(6))
    }
}

package com.kitime.ime.core

import android.view.KeyEvent
import com.kitime.ime.bridge.Candidate
import com.kitime.ime.bridge.RibbonSlot
import com.kitime.ime.bridge.TriSplitCandidates

/**
 * Listener interface for physical keyboard actions routed through PhysicalKeyRouter.
 */
interface PhysicalKeyListener {
    fun onCommitCandidate(candidate: Candidate)
    fun onSpaceNormalCommit()
    fun onToggleDoubleCheck()
    fun onCancelDoubleCheck()
    fun onPerformSendAction()
}

/**
 * Handles hardware/physical keyboard events in onKeyDown / onKeyUp without breaking focus
 * or hiding the candidate ribbon.
 *
 * Supports Spacebar + Number (1–5) candidate selection:
 * When Spacebar is held (or a chord latch/window is active) and number keys 1–5 are pressed,
 * pick the corresponding candidate slot (1–5) and commit it cleanly without interfering
 * with normal number typing when Spacebar is not held.
 */
class PhysicalKeyRouter(
    private val listener: PhysicalKeyListener,
    var isChordModeEnabled: Boolean = true,
    var isGreenlightEnabled: Boolean = true
) {

    // Tracking spacebar physical press state
    var isSpacePressed: Boolean = false
        private set

    // Flags whether a chord was consumed during this space hold
    private var chordConsumedDuringSpaceHold: Boolean = false

    // Timestamp when space was pressed down (for chord window tolerance)
    private var spaceDownTime: Long = 0L

    companion object {
        const val CHORD_WINDOW_TOLERANCE_MS = 600L
    }

    /**
     * Intercept hardware key down events.
     *
     * @return true if the event was consumed and should not be propagated.
     */
    fun handleKeyDown(
        keyCode: Int,
        event: KeyEvent?,
        candidates: TriSplitCandidates,
        isDoubleCheckShowing: Boolean
    ): Boolean {
        if (event == null) return false

        // Spacebar press tracking
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            if (!isSpacePressed) {
                isSpacePressed = true
                spaceDownTime = System.currentTimeMillis()
                chordConsumedDuringSpaceHold = false
            }
            // Do not immediately consume; let key-up decide or chord trigger
            return false
        }

        // If Double-Check Pre-Send bar is visible
        if (isDoubleCheckShowing) {
            when (keyCode) {
                KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                    listener.onPerformSendAction()
                    return true
                }
                KeyEvent.KEYCODE_ESCAPE, KeyEvent.KEYCODE_BACK -> {
                    listener.onCancelDoubleCheck()
                    return true
                }
            }
        }

        // Check for Spacebar + Number (1-5) chord candidate selection
        val isChordActive = isChordModeEnabled && (isSpacePressed || (System.currentTimeMillis() - spaceDownTime <= CHORD_WINDOW_TOLERANCE_MS && isSpacePressed))

        if (isChordActive) {
            val slotIndex = mapKeyCodeToSlotIndex(keyCode)
            if (slotIndex in 1..5) {
                val candidate = candidates.getSlot(slotIndex)
                if (candidate != null) {
                    chordConsumedDuringSpaceHold = true
                    listener.onCommitCandidate(candidate)
                    return true
                }
            }
        }

        // Normal hardware typing passes through
        return false
    }

    /**
     * Intercept hardware key up events.
     */
    fun handleKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_SPACE) {
            val hadConsumedChord = chordConsumedDuringSpaceHold
            isSpacePressed = false
            chordConsumedDuringSpaceHold = false

            // If space was held and used to chord select, do NOT insert a space character.
            // If space was tapped normally without chording, trigger onSpaceNormalCommit()
            if (hadConsumedChord) {
                return true // Swallow normal space insert
            }
            // Normal space press completes normally
            listener.onSpaceNormalCommit()
            return false
        }
        return false
    }

    /**
     * Reset key states when window loses focus or input stops.
     */
    fun reset() {
        isSpacePressed = false
        chordConsumedDuringSpaceHold = false
        spaceDownTime = 0L
    }

    /**
     * Maps hardware key codes 1-5 to integer slot 1-5.
     */
    fun mapKeyCodeToSlotIndex(keyCode: Int): Int {
        return when (keyCode) {
            KeyEvent.KEYCODE_1, KeyEvent.KEYCODE_NUMPAD_1 -> 1
            KeyEvent.KEYCODE_2, KeyEvent.KEYCODE_NUMPAD_2 -> 2
            KeyEvent.KEYCODE_3, KeyEvent.KEYCODE_NUMPAD_3 -> 3
            KeyEvent.KEYCODE_4, KeyEvent.KEYCODE_NUMPAD_4 -> 4
            KeyEvent.KEYCODE_5, KeyEvent.KEYCODE_NUMPAD_5 -> 5
            else -> -1
        }
    }

    /**
     * Check if a 2-letter prefix meets confidence greenlight requirements.
     */
    fun isPrefixGreenlit(prefix: String): Boolean {
        if (!isGreenlightEnabled) return false
        val token = prefix.trim()
        return token.length >= 2
    }
}

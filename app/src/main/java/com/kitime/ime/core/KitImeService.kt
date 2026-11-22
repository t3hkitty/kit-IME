package com.kitime.ime.core

import android.inputmethodservice.InputMethodService
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.widget.Button
import android.widget.LinearLayout
import com.kitime.ime.R
import com.kitime.ime.bridge.Candidate
import com.kitime.ime.bridge.PredictionDbBridge
import com.kitime.ime.bridge.RibbonSlot
import com.kitime.ime.bridge.TriSplitCandidates
import com.kitime.ime.haptic.SoftBzztHaptic
import com.kitime.ime.pref.KitImePreferences
import com.kitime.ime.ui.TriSplitRibbonView

/**
 * Kit-IME Core InputMethodService.
 *
 * Enforces:
 * - 3-zone prediction ribbon (Typo Rescue, Primary Prediction, Variant)
 * - Zero post-commit rewriting: commits are verbatim strings; never retroactively mutates past text.
 * - Hardware key routing & Spacebar + Number candidate chord selection.
 * - Soft spelling bzzt haptic on badly spelled/unrecognized word boundaries.
 * - Scaffold for 2-letter greenlighting and Double-Check Before Send.
 */
class KitImeService : InputMethodService(), PhysicalKeyListener {

    private lateinit var prefs: KitImePreferences
    private lateinit var haptic: SoftBzztHaptic
    private lateinit var dbBridge: PredictionDbBridge
    private lateinit var keyRouter: PhysicalKeyRouter

    private var rootView: View? = null
    private var ribbonView: TriSplitRibbonView? = null
    private var sendGuardBar: LinearLayout? = null
    private var btnSendConfirm: Button? = null
    private var btnSendCancel: Button? = null

    private var currentCandidates = TriSplitCandidates()
    private var isDoubleCheckActive = false

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        prefs = KitImePreferences(this)
        haptic = SoftBzztHaptic(this)
        dbBridge = PredictionDbBridge(this)
        keyRouter = PhysicalKeyRouter(
            listener = this,
            isChordModeEnabled = prefs.isSpaceChordModeEnabled,
            isGreenlightEnabled = prefs.isGreenlightEnabled
        )
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.view_ime_panel, null)
        rootView = view
        ribbonView = view.findViewById(R.id.tri_split_ribbon)
        sendGuardBar = view.findViewById(R.id.layout_send_guard_bar)
        btnSendConfirm = view.findViewById(R.id.btn_send_confirm)
        btnSendCancel = view.findViewById(R.id.btn_send_cancel)

        ribbonView?.onCandidateSelectedListener = { candidate ->
            onCommitCandidate(candidate)
        }

        btnSendConfirm?.setOnClickListener {
            onPerformSendAction()
        }

        btnSendCancel?.setOnClickListener {
            onCancelDoubleCheck()
        }

        return view
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        keyRouter.reset()
        isDoubleCheckActive = false
        sendGuardBar?.visibility = View.GONE
        updateCandidates("")
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        keyRouter.isChordModeEnabled = prefs.isSpaceChordModeEnabled
        keyRouter.isGreenlightEnabled = prefs.isGreenlightEnabled
        refreshCurrentInputPrefix()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        // Physical keyboard event interception
        val consumed = keyRouter.handleKeyDown(
            keyCode = keyCode,
            event = event,
            candidates = currentCandidates,
            isDoubleCheckShowing = isDoubleCheckActive
        )
        if (consumed) return true

        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        val consumed = keyRouter.handleKeyUp(keyCode, event)
        if (consumed) return true

        // Refresh prefix after hardware keyup
        mainHandler.post { refreshCurrentInputPrefix() }
        return super.onKeyUp(keyCode, event)
    }

    /**
     * Intercept hardware or software action keys (e.g. Enter with IME_ACTION_SEND).
     */
    fun triggerSendGuardOrDispatch() {
        if (prefs.isPreSendDoubleCheckEnabled && !isDoubleCheckActive) {
            onToggleDoubleCheck()
            return
        }
        onPerformSendAction()
    }

    /**
     * Commit candidate verbatim without any retroactive post-commit rewriting.
     */
    override fun onCommitCandidate(candidate: Candidate) {
        val ic = currentInputConnection ?: return

        // 1. Delete currently typed composing prefix (if any)
        val prefix = getCurrentWordPrefix()
        if (prefix.isNotEmpty()) {
            ic.deleteSurroundingText(prefix.length, 0)
        }

        // 2. Commit EXACT candidate text verbatim with zero retroactive changes
        ic.commitText(candidate.text, 1)

        // 3. Inform bridge confirmation sink
        dbBridge.recordCommit(candidate.text, candidateSelected = true, slot = candidate.slot)

        // 4. Update ribbon
        updateCandidates("")
    }

    override fun onSpaceNormalCommit() {
        val ic = currentInputConnection ?: return
        val prefix = getCurrentWordPrefix()

        // Check if word boundary was an unrecognized / badly-spelled token
        if (prefix.isNotBlank() && prefs.isSoftBzztEnabled && dbBridge.isUnrecognizedWord(prefix)) {
            haptic.triggerBzzt()
        }

        // Record normal space boundary commit; zero retroactive rewrites
        if (prefix.isNotBlank()) {
            dbBridge.recordCommit(prefix, candidateSelected = false, slot = null)
        }

        ic.commitText(" ", 1)
        updateCandidates("")
    }

    override fun onToggleDoubleCheck() {
        isDoubleCheckActive = true
        sendGuardBar?.visibility = View.VISIBLE
    }

    override fun onCancelDoubleCheck() {
        isDoubleCheckActive = false
        sendGuardBar?.visibility = View.GONE
    }

    override fun onPerformSendAction() {
        isDoubleCheckActive = false
        sendGuardBar?.visibility = View.GONE
        val ic = currentInputConnection ?: return
        ic.performEditorAction(EditorInfo.IME_ACTION_SEND)
    }

    /**
     * Inspect text before cursor to extract active word prefix token.
     */
    private fun getCurrentWordPrefix(): String {
        val ic = currentInputConnection ?: return ""
        val textBefore = ic.getTextBeforeCursor(40, 0)?.toString() ?: return ""
        val lastDelimiterIndex = textBefore.lastIndexOfAny(charArrayOf(' ', '\n', '\t', '.', ',', ';', ':', '!', '?'))
        return if (lastDelimiterIndex == -1) {
            textBefore
        } else {
            textBefore.substring(lastDelimiterIndex + 1)
        }
    }

    private fun refreshCurrentInputPrefix() {
        val prefix = getCurrentWordPrefix()
        updateCandidates(prefix)
    }

    private fun updateCandidates(prefix: String) {
        val ic = currentInputConnection
        val contextBefore = ic?.getTextBeforeCursor(80, 0)?.toString() ?: ""
        currentCandidates = dbBridge.getCandidates(prefix, contextBefore)
        ribbonView?.setCandidates(currentCandidates)
    }

    override fun onDestroy() {
        super.onDestroy()
        rootView = null
        ribbonView = null
        sendGuardBar = null
    }
}

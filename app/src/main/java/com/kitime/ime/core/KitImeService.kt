package com.kitime.ime.core

import android.content.ClipboardManager
import android.content.Context
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
import com.kitime.ime.data.ClipboardShelfManager
import com.kitime.ime.haptic.SoftBzztHaptic
import com.kitime.ime.pref.KitImePreferences
import com.kitime.ime.ui.ArrowPadPopup
import com.kitime.ime.ui.ClipboardShelfPopup
import com.kitime.ime.ui.EmojiSymbolDrawer
import com.kitime.ime.ui.KitTheme
import com.kitime.ime.ui.SoftKeyGridView
import com.kitime.ime.ui.ThemeManager
import com.kitime.ime.ui.TriSplitRibbonView

/**
 * Kit-IME Core InputMethodService.
 *
 * Full-featured daily driver with Gboard-style essentials:
 * - Selectable Theme palettes applied instantly across all views
 * - Quick toolbar with Arrows Pad, Emoji & Symbol Drawer, and Clipboard Shelf
 * - Long-press popups for alternate characters/diacritics
 * - Zero post-commit rewriting guarantee (commits verbatim strings; never auto-replaces)
 * - Spacebar + Number 1..5 chording
 * - Soft 18ms spelling "bzzt" pulse on unrecognized tokens
 * - Reliable onEvaluateInputViewShown() preventing KDE Connect / hardware drops
 */
class KitImeService : InputMethodService(), PhysicalKeyListener, SoftKeyGridView.KeyActionListener {

    private lateinit var prefs: KitImePreferences
    private lateinit var haptic: SoftBzztHaptic
    private lateinit var dbBridge: PredictionDbBridge
    private lateinit var keyRouter: PhysicalKeyRouter
    private lateinit var clipboardShelf: ClipboardShelfManager

    private var rootView: View? = null
    private var ribbonView: TriSplitRibbonView? = null
    private var softKeyGrid: SoftKeyGridView? = null
    private var sendGuardBar: LinearLayout? = null
    private var btnSendConfirm: Button? = null
    private var btnSendCancel: Button? = null

    private var btnToolbarArrows: Button? = null
    private var btnToolbarEmoji: Button? = null
    private var btnToolbarClipboard: Button? = null

    private var arrowPadPopup: ArrowPadPopup? = null
    private var emojiDrawer: EmojiSymbolDrawer? = null
    private var clipboardPopup: ClipboardShelfPopup? = null

    private var currentCandidates = TriSplitCandidates()
    private var isDoubleCheckActive = false

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreate() {
        super.onCreate()
        prefs = KitImePreferences(this)
        haptic = SoftBzztHaptic(this)
        dbBridge = PredictionDbBridge(this)
        clipboardShelf = ClipboardShelfManager(this)

        keyRouter = PhysicalKeyRouter(
            listener = this,
            isChordModeEnabled = prefs.isSpaceChordModeEnabled,
            isGreenlightEnabled = prefs.isGreenlightEnabled
        )

        arrowPadPopup = ArrowPadPopup(
            context = this,
            onKeyCommand = { keyCode -> sendDownUpKeyEvents(keyCode) },
            onTextCommand = { text -> currentInputConnection?.commitText(text, 1) }
        )

        emojiDrawer = EmojiSymbolDrawer(this) { emoji ->
            currentInputConnection?.commitText(emoji, 1)
        }

        clipboardPopup = ClipboardShelfPopup(this, clipboardShelf) { clipText ->
            currentInputConnection?.commitText(clipText, 1)
        }

        // Listen for new clipboards to populate the shelf
        setupClipboardListener()
    }

    private fun setupClipboardListener() {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.addPrimaryClipChangedListener {
            val clip = cm.primaryClip
            if (clip != null && clip.itemCount > 0) {
                val text = clip.getItemAt(0).coerceToText(this)?.toString()
                if (!text.isNullOrBlank()) {
                    clipboardShelf.addClip(text)
                }
            }
        }
    }

    override fun onEvaluateInputViewShown(): Boolean {
        super.onEvaluateInputViewShown()
        if (prefs.isHideSoftKbdOnPhysicalEnabled) {
            val config = resources.configuration
            return !(config.keyboard == android.content.res.Configuration.KEYBOARD_QWERTY &&
                    config.hardKeyboardHidden == android.content.res.Configuration.HARDKEYBOARDHIDDEN_NO)
        }
        return true
    }

    override fun onCreateInputView(): View {
        val view = layoutInflater.inflate(R.layout.view_ime_panel, null)
        rootView = view
        ribbonView = view.findViewById(R.id.tri_split_ribbon)
        softKeyGrid = view.findViewById(R.id.soft_key_grid)
        sendGuardBar = view.findViewById(R.id.layout_send_guard_bar)
        btnSendConfirm = view.findViewById(R.id.btn_send_confirm)
        btnSendCancel = view.findViewById(R.id.btn_send_cancel)

        btnToolbarArrows = view.findViewById(R.id.btn_toolbar_arrows)
        btnToolbarEmoji = view.findViewById(R.id.btn_toolbar_emoji)
        btnToolbarClipboard = view.findViewById(R.id.btn_toolbar_clipboard)

        softKeyGrid?.keyActionListener = this

        // Apply persisted theme palette
        applyCurrentTheme()

        ribbonView?.onCandidateSelectedListener = { candidate ->
            onCommitCandidate(candidate)
        }

        btnSendConfirm?.setOnClickListener {
            onPerformSendAction()
        }

        btnSendCancel?.setOnClickListener {
            onCancelDoubleCheck()
        }

        // Toolbar Buttons
        btnToolbarArrows?.setOnClickListener {
            val palette = ThemeManager.getPalette(ThemeManager.fromId(prefs.selectedTheme))
            arrowPadPopup?.show(view, palette)
        }

        btnToolbarEmoji?.setOnClickListener {
            val palette = ThemeManager.getPalette(ThemeManager.fromId(prefs.selectedTheme))
            emojiDrawer?.show(view, palette)
        }

        btnToolbarClipboard?.setOnClickListener {
            val palette = ThemeManager.getPalette(ThemeManager.fromId(prefs.selectedTheme))
            clipboardPopup?.show(view, palette)
        }

        return view
    }

    private fun applyCurrentTheme() {
        val theme = ThemeManager.fromId(prefs.selectedTheme)
        val palette = ThemeManager.getPalette(theme)

        rootView?.setBackgroundColor(palette.rootBackground)
        softKeyGrid?.applyTheme(theme)
    }

    override fun onStartInput(attribute: EditorInfo?, restarting: Boolean) {
        super.onStartInput(attribute, restarting)
        try {
            keyRouter.reset()
            isDoubleCheckActive = false
            sendGuardBar?.visibility = View.GONE
            updateCandidates("")
        } catch (e: Throwable) {
            android.util.Log.e("KitImeService", "Error in onStartInput", e)
        }
    }

    override fun onStartInputView(info: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        try {
            keyRouter.isChordModeEnabled = prefs.isSpaceChordModeEnabled
            keyRouter.isGreenlightEnabled = prefs.isGreenlightEnabled

            // Re-apply theme in case it changed in Settings
            applyCurrentTheme()

            refreshCurrentInputPrefix()
        } catch (e: Throwable) {
            android.util.Log.e("KitImeService", "Error in onStartInputView", e)
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        try {
            val consumed = keyRouter.handleKeyDown(
                keyCode = keyCode,
                event = event,
                candidates = currentCandidates,
                isDoubleCheckShowing = isDoubleCheckActive
            )
            if (consumed) return true
        } catch (e: Throwable) {
            android.util.Log.e("KitImeService", "Error in onKeyDown", e)
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        try {
            val consumed = keyRouter.handleKeyUp(keyCode, event)
            if (consumed) return true

            mainHandler.post { refreshCurrentInputPrefix() }
        } catch (e: Throwable) {
            android.util.Log.e("KitImeService", "Error in onKeyUp", e)
        }
        return super.onKeyUp(keyCode, event)
    }

    fun triggerSendGuardOrDispatch() {
        try {
            if (prefs.isPreSendDoubleCheckEnabled && !isDoubleCheckActive) {
                onToggleDoubleCheck()
                return
            }
            onPerformSendAction()
        } catch (e: Throwable) {
            android.util.Log.e("KitImeService", "Error in triggerSendGuardOrDispatch", e)
        }
    }

    // --- SoftKeyGridView.KeyActionListener ---

    override fun onCharacterKey(char: String) {
        try {
            val ic = currentInputConnection ?: return
            ic.commitText(char, 1)
            mainHandler.post { refreshCurrentInputPrefix() }
        } catch (e: Throwable) {
            android.util.Log.e("KitImeService", "Error in onCharacterKey", e)
        }
    }

    override fun onBackspaceKey() {
        try {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
            mainHandler.post { refreshCurrentInputPrefix() }
        } catch (e: Throwable) {
            android.util.Log.e("KitImeService", "Error in onBackspaceKey", e)
        }
    }

    override fun onSpaceKey() {
        try {
            onSpaceNormalCommit()
        } catch (e: Throwable) {
            android.util.Log.e("KitImeService", "Error in onSpaceKey", e)
        }
    }

    override fun onEnterKey() {
        try {
            val ic = currentInputConnection ?: return
            val currentInfo = currentInputEditorInfo

            val action = currentInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)
            val isSendAction = action == EditorInfo.IME_ACTION_SEND ||
                    action == EditorInfo.IME_ACTION_DONE ||
                    action == EditorInfo.IME_ACTION_GO

            if (isSendAction && prefs.isPreSendDoubleCheckEnabled && !isDoubleCheckActive) {
                onToggleDoubleCheck()
            } else if (action != null && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
                ic.performEditorAction(action)
            } else {
                sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
            }
        } catch (e: Throwable) {
            android.util.Log.e("KitImeService", "Error in onEnterKey", e)
        }
    }

    override fun onSymbolToggle() {
        val palette = ThemeManager.getPalette(ThemeManager.fromId(prefs.selectedTheme))
        emojiDrawer?.show(rootView ?: return, palette)
    }

    // --- PhysicalKeyListener Implementations ---

    override fun onCommitCandidate(candidate: Candidate) {
        try {
            val ic = currentInputConnection ?: return

            val prefix = getCurrentWordPrefix()
            if (prefix.isNotEmpty()) {
                ic.deleteSurroundingText(prefix.length, 0)
            }

            ic.commitText(candidate.text, 1)
            dbBridge.recordCommit(candidate.text, candidateSelected = true, slot = candidate.slot)
            updateCandidates("")
        } catch (e: Throwable) {
            android.util.Log.e("KitImeService", "Error in onCommitCandidate", e)
        }
    }

    override fun onSpaceNormalCommit() {
        try {
            val ic = currentInputConnection ?: return
            val prefix = getCurrentWordPrefix()

            if (prefix.isNotBlank() && prefs.isSoftBzztEnabled && dbBridge.isUnrecognizedWord(prefix)) {
                haptic.triggerBzzt()
            }

            ic.commitText(" ", 1)
            if (prefix.isNotBlank()) {
                dbBridge.recordCommit(prefix, candidateSelected = false, slot = null)
            }
            updateCandidates("")
        } catch (e: Throwable) {
            android.util.Log.e("KitImeService", "Error in onSpaceNormalCommit", e)
        }
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
        val currentInfo = currentInputEditorInfo
        val action = currentInfo?.imeOptions?.and(EditorInfo.IME_MASK_ACTION)

        if (action != null && action != EditorInfo.IME_ACTION_NONE && action != EditorInfo.IME_ACTION_UNSPECIFIED) {
            ic.performEditorAction(action)
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        }
    }

    private fun refreshCurrentInputPrefix() {
        try {
            val prefix = getCurrentWordPrefix()
            updateCandidates(prefix)
        } catch (e: Throwable) {
            android.util.Log.e("KitImeService", "Error in refreshCurrentInputPrefix", e)
        }
    }

    private fun getCurrentWordPrefix(): String {
        try {
            val ic = currentInputConnection ?: return ""
            val textBefore = ic.getTextBeforeCursor(40, 0)?.toString() ?: return ""
            val lastWordMatch = Regex("""\b[\w'-]+$""").find(textBefore)
            return lastWordMatch?.value ?: ""
        } catch (e: Throwable) {
            android.util.Log.e("KitImeService", "Error in getCurrentWordPrefix", e)
            return ""
        }
    }

    private fun updateCandidates(prefix: String) {
        try {
            val ic = currentInputConnection
            val contextBefore = ic?.getTextBeforeCursor(100, 0)?.toString() ?: ""

            val candidates = if (prefix.isNotBlank()) {
                dbBridge.getCandidates(prefix, contextBefore)
            } else {
                TriSplitCandidates()
            }

            currentCandidates = candidates
            ribbonView?.setCandidates(candidates)
        } catch (e: Throwable) {
            android.util.Log.e("KitImeService", "Error in updateCandidates", e)
        }
    }
}

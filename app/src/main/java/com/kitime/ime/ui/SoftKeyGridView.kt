package com.kitime.ime.ui

import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import com.kitime.ime.R

/**
 * Enhanced on-screen key grid for Kit-IME.
 * Supports:
 * - Dynamic theme palettes (Dark, Light, Kawaii, High-Contrast)
 * - Long-press popup triggers for alternate characters and diacritics
 * - QWERTY character keys and action row
 */
class SoftKeyGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    interface KeyActionListener {
        fun onCharacterKey(char: String)
        fun onBackspaceKey()
        fun onSpaceKey()
        fun onEnterKey()
        fun onSymbolToggle()
    }

    var keyActionListener: KeyActionListener? = null

    private var currentTheme: KitTheme = KitTheme.KAWAII_PURPLE
    private var palette: ThemePalette = ThemeManager.getPalette(currentTheme)

    private val longPressPopup = LongPressPopupView(context) { selectedChar ->
        keyActionListener?.onCharacterKey(selectedChar)
    }

    private val row1Keys = listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p")
    private val row2Keys = listOf("a", "s", "d", "f", "g", "h", "j", "k", "l")
    private val row3Keys = listOf("z", "x", "c", "v", "b", "n", "m")

    init {
        orientation = VERTICAL
        gravity = Gravity.CENTER_HORIZONTAL
        val padH = dp(2)
        val padV = dp(4)
        setPadding(padH, padV, padH, padV)
        buildKeyboardRows()
    }

    fun applyTheme(theme: KitTheme) {
        currentTheme = theme
        palette = ThemeManager.getPalette(theme)
        removeAllViews()
        buildKeyboardRows()
    }

    private fun buildKeyboardRows() {
        // Row 1
        addView(createKeyRow(row1Keys))

        // Row 2
        addView(createKeyRow(row2Keys))

        // Row 3 (Symbol toggle, letters, Backspace)
        val row3Layout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
        }

        // Left spacer/symbol toggle button
        val symBtn = createActionButton("?123", 1.2f) {
            keyActionListener?.onSymbolToggle()
        }
        row3Layout.addView(symBtn)

        // Middle character keys
        for (char in row3Keys) {
            val keyBtn = createCharButton(char, 1.0f)
            row3Layout.addView(keyBtn)
        }

        // Backspace button
        val backspaceBtn = createActionButton("⌫", 1.5f) {
            keyActionListener?.onBackspaceKey()
        }
        row3Layout.addView(backspaceBtn)
        addView(row3Layout)

        // Row 4 (Comma, Spacebar, Period, Enter)
        val row4Layout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
        }

        val commaBtn = createCharButton(",", 1.2f)
        row4Layout.addView(commaBtn)

        val spaceBtn = createActionButton("␣ Space", 4.5f) {
            keyActionListener?.onSpaceKey()
        }
        row4Layout.addView(spaceBtn)

        val dotBtn = createCharButton(".", 1.2f)
        row4Layout.addView(dotBtn)

        val enterBtn = createActionButton("⏎ Enter", 1.8f) {
            keyActionListener?.onEnterKey()
        }
        row4Layout.addView(enterBtn)

        addView(row4Layout)
    }

    private fun createKeyRow(keys: List<String>): LinearLayout {
        val rowLayout = LinearLayout(context).apply {
            orientation = HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT).apply {
                topMargin = dp(4)
            }
        }
        for (key in keys) {
            rowLayout.addView(createCharButton(key, 1.0f))
        }
        return rowLayout
    }

    private fun createCharButton(char: String, weight: Float): Button {
        return Button(context).apply {
            text = char
            textSize = 18f
            setTextColor(palette.keyTextColor)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(palette.keyBackground)
            }
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            layoutParams = LayoutParams(0, dp(44), weight).apply {
                setMargins(dp(2), 0, dp(2), 0)
            }
            setPadding(0, 0, 0, 0)
            setOnClickListener {
                keyActionListener?.onCharacterKey(char)
            }
            setOnLongClickListener {
                longPressPopup.show(this, char, palette)
                true
            }
        }
    }

    private fun createActionButton(label: String, weight: Float, action: () -> Unit): Button {
        return Button(context).apply {
            text = label
            textSize = 13f
            setTextColor(palette.actionTextColor)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(8).toFloat()
                setColor(palette.actionBackground)
            }
            typeface = Typeface.DEFAULT_BOLD
            isAllCaps = false
            layoutParams = LayoutParams(0, dp(44), weight).apply {
                setMargins(dp(2), 0, dp(2), 0)
            }
            setPadding(0, 0, 0, 0)
            setOnClickListener { action() }
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}

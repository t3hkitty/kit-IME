package com.kitime.ime.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.LinearLayout
import android.widget.PopupWindow

/**
 * Compact Pop-up Arrow Keys Pad for rapid cursor movement, selection, and document navigation.
 */
class ArrowPadPopup(
    private val context: Context,
    private val onKeyCommand: (Int) -> Unit,
    private val onTextCommand: (String) -> Unit
) {

    private var popupWindow: PopupWindow? = null

    fun show(anchorView: View, themePalette: ThemePalette) {
        dismiss()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            val pad = dp(8)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(18).toFloat()
                setColor(themePalette.rootBackground)
                setStroke(dp(2), themePalette.accentColor)
            }
        }

        // Top Row: Home, Up, End, Backspace
        val row1 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        row1.addView(createArrowButton("⇤", themePalette) { onKeyCommand(KeyEvent.KEYCODE_MOVE_HOME) })
        row1.addView(createArrowButton("▲", themePalette) { onKeyCommand(KeyEvent.KEYCODE_DPAD_UP) })
        row1.addView(createArrowButton("⇥", themePalette) { onKeyCommand(KeyEvent.KEYCODE_MOVE_END) })
        row1.addView(createArrowButton("⌫", themePalette) { onKeyCommand(KeyEvent.KEYCODE_DEL) })
        root.addView(row1)

        // Bottom Row: Left, Down, Right, Enter
        val row2 = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
            }
        }
        row2.addView(createArrowButton("◀", themePalette) { onKeyCommand(KeyEvent.KEYCODE_DPAD_LEFT) })
        row2.addView(createArrowButton("▼", themePalette) { onKeyCommand(KeyEvent.KEYCODE_DPAD_DOWN) })
        row2.addView(createArrowButton("▶", themePalette) { onKeyCommand(KeyEvent.KEYCODE_DPAD_RIGHT) })
        row2.addView(createArrowButton("⏎", themePalette) { onKeyCommand(KeyEvent.KEYCODE_ENTER) })
        root.addView(row2)

        popupWindow = PopupWindow(
            root,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = dp(10).toFloat()
            showAtLocation(anchorView, Gravity.CENTER, 0, 0)
        }
    }

    private fun createArrowButton(
        label: String,
        themePalette: ThemePalette,
        onClick: () -> Unit
    ): Button {
        return Button(context).apply {
            text = label
            textSize = 18f
            setTextColor(themePalette.actionTextColor)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(10).toFloat()
                setColor(themePalette.actionBackground)
            }
            layoutParams = LinearLayout.LayoutParams(dp(54), dp(46)).apply {
                setMargins(dp(3), 0, dp(3), 0)
            }
            setPadding(0, 0, 0, 0)
            setOnClickListener { onClick() }
        }
    }

    fun dismiss() {
        popupWindow?.dismiss()
        popupWindow = null
    }

    private fun dp(value: Int): Int {
        return (value * context.resources.displayMetrics.density).toInt()
    }
}

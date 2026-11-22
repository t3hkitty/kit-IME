package com.kitime.ime.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.PopupWindow

/**
 * Long-press popup for alternate characters (diacritics, numbers, secondary symbols).
 * Displays a compact plump floating bar above the pressed key.
 */
class LongPressPopupView(
    private val context: Context,
    private val onCharSelected: (String) -> Unit
) {

    companion object {
        // Mapping of base characters to long-press alternates
        val ALTERNATE_MAP = mapOf(
            "q" to listOf("1", "@", "q"),
            "w" to listOf("2", "#", "w"),
            "e" to listOf("3", "é", "è", "ê", "ë", "e"),
            "r" to listOf("4", "$", "r"),
            "t" to listOf("5", "%", "t"),
            "y" to listOf("6", "^", "y"),
            "u" to listOf("7", "ú", "ù", "û", "ü", "u"),
            "i" to listOf("8", "í", "ì", "î", "ï", "i"),
            "o" to listOf("9", "ó", "ò", "ô", "ö", "o"),
            "p" to listOf("0", "*", "p"),
            "a" to listOf("á", "à", "â", "ä", "ã", "a"),
            "s" to listOf("ß", "$", "s"),
            "d" to listOf("ð", "d"),
            "c" to listOf("ç", "c"),
            "n" to listOf("ñ", "n"),
            "." to listOf("...", ",", "?", "!", "-", "_", ":", ";"),
            "," to listOf(";", ":", "'", "\"", "(", ")"),
            "?" to listOf("¿", "!", "‽")
        )
    }

    private var popupWindow: PopupWindow? = null

    fun show(anchorView: View, char: String, themePalette: ThemePalette) {
        dismiss()

        val alternates = ALTERNATE_MAP[char.lowercase()] ?: listOf(char.uppercase())

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            val pad = dp(4)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(14).toFloat()
                setColor(themePalette.rootBackground)
                setStroke(dp(2), themePalette.accentColor)
            }
        }

        for (alt in alternates) {
            val btn = Button(context).apply {
                text = alt
                textSize = 16f
                setTextColor(themePalette.keyTextColor)
                isAllCaps = false
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(themePalette.keyBackground)
                }
                layoutParams = LinearLayout.LayoutParams(dp(38), dp(42)).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    onCharSelected(alt)
                    dismiss()
                }
            }
            container.addView(btn)
        }

        popupWindow = PopupWindow(
            container,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = dp(8).toFloat()
            // Show centered above anchor
            val location = IntArray(2)
            anchorView.getLocationOnScreen(location)
            showAtLocation(
                anchorView,
                Gravity.NO_GRAVITY,
                (location[0] - dp(20)).coerceAtLeast(dp(8)),
                (location[1] - dp(52)).coerceAtLeast(dp(20))
            )
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

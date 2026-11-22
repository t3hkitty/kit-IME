package com.kitime.ime.ui

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.GridLayout
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView

/**
 * Compact Emoji & Symbol Drawer.
 * Renders categorized quick emojis and symbols with verbatim text commit.
 */
class EmojiSymbolDrawer(
    private val context: Context,
    private val onEmojiSelected: (String) -> Unit
) {

    companion object {
        val FAVORITE_EMOJIS = listOf(
            "🐾", "🌸", "✨", "💜", "🍙", "🐱", "😸", "😻", "😽",
            "❤️", "🔥", "👍", "🙌", "😊", "😂", "🥳", "🥺", "👀",
            "💯", "🎉", "⚡", "☕", "💻", "🚀", "💡", "🎯", "📌"
        )

        val SYMBOLS = listOf(
            "@", "#", "$", "%", "&", "*", "-", "+", "=",
            "(", ")", "/", "\\", "<", ">", "[", "]", "{", "}",
            "^", "~", "`", "|", "°", "•", "★", "→", "✓"
        )
    }

    private var popupWindow: PopupWindow? = null

    fun show(anchorView: View, themePalette: ThemePalette) {
        dismiss()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val pad = dp(6)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(themePalette.rootBackground)
                setStroke(dp(2), themePalette.accentColor)
            }
        }

        // Section Title: Quick Emojis
        val emojiTitle = TextView(context).apply {
            text = "🌸 Quick Emojis & Favorites"
            setTextColor(themePalette.ribbonTextColor)
            textSize = 12f
            setPadding(dp(4), dp(2), dp(4), dp(4))
        }
        root.addView(emojiTitle)

        // Emoji Scroll Row
        val emojiScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val emojiRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        for (emoji in FAVORITE_EMOJIS) {
            val btn = Button(context).apply {
                text = emoji
                textSize = 18f
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(themePalette.keyBackground)
                }
                layoutParams = LinearLayout.LayoutParams(dp(40), dp(42)).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    onEmojiSelected(emoji)
                }
            }
            emojiRow.addView(btn)
        }
        emojiScroll.addView(emojiRow)
        root.addView(emojiScroll)

        // Section Title: Quick Symbols
        val symTitle = TextView(context).apply {
            text = "⚡ Quick Symbols"
            setTextColor(themePalette.ribbonTextColor)
            textSize = 12f
            setPadding(dp(4), dp(8), dp(4), dp(4))
        }
        root.addView(symTitle)

        // Symbols Scroll Row
        val symScroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val symRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }
        for (sym in SYMBOLS) {
            val btn = Button(context).apply {
                text = sym
                textSize = 16f
                setTextColor(themePalette.keyTextColor)
                background = GradientDrawable().apply {
                    shape = GradientDrawable.RECTANGLE
                    cornerRadius = dp(8).toFloat()
                    setColor(themePalette.keyBackground)
                }
                layoutParams = LinearLayout.LayoutParams(dp(38), dp(40)).apply {
                    setMargins(dp(2), 0, dp(2), 0)
                }
                setPadding(0, 0, 0, 0)
                setOnClickListener {
                    onEmojiSelected(sym)
                }
            }
            symRow.addView(btn)
        }
        symScroll.addView(symRow)
        root.addView(symScroll)

        popupWindow = PopupWindow(
            root,
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            isOutsideTouchable = true
            elevation = dp(12).toFloat()
            showAtLocation(anchorView, Gravity.BOTTOM, 0, dp(10))
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

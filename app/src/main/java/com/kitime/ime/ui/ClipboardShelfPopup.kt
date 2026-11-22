package com.kitime.ime.ui

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.TextView
import com.kitime.ime.data.ClipItem
import com.kitime.ime.data.ClipboardShelfManager

/**
 * Compact Clipboard Shelf Popup for quick insertion of pinned & recent snippets.
 */
class ClipboardShelfPopup(
    private val context: Context,
    private val clipboardShelf: ClipboardShelfManager,
    private val onClipSelected: (String) -> Unit
) {

    private var popupWindow: PopupWindow? = null

    fun show(anchorView: View, themePalette: ThemePalette) {
        dismiss()

        val root = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val pad = dp(8)
            setPadding(pad, pad, pad, pad)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(16).toFloat()
                setColor(themePalette.rootBackground)
                setStroke(dp(2), themePalette.accentColor)
            }
        }

        // Header
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
        }
        val title = TextView(context).apply {
            text = "📋 Clipboard Shelf (Pinned & Recent)"
            setTextColor(themePalette.ribbonTextColor)
            textSize = 13f
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        val clearBtn = Button(context).apply {
            text = "Clear Unpinned ✕"
            textSize = 11f
            setTextColor(themePalette.actionTextColor)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = dp(6).toFloat()
                setColor(themePalette.actionBackground)
            }
            setPadding(dp(8), dp(2), dp(8), dp(2))
            setOnClickListener {
                clipboardShelf.clearUnpinned()
                dismiss()
            }
        }
        header.addView(title)
        header.addView(clearBtn)
        root.addView(header)

        // Clip Items List (Horizontal Scroll)
        val clips = clipboardShelf.getClips()
        val scroll = HorizontalScrollView(context).apply {
            isHorizontalScrollBarEnabled = false
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dp(6)
            }
        }
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
        }

        if (clips.isEmpty()) {
            val emptyNotice = TextView(context).apply {
                text = "No clipboard history yet. Copy text to see it here! 🐾"
                setTextColor(themePalette.keyTextColor)
                textSize = 12f
                setPadding(dp(8), dp(12), dp(8), dp(12))
            }
            row.addView(emptyNotice)
        } else {
            for (clip in clips) {
                val clipCard = LinearLayout(context).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_VERTICAL
                    background = GradientDrawable().apply {
                        shape = GradientDrawable.RECTANGLE
                        cornerRadius = dp(8).toFloat()
                        setColor(themePalette.keyBackground)
                        if (clip.isPinned) {
                            setStroke(dp(2), themePalette.accentColor)
                        }
                    }
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        dp(42)
                    ).apply {
                        setMargins(dp(3), 0, dp(3), 0)
                    }
                    setPadding(dp(8), 0, dp(4), 0)
                }

                val preview = if (clip.text.length > 25) clip.text.take(23) + "…" else clip.text
                val textBtn = TextView(context).apply {
                    text = "${if (clip.isPinned) "📌 " else ""}$preview"
                    setTextColor(themePalette.keyTextColor)
                    textSize = 12f
                    setOnClickListener {
                        onClipSelected(clip.text)
                        dismiss()
                    }
                }
                clipCard.addView(textBtn)

                val pinBtn = Button(context).apply {
                    text = if (clip.isPinned) "★" else "☆"
                    textSize = 12f
                    setTextColor(themePalette.accentColor)
                    background = null
                    layoutParams = LinearLayout.LayoutParams(dp(32), dp(38))
                    setPadding(0, 0, 0, 0)
                    setOnClickListener {
                        clipboardShelf.togglePin(clip.id)
                        show(anchorView, themePalette) // Re-render
                    }
                }
                clipCard.addView(pinBtn)

                row.addView(clipCard)
            }
        }

        scroll.addView(row)
        root.addView(scroll)

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

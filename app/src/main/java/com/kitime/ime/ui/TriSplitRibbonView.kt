package com.kitime.ime.ui

import android.content.Context
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.kitime.ime.R
import com.kitime.ime.bridge.Candidate
import com.kitime.ime.bridge.RibbonSlot
import com.kitime.ime.bridge.TriSplitCandidates

/**
 * Tri-Split Ribbon View: 3-zone candidate ribbon.
 *
 * - Left Slot: Typo Rescue candidate placeholder/slot (1)
 * - Center Slot: Primary Prediction candidate slot (2) (with 2-letter greenlight state hook)
 * - Right Slot: Punctuated / Contraction Variant candidate slot (3)
 *
 * Implements high-density rounded plumpitude styling and immediate verbatim commit taps.
 */
class TriSplitRibbonView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private val leftSlotView: TextView
    private val centerSlotView: TextView
    private val rightSlotView: TextView

    private var currentCandidates: TriSplitCandidates = TriSplitCandidates()
    var onCandidateSelectedListener: ((Candidate) -> Unit)? = null

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        val pad = dp(6)
        setPadding(pad, pad, pad, pad)
        setBackgroundColor(ContextCompat.getColor(context, R.color.kawaii_indigo))

        leftSlotView = createSlotTextView("[1] Rescue", RibbonSlot.LEFT_TYPO_RESCUE)
        centerSlotView = createSlotTextView("[2] Primary", RibbonSlot.CENTER_PRIMARY)
        rightSlotView = createSlotTextView("[3] Variant", RibbonSlot.RIGHT_VARIANT)

        val params = LayoutParams(0, LayoutParams.MATCH_PARENT, 1.0f).apply {
            setMargins(dp(4), 0, dp(4), 0)
        }

        addView(leftSlotView, params)
        addView(centerSlotView, params)
        addView(rightSlotView, params)
    }

    private fun createSlotTextView(label: String, slot: RibbonSlot): TextView {
        return TextView(context).apply {
            text = label
            textSize = 14f
            gravity = Gravity.CENTER
            setTextColor(ContextCompat.getColor(context, R.color.kawaii_lavender))
            setBackgroundResource(R.drawable.bg_candidate_slot)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(4), dp(8), dp(4), dp(8))
            isClickable = true
            isFocusable = false

            setOnClickListener {
                val candidate = currentCandidates.getSlot(slot.index)
                if (candidate != null) {
                    onCandidateSelectedListener?.invoke(candidate)
                }
            }
        }
    }

    fun setCandidates(candidates: TriSplitCandidates) {
        currentCandidates = candidates

        bindSlot(leftSlotView, candidates.typoRescue, "[1] Rescue")
        bindSlot(centerSlotView, candidates.primary, "[2] Primary")
        bindSlot(rightSlotView, candidates.variant, "[3] Variant")
    }

    private fun bindSlot(view: TextView, candidate: Candidate?, defaultPlaceholder: String) {
        if (candidate != null && candidate.text.isNotBlank()) {
            view.text = candidate.text
            view.visibility = View.VISIBLE

            if (candidate.isGreenlit) {
                view.setBackgroundResource(R.drawable.bg_candidate_greenlit)
                view.setTextColor(ContextCompat.getColor(context, R.color.kawaii_greenlight))
            } else {
                view.setBackgroundResource(R.drawable.bg_candidate_slot)
                view.setTextColor(ContextCompat.getColor(context, R.color.kawaii_lavender))
            }
        } else {
            view.text = defaultPlaceholder
            view.setBackgroundResource(R.drawable.bg_candidate_slot)
            view.setTextColor(ContextCompat.getColor(context, R.color.kawaii_slate))
        }
    }

    private fun dp(value: Int): Int {
        return (value * resources.displayMetrics.density).toInt()
    }
}

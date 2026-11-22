package com.kitime.ime.bridge

/**
 * Slot destination in the Tri-Split ribbon or extended candidates.
 */
enum class RibbonSlot(val index: Int) {
    LEFT_TYPO_RESCUE(1),
    CENTER_PRIMARY(2),
    RIGHT_VARIANT(3),
    EXTENDED_FOUR(4),
    EXTENDED_FIVE(5)
}

/**
 * Candidate item rendered inside the candidate ribbon.
 *
 * @property text The exact verbatim string to commit upon selection.
 * @property slot Targeted slot position (1..5).
 * @property isGreenlit Whether prefix confidence greenlight threshold is achieved.
 * @property confidence Confidence score (0.0 .. 1.0).
 */
data class Candidate(
    val text: String,
    val slot: RibbonSlot,
    val isGreenlit: Boolean = false,
    val confidence: Float = 1.0f
)

/**
 * Candidate result holding tri-split candidate options.
 */
data class TriSplitCandidates(
    val typoRescue: Candidate? = null,
    val primary: Candidate? = null,
    val variant: Candidate? = null,
    val slot4: Candidate? = null,
    val slot5: Candidate? = null
) {
    fun getSlot(index: Int): Candidate? = when (index) {
        1 -> typoRescue
        2 -> primary
        3 -> variant
        4 -> slot4
        5 -> slot5
        else -> null
    }

    fun isEmpty(): Boolean =
        typoRescue == null && primary == null && variant == null && slot4 == null && slot5 == null
}

/**
 * CandidateProvider interface for querying prediction engine.
 */
interface CandidateProvider {
    /**
     * Provide candidate recommendations for the current prefix token.
     */
    fun getCandidates(prefix: String, contextBefore: String): TriSplitCandidates

    /**
     * Check if a completed word boundary token is considered misspell / unrecognized.
     */
    fun isUnrecognizedWord(token: String): Boolean
}

/**
 * ConfirmationSink interface for recording confirmed text commits.
 */
interface ConfirmationSink {
    /**
     * Log committed verbatim text for learning/statistics.
     * Note: ZERO post-commit rewriting must occur.
     */
    fun recordCommit(token: String, candidateSelected: Boolean, slot: RibbonSlot?)
}

/**
 * SnapshotBridge for exporting or syncing corpus / personalization state.
 */
interface SnapshotBridge {
    fun exportSnapshot(): ByteArray
    fun importSnapshot(data: ByteArray): Boolean
}

/**
 * Typealias for default bridge implementation.
 */
typealias PredictionDbBridge = com.kitime.ime.data.PredictionDbBridge


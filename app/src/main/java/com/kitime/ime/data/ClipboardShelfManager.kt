package com.kitime.ime.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * Single clipboard clip item with Pin/Unpin support.
 */
data class ClipItem(
    val id: String,
    val text: String,
    val timestamp: Long = System.currentTimeMillis(),
    var isPinned: Boolean = false
)

/**
 * Manages local clipboard history ring (up to 25 items) with pinned clips preserved at the top.
 * Respects user privacy and sensitive/password fields.
 */
class ClipboardShelfManager(context: Context) {

    private val prefs = context.getSharedPreferences("kit_ime_clipboard_shelf", Context.MODE_PRIVATE)

    companion object {
        private const val KEY_CLIPS_JSON = "key_clips_json"
        private const val MAX_RECENT_CLIPS = 25
    }

    private val clips = mutableListOf<ClipItem>()

    init {
        loadClips()
    }

    @Synchronized
    fun addClip(text: String, isSensitive: Boolean = false): ClipItem? {
        if (text.isBlank() || isSensitive) return null

        val trimmed = text.trim()
        // If already exists, move or keep pinned
        val existingIndex = clips.indexOfFirst { it.text == trimmed }
        if (existingIndex >= 0) {
            val existing = clips.removeAt(existingIndex)
            val updated = existing.copy(timestamp = System.currentTimeMillis())
            if (updated.isPinned) {
                clips.add(0, updated)
            } else {
                val firstUnpinned = clips.indexOfFirst { !it.isPinned }
                if (firstUnpinned >= 0) clips.add(firstUnpinned, updated) else clips.add(updated)
            }
            saveClips()
            return updated
        }

        val newItem = ClipItem(
            id = "clip_${System.currentTimeMillis()}_${trimmed.hashCode()}",
            text = trimmed,
            timestamp = System.currentTimeMillis(),
            isPinned = false
        )

        val firstUnpinned = clips.indexOfFirst { !it.isPinned }
        if (firstUnpinned >= 0) {
            clips.add(firstUnpinned, newItem)
        } else {
            clips.add(newItem)
        }

        // Enforce max capacity for unpinned
        val unpinnedCount = clips.count { !it.isPinned }
        if (unpinnedCount > MAX_RECENT_CLIPS) {
            val lastUnpinnedIndex = clips.indexOfLast { !it.isPinned }
            if (lastUnpinnedIndex >= 0) {
                clips.removeAt(lastUnpinnedIndex)
            }
        }

        saveClips()
        return newItem
    }

    @Synchronized
    fun togglePin(clipId: String): Boolean {
        val index = clips.indexOfFirst { it.id == clipId }
        if (index < 0) return false

        val item = clips.removeAt(index)
        item.isPinned = !item.isPinned

        if (item.isPinned) {
            clips.add(0, item)
        } else {
            val firstUnpinned = clips.indexOfFirst { !it.isPinned }
            if (firstUnpinned >= 0) clips.add(firstUnpinned, item) else clips.add(item)
        }

        saveClips()
        return item.isPinned
    }

    @Synchronized
    fun deleteClip(clipId: String): Boolean {
        val removed = clips.removeAll { it.id == clipId }
        if (removed) saveClips()
        return removed
    }

    @Synchronized
    fun clearUnpinned() {
        clips.removeAll { !it.isPinned }
        saveClips()
    }

    @Synchronized
    fun getClips(): List<ClipItem> {
        return clips.toList()
    }

    private fun loadClips() {
        val jsonStr = prefs.getString(KEY_CLIPS_JSON, null) ?: return
        try {
            val array = JSONArray(jsonStr)
            clips.clear()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                clips.add(
                    ClipItem(
                        id = obj.getString("id"),
                        text = obj.getString("text"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        isPinned = obj.optBoolean("isPinned", false)
                    )
                )
            }
        } catch (e: Exception) {
            // Ignore parse errors
        }
    }

    private fun saveClips() {
        try {
            val array = JSONArray()
            for (clip in clips) {
                val obj = JSONObject().apply {
                    put("id", clip.id)
                    put("text", clip.text)
                    put("timestamp", clip.timestamp)
                    put("isPinned", clip.isPinned)
                }
                array.put(obj)
            }
            prefs.edit().putString(KEY_CLIPS_JSON, array.toString()).apply()
        } catch (e: Exception) {
            // Ignore serialize errors
        }
    }
}

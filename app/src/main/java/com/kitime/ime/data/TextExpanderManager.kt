package com.kitime.ime.data

import android.content.Context
import android.content.SharedPreferences
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Manager for Cast Slots (<char1>, <char2>, <loc1>) and TextExpander shortcuts.
 * Supports dynamic macro tokens: {{clip}}, {{date}}, {{time}}, {{name}}, {{email}}.
 */
class TextExpanderManager(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREF_FILE_NAME = "kit_text_expander_prefs"

        // Cast Slots
        const val KEY_CHAR1 = "slot_char1"
        const val KEY_CHAR2 = "slot_char2"
        const val KEY_LOC1 = "slot_loc1"

        // Macro mappings stored as newline-delimited key=value
        const val KEY_EXPANDER_SNIPPETS = "macro_expander_snippets"

        const val DEFAULT_CHAR1 = "Kitti"
        const val DEFAULT_CHAR2 = "Meow"
        const val DEFAULT_LOC1 = "Neo-Tokyo"

        val DEFAULT_SNIPPETS = """
            ;date={{date}}
            ;time={{time}}
            ;clip={{clip}}
            ;sig=Best regards, {{name}} <{{email}}>
            ;shrug=¯\_(ツ)_/¯
            ;cat=(=^･ω･^=)
        """.trimIndent()
    }

    var char1: String
        get() = prefs.getString(KEY_CHAR1, DEFAULT_CHAR1) ?: DEFAULT_CHAR1
        set(value) = prefs.edit().putString(KEY_CHAR1, value).apply()

    var char2: String
        get() = prefs.getString(KEY_CHAR2, DEFAULT_CHAR2) ?: DEFAULT_CHAR2
        set(value) = prefs.edit().putString(KEY_CHAR2, value).apply()

    var loc1: String
        get() = prefs.getString(KEY_LOC1, DEFAULT_LOC1) ?: DEFAULT_LOC1
        set(value) = prefs.edit().putString(KEY_LOC1, value).apply()

    var rawSnippets: String
        get() = prefs.getString(KEY_EXPANDER_SNIPPETS, DEFAULT_SNIPPETS) ?: DEFAULT_SNIPPETS
        set(value) = prefs.edit().putString(KEY_EXPANDER_SNIPPETS, value).apply()

    /**
     * Parse snippets into key -> template map.
     */
    fun getSnippetMap(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        rawSnippets.lines().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.isNotBlank() && !trimmed.startsWith("#") && trimmed.contains("=")) {
                val parts = trimmed.split("=", limit = 2)
                if (parts.size == 2) {
                    map[parts[0].trim()] = parts[1]
                }
            }
        }
        return map
    }

    /**
     * Expands macro template tokens:
     * - {{date}} -> YYYY-MM-DD
     * - {{time}} -> HH:mm
     * - {{clip}} -> clipboard contents
     * - {{name}} -> char1
     * - {{email}} -> user configured email
     * - <char1>, <char2>, <loc1>
     */
    fun expand(template: String, clipboardText: String = "", emailText: String = "me@kawaii.dev"): String {
        val now = Date()
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now)
        val timeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now)

        return template
            .replace("{{date}}", dateStr)
            .replace("{{time}}", timeStr)
            .replace("{{clip}}", clipboardText)
            .replace("{{name}}", char1)
            .replace("{{email}}", emailText)
            .replace("<char1>", char1)
            .replace("<char2>", char2)
            .replace("<loc1>", loc1)
    }
}

package com.kitime.ime.pref

import android.content.Context
import android.content.SharedPreferences

/**
 * Sticky preferences manager with instant listeners and typed defaults.
 */
class KitImePreferences(context: Context) {

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREF_FILE_NAME, Context.MODE_PRIVATE)

    companion object {
        const val PREF_FILE_NAME = "kit_ime_prefs"

        const val KEY_SPACE_CHORD_MODE = "pref_space_chord_mode"
        const val KEY_GREENLIGHT_ENABLED = "pref_greenlight_enabled"
        const val KEY_SOFT_BZZT_ENABLED = "pref_soft_bzzt_enabled"
        const val KEY_PRE_SEND_DOUBLE_CHECK = "pref_pre_send_double_check"
        const val KEY_HIDE_SOFT_KBD_PHYSICAL = "pref_hide_soft_kbd_physical"
        const val KEY_SAF_FOLDER_URI = "pref_saf_folder_uri"
        const val KEY_GOOGLE_DOC_URLS = "pref_google_doc_urls"

        // Default values
        const val DEFAULT_SPACE_CHORD_MODE = true
        const val DEFAULT_GREENLIGHT_ENABLED = true
        const val DEFAULT_SOFT_BZZT_ENABLED = true
        const val DEFAULT_PRE_SEND_DOUBLE_CHECK = true
        const val DEFAULT_HIDE_SOFT_KBD_PHYSICAL = false
    }

    var safFolderUri: String?
        get() = prefs.getString(KEY_SAF_FOLDER_URI, null)
        set(value) = prefs.edit().putString(KEY_SAF_FOLDER_URI, value).apply()

    var googleDocUrls: String
        get() = prefs.getString(KEY_GOOGLE_DOC_URLS, "") ?: ""
        set(value) = prefs.edit().putString(KEY_GOOGLE_DOC_URLS, value).apply()

    fun getGoogleDocUrlList(): List<String> {
        return googleDocUrls.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !it.startsWith("#") }
    }

    var isSpaceChordModeEnabled: Boolean
        get() = prefs.getBoolean(KEY_SPACE_CHORD_MODE, DEFAULT_SPACE_CHORD_MODE)
        set(value) = prefs.edit().putBoolean(KEY_SPACE_CHORD_MODE, value).apply()

    var isGreenlightEnabled: Boolean
        get() = prefs.getBoolean(KEY_GREENLIGHT_ENABLED, DEFAULT_GREENLIGHT_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_GREENLIGHT_ENABLED, value).apply()

    var isSoftBzztEnabled: Boolean
        get() = prefs.getBoolean(KEY_SOFT_BZZT_ENABLED, DEFAULT_SOFT_BZZT_ENABLED)
        set(value) = prefs.edit().putBoolean(KEY_SOFT_BZZT_ENABLED, value).apply()

    var isPreSendDoubleCheckEnabled: Boolean
        get() = prefs.getBoolean(KEY_PRE_SEND_DOUBLE_CHECK, DEFAULT_PRE_SEND_DOUBLE_CHECK)
        set(value) = prefs.edit().putBoolean(KEY_PRE_SEND_DOUBLE_CHECK, value).apply()

    var isHideSoftKbdOnPhysicalEnabled: Boolean
        get() = prefs.getBoolean(KEY_HIDE_SOFT_KBD_PHYSICAL, DEFAULT_HIDE_SOFT_KBD_PHYSICAL)
        set(value) = prefs.edit().putBoolean(KEY_HIDE_SOFT_KBD_PHYSICAL, value).apply()

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}

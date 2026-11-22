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

        // Default values
        const val DEFAULT_SPACE_CHORD_MODE = true
        const val DEFAULT_GREENLIGHT_ENABLED = true
        const val DEFAULT_SOFT_BZZT_ENABLED = true
        const val DEFAULT_PRE_SEND_DOUBLE_CHECK = true
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

    fun registerListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.registerOnSharedPreferenceChangeListener(listener)
    }

    fun unregisterListener(listener: SharedPreferences.OnSharedPreferenceChangeListener) {
        prefs.unregisterOnSharedPreferenceChangeListener(listener)
    }
}

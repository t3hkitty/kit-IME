package com.kitime.ime.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import com.kitime.ime.R
import com.kitime.ime.pref.KitImePreferences

/**
 * Settings Activity for Kit-IME preferences with instant listeners.
 */
class KitImeSettingsActivity : AppCompatActivity() {

    private lateinit var prefs: KitImePreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        prefs = KitImePreferences(this)

        val switchSpaceChord = findViewById<SwitchCompat>(R.id.switch_space_chord)
        val switchGreenlight = findViewById<SwitchCompat>(R.id.switch_greenlight)
        val switchSoftBzzt = findViewById<SwitchCompat>(R.id.switch_soft_bzzt)
        val switchSendGuard = findViewById<SwitchCompat>(R.id.switch_send_guard)

        switchSpaceChord.isChecked = prefs.isSpaceChordModeEnabled
        switchGreenlight.isChecked = prefs.isGreenlightEnabled
        switchSoftBzzt.isChecked = prefs.isSoftBzztEnabled
        switchSendGuard.isChecked = prefs.isPreSendDoubleCheckEnabled

        switchSpaceChord.setOnCheckedChangeListener { _, isChecked ->
            prefs.isSpaceChordModeEnabled = isChecked
        }

        switchGreenlight.setOnCheckedChangeListener { _, isChecked ->
            prefs.isGreenlightEnabled = isChecked
        }

        switchSoftBzzt.setOnCheckedChangeListener { _, isChecked ->
            prefs.isSoftBzztEnabled = isChecked
        }

        switchSendGuard.setOnCheckedChangeListener { _, isChecked ->
            prefs.isPreSendDoubleCheckEnabled = isChecked
        }
    }
}

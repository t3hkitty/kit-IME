package com.kitime.ime.haptic

import android.content.Context
import android.os.Build
import android.os.CombinedVibration
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

/**
 * Soft Spelling "Bzzt" Haptic Feedback.
 *
 * Provides a subtle, low-amplitude tactile pulse (VibrationEffect.createOneShot(18L, 45) on API 26+)
 * with graceful fallback when a word boundary finishes on a token flagged as an unrecognized/badly-spelled
 * word—strictly without mutating the text.
 */
class SoftBzztHaptic(context: Context) {

    private val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        val vibratorManager = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
        vibratorManager?.defaultVibrator
    } else {
        @Suppress("DEPRECATION")
        context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
    }

    /**
     * Trigger a 18ms soft bzzt pulse at amplitude 45.
     */
    fun triggerBzzt() {
        val vib = vibrator ?: return
        if (!vib.hasVibrator()) return

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                // Subtle 18ms, amplitude 45/255
                val effect = VibrationEffect.createOneShot(18L, 45)
                vib.vibrate(effect)
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(18L)
            }
        } catch (_: SecurityException) {
            // Graceful fallback if vibration permission or policy is restricted
        }
    }
}

package app.bedrock.controllers

import android.content.Context
import android.provider.Settings
import android.util.Log

/**
 * True system-wide grayscale via the accessibility daltonizer. Requires
 * WRITE_SECURE_SETTINGS, which Play-store users grant once over ADB:
 *   adb shell pm grant app.bedrock android.permission.WRITE_SECURE_SETTINGS
 * Everyone else gets the overlay dim only.
 */
class GrayscaleController(private val context: Context) {

    fun isCapable(): Boolean =
        context.checkSelfPermission(android.Manifest.permission.WRITE_SECURE_SETTINGS) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    fun setGrayscale(enabled: Boolean) {
        if (!isCapable()) {
            Log.i(TAG, "WRITE_SECURE_SETTINGS not granted; skipping grayscale")
            return
        }
        Settings.Secure.putInt(
            context.contentResolver,
            "accessibility_display_daltonizer_enabled",
            if (enabled) 1 else 0,
        )
        Settings.Secure.putInt(
            context.contentResolver,
            "accessibility_display_daltonizer",
            if (enabled) DALTONIZER_MONOCHROMACY else DALTONIZER_DISABLED,
        )
        Log.i(TAG, "grayscale ${if (enabled) "on" else "off"}")
    }

    private companion object {
        const val TAG = "GrayscaleController"
        const val DALTONIZER_MONOCHROMACY = 0
        const val DALTONIZER_DISABLED = -1
    }
}

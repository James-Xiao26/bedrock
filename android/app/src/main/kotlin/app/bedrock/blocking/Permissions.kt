package app.bedrock.blocking

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Process
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat

/**
 * Status and settings deep-links for the four grants Bedrock needs. Pure
 * queries over the app context; the runtime notification request needs an
 * Activity and lives in MainActivity instead.
 */
object Permissions {

    /** Snapshot of every grant, as the Dart onboarding polls it. */
    fun status(context: Context): Map<String, Boolean> = mapOf(
        "notifications" to notificationsEnabled(context),
        "accessibility" to accessibilityEnabled(context),
        "usageAccess" to usageAccessGranted(context),
        "overlay" to Settings.canDrawOverlays(context),
    )

    fun notificationsEnabled(context: Context): Boolean =
        NotificationManagerCompat.from(context).areNotificationsEnabled()

    /** True when Bedrock's accessibility service is in the enabled-services list. */
    fun accessibilityEnabled(context: Context): Boolean {
        val component =
            "${context.packageName}/${AppBlockerAccessibilityService::class.java.name}"
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ) ?: return false
        return enabled.split(':').any { it.equals(component, ignoreCase = true) }
    }

    @Suppress("DEPRECATION") // the op-check APIs are deprecated but remain the way to read usage-access grant
    fun usageAccessGranted(context: Context): Boolean {
        val appOps = context.getSystemService(AppOpsManager::class.java) ?: return false
        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            appOps.unsafeCheckOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            )
        } else {
            appOps.checkOpNoThrow(
                AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName,
            )
        }
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun openAccessibilitySettings(context: Context) =
        open(context, Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))

    fun openUsageAccessSettings(context: Context) =
        open(context, Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))

    fun openOverlaySettings(context: Context) = open(
        context,
        Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:${context.packageName}"),
        ),
    )

    private fun open(context: Context, intent: Intent) {
        context.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}

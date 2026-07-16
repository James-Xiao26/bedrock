package app.bedrock.blocking

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.telecom.TelecomManager

/**
 * Which packages stay reachable during lockdown. Two layers:
 * - a hardcoded system layer (dialer/in-call UI, launcher, SystemUI,
 *   Settings, and Bedrock itself) that the user can never remove - both for
 *   safety (emergency calls) and Play policy (Settings/Play Store must stay
 *   reachable so the app is always uninstallable);
 * - the user's own allowlist from config.
 */
class Allowlist(private val context: Context) {

    /** Recomputed at lockdown start; defaults change rarely. */
    fun resolve(userAllowlist: Set<String>): Set<String> {
        val system = buildSet {
            add(context.packageName)
            add("com.android.systemui")
            add("com.android.settings")
            add("com.android.vending") // Play Store: policy, and the bypass purchase
            add("com.android.emergency")
            add("com.android.phone")
            add("com.android.server.telecom")

            context.getSystemService(TelecomManager::class.java)
                ?.defaultDialerPackage?.let { add(it) }
            add("com.google.android.dialer")
            add("com.android.dialer")
            add("com.android.incallui")

            addAll(homePackages())
        }
        return system + userAllowlist
    }

    /**
     * Every installed home-screen app, not just the current default: the
     * default can be the system resolver, and bouncing the launcher would
     * turn the phone into a kiosk (Play policy: Settings and uninstall must
     * stay reachable).
     */
    private fun homePackages(): Set<String> {
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        return context.packageManager
            .queryIntentActivities(home, PackageManager.MATCH_ALL)
            .map { it.activityInfo.packageName }
            .toSet()
    }
}

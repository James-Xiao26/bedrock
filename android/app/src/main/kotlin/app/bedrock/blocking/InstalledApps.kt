package app.bedrock.blocking

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import android.util.Base64
import java.io.ByteArrayOutputStream

/**
 * Launchable user apps for the "Always Allowed" picker: package, label, and a
 * small PNG icon (base64). Bedrock itself is omitted (it is always reachable).
 * Package visibility is granted by the LAUNCHER <queries> entry in the manifest.
 */
class InstalledApps(private val context: Context) {

    // ponytail: encodes icons on the calling (main) thread - fine for a settings
    // screen with a few hundred apps; move to a Dispatcher if it ever janks.
    fun list(): List<Map<String, Any?>> {
        val pm = context.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(main, PackageManager.MATCH_ALL)
            .asSequence()
            .distinctBy { it.activityInfo.packageName }
            .filter { it.activityInfo.packageName != context.packageName }
            .map {
                mapOf(
                    "package" to it.activityInfo.packageName,
                    "label" to it.loadLabel(pm).toString(),
                    "icon" to encodeIcon(it.loadIcon(pm)),
                )
            }
            .sortedBy { (it["label"] as String).lowercase() }
            .toList()
    }

    private fun encodeIcon(drawable: Drawable): String {
        val px = (48 * context.resources.displayMetrics.density).toInt().coerceAtLeast(1)
        val bmp = Bitmap.createBitmap(px, px, Bitmap.Config.ARGB_8888)
        drawable.setBounds(0, 0, px, px)
        drawable.draw(Canvas(bmp))
        val out = ByteArrayOutputStream()
        bmp.compress(Bitmap.CompressFormat.PNG, 100, out)
        bmp.recycle()
        return Base64.encodeToString(out.toByteArray(), Base64.NO_WRAP)
    }
}

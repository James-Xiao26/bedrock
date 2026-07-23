package app.bedrock.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.text.format.DateFormat
import android.widget.RemoteViews
import app.bedrock.MainActivity
import app.bedrock.R
import app.bedrock.engine.BedrockEngine
import java.util.Date

/** Minimal projection the widget renders. Filled by [BedrockEngine.widgetStatus]. */
data class WidgetStatus(
    val active: Boolean,
    val manual: Boolean,
    val openMs: Long?,
    val closeMs: Long?,
)

/**
 * Home-screen widget: shows whether downtime is on now, or when it starts next.
 * Labels are absolute ("until 7:00 AM", "Mon 11:00 PM") so the text stays correct
 * between refreshes - the widget only needs updating when the engine broadcasts a
 * state/config change (via [refresh]) or the platform calls [onUpdate].
 */
class BedrockWidget : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        val views = buildViews(context)
        for (id in ids) mgr.updateAppWidget(id, views)
    }

    companion object {
        /** Push fresh views to every placed widget. Called by the engine on state/config change. */
        fun refresh(context: Context) {
            val mgr = AppWidgetManager.getInstance(context) ?: return
            val ids = mgr.getAppWidgetIds(ComponentName(context, BedrockWidget::class.java))
            if (ids.isEmpty()) return
            val views = buildViews(context)
            for (id in ids) mgr.updateAppWidget(id, views)
        }

        private fun buildViews(context: Context): RemoteViews {
            val status = BedrockEngine.get(context).widgetStatus()
            val (title, detail) = label(context, status)

            return RemoteViews(context.packageName, R.layout.widget_downtime).apply {
                setTextViewText(R.id.widget_title, title)
                setTextViewText(R.id.widget_detail, detail)
                setOnClickPendingIntent(R.id.widget_root, openAppIntent(context))
            }
        }

        private fun label(context: Context, s: WidgetStatus): Pair<String, String> = when {
            s.active && s.manual -> "Downtime on" to "on-demand"
            s.active && s.closeMs != null -> "Downtime on" to "until ${time(context, s.closeMs)}"
            s.active -> "Downtime on" to ""
            s.openMs != null -> "Next downtime" to "${weekday(context, s.openMs)} ${time(context, s.openMs)}"
            else -> "No downtime scheduled" to ""
        }

        private fun time(context: Context, ms: Long): String =
            DateFormat.getTimeFormat(context).format(Date(ms))

        private fun weekday(context: Context, ms: Long): String =
            DateFormat.format("EEE", ms).toString()

        private fun openAppIntent(context: Context): PendingIntent {
            val intent = Intent(context, MainActivity::class.java)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            return PendingIntent.getActivity(
                context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
            )
        }
    }
}

package com.genowhirl.breathe.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.res.ColorStateList
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import com.genowhirl.breathe.R
import com.genowhirl.breathe.data.Store
import com.genowhirl.breathe.engine.BreathingService
import com.genowhirl.breathe.engine.Session
import com.genowhirl.breathe.engine.SessionState
import com.genowhirl.breathe.engine.Status
import com.genowhirl.breathe.model.Phase
import com.genowhirl.breathe.model.breathLevel
import com.genowhirl.breathe.ui.MainActivity
import com.genowhirl.breathe.ui.cycleLabel
import com.genowhirl.breathe.ui.formatSeconds
import com.genowhirl.breathe.ui.theme.PhaseColors
import kotlin.math.ceil
import kotlin.math.roundToInt

/** Home-screen widget: shows the current stage and controls the session and its settings. */
class BreathWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        BreathWidget.refresh(context)
    }

    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        BreathWidget.refresh(context)
    }

}

/** Handles the widget's own buttons. Not exported, so only this app's PendingIntents reach it. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val store = Store.get(context)
        when (intent.action) {
            ACTION_PREV -> store.cyclePreset(-1)
            ACTION_NEXT -> store.cyclePreset(+1)
            ACTION_SOUND -> store.updateSettings { it.copy(soundEnabled = !it.soundEnabled) }
            ACTION_VIBRATION -> store.updateSettings { it.copy(vibrationEnabled = !it.vibrationEnabled) }
        }
    }

    companion object {
        const val ACTION_PREV = "com.genowhirl.breathe.widget.PREV"
        const val ACTION_NEXT = "com.genowhirl.breathe.widget.NEXT"
        const val ACTION_SOUND = "com.genowhirl.breathe.widget.SOUND"
        const val ACTION_VIBRATION = "com.genowhirl.breathe.widget.VIBRATION"
    }
}

object BreathWidget {
    private const val PROGRESS_MAX = 1000

    fun hasWidgets(context: Context): Boolean = ids(context).isNotEmpty()

    fun refresh(context: Context, state: SessionState = Session.state.value) {
        val manager = AppWidgetManager.getInstance(context) ?: return
        val ids = ids(context)
        if (ids.isEmpty()) return
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val views = RemoteViews(
                    mapOf(
                        SizeF(100f, 40f) to build(context, state, R.layout.widget_compact),
                        SizeF(220f, 100f) to build(context, state, R.layout.widget_full),
                    ),
                )
                manager.updateAppWidget(ids, views)
            } else {
                for (id in ids) {
                    val options = manager.getAppWidgetOptions(id)
                    val w = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
                    val h = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                    val layout = if (w in 1 until 220 || h in 1 until 100) R.layout.widget_compact else R.layout.widget_full
                    manager.updateAppWidget(id, build(context, state, layout))
                }
            }
        }
    }

    private fun ids(context: Context): IntArray =
        AppWidgetManager.getInstance(context)
            ?.getAppWidgetIds(ComponentName(context, BreathWidgetReceiver::class.java))
            ?: IntArray(0)

    private fun build(context: Context, state: SessionState, layout: Int): RemoteViews {
        val store = Store.get(context)
        val pattern = store.pattern.value
        val settings = store.settings.value
        val now = SystemClock.elapsedRealtime()
        val presetName = store.matchingPreset(pattern)?.name ?: context.getString(R.string.custom_pattern)
        val views = RemoteViews(context.packageName, layout)

        val active = state.isActive
        val phaseColor = if (active) PhaseColors.argb(state.phase) else PhaseColors.argb(Phase.INHALE)
        val title = when (state.status) {
            Status.RUNNING -> context.getString(BreathingService.phaseLabel(state.phase))
            Status.PAUSED -> context.getString(R.string.paused)
            Status.FINISHED -> context.getString(R.string.finished)
            Status.IDLE -> context.getString(R.string.ready)
        }
        views.setTextViewText(R.id.phase, title)
        views.setTextColor(R.id.phase, if (active) phaseColor else 0xFFE6E9F2.toInt())

        val countdown = if (active) {
            ceil(state.phaseRemaining(now) / 1000.0).toInt().coerceAtLeast(0).toString()
        } else {
            "${pattern.inhale}·${pattern.holdIn}·${pattern.exhale}·${pattern.holdOut}"
        }
        views.setTextViewText(R.id.countdown, countdown)

        val subtitle = if (active) {
            context.getString(R.string.widget_running_detail, presetName, cycleLabel(context.resources, state.cycle, settings))
        } else {
            context.getString(
                R.string.widget_idle_detail,
                formatSeconds(pattern.cycleMillis / 1000.0),
                formatSeconds(pattern.breathsPerMinute),
            )
        }
        views.setTextViewText(R.id.subtitle, subtitle)

        val level = if (active) breathLevel(state.phase, state.phaseProgress(now), natural = false) else 0f
        views.setProgressBar(R.id.progress, PROGRESS_MAX, (level * PROGRESS_MAX).roundToInt(), false)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            views.setColorStateList(R.id.progress, "setProgressTintList", ColorStateList.valueOf(phaseColor))
        }

        views.setImageViewResource(
            R.id.play,
            if (state.status == Status.RUNNING) R.drawable.ic_pause else R.drawable.ic_play,
        )
        views.setContentDescription(
            R.id.play,
            context.getString(if (state.status == Status.RUNNING) R.string.pause else R.string.start),
        )
        views.setOnClickPendingIntent(R.id.play, serviceIntent(context, BreathingService.ACTION_TOGGLE, 10))
        views.setOnClickPendingIntent(R.id.header, openApp(context))

        if (layout == R.layout.widget_full) {
            views.setViewVisibility(R.id.stop, if (active) View.VISIBLE else View.GONE)
            views.setOnClickPendingIntent(R.id.stop, serviceIntent(context, BreathingService.ACTION_STOP, 11))
            views.setTextViewText(R.id.preset, presetName)
            views.setOnClickPendingIntent(R.id.prev, broadcast(context, WidgetActionReceiver.ACTION_PREV, 20))
            views.setOnClickPendingIntent(R.id.next, broadcast(context, WidgetActionReceiver.ACTION_NEXT, 21))
            views.setImageViewResource(
                R.id.sound,
                if (settings.soundEnabled) R.drawable.ic_volume_up else R.drawable.ic_volume_off,
            )
            views.setOnClickPendingIntent(R.id.sound, broadcast(context, WidgetActionReceiver.ACTION_SOUND, 22))
            views.setImageViewResource(
                R.id.vibration,
                if (settings.vibrationEnabled) R.drawable.ic_vibration else R.drawable.ic_vibration_off,
            )
            views.setOnClickPendingIntent(R.id.vibration, broadcast(context, WidgetActionReceiver.ACTION_VIBRATION, 23))
        }
        return views
    }

    private fun serviceIntent(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getForegroundService(
            context, code,
            Intent(context, BreathingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun broadcast(context: Context, action: String, code: Int): PendingIntent =
        PendingIntent.getBroadcast(
            context, code,
            Intent(context, WidgetActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun openApp(context: Context): PendingIntent =
        PendingIntent.getActivity(
            context, 30,
            Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
}

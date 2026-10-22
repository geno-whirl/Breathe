package com.genowhirl.breathe.engine

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.genowhirl.breathe.R
import com.genowhirl.breathe.audio.Cue
import com.genowhirl.breathe.audio.CuePlayer
import com.genowhirl.breathe.audio.Haptics
import com.genowhirl.breathe.audio.toCue
import com.genowhirl.breathe.data.SessionLog
import com.genowhirl.breathe.data.Store
import com.genowhirl.breathe.model.AppSettings
import com.genowhirl.breathe.model.BreathPattern
import com.genowhirl.breathe.model.BreathRunner
import com.genowhirl.breathe.model.SessionRecord
import com.genowhirl.breathe.model.Phase
import com.genowhirl.breathe.model.RunnerEvent
import com.genowhirl.breathe.model.VibrationMode
import com.genowhirl.breathe.model.countdownInCounts
import com.genowhirl.breathe.model.sessionLimit
import com.genowhirl.breathe.ui.MainActivity
import com.genowhirl.breathe.ui.cycleLabel
import com.genowhirl.breathe.ui.formatClock
import com.genowhirl.breathe.ui.theme.PhaseColors
import com.genowhirl.breathe.widget.BreathWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/**
 * Runs the breathing session. As a foreground service holding a partial wake lock it keeps
 * cueing with sound and vibration while the phone is locked, and it drives the widget.
 */
class BreathingService : Service() {
    // Session state is only touched on the main thread; audio work happens on the player's own thread.
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var store: Store
    private lateinit var player: CuePlayer
    private lateinit var haptics: Haptics
    private var runner: BreathRunner? = null
    private var loop: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var lastNotificationAt = 0L
    private var startedAt = 0L
    private var finishing = false
    private var startPreset = ""
    private var startPattern = BreathPattern()
    private var lastNotifiedStatus: Status? = null
    private var lastTickSecond = -1L

    override fun onCreate() {
        super.onCreate()
        store = Store.get(this)
        player = CuePlayer(this)
        haptics = Haptics(this)
        createChannel()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // Started with startForegroundService(), so we must always go to the foreground first.
        goForeground()
        val now = SystemClock.elapsedRealtime()
        val r = runner
        when (intent?.action) {
            ACTION_START -> if (r == null) startSession(now) else if (r.isPaused) resumeSession(now)
            ACTION_TOGGLE -> when {
                r == null -> startSession(now)
                r.isPaused -> resumeSession(now)
                else -> pauseSession(now)
            }
            ACTION_PAUSE -> if (r != null && !r.isPaused) pauseSession(now)
            ACTION_RESUME -> if (r != null && r.isPaused) resumeSession(now)
            ACTION_STOP -> stopSession(finished = false)
            else -> if (r == null) stopSession(finished = false)
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        runner?.let { record(it, finished = false) }
        runner = null
        loop?.cancel()
        scope.cancel()
        // Let the end-of-session vibration play out; the service stops right after starting it.
        if (!finishing) haptics.cancel()
        player.release()
        releaseWakeLock()
        if (Session.state.value.isActive) Session.publish(SessionState())
        BreathWidget.refresh(this)
        super.onDestroy()
    }

    private val settings: AppSettings get() = store.settings.value

    private fun startSession(now: Long) {
        val r = BreathRunner(
            pattern = { store.pattern.value },
            limit = { store.settings.value.sessionLimit },
        )
        runner = r
        finishing = false
        getSystemService(NotificationManager::class.java)?.cancel(COMPLETE_NOTIFICATION_ID)
        startedAt = System.currentTimeMillis()
        startPreset = store.matchingPreset()?.name ?: getString(R.string.custom_pattern)
        startPattern = store.pattern.value
        acquireWakeLock()
        val s = settings
        if (s.soundEnabled) player.prepare(s.soundStyle)
        val events = r.start(now)
        handle(events)
        publish(r, now, force = true)
        startLoop()
    }

    private fun pauseSession(now: Long) {
        val r = runner ?: return
        r.pause(now)
        loop?.cancel()
        haptics.cancel()
        releaseWakeLock()
        publish(r, now, force = true)
    }

    private fun resumeSession(now: Long) {
        val r = runner ?: return
        r.resume(now)
        acquireWakeLock()
        publish(r, now, force = true)
        startLoop()
    }

    private fun stopSession(finished: Boolean) {
        loop?.cancel()
        val r = runner
        runner = null
        finishing = finished
        if (!finished) haptics.cancel()
        releaseWakeLock()
        if (r != null) record(r, finished)
        if (finished && r != null) {
            postCompletion(r)
            val now = SystemClock.elapsedRealtime()
            Session.publish(
                SessionState(
                    status = Status.FINISHED,
                    cycle = r.cycle,
                    activeMillis = r.activeMillis(now),
                    snapshotAt = now,
                ),
            )
        } else {
            Session.publish(SessionState())
        }
        BreathWidget.refresh(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    /** Adds the session to the history, unless it was only a few seconds long. */
    private fun record(r: BreathRunner, finished: Boolean) {
        val active = r.activeMillis(SystemClock.elapsedRealtime())
        if (active < SessionLog.MIN_ACTIVE_MILLIS) return
        SessionLog.get(this).add(
            SessionRecord(
                startedAt = startedAt,
                endedAt = System.currentTimeMillis(),
                activeMillis = active,
                // A finished session ends exactly at the end of its last cycle.
                cycles = if (finished) r.cycle else r.cycle - 1,
                completed = finished,
                presetName = startPreset,
                pattern = startPattern,
            ),
        )
    }

    private fun startLoop() {
        loop?.cancel()
        loop = scope.launch {
            while (isActive) {
                val r = runner ?: break
                val now = SystemClock.elapsedRealtime()
                val events = r.update(now)
                if (events.isNotEmpty()) handle(events)
                if (r.isFinished) {
                    stopSession(finished = true)
                    break
                }
                maybeTick(r, now)
                val widgets = BreathWidget.hasWidgets(this@BreathingService) && isInteractive()
                publish(r, now, force = events.isNotEmpty(), widgets = widgets)

                var wakeAt = r.phaseEnd
                if (widgets) wakeAt = min(wakeAt, now + WIDGET_FRAME_MS)
                if (settings.tickSeconds && settings.soundEnabled) {
                    // Ticks follow the countdown: every second, or every count.
                    val step = tickStepMillis()
                    val nextTick = r.phaseStart + (floor((now - r.phaseStart) / step + 1) * step).toLong()
                    if (nextTick < r.phaseEnd - 150) wakeAt = min(wakeAt, nextTick)
                }
                delay(max(4L, wakeAt - SystemClock.elapsedRealtime()))
            }
        }
    }

    private fun handle(events: List<RunnerEvent>) {
        // After a long stall several boundaries can pass at once; only cue the latest one.
        val last = events.last()
        val s = settings
        when (last) {
            is RunnerEvent.Finished -> {
                if (s.soundEnabled) player.play(s.soundStyle, Cue.FINISH, s.soundVolume)
                if (s.vibrationEnabled) haptics.cue(Cue.FINISH, s.vibrationStrength)
            }
            is RunnerEvent.PhaseStarted -> {
                lastTickSecond = 0
                val isHold = last.phase == Phase.HOLD_IN || last.phase == Phase.HOLD_OUT
                if (s.soundEnabled && (!isHold || s.soundOnHolds)) {
                    player.play(s.soundStyle, last.phase.toCue(), s.soundVolume)
                }
                if (s.vibrationEnabled && (!isHold || s.vibrationOnHolds)) {
                    if (s.vibrationMode == VibrationMode.WAVE) {
                        haptics.wave(last.phase, last.durationMs, s.vibrationStrength)
                    } else {
                        haptics.cue(last.phase.toCue(), s.vibrationStrength)
                    }
                } else if (s.vibrationEnabled) {
                    haptics.cancel()
                }
            }
        }
    }

    private fun tickStepMillis(): Double {
        val pattern = store.pattern.value
        return pattern.countdownStepMillis(settings.countdownInCounts(pattern.mode))
    }

    private fun maybeTick(r: BreathRunner, now: Long) {
        val s = settings
        if (!s.tickSeconds || !s.soundEnabled) return
        val tick = floor((now - r.phaseStart + 1) / tickStepMillis()).toLong()
        if (tick > lastTickSecond && tick > 0 && now < r.phaseEnd - 150) {
            lastTickSecond = tick
            player.play(s.soundStyle, Cue.TICK, s.soundVolume)
        }
    }

    private fun publish(r: BreathRunner, now: Long, force: Boolean, widgets: Boolean = true) {
        val state = SessionState(
            status = if (r.isPaused) Status.PAUSED else Status.RUNNING,
            phase = r.phase,
            phaseStart = r.phaseStart,
            phaseDuration = r.phaseDuration,
            pausedPhaseElapsed = r.phaseElapsed(now),
            cycle = r.cycle,
            activeMillis = r.activeMillis(now),
            snapshotAt = now,
        )
        Session.publish(state)
        if (widgets || force) BreathWidget.refresh(this, state)
        if (force && (state.status != lastNotifiedStatus || now - lastNotificationAt > 250)) {
            lastNotificationAt = now
            lastNotifiedStatus = state.status
            getSystemService(NotificationManager::class.java)?.notify(NOTIFICATION_ID, buildNotification(state))
        }
    }

    private fun goForeground() {
        val notification = buildNotification(Session.state.value)
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
        } else {
            0
        }
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun buildNotification(state: SessionState): Notification {
        val now = SystemClock.elapsedRealtime()
        val presetName = store.matchingPreset()?.name ?: getString(R.string.custom_pattern)
        val title = when (state.status) {
            Status.PAUSED -> getString(R.string.paused)
            Status.RUNNING -> getString(phaseLabel(state.phase))
            else -> getString(R.string.app_name)
        }
        val text = getString(R.string.notification_detail, presetName, cycleLabel(resources, state.cycle, settings))
        val builder = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_breath)
            .setContentTitle(title)
            .setContentText(text)
            .setColor(PhaseColors.argb(state.phase))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setShowWhen(false)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
        if (state.status == Status.RUNNING) {
            // A system-driven countdown, so the notification stays live without constant updates.
            builder.setUsesChronometer(true)
                .setChronometerCountDown(true)
                .setShowWhen(true)
                .setWhen(System.currentTimeMillis() + state.phaseRemaining(now) + 999)
            builder.addAction(R.drawable.ic_pause, getString(R.string.pause), serviceIntent(ACTION_PAUSE, 1))
        } else if (state.status == Status.PAUSED) {
            builder.addAction(R.drawable.ic_play, getString(R.string.resume), serviceIntent(ACTION_RESUME, 2))
        }
        builder.addAction(R.drawable.ic_stop, getString(R.string.stop), serviceIntent(ACTION_STOP, 3))
        return builder.build()
    }

    private fun serviceIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getForegroundService(
            this, requestCode,
            Intent(this, BreathingService::class.java).setAction(action),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )

    private fun createChannel() {
        val nm = getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(CHANNEL_ID, getString(R.string.channel_name), NotificationManager.IMPORTANCE_LOW).apply {
            description = getString(R.string.channel_description)
            setShowBadge(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        // Silent: the app plays its own finishing sound and vibration.
        val complete = NotificationChannel(
            COMPLETE_CHANNEL_ID,
            getString(R.string.complete_channel_name),
            NotificationManager.IMPORTANCE_DEFAULT,
        ).apply {
            description = getString(R.string.complete_channel_description)
            setSound(null, null)
            enableVibration(false)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        }
        nm.createNotificationChannels(listOf(channel, complete))
    }

    /** A note that stays after the session, with what was done. */
    private fun postCompletion(r: BreathRunner) {
        val active = r.activeMillis(SystemClock.elapsedRealtime())
        val cycles = resources.getQuantityString(R.plurals.cycles_count, r.cycle, r.cycle)
        val notification = NotificationCompat.Builder(this, COMPLETE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_breath)
            .setContentTitle(getString(R.string.session_complete))
            .setContentText(getString(R.string.session_complete_detail, startPreset, cycles, formatClock(active)))
            .setColor(PhaseColors.argb(Phase.INHALE))
            .setAutoCancel(true)
            .setSilent(true)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(
                PendingIntent.getActivity(
                    this, 4,
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
        runCatching { getSystemService(NotificationManager::class.java)?.notify(COMPLETE_NOTIFICATION_ID, notification) }
    }

    private fun isInteractive(): Boolean = getSystemService(PowerManager::class.java)?.isInteractive != false

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return
        wakeLock = getSystemService(PowerManager::class.java)
            ?.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "Breathe:session")
            ?.apply {
                setReferenceCounted(false)
                acquire(4 * 60 * 60 * 1000L)
            }
    }

    private fun releaseWakeLock() {
        wakeLock?.takeIf { it.isHeld }?.release()
        wakeLock = null
    }

    companion object {
        const val ACTION_START = "com.genowhirl.breathe.START"
        const val ACTION_TOGGLE = "com.genowhirl.breathe.TOGGLE"
        const val ACTION_PAUSE = "com.genowhirl.breathe.PAUSE"
        const val ACTION_RESUME = "com.genowhirl.breathe.RESUME"
        const val ACTION_STOP = "com.genowhirl.breathe.STOP"

        private const val CHANNEL_ID = "session"
        private const val NOTIFICATION_ID = 1
        private const val COMPLETE_CHANNEL_ID = "complete"
        private const val COMPLETE_NOTIFICATION_ID = 2
        private const val WIDGET_FRAME_MS = 150L

        fun phaseLabel(phase: Phase): Int = when (phase) {
            Phase.INHALE -> R.string.phase_inhale
            Phase.HOLD_IN -> R.string.phase_hold_in
            Phase.EXHALE -> R.string.phase_exhale
            Phase.HOLD_OUT -> R.string.phase_hold_out
        }
    }
}

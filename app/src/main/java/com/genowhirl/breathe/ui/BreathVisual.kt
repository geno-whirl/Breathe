package com.genowhirl.breathe.ui

import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.genowhirl.breathe.R
import com.genowhirl.breathe.engine.BreathingService
import com.genowhirl.breathe.engine.SessionState
import com.genowhirl.breathe.engine.Status
import com.genowhirl.breathe.model.AnimationStyle
import com.genowhirl.breathe.model.AppSettings
import com.genowhirl.breathe.model.BreathPattern
import com.genowhirl.breathe.model.Phase
import com.genowhirl.breathe.model.breathLevel
import com.genowhirl.breathe.ui.theme.LocalAmbience
import com.genowhirl.breathe.ui.theme.PhaseColors
import com.genowhirl.breathe.ui.theme.PhaseLabelStyle
import kotlinx.coroutines.delay
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@Composable
fun BreathVisual(
    state: SessionState,
    pattern: BreathPattern,
    settings: AppSettings,
    modifier: Modifier = Modifier,
) {
    val animate = settings.animationEnabled
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(state.status, animate) {
        now = SystemClock.elapsedRealtime()
        if (state.status != Status.RUNNING) return@LaunchedEffect
        while (true) {
            if (animate) {
                withFrameMillis { }
            } else {
                delay(200)
            }
            now = SystemClock.elapsedRealtime()
        }
    }

    val active = state.isActive
    val phase = if (active) state.phase else Phase.INHALE
    val progress = if (active) state.phaseProgress(now) else 0f
    val level = if (active) breathLevel(phase, progress, settings.naturalEasing) else 0.45f
    val color by animateColorAsState(
        if (active) PhaseColors.of(phase) else PhaseColors.inhale,
        animationSpec = tween(600),
        label = "phaseColor",
    )

    // A slow idle shimmer so the screen feels alive before a session starts.
    val idle = rememberInfiniteTransition(label = "idle")
    val idlePulse by idle.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(4000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "idlePulse",
    )
    val ambience = LocalAmbience.current
    val durations = pattern.phaseMillis()
    val cycle = durations.sum().coerceAtLeast(1L)
    // Where we are in the current cycle, 0..1, for the ring and the travelling dot.
    val cyclePosition = if (active) {
        (durations.take(phase.ordinal).sum() + progress * durations[phase.ordinal]) / cycle
    } else {
        0f
    }

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val compact = min(maxWidth.value, maxHeight.value) < 300f
        if (animate) {
            Canvas(Modifier.fillMaxSize()) {
                val shownLevel = if (active) level else 0.38f + 0.12f * idlePulse
                when (settings.animationStyle) {
                    AnimationStyle.ORB -> {
                        drawCycleRing(durations, cycle, cyclePosition, active, ambience.track)
                        drawOrb(shownLevel, color, ambience.glow)
                    }
                    AnimationStyle.BOX -> drawBox(phase, progress, shownLevel, color, active, ambience.track)
                    AnimationStyle.MINIMAL -> drawCycleRing(durations, cycle, cyclePosition, active, ambience.track)
                }
            }
        }

        val textColor = MaterialTheme.colorScheme.onBackground
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            val label = when (state.status) {
                Status.RUNNING -> stringResource(BreathingService.phaseLabel(phase))
                Status.PAUSED -> stringResource(R.string.paused)
                Status.FINISHED -> stringResource(R.string.well_done)
                Status.IDLE -> stringResource(R.string.ready)
            }
            Text(
                label,
                style = PhaseLabelStyle,
                color = textColor,
                textAlign = TextAlign.Center,
            )
            if (active && settings.showCountdown) {
                val seconds = ceil(state.phaseRemaining(now) / 1000.0).toInt().coerceAtLeast(0)
                Text(
                    seconds.toString(),
                    style = if (compact) MaterialTheme.typography.displayMedium else MaterialTheme.typography.displayLarge,
                    color = textColor,
                )
            } else if (state.status == Status.FINISHED) {
                Text(
                    stringResource(R.string.finished_summary, state.cycle, formatClock(state.activeMillis)),
                    style = MaterialTheme.typography.bodyMedium,
                    color = textColor.copy(alpha = 0.75f),
                )
            }
        }
    }
}

/** The cycle as a ring of four coloured arcs, lit up to the current moment. */
private fun DrawScope.drawCycleRing(
    durations: LongArray,
    cycle: Long,
    position: Float,
    active: Boolean,
    track: Color,
) {
    val stroke = 5.dp.toPx()
    val radius = size.minDimension / 2 - 10.dp.toPx()
    val topLeft = Offset(center.x - radius, center.y - radius)
    val arcSize = Size(radius * 2, radius * 2)
    val nonEmpty = durations.count { it > 0 }
    val gap = if (nonEmpty > 1) 5f else 0f
    var start = -90f
    Phase.entries.forEach { p ->
        val ms = durations[p.ordinal]
        if (ms <= 0) return@forEach
        val sweep = 360f * ms / cycle
        val c = PhaseColors.of(p)
        drawArc(
            color = if (active) c.copy(alpha = 0.22f) else c.copy(alpha = 0.55f),
            startAngle = start + gap / 2,
            sweepAngle = (sweep - gap).coerceAtLeast(0.5f),
            useCenter = false,
            topLeft = topLeft,
            size = arcSize,
            style = Stroke(stroke, cap = StrokeCap.Round),
        )
        if (active) {
            val lit = (position * 360f - (start + 90f)).coerceIn(0f, sweep)
            if (lit > gap / 2) {
                drawArc(
                    color = c,
                    startAngle = start + gap / 2,
                    sweepAngle = (lit - gap / 2).coerceAtMost(sweep - gap).coerceAtLeast(0.5f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(stroke, cap = StrokeCap.Round),
                )
            }
        }
        start += sweep
    }
    if (nonEmpty == 0) drawCircle(track, radius, style = Stroke(stroke))
    if (active) {
        val angle = Math.toRadians((position * 360f - 90f).toDouble())
        val dot = Offset(center.x + radius * cos(angle).toFloat(), center.y + radius * sin(angle).toFloat())
        val phaseIndex = phaseAt(durations, cycle, position)
        val c = PhaseColors.of(phaseIndex)
        drawCircle(Brush.radialGradient(listOf(c.copy(alpha = 0.55f), Color.Transparent), dot, 18.dp.toPx()), 18.dp.toPx(), dot)
        drawCircle(Color.White, 6.dp.toPx(), dot)
        drawCircle(c, 4.dp.toPx(), dot)
    }
}

private fun phaseAt(durations: LongArray, cycle: Long, position: Float): Phase {
    var acc = 0L
    val target = position * cycle
    Phase.entries.forEach { p ->
        acc += durations[p.ordinal]
        if (target < acc) return p
    }
    return Phase.entries.last { durations[it.ordinal] > 0 }
}

/** A glowing orb that grows with the in-breath and shrinks with the out-breath. */
private fun DrawScope.drawOrb(level: Float, color: Color, glow: Color) {
    val maxR = size.minDimension / 2 - 28.dp.toPx()
    val minR = maxR * 0.5f
    val r = minR + (maxR - minR) * level
    // Soft halo around the bubble.
    drawCircle(
        Brush.radialGradient(
            listOf(glow.copy(alpha = 0.45f), color.copy(alpha = 0.16f), Color.Transparent),
            center,
            r * 1.5f,
        ),
        r * 1.5f,
    )
    // A translucent bubble, clear in the middle and denser toward the rim, so text over it
    // keeps the contrast of the background in both themes.
    drawCircle(
        Brush.radialGradient(
            listOf(color.copy(alpha = 0.08f), color.copy(alpha = 0.18f), color.copy(alpha = 0.42f)),
            center,
            r,
        ),
        r,
    )
    drawCircle(color.copy(alpha = 0.9f), r, style = Stroke(2.dp.toPx()))
    val inset = r * 0.86f
    drawArc(
        color = Color.White.copy(alpha = 0.28f),
        startAngle = 200f,
        sweepAngle = 55f,
        useCenter = false,
        topLeft = Offset(center.x - inset, center.y - inset),
        size = Size(inset * 2, inset * 2),
        style = Stroke(3.dp.toPx(), cap = StrokeCap.Round),
    )
}

/**
 * Box breathing: a dot travels around a rounded square (up for the inhale, across for the hold,
 * down for the exhale, back for the second hold) while the square fills like the lungs.
 */
private fun DrawScope.drawBox(
    phase: Phase,
    progress: Float,
    level: Float,
    color: Color,
    active: Boolean,
    track: Color,
) {
    val side = min(size.width, size.height) * 0.68f
    val left = center.x - side / 2
    val top = center.y - side / 2
    val corner = side * 0.12f
    val rect = Rect(left, top, left + side, top + side)
    val shape = Path().apply { addRoundRect(androidx.compose.ui.geometry.RoundRect(rect, CornerRadius(corner))) }

    clipPath(shape) {
        val fillTop = rect.bottom - side * level
        drawRect(
            Brush.verticalGradient(listOf(color.copy(alpha = 0.38f), color.copy(alpha = 0.12f)), fillTop, rect.bottom),
            topLeft = Offset(rect.left, fillTop),
            size = Size(side, rect.bottom - fillTop),
        )
    }
    drawPath(shape, track, style = Stroke(5.dp.toPx()))

    // Corners in travel order: bottom-left, top-left, top-right, bottom-right.
    val corners = listOf(
        Offset(rect.left, rect.bottom),
        Offset(rect.left, rect.top),
        Offset(rect.right, rect.top),
        Offset(rect.right, rect.bottom),
    )
    val from = corners[phase.ordinal]
    val to = corners[(phase.ordinal + 1) % 4]
    val t = if (active) progress else 0f
    val dot = Offset(from.x + (to.x - from.x) * t, from.y + (to.y - from.y) * t)
    if (active) {
        drawLine(color, from, dot, 5.dp.toPx(), cap = StrokeCap.Round)
    }
    drawCircle(Brush.radialGradient(listOf(color.copy(alpha = 0.6f), Color.Transparent), dot, 20.dp.toPx()), 20.dp.toPx(), dot)
    drawCircle(Color.White, 7.dp.toPx(), dot)
    drawCircle(color, 5.dp.toPx(), dot)
}

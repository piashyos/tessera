package app.tessera.core.ui.theme

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ImageShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.toSize

/** Grain alpha at [intensity] = 1f. Deliberately low so the texture reads as a subtle film grain. */
private const val MaxGrainAlpha = 0.12f

/** Peak alpha of a gradient blob on a light / dark background — "barely visible" is the point. */
private const val BackdropAlphaLight = 0.35f
private const val BackdropAlphaDark = 0.25f

/** A gradient blob at rest: centre as fractions of (width, height), radius as a fraction of the width. */
private class Blob(val cx: Float, val cy: Float, val radius: Float)

private val Blobs = listOf(Blob(0.15f, 0.12f, 0.80f), Blob(0.90f, 0.48f, 0.70f), Blob(0.30f, 0.92f, 0.85f))

/**
 * Provides the app's base background — the theme [background][androidx.compose.material3.ColorScheme.background]
 * color plus, optionally, soft gradient blobs ([gradientEnabled]) and a tiled monochrome film-grain texture
 * ([enabled], in the spirit of Arc) — and lays [content] on top of it.
 *
 * Layers, bottom to top: background color → three gradient blobs (primary / tertiary / secondary container) →
 * grain → [content]. The grain stays above the blobs so the gradient does not wash it out. Both are drawn
 * BEHIND the content, so they only show on the background: opaque elements (cards, bars, sheets) paint over
 * them and stay crisp. For this to work the screens above must NOT paint their own opaque full-screen
 * background (their Scaffolds / root Surfaces are transparent), so the background comes from here.
 *
 * [intensity] (0f..1f) maps linearly to the grain alpha, capped at [MaxGrainAlpha]; [grainSize] (1..4) is the
 * side of one grain speck in px.
 *
 * While [gradientEnabled] and [motionEnabled]: a tap makes the blobs breathe, a scroll or swipe moves them in
 * three different directions and they glide on and settle back ([motion] holds the animation state; tests pass
 * their own). The overlay listens with a read-only pointer-input node on the root: it never consumes, so it does
 * not steal touches from the content. The blob and grain layers are draw-only (no pointer input, no semantics);
 * motion only redraws the blob layer, never the grain. With [motionEnabled] false there is no listener at all.
 */
@Composable
fun GrainOverlay(
    enabled: Boolean,
    intensity: Float,
    modifier: Modifier = Modifier,
    grainSize: Int = 1,
    gradientEnabled: Boolean = false,
    motionEnabled: Boolean = true,
    motion: BackdropMotionState = rememberBackdropMotionState(),
    content: @Composable () -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val background = colors.background
    val alpha = intensity.coerceIn(0f, 1f) * MaxGrainAlpha
    val showGrain = enabled && alpha > 0f
    // One deterministic noise tile, built once while grain is on and repeated across the viewport.
    // A fixed seed keeps the pattern stable across recompositions/config changes so it never
    // visibly "reshuffles"; keying on [showGrain] / [grainSize] avoids allocating the bitmap while grain is off.
    val brush = remember(showGrain, grainSize) {
        if (!showGrain) return@remember null
        val tile = grainTile(grainSize)
        val bitmap = Bitmap.createBitmap(tile.side, tile.side, Bitmap.Config.ARGB_8888)
        bitmap.setPixels(tile.pixels, 0, tile.side, 0, 0, tile.side, tile.side)
        ShaderBrush(ImageShader(bitmap.asImageBitmap(), TileMode.Repeated, TileMode.Repeated))
    }
    val blobAlpha = if (background.luminance() < 0.5f) BackdropAlphaDark else BackdropAlphaLight
    val blobColors = listOf(colors.primaryContainer, colors.tertiaryContainer, colors.secondaryContainer)
    Box(
        modifier
            .fillMaxSize()
            .drawBehind { drawRect(background) }
            .then(if (gradientEnabled && motionEnabled) Modifier.pointerInput(motion) { trackBackdropGestures(motion) } else Modifier),
    ) {
        if (gradientEnabled) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer()
                    .drawWithCache {
                        // Brushes are built once, at the glow's peak alpha; a frame of motion only translates / scales them
                        // and sets the draw alpha (at rest 1 / PulseGlow, so the resting tint is blobAlpha).
                        val centers = Blobs.map { Offset(it.cx * size.width, it.cy * size.height) }
                        val radii = Blobs.map { it.radius * size.width }
                        val brushes = Blobs.indices.map { i ->
                            val c = blobColors[i]
                            Brush.radialGradient(
                                listOf(c.copy(alpha = blobAlpha * PulseGlow), c.copy(alpha = 0f)),
                                center = centers[i],
                                radius = radii[i],
                            )
                        }
                        onDrawBehind {
                            val breath = motion.scale
                            val glow = pulseGlowAlpha(motion.pulse)
                            for (i in Blobs.indices) {
                                val shift = motion.offset(i)
                                translate(shift.x, shift.y) {
                                    scale(breath, breath, pivot = centers[i]) {
                                        drawCircle(brushes[i], radius = radii[i], center = centers[i], alpha = glow)
                                    }
                                }
                            }
                        }
                    },
            )
        }
        if (brush != null) {
            Box(Modifier.fillMaxSize().graphicsLayer().drawBehind { drawRect(brush = brush, alpha = alpha) })
        }
        content()
    }
}

/** The last position sample older than this at release means the finger had stopped: no fling. */
private const val VelocityStaleMillis = 40L

/**
 * Feeds [motion] from the pointers on the root in the Initial pass and never consumes. A pointer counts as dragging
 * only while it stays down (`pressed && previousPressed`); the frame's shift is the mean over such pointers, summed up
 * into one position for the [VelocityTracker]. On the last pointer's release, in this order: a cancel (an "up" that is
 * already consumed — ui 1.11.3 synthesizes it so, also when the node goes away) settles the blobs with no breath;
 * otherwise, with the travel within touch slop, a single finger and a press no longer than the long-press timeout is a
 * tap (breath), a longer one or one with a second finger is nothing; everything else is a swipe (fling).
 */
private suspend fun PointerInputScope.trackBackdropGestures(motion: BackdropMotionState) {
    val tracker = VelocityTracker()
    val maxFling = viewConfiguration.maximumFlingVelocity
    awaitPointerEventScope {
        var active = false
        var multiTouch = false
        var downTime = 0L
        var lastSample = 0L
        var position = Offset.Zero
        var travel = 0f
        // The node can go away mid-gesture (gradient or motion switched off while a finger is down): treat it as a cancel.
        try {
            while (true) {
                val changes = awaitPointerEvent(PointerEventPass.Initial).changes
                if (changes.any { it.changedToUpIgnoreConsumed() && it.isConsumed }) {
                    if (active) motion.onCancel()
                    active = false
                    continue
                }
                if (!active) {
                    val down = changes.firstOrNull { it.changedToDown() } ?: continue
                    active = true
                    multiTouch = false
                    downTime = down.uptimeMillis
                    lastSample = downTime
                    position = Offset.Zero
                    travel = 0f
                    tracker.resetTracking()
                    motion.onPress(size.toSize())
                }
                if (changes.count { it.pressed } > 1) multiTouch = true
                // A batched move (several points in one event) feeds the tracker every point, not only the last: with one
                // finger the accumulated position is that finger's position plus a constant, so history maps straight over.
                changes.singleOrNull { it.pressed && it.previousPressed }?.takeIf { !multiTouch || changes.count { c -> c.pressed } == 1 }
                    ?.let { c ->
                        val shift = position - c.previousPosition
                        for (h in c.historical) {
                            if (h.uptimeMillis <= lastSample) continue
                            tracker.addPosition(h.uptimeMillis, h.position + shift)
                            lastSample = h.uptimeMillis
                        }
                    }
                val dragging = changes.filter { it.pressed && it.previousPressed }
                if (dragging.isNotEmpty()) {
                    val delta = dragging.fold(Offset.Zero) { acc, c -> acc + (c.position - c.previousPosition) } / dragging.size.toFloat()
                    if (delta != Offset.Zero) {
                        position += delta
                        travel = maxOf(travel, position.getDistance())
                        motion.onDrag(delta, size.toSize())
                    }
                }
                val now = changes.first().uptimeMillis
                // Only while a finger is down: the release frame repeats the last position and would drag the speed down.
                if (changes.any { it.pressed }) {
                    tracker.addPosition(now, position)
                    lastSample = now
                }
                if (changes.none { it.pressed }) {
                    active = false
                    val still = travel <= viewConfiguration.touchSlop
                    when {
                        still && !multiTouch && now - downTime <= viewConfiguration.longPressTimeoutMillis -> motion.onTap()
                        still -> motion.onSettle()
                        else -> {
                            // A finger that stood still before lifting has no speed (as in Compose's own fling handling).
                            val v = if (now - lastSample > VelocityStaleMillis) Velocity.Zero else tracker.calculateVelocity(Velocity(maxFling, maxFling))
                            motion.onFling(Offset(v.x, v.y), size.toSize())
                        }
                    }
                }
            }
        } finally {
            if (active) motion.onCancel()
            motion.endPulse()
        }
    }
}

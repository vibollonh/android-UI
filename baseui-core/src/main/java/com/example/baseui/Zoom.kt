package com.example.baseui

/** Drags that dismiss a zoom presentation. */
enum class ZoomDismissGesture {
    /** Drag down: the view shrinks and follows the finger. */
    Down,
    /** Drag right: the view slides right and zooms out. */
    Right,
}

/**
 * Look and behaviour of a zoom presentation (Compose `ZoomPresentation`, View `BaseZoomTransition`).
 */
data class ZoomConfig(
    /** Empty: only code (a close button, system back) dismisses. */
    val dismissGestures: Set<ZoomDismissGesture> = setOf(ZoomDismissGesture.Down, ZoomDismissGesture.Right),
    /**
     * With a navigation stack inside the presented screen, drags only dismiss from its first screen;
     * pushed screens go back with back / the back gesture. `true` lets drag down dismiss from pushed
     * screens too.
     */
    val dismissesFromPushedScreens: Boolean = false,
    /** Drag distance (dp) after which letting go dismisses; a fast flick dismisses sooner. */
    val dismissDistanceDp: Float = 120f,
    /** Smallest scale while dragging. */
    val minimumScale: Float = 0.7f,
    /** Dims the screen behind; `0` turns dimming off. */
    val dimAlpha: Float = 0.35f,
    /** Corner radius of the source (dp); `null` uses 16dp. */
    val sourceCornerRadiusDp: Float? = null,
    val presentDurationMs: Int = 450,
    val dismissDurationMs: Int = 400,
)

/** Drag-to-dismiss maths shared by both implementations. Distances in pixels. */
object ZoomDrag {

    enum class Axis { Down, Right }

    data class Frame(val translationX: Float, val translationY: Float, val scale: Float, val progress: Float)

    /** First movement picks the axis; `null` while undecided or when that direction is off. */
    fun axis(dx: Float, dy: Float, slop: Float, gestures: Set<ZoomDismissGesture>): Axis? {
        if (dx * dx + dy * dy < slop * slop) return null
        return if (kotlin.math.abs(dx) > kotlin.math.abs(dy)) {
            if (dx > 0 && ZoomDismissGesture.Right in gestures) Axis.Right else null
        } else {
            if (dy > 0 && ZoomDismissGesture.Down in gestures) Axis.Down else null
        }
    }

    /** Down: shrinks and drifts with the finger. Right: slides right and zooms out. */
    fun frame(axis: Axis, dx: Float, dy: Float, density: Float, minimumScale: Float): Frame {
        val progress = when (axis) {
            Axis.Down -> (dy.coerceAtLeast(0f) / (400 * density)).coerceIn(0f, 1f)
            Axis.Right -> (dx.coerceAtLeast(0f) / (300 * density)).coerceIn(0f, 1f)
        }
        val scale = 1 - (1 - minimumScale) * progress
        return when (axis) {
            Axis.Down -> Frame(dx * 0.6f, dy.coerceAtLeast(0f) * 0.55f, scale, progress)
            Axis.Right -> Frame(dx.coerceAtLeast(0f) * 0.7f, dy * 0.3f, scale, progress)
        }
    }

    /** Whether letting go dismisses. */
    fun shouldDismiss(axis: Axis, dx: Float, dy: Float, velocity: Float, density: Float, distanceDp: Float): Boolean {
        val distance = if (axis == Axis.Down) dy else dx
        return distance >= distanceDp * density || (velocity > 900 * density && distance > 20 * density)
    }
}

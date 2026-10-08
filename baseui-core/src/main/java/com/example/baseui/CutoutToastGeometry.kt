package com.example.baseui

import android.graphics.RectF

/** Icon tint and meaning of a cutout toast. Icons are drawn by the Compose / View layers. */
enum class ToastStyle(val tint: Int) {
    Success(0xFF34C759.toInt()),
    Error(0xFFFF453A.toInt()),
    Warning(0xFFFF9F0A.toInt()),
    Info(0xFF0A84FF.toInt()),
}

/**
 * Where a cutout toast starts and ends. Shared by the Compose and View implementations.
 *
 * - Punch-hole phones: the pill starts as the camera hole and grows out of it; the content sits below
 *   the hole, which stays a black strip.
 * - Notch / no cutout: the pill pops in just below the status bar.
 */
object CutoutToastGeometry {

    data class Frames(
        val collapsed: RectF,
        val expanded: RectF,
        /** Distance from the expanded frame's top to the content (keeps the camera area clear). */
        val contentTop: Float,
        val growsFromCutout: Boolean,
    )

    /**
     * @param contentWidth width the message + icon need, padding included
     * @param contentHeight height of one content row, padding included
     * @param sideInset gap to the screen edges when [fullWidth]
     * @param topPadding how far the expanded pill starts above the camera hole (punch-hole phones), so it
     *   surrounds the hole with a margin on top as well
     */
    fun frames(
        screen: DeviceScreenInfo,
        contentWidth: Float,
        contentHeight: Float,
        sideInset: Float,
        fullWidth: Boolean,
        minWidth: Float,
        topPadding: Float = 0f,
    ): Frames {
        val width = screen.widthPx.toFloat()
        val maxWidth = width - sideInset * 2
        val cutout = screen.cutoutRect
        if (screen.cutout == CutoutType.PunchHole && cutout != null) {
            val collapsed = RectF(cutout)
            // The pill starts [topPadding] above the hole (never above the screen edge).
            val top = (cutout.top - topPadding).coerceAtLeast(0f)
            // Same gap on the sides as above the pill, so it hugs the screen evenly.
            val inset = if (fullWidth) top.coerceAtLeast(sideInset / 2) else sideInset
            val w = if (fullWidth) width - inset * 2 else contentWidth.coerceIn(minWidth, maxWidth)
            // Content starts below the hole, which stays inside a black strip.
            val contentTop = cutout.bottom - top
            val expanded = RectF(width / 2 - w / 2, top, width / 2 + w / 2, top + contentTop + contentHeight)
            return Frames(collapsed, expanded, contentTop, growsFromCutout = true)
        }

        val top = screen.safeTopPx + sideInset / 2
        val w = if (fullWidth) maxWidth else contentWidth.coerceIn(minWidth, maxWidth)
        val small = contentHeight * 0.75f
        val collapsed = RectF(width / 2 - small / 2, top, width / 2 + small / 2, top + small)
        val expanded = RectF(width / 2 - w / 2, top, width / 2 + w / 2, top + contentHeight)
        return Frames(collapsed, expanded, 0f, growsFromCutout = false)
    }

    /** Linear interpolation between two frames (`t` may overshoot for springs). */
    fun lerp(from: RectF, to: RectF, t: Float): RectF = RectF(
        from.left + (to.left - from.left) * t,
        from.top + (to.top - from.top) * t,
        from.right + (to.right - from.right) * t,
        from.bottom + (to.bottom - from.bottom) * t,
    )
}

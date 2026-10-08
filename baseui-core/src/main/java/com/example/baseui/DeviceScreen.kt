package com.example.baseui

import android.graphics.Rect
import android.os.Build
import android.view.RoundedCorner
import android.view.View
import android.view.WindowInsets

/** What sits at the top of the screen. */
enum class CutoutType {
    /** A small camera hole in the display (the Android counterpart of the Dynamic Island). */
    PunchHole,
    /** A wide cutout reaching in from the top edge. */
    Notch,
    None,
}

/**
 * Screen facts BaseUI components adapt to. All sizes are in pixels.
 *
 *     val screen = DeviceScreen.from(view)
 *     if (screen.cutout == CutoutType.PunchHole) { ... }
 */
data class DeviceScreenInfo(
    val cutout: CutoutType,
    /** Bounds of the top cutout in window coordinates, or `null` when there is none. */
    val cutoutRect: Rect?,
    /** Corner radius of the physical display; `0` when unknown (API < 31) or square. */
    val cornerRadiusPx: Int,
    /** Height of the status bar / cutout area. */
    val safeTopPx: Int,
    val widthPx: Int,
    val heightPx: Int,
)

object DeviceScreen {

    /** Reads the screen from a view attached to a window; before attachment everything is `None` / `0`. */
    fun from(view: View): DeviceScreenInfo {
        val insets = view.rootWindowInsets
        val root = view.rootView
        val width = root.width.takeIf { it > 0 } ?: view.resources.displayMetrics.widthPixels
        val height = root.height.takeIf { it > 0 } ?: view.resources.displayMetrics.heightPixels
        if (insets == null) {
            return DeviceScreenInfo(CutoutType.None, null, 0, 0, width, height)
        }

        val cutout = insets.displayCutout
        val topCutout = cutout?.boundingRects
            ?.filter { it.top <= 0 || it.top < height / 10 }
            ?.minByOrNull { it.top }
            ?.let { box -> preciseCutout(cutout, box) ?: box }
        val safeTop = insets.getInsets(WindowInsets.Type.statusBars() or WindowInsets.Type.displayCutout()).top

        return DeviceScreenInfo(
            cutout = classify(topCutout, width),
            cutoutRect = topCutout?.let(::Rect),
            cornerRadiusPx = cornerRadius(insets),
            safeTopPx = safeTop,
            widthPx = width,
            heightPx = height,
        )
    }

    /**
     * Pure classification (no Android state), so it can be unit-tested.
     * A punch-hole is small and roughly round and doesn't touch the top edge; a notch is wide or
     * starts at the top edge.
     */
    fun classify(cutout: Rect?, screenWidthPx: Int): CutoutType =
        if (cutout == null) CutoutType.None else classify(cutout.width(), cutout.height(), screenWidthPx)

    fun classify(cutoutWidthPx: Int, cutoutHeightPx: Int, screenWidthPx: Int): CutoutType {
        val w = cutoutWidthPx
        val h = cutoutHeightPx
        if (w <= 0 || h <= 0) return CutoutType.None
        val small = w < screenWidthPx * 0.2f
        val roundish = w <= h * 2.2f
        return if (small && roundish) CutoutType.PunchHole else CutoutType.Notch
    }

    /**
     * The bounding rects are padded boxes; the cutout path (API 31+) is the actual camera shape, so
     * a toast can start exactly as the hole.
     */
    private fun preciseCutout(cutout: android.view.DisplayCutout, box: Rect): Rect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return null
        val path = cutout.cutoutPath ?: return null
        val bounds = android.graphics.RectF()
        path.computeBounds(bounds, true)
        val precise = Rect()
        bounds.roundOut(precise)
        return precise.takeIf { !it.isEmpty && Rect.intersects(it, box) }
    }

    private fun cornerRadius(insets: WindowInsets): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return 0
        return insets.getRoundedCorner(RoundedCorner.POSITION_TOP_LEFT)?.radius ?: 0
    }
}

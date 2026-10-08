package com.example.baseui.views

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.Outline
import android.graphics.Rect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.PathInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.activity.BackEventCompat
import androidx.activity.OnBackPressedCallback
import androidx.core.view.drawToBitmap
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import com.example.baseui.DeviceScreen
import com.example.baseui.ZoomConfig
import com.example.baseui.ZoomDrag
import kotlin.math.abs

/**
 * Zoom presentation for Fragment-based screens: the fragment grows out of a source view and shrinks
 * back into it. Drag down / right (see [ZoomConfig.dismissGestures]) or the back gesture dismisses.
 *
 *     BaseZoomTransition.present(activity, cardView, WalletDetailFragment.newInstance(card))
 *
 *     // inside the fragment:
 *     closeButton.setOnClickListener { dismissZoom() }
 *
 * Pushed screens inside the fragment (its childFragmentManager back stack) go back with back and
 * don't close by dragging, unless [ZoomConfig.dismissesFromPushedScreens].
 */
object BaseZoomTransition {

    private val presentations = HashMap<Fragment, Presentation>()

    fun present(
        activity: FragmentActivity,
        source: View,
        fragment: Fragment,
        config: ZoomConfig = ZoomConfig(),
        onDismissed: (() -> Unit)? = null,
    ) {
        if (presentations.isNotEmpty()) return
        val decor = activity.window.decorView as ViewGroup
        val presentation = Presentation(activity, decor, source, fragment, config, onDismissed)
        presentations[fragment] = presentation
        presentation.start()
    }

    /** Zooms the presentation showing [fragment] (or one of its children) back into its source. */
    fun dismiss(fragment: Fragment) {
        var f: Fragment? = fragment
        while (f != null) {
            presentations[f]?.let { it.dismiss(); return }
            f = f.parentFragment
        }
    }

    private class Presentation(
        private val activity: FragmentActivity,
        private val decor: ViewGroup,
        private val source: View,
        private val fragment: Fragment,
        val config: ZoomConfig,
        private val onDismissed: (() -> Unit)?,
    ) {
        private val density = activity.resources.displayMetrics.density
        private val overlay = ZoomOverlay(activity)
        private val dim = View(activity).apply { setBackgroundColor(Color.BLACK); alpha = 0f }
        private val clip = FrameLayout(activity).apply { clipToOutline = true }
        private val container = FrameLayout(activity).apply { id = View.generateViewId(); pivotX = 0f; pivotY = 0f }
        private val snapshot = ImageView(activity).apply { scaleType = ImageView.ScaleType.CENTER_CROP }

        private val full = RectF()
        private var current = RectF()
        private var radius = 0f
        private val screenRadius = DeviceScreen.from(decor).cornerRadiusPx.toFloat()
        private val sourceRadius = (config.sourceCornerRadiusDp ?: 16f) * density
        private var closing = false

        /** Back: pops a pushed screen first; on the first screen it zooms the whole presentation out. */
        private val back = object : OnBackPressedCallback(true) {
            override fun handleOnBackProgressed(backEvent: BackEventCompat) {
                if (!isShowingPushedScreen) drag(ZoomDrag.Axis.Right, backEvent.progress * 300 * density, 0f)
            }
            override fun handleOnBackCancelled() {
                if (!isShowingPushedScreen) springBack()
            }
            override fun handleOnBackPressed() {
                if (isShowingPushedScreen) popPushedScreen() else dismiss()
            }
        }

        /** The deepest fragment manager with a back stack (the presented fragment's own navigation). */
        private fun popPushedScreen() {
            var manager = fragment.childFragmentManager
            while (true) {
                val deeper = manager.fragments.lastOrNull { it.isVisible }?.childFragmentManager
                if (deeper != null && deeper.backStackEntryCount > 0) manager = deeper else break
            }
            manager.popBackStack()
        }

        fun start() {
            overlay.addView(dim, FrameLayout.LayoutParams(-1, -1))
            clip.addView(container, FrameLayout.LayoutParams(decor.width, decor.height))
            clip.addView(snapshot, FrameLayout.LayoutParams(-1, -1))
            overlay.addView(clip, FrameLayout.LayoutParams(0, 0))
            decor.addView(overlay, FrameLayout.LayoutParams(-1, -1))
            overlay.presentation = this

            full.set(0f, 0f, decor.width.toFloat(), decor.height.toFloat())
            snapshot.setImageBitmap(source.drawToBitmap())
            activity.supportFragmentManager.beginTransaction().add(container.id, fragment).commitNow()
            // Registered on the activity (not the fragment) so it outranks nothing it shouldn't, and stays
            // the one place that decides between "go back" and "zoom out".
            activity.onBackPressedDispatcher.addCallback(back)

            val start = sourceRect() ?: RectF(full).apply { inset(full.width() * 0.08f, full.height() * 0.08f) }
            source.alpha = 0f
            apply(start, sourceRadius)
            animate(from = RectF(start), to = full, fromRadius = sourceRadius, toRadius = screenRadius, useSpring = true) {}
            dim.animate().alpha(config.dimAlpha).setDuration(config.presentDurationMs.toLong()).start()
            snapshot.animate().alpha(0f).setDuration(config.presentDurationMs * 35L / 100).start()
        }

        fun dismiss() {
            if (closing) return
            closing = true
            back.remove()
            val target = sourceRect()
            val from = RectF(current)
            if (target != null) {
                snapshot.animate().alpha(1f).setStartDelay(config.dismissDurationMs * 45L / 100)
                    .setDuration(config.dismissDurationMs * 40L / 100).start()
                animate(from, target, radius, sourceRadius, useSpring = false) { finish() }
            } else {
                overlay.animate().alpha(0f).setDuration(220).withEndAction { finish() }.start()
            }
            dim.animate().alpha(0f).setDuration(config.dismissDurationMs.toLong()).start()
        }

        private fun finish() {
            source.alpha = 1f
            activity.supportFragmentManager.beginTransaction().remove(fragment).commitNowAllowingStateLoss()
            decor.removeView(overlay)
            presentations.remove(fragment)
            onDismissed?.invoke()
        }

        // MARK: Drag

        val isShowingPushedScreen: Boolean
            get() = fragment.isAdded && fragment.childFragmentManager.backStackEntryCount > 0

        val canDrag: Boolean
            get() = !closing && config.dismissGestures.isNotEmpty() &&
                (!isShowingPushedScreen || config.dismissesFromPushedScreens)

        fun drag(axis: ZoomDrag.Axis, dx: Float, dy: Float) {
            val f = ZoomDrag.frame(axis, dx, dy, density, config.minimumScale)
            val w = full.width() * f.scale
            val h = full.height() * f.scale
            val cx = full.centerX() + f.translationX
            val cy = full.centerY() + f.translationY
            apply(RectF(cx - w / 2, cy - h / 2, cx + w / 2, cy + h / 2), screenRadius + 20 * density * f.progress)
            dim.alpha = config.dimAlpha * (1 - f.progress)
        }

        fun release(axis: ZoomDrag.Axis, dx: Float, dy: Float, velocity: Float) {
            if (ZoomDrag.shouldDismiss(axis, dx, dy, velocity, density, config.dismissDistanceDp)) dismiss() else springBack()
        }

        private fun springBack() {
            animate(RectF(current), full, radius, screenRadius, useSpring = true) {}
            dim.animate().alpha(config.dimAlpha).setDuration(250).start()
        }

        // MARK: Geometry

        private fun sourceRect(): RectF? {
            if (!source.isAttachedToWindow) return null
            val r = Rect()
            if (!source.getGlobalVisibleRect(r)) return null
            val loc = IntArray(2).also { source.getLocationInWindow(it) }
            return RectF(loc[0].toFloat(), loc[1].toFloat(), loc[0] + source.width.toFloat(), loc[1] + source.height.toFloat())
        }

        /** Places the clip at [rect] and scales the full-size screen to its width, pinned to its top. */
        private fun apply(rect: RectF, cornerRadius: Float) {
            current = RectF(rect)
            radius = cornerRadius
            val params = clip.layoutParams as FrameLayout.LayoutParams
            params.width = rect.width().toInt().coerceAtLeast(1)
            params.height = rect.height().toInt().coerceAtLeast(1)
            clip.layoutParams = params
            clip.translationX = rect.left
            clip.translationY = rect.top
            val scale = rect.width() / full.width()
            container.scaleX = scale
            container.scaleY = scale
            clip.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) = outline.setRoundRect(0, 0, view.width, view.height, cornerRadius)
            }
        }

        private var running: Any? = null

        private fun animate(from: RectF, to: RectF, fromRadius: Float, toRadius: Float, useSpring: Boolean, end: () -> Unit) {
            (running as? SpringAnimation)?.cancel()
            (running as? ValueAnimator)?.cancel()
            fun at(t: Float) = apply(
                RectF(from.left + (to.left - from.left) * t, from.top + (to.top - from.top) * t,
                    from.right + (to.right - from.right) * t, from.bottom + (to.bottom - from.bottom) * t),
                fromRadius + (toRadius - fromRadius) * t.coerceIn(0f, 1f),
            )
            running = if (useSpring) {
                SpringAnimation(FloatValueHolder(0f)).apply {
                    spring = SpringForce(1f).setDampingRatio(0.86f).setStiffness(260f)
                    setStartValue(0f)
                    addUpdateListener { _, value, _ -> at(value) }
                    addEndListener { _, canceled, _, _ -> if (!canceled) end() }
                    start()
                }
            } else {
                ValueAnimator.ofFloat(0f, 1f).apply {
                    duration = config.dismissDurationMs.toLong()
                    interpolator = PathInterpolator(0.2f, 0f, 0f, 1f)
                    addUpdateListener { at(it.animatedValue as Float) }
                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationEnd(animation: Animator) = end()
                    })
                    start()
                }
            }
        }
    }

    /**
     * Full-window layer: decides whether a drag dismisses (direction allowed, not on a pushed screen,
     * and no scrolled content under the finger that could still scroll back), then drives it.
     */
    private class ZoomOverlay(context: Context) : FrameLayout(context) {
        var presentation: Presentation? = null
        private val slop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()
        private var downX = 0f
        private var downY = 0f
        private var axis: ZoomDrag.Axis? = null
        private var ignoring = false
        private var tracker: VelocityTracker? = null

        init { isClickable = true }   // keeps touches off the screen behind

        override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
            val p = presentation ?: return false
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.x; downY = ev.y; axis = null; ignoring = !p.canDrag
                    tracker?.recycle(); tracker = VelocityTracker.obtain().also { it.addMovement(ev) }
                }
                MotionEvent.ACTION_MOVE -> {
                    tracker?.addMovement(ev)
                    if (ignoring || axis != null) return axis != null
                    val dx = ev.x - downX
                    val dy = ev.y - downY
                    val a = ZoomDrag.axis(dx, dy, slop, p.config.dismissGestures)
                    if (a == null) {
                        if (abs(dx) > slop || abs(dy) > slop) ignoring = true
                        return false
                    }
                    if (canChildScrollBack(this, downX, downY, a)) { ignoring = true; return false }
                    axis = a
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            return false
        }

        @SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(ev: MotionEvent): Boolean {
            val p = presentation ?: return true
            val a = axis ?: return true
            tracker?.addMovement(ev)
            when (ev.actionMasked) {
                MotionEvent.ACTION_MOVE -> p.drag(a, ev.x - downX, ev.y - downY)
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    tracker?.computeCurrentVelocity(1000)
                    val v = if (a == ZoomDrag.Axis.Down) tracker?.yVelocity ?: 0f else tracker?.xVelocity ?: 0f
                    p.release(a, ev.x - downX, ev.y - downY, v)
                    axis = null
                }
            }
            return true
        }

        /** A view under the finger that can still scroll towards the drag's start (up / left). */
        private fun canChildScrollBack(group: ViewGroup, x: Float, y: Float, axis: ZoomDrag.Axis): Boolean {
            for (i in group.childCount - 1 downTo 0) {
                val child = group.getChildAt(i)
                if (child.visibility != VISIBLE) continue
                val loc = IntArray(2).also { child.getLocationInWindow(it) }
                val origin = IntArray(2).also { getLocationInWindow(it) }
                val left = loc[0] - origin[0]
                val top = loc[1] - origin[1]
                val inside = x >= left && x < left + child.width * child.scaleX && y >= top && y < top + child.height * child.scaleY
                if (!inside) continue
                val scrolls = if (axis == ZoomDrag.Axis.Down) child.canScrollVertically(-1) else child.canScrollHorizontally(-1)
                if (scrolls) return true
                if (child is ViewGroup && canChildScrollBack(child, x, y, axis)) return true
            }
            return false
        }
    }
}

/** Zooms the presentation showing this fragment (or its parent) back into its source. */
fun Fragment.dismissZoom() = BaseZoomTransition.dismiss(this)

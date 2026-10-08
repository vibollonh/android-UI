package com.example.baseui.views

import android.animation.ValueAnimator
import android.app.Activity
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Outline
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.view.animation.AccelerateInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.view.doOnPreDraw
import androidx.dynamicanimation.animation.FloatValueHolder
import androidx.dynamicanimation.animation.SpringAnimation
import androidx.dynamicanimation.animation.SpringForce
import com.example.baseui.CutoutToastGeometry
import com.example.baseui.DeviceScreen
import com.example.baseui.ToastStyle
import java.util.WeakHashMap

/**
 * Cutout toast for View-based screens. Drawn over the activity's window (status bar included).
 *
 *     CutoutToast.show(activity, "Transfer completed", ToastStyle.Success)
 *
 * - Punch-hole phones: the black pill starts as the camera hole and grows out of it.
 * - Notch / no cutout: it pops in as a pill just below the status bar.
 * Toasts are queued per activity; tap one to dismiss it.
 */
object CutoutToast {

    var fullWidth = true
    /** Punch-hole phones: how far the pill starts above the camera hole, in dp. */
    var topPaddingDp = 8f
    var backgroundColor = Color.BLACK
    var textColor = Color.WHITE

    private val presenters = WeakHashMap<Activity, Presenter>()

    fun show(activity: Activity, message: String, style: ToastStyle = ToastStyle.Info, durationMs: Long = 2500) {
        presenters.getOrPut(activity) { Presenter(activity) }.enqueue(Item(message, style, durationMs))
    }

    /** Collapses the toast on screen; queued ones continue. */
    fun dismiss(activity: Activity) {
        presenters[activity]?.collapse()
    }

    private data class Item(val message: String, val style: ToastStyle, val durationMs: Long)

    private class Presenter(private val activity: Activity) {
        private val queue = ArrayDeque<Item>()
        private var showing = false
        private var collapsing = false
        private var pill: FrameLayout? = null
        private var frames: CutoutToastGeometry.Frames? = null
        private var expandedRadius = 0f
        private val collapseRunnable = Runnable { collapse() }

        fun enqueue(item: Item) {
            queue.addLast(item)
            if (!showing) showNext()
        }

        private fun showNext() {
            val item = queue.removeFirstOrNull()
            val decor = activity.window?.decorView as? ViewGroup
            if (item == null || decor == null) { showing = false; return }
            showing = true
            collapsing = false

            val density = activity.resources.displayMetrics.density
            fun dp(v: Float) = v * density
            val screen = DeviceScreen.from(decor)
            val growsFromCutout = screen.cutoutRect != null

            val icon = ImageView(activity).apply {
                setImageResource(item.style.iconRes)
                imageTintList = ColorStateList.valueOf(item.style.tint)
            }
            val text = TextView(activity).apply {
                this.text = item.message
                setTextColor(textColor)
                textSize = 14f
                typeface = Typeface.DEFAULT_BOLD
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
            }
            val content = LinearLayout(activity).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                val padTop = dp(if (growsFromCutout) 10f else 12f).toInt()
                val padBottom = dp(if (growsFromCutout) 18f else 12f).toInt()
                setPadding(dp(16f).toInt(), padTop, dp(16f).toInt(), padBottom)
                addView(icon, LinearLayout.LayoutParams(dp(24f).toInt(), dp(24f).toInt()).apply { marginEnd = dp(10f).toInt() })
                addView(text, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                alpha = 0f
                accessibilityLiveRegion = View.ACCESSIBILITY_LIVE_REGION_POLITE
            }

            // Measure the content to size the pill.
            val maxWidth = screen.widthPx - dp(24f)
            content.measure(
                View.MeasureSpec.makeMeasureSpec(maxWidth.toInt(), View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            )
            val f = CutoutToastGeometry.frames(
                screen, content.measuredWidth.toFloat(), content.measuredHeight.toFloat(),
                sideInset = dp(10f), fullWidth = fullWidth, minWidth = dp(160f),
                topPadding = dp(topPaddingDp),
            )
            frames = f
            expandedRadius = if (fullWidth && screen.cornerRadiusPx > 0) {
                (screen.cornerRadiusPx - f.expanded.left).coerceAtLeast(f.expanded.height() / 4)
            } else f.expanded.height() / 2

            val pill = FrameLayout(activity).apply {
                setBackgroundColor(backgroundColor)
                clipToOutline = true
                elevation = dp(8f)
                addView(content, FrameLayout.LayoutParams(f.expanded.width().toInt(), content.measuredHeight).apply {
                    topMargin = f.contentTop.toInt()
                })
                setOnClickListener { collapse() }
                if (!f.growsFromCutout) alpha = 0f
            }
            this.pill = pill
            decor.addView(pill, FrameLayout.LayoutParams(0, 0))
            apply(0f)

            pill.doOnPreDraw {
                val spring = SpringAnimation(FloatValueHolder(0f)).apply {
                    spring = SpringForce(1f).setDampingRatio(0.72f).setStiffness(320f)
                    addUpdateListener { _, value, _ -> apply(value) }
                }
                spring.start()
                content.animate().alpha(1f).setStartDelay(140).setDuration(180).start()
            }
            pill.postDelayed(collapseRunnable, item.durationMs + 400)
        }

        fun collapse() {
            val pill = pill ?: return
            if (collapsing) return
            collapsing = true
            pill.removeCallbacks(collapseRunnable)
            (pill.getChildAt(0)).animate().alpha(0f).setStartDelay(0).setDuration(100).start()
            ValueAnimator.ofFloat(1f, 0f).apply {
                duration = 300
                interpolator = AccelerateInterpolator()
                addUpdateListener { apply(it.animatedValue as Float) }
                doOnEnd {
                    (pill.parent as? ViewGroup)?.removeView(pill)
                    this@Presenter.pill = null
                    showNext()
                }
                start()
            }
        }

        /** Places the pill between its collapsed (0) and expanded (1) frames. */
        private fun apply(t: Float) {
            val pill = pill ?: return
            val f = frames ?: return
            val rect: RectF = CutoutToastGeometry.lerp(f.collapsed, f.expanded, t)
            val params = pill.layoutParams as FrameLayout.LayoutParams
            params.width = rect.width().toInt().coerceAtLeast(1)
            params.height = rect.height().toInt().coerceAtLeast(1)
            pill.layoutParams = params
            pill.translationX = rect.left
            pill.translationY = rect.top
            if (!f.growsFromCutout) pill.alpha = (t * 3).coerceIn(0f, 1f)
            val collapsedRadius = f.collapsed.height() / 2
            val radius = (collapsedRadius + (expandedRadius - collapsedRadius) * t.coerceIn(0f, 1f))
                .coerceAtMost(rect.height() / 2)
            pill.outlineProvider = object : ViewOutlineProvider() {
                override fun getOutline(view: View, outline: Outline) {
                    outline.setRoundRect(0, 0, view.width, view.height, radius)
                }
            }
            // Content keeps its expanded width and stays centred while the pill grows around it.
            val content = pill.getChildAt(0)
            content.translationX = (rect.width() - f.expanded.width()) / 2
        }

        private fun ValueAnimator.doOnEnd(block: () -> Unit) = addListener(object : android.animation.AnimatorListenerAdapter() {
            override fun onAnimationEnd(animation: android.animation.Animator) = block()
        })
    }

    private val ToastStyle.iconRes: Int
        get() = when (this) {
            ToastStyle.Success -> R.drawable.baseui_ic_success
            ToastStyle.Error -> R.drawable.baseui_ic_error
            ToastStyle.Warning -> R.drawable.baseui_ic_warning
            ToastStyle.Info -> R.drawable.baseui_ic_info
        }
}

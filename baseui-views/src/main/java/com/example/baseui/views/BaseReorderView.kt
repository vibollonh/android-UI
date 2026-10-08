package com.example.baseui.views

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.PointF
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.withStyledAttributes
import androidx.core.view.doOnPreDraw
import androidx.core.view.drawToBitmap
import androidx.core.widget.NestedScrollView
import com.example.baseui.ItemPosition
import com.example.baseui.Reorder
import com.example.baseui.ReorderLayout
import com.example.baseui.ReorderMode
import com.example.baseui.ReorderSection
import com.google.android.material.color.MaterialColors
import kotlin.math.abs
import kotlin.math.hypot

/** How an item looks. The view draws it as a row, tile, card or banner depending on the section's layout. */
data class ReorderRow(val title: String, val subtitle: String? = null, val iconRes: Int? = null)

/**
 * View version of `BaseReorderCollection`: drag items within and across sections, or set
 * [mode] = [ReorderMode.Sections] to reorder whole sections. Each section can use its own layout.
 *
 *     <com.example.baseui.views.BaseReorderView
 *         android:id="@+id/board"
 *         app:defaultLayout="list" />
 *
 *     board.setup(key = { it.id }, row = { ReorderRow(it.name, it.note, it.icon) })
 *     board.sections = sections
 *     board.onChange = { save(it) }
 *
 * Hold an item to lift it; it follows the finger while the others make room. Scrolls itself.
 */
class BaseReorderView<T> @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    var sections: List<ReorderSection<T>> = emptyList()
        set(value) {
            field = value
            if (drag == null) render(animate = false)
        }

    var mode: ReorderMode = ReorderMode.Items
        set(value) { field = value; render(animate = false) }

    var defaultLayout: ReorderLayout = ReorderLayout.List
        set(value) { field = value; render(animate = false) }

    var allowsCrossSectionMoves = true
    var emptySectionText: String? = "Drag items here"
        set(value) { field = value; render(animate = false) }
    var canMoveItem: (T) -> Boolean = { true }

    /** Called after every drop with the new order. */
    var onChange: ((List<ReorderSection<T>>) -> Unit)? = null

    private var key: (T) -> Any = { it as Any }
    private var row: (T) -> ReorderRow = { ReorderRow(it.toString()) }

    fun setup(key: (T) -> Any, row: (T) -> ReorderRow) {
        this.key = key
        this.row = row
        render(animate = false)
    }

    // MARK: Views

    private val scroll = NestedScrollView(context).apply { isFillViewport = true; clipToPadding = false }
    private val content = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), 0, dp(16), dp(24))
    }
    /** Item / section-row views by key, and section blocks by id, from the last render. */
    private val itemViews = LinkedHashMap<Any, View>()
    private val sectionViews = LinkedHashMap<String, View>()
    private val carouselScroll = HashMap<String, Int>()

    init {
        scroll.addView(content, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
        context.withStyledAttributes(attrs, R.styleable.BaseReorderView) {
            val columns = getInt(R.styleable.BaseReorderView_gridColumns, 3)
            defaultLayout = if (getInt(R.styleable.BaseReorderView_defaultLayout, 0) == 1) ReorderLayout.Grid(columns) else ReorderLayout.List
        }
    }

    private fun shown(): List<ReorderSection<T>> = drag?.working ?: sections
    private fun layoutOf(section: ReorderSection<T>) = section.layout ?: defaultLayout
    private fun sectionKey(id: String) = "section:$id"

    /** Rebuilds the content; with [animate], every view glides from its previous place (FLIP). */
    private fun render(animate: Boolean) {
        val before = if (animate) positions() else emptyMap()
        // Keep carousels where the user scrolled them.
        content.children().filterIsInstance<ViewGroup>().forEach { block ->
            block.findViewWithTag<HorizontalScrollView>("carousel")?.let { carouselScroll[block.tag as String] = it.scrollX }
        }
        content.removeAllViews()
        itemViews.clear()
        sectionViews.clear()

        val list = shown()
        if (mode == ReorderMode.Sections) {
            content.addView(header("Sections", pinned = false))
            val group = group()
            list.forEachIndexed { i, s ->
                val v = sectionRow(s)
                itemViews[sectionKey(s.id)] = v
                group.addView(v)
                if (i < list.lastIndex) group.addView(divider())
            }
            content.addView(group)
        } else {
            list.forEach { s ->
                val block = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL; tag = s.id }
                block.addView(header(s.title, s.pinned))
                block.addView(body(s))
                sectionViews[s.id] = block
                content.addView(block)
            }
        }
        drag?.let { d -> itemViews[d.key]?.alpha = 0f }
        updateSwapHighlight()
        content.children().filterIsInstance<ViewGroup>().forEach { block ->
            block.findViewWithTag<HorizontalScrollView>("carousel")?.let { hs ->
                carouselScroll[block.tag as? String]?.let { x -> hs.post { hs.scrollTo(x, 0) } }
            }
        }
        if (animate) content.doOnPreDraw {
            val after = positions()
            for ((k, now) in after) {
                val was = before[k] ?: continue
                val view = itemViews[k] ?: continue
                val dx = was.x - now.x
                val dy = was.y - now.y
                if (abs(dx) < 0.5f && abs(dy) < 0.5f) continue
                view.translationX = dx
                view.translationY = dy
                view.animate().translationX(0f).translationY(0f).setDuration(220)
                    .setInterpolator(android.view.animation.DecelerateInterpolator(1.6f)).start()
            }
        }
    }

    /** Item positions relative to the scrolling content. */
    private fun positions(): Map<Any, PointF> = itemViews.mapValues { (_, v) -> offsetIn(v, content) }

    private fun offsetIn(view: View, ancestor: View): PointF {
        var x = 0f
        var y = 0f
        var v: View? = view
        while (v != null && v !== ancestor) {
            x += v.left - (if (v !== view) v.scrollX else 0) .toFloat()
            y += v.top - (if (v !== view) v.scrollY else 0).toFloat()
            v = v.parent as? View
        }
        return PointF(x, y)
    }

    /** Bounds of [view] in this view's coordinates. */
    private fun boundsOf(view: View): RectF {
        val a = IntArray(2).also { view.getLocationInWindow(it) }
        val b = IntArray(2).also { getLocationInWindow(it) }
        val l = (a[0] - b[0]).toFloat()
        val t = (a[1] - b[1]).toFloat()
        return RectF(l, t, l + view.width, t + view.height)
    }

    // MARK: Drag

    private class Drag<T>(
        var working: List<ReorderSection<T>>,
        val key: Any,
        var position: ItemPosition,
        val sectionId: String,
        val ghost: ImageView,
        val grab: PointF,
    ) {
        var pointer = PointF()
        var swapKey: Any? = null
    }

    private var drag: Drag<T>? = null
    private var downPoint: PointF? = null
    private val slop = ViewConfiguration.get(context).scaledTouchSlop
    private val longPress = Runnable { downPoint?.let { startDrag(it) } }
    private val autoscroll = object : Runnable {
        override fun run() {
            val d = drag ?: return
            val edge = dp(72).toFloat()
            val speed = when {
                d.pointer.y < edge -> -((edge - d.pointer.y) / edge).coerceAtMost(1f)
                d.pointer.y > height - edge -> ((d.pointer.y - (height - edge)) / edge).coerceAtMost(1f)
                else -> 0f
            }
            if (speed != 0f) {
                val before = scroll.scrollY
                scroll.scrollBy(0, (speed * dp(14)).toInt())
                if (scroll.scrollY != before) locate()
            }
            postOnAnimation(this)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downPoint = PointF(ev.x, ev.y)
                postDelayed(longPress, (ViewConfiguration.getLongPressTimeout() * 0.6).toLong())
            }
            MotionEvent.ACTION_MOVE -> {
                val down = downPoint
                if (drag == null && down != null && hypot(ev.x - down.x, ev.y - down.y) > slop) {
                    removeCallbacks(longPress)   // it's a scroll
                    downPoint = null
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                removeCallbacks(longPress)
                downPoint = null
            }
        }
        return drag != null
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val d = drag ?: return super.onTouchEvent(ev)
        when (ev.actionMasked) {
            MotionEvent.ACTION_MOVE -> {
                d.pointer = PointF(ev.x, ev.y)
                d.ghost.translationX = ev.x - d.grab.x
                d.ghost.translationY = ev.y - d.grab.y
                locate()
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> endDrag()
        }
        return true
    }

    private fun startDrag(at: PointF) {
        val list = sections
        var hit: Triple<Any, ItemPosition, String>? = null
        if (mode == ReorderMode.Sections) {
            list.forEachIndexed { i, s ->
                val v = itemViews[sectionKey(s.id)] ?: return@forEachIndexed
                if (boundsOf(v).contains(at.x, at.y) && !s.pinned) hit = Triple(sectionKey(s.id), ItemPosition(i, -1), s.id)
            }
        } else {
            list.forEachIndexed { si, s ->
                s.items.forEachIndexed { ii, item ->
                    val v = itemViews[key(item)] ?: return@forEachIndexed
                    if (boundsOf(v).contains(at.x, at.y) && s.allowsItemMoves && canMoveItem(item)) {
                        hit = Triple(key(item), ItemPosition(si, ii), s.id)
                    }
                }
            }
        }
        val (k, position, sectionId) = hit ?: return
        val view = itemViews[k] ?: return
        val bounds = boundsOf(view)
        val ghost = ImageView(context).apply {
            setImageBitmap(view.drawToBitmap())
            elevation = dp(12).toFloat()
            background = rounded(MaterialColors.getColor(this@BaseReorderView, com.google.android.material.R.attr.colorSurfaceContainerHigh), dp(16).toFloat())
            clipToOutline = true
            translationX = bounds.left
            translationY = bounds.top
            scaleX = 1.04f
            scaleY = 1.04f
        }
        addView(ghost, LayoutParams(view.width, view.height))
        view.alpha = 0f
        drag = Drag(list, k, position, sectionId, ghost, PointF(at.x - bounds.left, at.y - bounds.top)).also { it.pointer = at }
        parent?.requestDisallowInterceptTouchEvent(true)
        performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        postOnAnimation(autoscroll)
    }

    /** Moves the dragged item to where the finger is (or picks a swap target). */
    private fun locate() {
        val d = drag ?: return
        val p = d.pointer
        val list = d.working
        if (mode == ReorderMode.Sections) {
            val from = list.indexOfFirst { it.id == d.sectionId }
            var to = 0
            list.forEachIndexed { i, s ->
                if (i != from) itemViews[sectionKey(s.id)]?.let { if (p.y > boundsOf(it).centerY()) to++ }
            }
            val moved = Reorder.moveSection(list, from, to)
            if (moved !== list) commit(moved)
            return
        }

        val target = list.indices.lastOrNull { i -> sectionViews[list[i].id]?.let { boundsOf(it).top <= p.y + 4 } == true } ?: 0
        val section = list[target]
        val oldSwap = d.swapKey
        d.swapKey = null
        if (!Reorder.canDrop(list, d.position, target, allowsCrossSectionMoves)) { if (oldSwap != null) updateSwapHighlight(); return }

        val cells = section.items.mapNotNull { item ->
            val k = key(item)
            if (k == d.key) null else itemViews[k]?.let { k to boundsOf(it) }
        }
        if (section.swapsOnDrop && target != d.position.section && cells.isNotEmpty()) {
            d.swapKey = cells.minBy { (_, r) -> hypot(p.x - r.centerX(), p.y - r.centerY()) }.first
            if (d.swapKey != oldSwap) updateSwapHighlight()
            return
        }
        if (oldSwap != null) updateSwapHighlight()

        val layout = layoutOf(section)
        var index = 0
        for ((_, r) in cells) {
            val before = when {
                layout.scrollsSideways -> p.x < r.centerX()
                layout is ReorderLayout.Grid -> p.y < r.top || (p.y <= r.bottom && p.x < r.centerX())
                else -> p.y < r.centerY()
            }
            if (before) break
            index++
        }
        val to = ItemPosition(target, index)
        if (to != d.position) {
            val moved = Reorder.moveItem(list, d.position, to)
            d.position = to
            commit(moved)
        }
    }

    private fun commit(working: List<ReorderSection<T>>) {
        drag?.working = working
        performHapticFeedback(HapticFeedbackConstants.SEGMENT_TICK)
        render(animate = true)
    }

    private fun updateSwapHighlight() {
        val swap = drag?.swapKey
        for ((k, v) in itemViews) v.foreground = if (k == swap) outline() else null
    }

    private fun endDrag() {
        val d = drag ?: return
        removeCallbacks(autoscroll)
        val original = sections
        var result = d.working
        d.swapKey?.let { swapKey ->
            val target = result.indexOfFirst { s -> s.items.any { key(it) == swapKey } }
            val index = result[target].items.indexOfFirst { key(it) == swapKey }
            result = Reorder.swapItem(result, d.position, ItemPosition(target, index))
            d.working = result
            d.swapKey = null
            render(animate = true)
        }
        // Glide the lifted copy into its slot, then show the real view.
        post {
            val slot = itemViews[d.key]
            val bounds = slot?.let(::boundsOf)
            d.ghost.animate()
                .translationX(bounds?.left ?: d.ghost.translationX)
                .translationY(bounds?.top ?: d.ghost.translationY)
                .scaleX(1f).scaleY(1f).setDuration(200)
                .withEndAction {
                    removeView(d.ghost)
                    drag = null
                    sections = result
                    if (result !== original) onChange?.invoke(result)
                }
                .start()
        }
    }

    // MARK: Building blocks

    private fun body(s: ReorderSection<T>): View {
        val layout = layoutOf(s)
        val locked = !s.allowsItemMoves
        if (s.items.isEmpty()) return emptyZone()
        return when (layout) {
            ReorderLayout.List -> group().apply {
                s.items.forEachIndexed { i, item ->
                    addView(cell(row(item), layout, locked).also { itemViews[key(item)] = it })
                    if (i < s.items.lastIndex) addView(divider())
                }
            }
            is ReorderLayout.Grid -> LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                s.items.chunked(layout.columns.coerceAtLeast(1)).forEach { chunk ->
                    addView(LinearLayout(context).apply {
                        chunk.forEachIndexed { i, item ->
                            val tile = cell(row(item), layout, locked).also { itemViews[key(item)] = it }
                            addView(tile, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply {
                                if (i > 0) marginStart = dp(8)
                            })
                        }
                        repeat(layout.columns - chunk.size) {
                            addView(View(context), LinearLayout.LayoutParams(0, 1, 1f).apply { marginStart = dp(8) })
                        }
                    }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
                }
            }
            ReorderLayout.Banner -> LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                s.items.forEach { item ->
                    addView(cell(row(item), layout, locked).also { itemViews[key(item)] = it },
                        LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { bottomMargin = dp(8) })
                }
            }
            ReorderLayout.Carousel, ReorderLayout.HorizontalBanner -> {
                val pageWidth = (width.takeIf { it > 0 } ?: resources.displayMetrics.widthPixels) - dp(32)
                PagingScrollView(context, paging = layout == ReorderLayout.HorizontalBanner, pageWidth = pageWidth + dp(10)).apply {
                    tag = "carousel"
                    isHorizontalScrollBarEnabled = false
                    addView(LinearLayout(context).apply {
                        s.items.forEachIndexed { i, item ->
                            val card = cell(row(item), layout, locked).also { itemViews[key(item)] = it }
                            val w = if (layout == ReorderLayout.HorizontalBanner) pageWidth else dp(150)
                            addView(card, LinearLayout.LayoutParams(w, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                                if (i > 0) marginStart = dp(10)
                            })
                        }
                    })
                }
            }
        }
    }

    private fun cell(r: ReorderRow, layout: ReorderLayout, locked: Boolean): View {
        val primary = color(androidx.appcompat.R.attr.colorPrimary)
        val onPrimary = color(com.google.android.material.R.attr.colorOnPrimary)
        val surface = color(com.google.android.material.R.attr.colorSurfaceContainerLow)
        val onSurface = color(com.google.android.material.R.attr.colorOnSurface)
        val variant = color(com.google.android.material.R.attr.colorOnSurfaceVariant)
        val outline = color(com.google.android.material.R.attr.colorOutline)
        fun icon(res: Int?, tint: Int, size: Int) = ImageView(context).apply {
            res?.let(::setImageResource)
            imageTintList = ColorStateList.valueOf(tint)
            layoutParams = LinearLayout.LayoutParams(dp(size), dp(size))
        }
        fun text(t: String?, sp: Float, c: Int, bold: Boolean = false) = TextView(context).apply {
            text = t
            setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
            setTextColor(c)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            visibility = if (t == null) GONE else VISIBLE
        }
        return when (layout) {
            ReorderLayout.List -> LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(60)
                setPadding(dp(16), dp(10), dp(16), dp(10))
                setBackgroundColor(surface)
                addView(icon(r.iconRes, primary, 24).also { if (r.iconRes == null) it.visibility = GONE })
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(text(r.title, 16f, onSurface))
                    addView(text(r.subtitle, 12f, variant))
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = dp(16) })
                addView(icon(if (locked) R.drawable.baseui_ic_pin else R.drawable.baseui_ic_drag, outline, 22))
            }
            is ReorderLayout.Grid -> LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER
                minimumHeight = dp(104)
                setPadding(dp(6), dp(12), dp(6), dp(12))
                background = rounded(surface, dp(14).toFloat())
                addView(icon(r.iconRes, primary, 24))
                addView(text(r.title, 14f, onSurface).apply { gravity = Gravity.CENTER; setPadding(0, dp(6), 0, 0) })
                addView(text(r.subtitle, 11f, variant).apply { gravity = Gravity.CENTER })
            }
            ReorderLayout.Carousel -> LinearLayout(context).apply {
                orientation = LinearLayout.VERTICAL
                minimumHeight = dp(128)
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = rounded(surface, dp(16).toFloat())
                addView(FrameLayout(context).apply {
                    background = rounded(color(com.google.android.material.R.attr.colorPrimaryContainer), dp(18).toFloat())
                    addView(icon(r.iconRes, color(com.google.android.material.R.attr.colorOnPrimaryContainer), 20),
                        LayoutParams(dp(20), dp(20), Gravity.CENTER))
                }, LinearLayout.LayoutParams(dp(36), dp(36)))
                addView(View(context), LinearLayout.LayoutParams(1, 0, 1f).apply { height = dp(16) })
                addView(text(r.title, 14f, onSurface, bold = true))
                addView(text(r.subtitle, 12f, variant))
            }
            ReorderLayout.Banner, ReorderLayout.HorizontalBanner -> LinearLayout(context).apply {
                gravity = Gravity.CENTER_VERTICAL
                minimumHeight = dp(100)
                setPadding(dp(18), dp(18), dp(18), dp(18))
                background = rounded(primary, dp(20).toFloat())
                addView(LinearLayout(context).apply {
                    orientation = LinearLayout.VERTICAL
                    addView(text(r.title, 22f, onPrimary))
                    addView(text(r.subtitle, 14f, onPrimary).apply { alpha = 0.85f })
                }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                addView(icon(r.iconRes, onPrimary, 40).apply { alpha = 0.6f })
            }
        }
    }

    private fun sectionRow(s: ReorderSection<T>): View {
        val count = if (s.items.size == 1) "1 item" else "${s.items.size} items"
        val icon = when (layoutOf(s)) {
            ReorderLayout.List -> R.drawable.baseui_ic_layout_list
            is ReorderLayout.Grid -> R.drawable.baseui_ic_layout_grid
            ReorderLayout.Carousel, ReorderLayout.HorizontalBanner -> R.drawable.baseui_ic_layout_carousel
            ReorderLayout.Banner -> R.drawable.baseui_ic_layout_banner
        }
        return cell(ReorderRow(s.title, if (s.pinned) "Pinned · $count" else count, icon), ReorderLayout.List, locked = s.pinned)
    }

    private fun header(title: String, pinned: Boolean) = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(4), dp(18), 0, dp(8))
        val primary = color(androidx.appcompat.R.attr.colorPrimary)
        if (pinned) addView(ImageView(context).apply {
            setImageResource(R.drawable.baseui_ic_pin)
            imageTintList = ColorStateList.valueOf(primary)
        }, LinearLayout.LayoutParams(dp(14), dp(14)).apply { marginEnd = dp(4) })
        addView(TextView(context).apply {
            text = title
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(primary)
        })
    }

    private fun group() = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        background = rounded(color(com.google.android.material.R.attr.colorSurfaceContainerLow), dp(16).toFloat())
        clipToOutline = true
    }

    private fun divider() = View(context).apply {
        setBackgroundColor(color(com.google.android.material.R.attr.colorOutlineVariant))
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(1)).apply { marginStart = dp(56) }
    }

    private fun emptyZone(): View = LinearLayout(context).apply {
        gravity = Gravity.CENTER_VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = GradientDrawable().apply {
            cornerRadius = dp(16).toFloat()
            setStroke(dp(1), color(com.google.android.material.R.attr.colorOutline), dp(6).toFloat(), dp(4).toFloat())
        }
        addView(ImageView(context).apply {
            setImageResource(R.drawable.baseui_ic_inbox)
            imageTintList = ColorStateList.valueOf(color(com.google.android.material.R.attr.colorOutline))
        }, LinearLayout.LayoutParams(dp(22), dp(22)).apply { marginEnd = dp(12) })
        addView(TextView(context).apply {
            text = emptySectionText
            setTextColor(color(com.google.android.material.R.attr.colorOnSurfaceVariant))
        })
        visibility = if (emptySectionText == null) GONE else VISIBLE
    }

    private fun outline() = GradientDrawable().apply {
        cornerRadius = dp(16).toFloat()
        setStroke(dp(2), color(androidx.appcompat.R.attr.colorPrimary))
        setColor(Color.TRANSPARENT)
    }

    private fun rounded(color: Int, radius: Float) = GradientDrawable().apply { setColor(color); cornerRadius = radius }
    private fun color(attr: Int) = MaterialColors.getColor(this, attr)
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()
    private fun ViewGroup.children(): List<View> = (0 until childCount).map(::getChildAt)
}

/** Horizontal scroller for carousels; with [paging], a fling or release snaps to whole pages. */
private class PagingScrollView(context: Context, private val paging: Boolean, private val pageWidth: Int) : HorizontalScrollView(context) {
    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(ev: MotionEvent): Boolean {
        val handled = super.onTouchEvent(ev)
        if (paging && (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL)) {
            post { smoothScrollTo(((scrollX + pageWidth / 2) / pageWidth) * pageWidth, 0) }
        }
        return handled
    }

    override fun fling(velocityX: Int) {
        if (!paging) return super.fling(velocityX)
        val page = scrollX / pageWidth + if (velocityX > 0) 1 else 0
        smoothScrollTo(page * pageWidth, 0)
    }
}

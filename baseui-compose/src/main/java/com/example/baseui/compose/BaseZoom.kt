package com.example.baseui.compose

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.GraphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import com.example.baseui.DeviceScreen
import com.example.baseui.ZoomConfig
import com.example.baseui.ZoomDismissGesture
import com.example.baseui.ZoomDrag
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.max

/** The zoom presenter of the nearest [BaseZoomHost]. */
val LocalZoom = staticCompositionLocalOf<ZoomState> { error("No BaseZoomHost above this composable") }

/** What the presented content can do. */
@Stable
class ZoomScope internal constructor(private val state: ZoomState) {
    /**
     * Turn drag-to-dismiss off while the content shows a pushed screen of its own navigation (pushed
     * screens go back instead), unless the config's `dismissesFromPushedScreens` is set.
     */
    var isShowingPushedScreen by mutableStateOf(false)

    /** Zooms back into the source. */
    fun dismiss() = state.dismiss()
}

/**
 * Zoom presentations: a screen grows out of a source composable and shrinks back into it.
 *
 *     // once, at the root:
 *     BaseZoomHost { App() }
 *
 *     // anywhere below:
 *     val zoom = LocalZoom.current
 *     Card(Modifier.zoomSource(card.id).clickable {
 *         zoom.present(card.id) { WalletDetail(card, close = ::dismiss) }
 *     })
 */
@Stable
class ZoomState internal constructor() {
    internal class Source(var bounds: Rect = Rect.Zero, var radiusPx: Float = 0f, var layer: GraphicsLayer? = null)

    internal val sources = HashMap<Any, Source>()
    internal var presentedKey by mutableStateOf<Any?>(null)
    internal var content by mutableStateOf<(@Composable ZoomScope.() -> Unit)?>(null)
    internal var config by mutableStateOf(ZoomConfig())
    /** Set while the zoomed view stands in for the source (present start → dismiss end). */
    internal var hiddenKey by mutableStateOf<Any?>(null)
    internal var dismissRequests by mutableStateOf(0)
    internal val scope = ZoomScope(this)

    val isPresenting: Boolean get() = presentedKey != null

    fun present(key: Any, config: ZoomConfig = ZoomConfig(), content: @Composable ZoomScope.() -> Unit) {
        if (presentedKey != null) return
        this.config = config
        this.content = content
        scope.isShowingPushedScreen = false
        presentedKey = key
    }

    fun dismiss() {
        if (presentedKey != null) dismissRequests++
    }
}

/**
 * Marks this composable as the source of the zoom presentation with [key]: records its bounds and a
 * picture of it (for the cross-fade), and hides it while the zoomed screen stands in for it.
 */
@Composable
fun Modifier.zoomSource(key: Any, cornerRadius: Dp = 16.dp): Modifier {
    val zoom = LocalZoom.current
    val layer = rememberGraphicsLayer()
    val radiusPx = with(LocalDensity.current) { cornerRadius.toPx() }
    val source = remember(key) { zoom.sources.getOrPut(key) { ZoomState.Source() } }
    source.layer = layer
    source.radiusPx = radiusPx
    val hidden = zoom.hiddenKey == key
    return this
        .onGloballyPositioned { source.bounds = Rect(it.positionInRoot(), Size(it.size.width.toFloat(), it.size.height.toFloat())) }
        .drawWithContent {
            layer.record { this@drawWithContent.drawContent() }
            if (!hidden) drawLayer(layer)
        }
}

/** Provides [LocalZoom] and draws the presented screen over [content]. Put it at the root of the screen. */
@Composable
fun BaseZoomHost(modifier: Modifier = Modifier.fillMaxSize(), content: @Composable () -> Unit) {
    val state = remember { ZoomState() }
    CompositionLocalProvider(LocalZoom provides state) {
        Box(modifier) {
            content()
            ZoomOverlay(state)
        }
    }
}

@Composable
private fun ZoomOverlay(state: ZoomState) {
    val key = state.presentedKey ?: return
    val content = state.content ?: return
    val config = state.config
    val density = LocalDensity.current
    val view = LocalView.current
    val touchSlop = LocalViewConfiguration.current.touchSlop
    val scope = rememberCoroutineScope()

    var overlay by remember { mutableStateOf(Rect.Zero) }
    val rect = remember { Animatable(Rect.Zero, Rect.VectorConverter) }
    val radius = remember { Animatable(0f) }
    val dim = remember { Animatable(0f) }
    val snapshotAlpha = remember { Animatable(1f) }
    var started by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }

    val screenRadius = remember(view) { DeviceScreen.from(view).cornerRadiusPx.toFloat() }
    val source = state.sources[key]
    fun sourceRect(): Rect? = source?.bounds?.takeIf { it.width > 0 }?.translate(-overlay.topLeft)
    fun full(): Rect = Rect(Offset.Zero, overlay.size)

    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            val target = sourceRect()
            coroutineScope {
                if (target != null) {
                    launch { rect.animateTo(target, tween(config.dismissDurationMs, easing = androidx.compose.animation.core.FastOutSlowInEasing)) }
                    launch { radius.animateTo(source!!.radiusPx, tween(config.dismissDurationMs)) }
                    launch { delay(config.dismissDurationMs * 45L / 100); snapshotAlpha.animateTo(1f, tween(config.dismissDurationMs * 40 / 100)) }
                } else {
                    launch { rect.animateTo(full().deflate(full().width * 0.1f), tween(250)) }
                }
                launch { dim.animateTo(0f, tween(config.dismissDurationMs)) }
            }
            // The source is visible again in the same frame the zoomed copy goes away.
            state.hiddenKey = null
            state.presentedKey = null
            state.content = null
        }
    }

    // Present: grow out of the source.
    LaunchedEffect(key) {
        // Wait for the first measure; keying on the size would restart (and cancel) the animations.
        snapshotFlow { overlay.size }.first { it != Size.Zero }
        started = true
        state.hiddenKey = key
        val start = sourceRect() ?: full().deflate(full().width * 0.08f)
        rect.snapTo(start)
        radius.snapTo(source?.radiusPx ?: screenRadius)
        snapshotAlpha.snapTo(if (source != null) 1f else 0f)
        launch { rect.animateTo(full(), spring(dampingRatio = 0.86f, stiffness = 260f)) }
        launch { radius.animateTo(screenRadius, tween(config.presentDurationMs)) }
        launch { dim.animateTo(config.dimAlpha, tween(config.presentDurationMs)) }
        launch { snapshotAlpha.animateTo(0f, tween(config.presentDurationMs * 35 / 100)) }
    }
    LaunchedEffect(state.dismissRequests) { if (state.dismissRequests > 0 && started) close() }

    // Drag to dismiss.
    val dragAllowed = config.dismissGestures.isNotEmpty() &&
        (!state.scope.isShowingPushedScreen || config.dismissesFromPushedScreens)
    var axis by remember { mutableStateOf<ZoomDrag.Axis?>(null) }
    var drag by remember { mutableStateOf(Offset.Zero) }

    fun applyDrag() {
        val a = axis ?: return
        val f = ZoomDrag.frame(a, drag.x, drag.y, density.density, config.minimumScale)
        val w = full().width * f.scale
        val h = full().height * f.scale
        val center = full().center + Offset(f.translationX, f.translationY)
        scope.launch {
            rect.snapTo(Rect(center - Offset(w / 2, h / 2), Size(w, h)))
            radius.snapTo(screenRadius + 20 * density.density * f.progress)
            dim.snapTo(config.dimAlpha * (1 - f.progress))
        }
    }

    fun release(velocity: Velocity) {
        val a = axis ?: return
        val speed = if (a == ZoomDrag.Axis.Down) velocity.y else velocity.x
        axis = null
        if (ZoomDrag.shouldDismiss(a, drag.x, drag.y, speed, density.density, config.dismissDistanceDp)) {
            close()
        } else {
            scope.launch {
                launch { rect.animateTo(full(), spring(dampingRatio = 0.8f, stiffness = 400f)) }
                launch { radius.animateTo(screenRadius) }
                launch { dim.animateTo(config.dimAlpha) }
            }
        }
        drag = Offset.Zero
    }

    // Scrolling content: once it can't scroll further down at the top, the rest of the pull dismisses.
    val nested = remember(dragAllowed) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (axis == ZoomDrag.Axis.Down && available.y < 0 && drag.y > 0) {
                    val used = max(available.y, -drag.y)
                    drag = drag.copy(y = drag.y + used)
                    applyDrag()
                    return Offset(0f, used)
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (!dragAllowed || closing || source != NestedScrollSource.UserInput) return Offset.Zero
                if (available.y > 0 && ZoomDismissGesture.Down in config.dismissGestures && (axis == null || axis == ZoomDrag.Axis.Down)) {
                    axis = ZoomDrag.Axis.Down
                    drag = drag.copy(y = drag.y + available.y)
                    applyDrag()
                    return Offset(0f, available.y)
                }
                return Offset.Zero
            }

            override suspend fun onPreFling(available: Velocity): Velocity {
                if (axis == null) return Velocity.Zero
                release(available)
                return available
            }
        }
    }

    // Back gesture: the predictive back drives the same zoom-out as dragging right.
    PredictiveBackHandler(enabled = started && !closing) { progress ->
        try {
            progress.collect { event ->
                axis = ZoomDrag.Axis.Right
                drag = Offset(event.progress * 300 * density.density, 0f)
                applyDrag()
            }
            axis = null
            close()
        } catch (e: CancellationException) {
            release(Velocity.Zero)
            throw e
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { overlay = Rect(it.positionInRoot(), Size(it.size.width.toFloat(), it.size.height.toFloat())) },
    ) {
        // Dimming; also keeps touches off the screen behind.
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = dim.value)).pointerInput(Unit) { detectTapGestures { } })
        if (overlay.size == Size.Zero || !started) return@Box
        val r = rect.value
        val scale = r.width / full().width
        Box(
            Modifier
                .offset { r.topLeft.round() }
                .size(with(density) { r.width.toDp() }, with(density) { r.height.toDp() })
                .clip(RoundedCornerShape(with(density) { radius.value.toDp() }))
                .nestedScroll(nested)
                .pointerInput(dragAllowed) {
                    if (!dragAllowed) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        var total = Offset.Zero
                        var decided = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Main)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (!change.pressed) {
                                if (axis != null && decided) release(tracker.calculateVelocity())
                                break
                            }
                            // A child (scroll, horizontal list) took it: leave it alone.
                            if (!decided && change.isConsumed) break
                            total += change.position - change.previousPosition
                            if (!decided) {
                                val a = ZoomDrag.axis(total.x, total.y, touchSlop, config.dismissGestures)
                                if (a == null) {
                                    if (total.getDistance() > touchSlop) break
                                    continue
                                }
                                decided = true
                                axis = a
                            }
                            drag = total
                            change.consume()
                            applyDrag()
                        }
                    }
                },
        ) {
            // The screen keeps its full() size and is scaled to the clip's width, pinned to its top.
            Box(
                Modifier
                    .wrapContentSize(Alignment.TopStart, unbounded = true)
                    .requiredSize(with(density) { full().width.toDp() }, with(density) { full().height.toDp() })
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    },
            ) { state.scope.content() }

            // Picture of the source, aspect-filled, cross-fading at the small end.
            val layer = source?.layer
            if (layer != null && snapshotAlpha.value > 0f) {
                Box(
                    Modifier.fillMaxSize().drawWithContent {
                        val src = layer.size
                        if (src.width == 0 || src.height == 0) return@drawWithContent
                        val s = max(size.width / src.width, size.height / src.height)
                        translate((size.width - src.width * s) / 2, (size.height - src.height * s) / 2) {
                            scale(s, s, pivot = Offset.Zero) {
                                layer.alpha = snapshotAlpha.value
                                drawLayer(layer)
                                layer.alpha = 1f
                            }
                        }
                    },
                )
            }
        }
    }
}

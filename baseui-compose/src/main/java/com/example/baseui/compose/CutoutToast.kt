package com.example.baseui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.baseui.CutoutToastGeometry
import com.example.baseui.DeviceScreen
import com.example.baseui.ToastStyle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.roundToInt

/**
 * Queue of cutout toasts. Show from anywhere (click handlers, view models through a callback):
 *
 *     val toast = rememberCutoutToastState()
 *     CutoutToastHost(toast)          // once, on top of the screen content
 *     toast.show("Transfer completed", ToastStyle.Success)
 */
@Stable
class CutoutToastState {
    internal data class Item(val message: String, val style: ToastStyle, val durationMs: Long)

    internal val queue = Channel<Item>(Channel.UNLIMITED)
    internal var dismissSignal: CompletableDeferred<Unit>? = null

    fun show(message: String, style: ToastStyle = ToastStyle.Info, durationMs: Long = 2500) {
        queue.trySend(Item(message, style, durationMs))
    }

    /** Collapses the toast on screen now; queued ones continue. */
    fun dismiss() {
        dismissSignal?.complete(Unit)
    }

    /** Clears the queue and collapses the toast on screen. */
    fun cancelAll() {
        while (queue.tryReceive().isSuccess) { /* drain */ }
        dismiss()
    }
}

@Composable
fun rememberCutoutToastState(): CutoutToastState = remember { CutoutToastState() }

/**
 * Draws the toasts of [state]. Put it last inside a full-screen `Box` of an edge-to-edge activity, so it
 * can draw over the status bar.
 *
 * - Punch-hole phones: the black pill starts as the camera hole and grows out of it; the content sits
 *   below the hole.
 * - Notch / no cutout: it pops in as a pill just below the status bar.
 */
@Composable
fun CutoutToastHost(
    state: CutoutToastState,
    modifier: Modifier = Modifier,
    /** Stretch edge to edge (minus a gap matching the gap above the camera). */
    fullWidth: Boolean = true,
    /** Punch-hole phones: how far the pill starts above the camera hole. */
    topPadding: Dp = 8.dp,
    background: Color = Color.Black,
    contentColor: Color = Color.White,
    tapToDismiss: Boolean = true,
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    val scope = rememberCoroutineScope()
    val textStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)

    var current by remember { mutableStateOf<CutoutToastState.Item?>(null) }
    val progress = remember { Animatable(0f) }
    val contentAlpha = remember { Animatable(0f) }

    LaunchedEffect(state) {
        for (item in state.queue) {
            current = item
            progress.snapTo(0f)
            contentAlpha.snapTo(0f)
            val dismiss = CompletableDeferred<Unit>().also { state.dismissSignal = it }
            launch { progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = 320f)) }
            launch { kotlinx.coroutines.delay(140); contentAlpha.animateTo(1f, tween(180)) }
            withTimeoutOrNull(item.durationMs + 400) { dismiss.await() }
            // Collapse without a bounce, then go straight to the next one.
            launch { contentAlpha.animateTo(0f, tween(100)) }
            progress.animateTo(0f, tween(300, easing = FastOutLinearInEasing))
            current = null
        }
    }

    val item = current ?: return
    val screen = DeviceScreen.from(view)
    with(density) {
        val padH = 16.dp.toPx()
        val iconSize = 24.dp.toPx()
        val spacing = 10.dp.toPx()
        val maxText = screen.widthPx - 24.dp.toPx() - padH * 2 - iconSize - spacing
        val measured = measurer.measure(item.message, textStyle, maxLines = 2,
            constraints = Constraints(maxWidth = maxText.roundToInt().coerceAtLeast(1)))
        val rowHeight = maxOf(measured.size.height.toFloat(), iconSize)
        val growsFromCutout = screen.cutoutRect != null
        val padTop = if (growsFromCutout) 10.dp.toPx() else 12.dp.toPx()
        val padBottom = if (growsFromCutout) 18.dp.toPx() else 12.dp.toPx()

        val frames = CutoutToastGeometry.frames(
            screen = screen,
            contentWidth = padH * 2 + iconSize + spacing + measured.size.width,
            contentHeight = padTop + rowHeight + padBottom,
            sideInset = 10.dp.toPx(),
            fullWidth = fullWidth,
            minWidth = 160.dp.toPx(),
            topPadding = topPadding.toPx(),
        )
        val t = progress.value
        val rect = CutoutToastGeometry.lerp(frames.collapsed, frames.expanded, t)
        // Concentric with the screen corners when full width, never more than a capsule.
        val expandedRadius = if (fullWidth && screen.cornerRadiusPx > 0) {
            (screen.cornerRadiusPx - frames.expanded.left).coerceAtLeast(frames.expanded.height() / 4)
        } else frames.expanded.height() / 2
        val collapsedRadius = frames.collapsed.height() / 2
        val radius = (collapsedRadius + (expandedRadius - collapsedRadius) * t.coerceIn(0f, 1f))
            .coerceAtMost(rect.height() / 2)

        Box(modifier.fillMaxSize()) {
            Box(
                Modifier
                    .offset { IntOffset(rect.left.roundToInt(), rect.top.roundToInt()) }
                    .size(rect.width().toDp(), rect.height().toDp())
                    // A pill on the hardware hole is invisible; elsewhere fade in with the growth.
                    .alpha(if (frames.growsFromCutout) 1f else (t * 3).coerceIn(0f, 1f))
                    .clip(RoundedCornerShape(radius.toDp()))
                    .background(background)
                    .clickable(
                        enabled = tapToDismiss,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                    ) { scope.launch { state.dismiss() } },
            ) {
                Row(
                    Modifier
                        .padding(top = frames.contentTop.toDp())
                        .fillMaxWidth()
                        .height((padTop + rowHeight + padBottom).toDp())
                        .padding(horizontal = padH.toDp())
                        .alpha(contentAlpha.value)
                        .semantics { liveRegion = LiveRegionMode.Polite },
                    horizontalArrangement = Arrangement.spacedBy(spacing.toDp()),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(item.style.icon, contentDescription = null, tint = Color(item.style.tint),
                        modifier = Modifier.size(iconSize.toDp()))
                    Text(item.message, color = contentColor, style = textStyle, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
        }
    }
}

private val ToastStyle.icon: ImageVector
    get() = when (this) {
        ToastStyle.Success -> Icons.Filled.CheckCircle
        ToastStyle.Error -> Icons.Filled.Error
        ToastStyle.Warning -> Icons.Filled.Warning
        ToastStyle.Info -> Icons.Filled.Info
    }

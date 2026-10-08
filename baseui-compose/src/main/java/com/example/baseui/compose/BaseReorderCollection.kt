package com.example.baseui.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector2D
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DragIndicator
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.ViewAgenda
import androidx.compose.material.icons.outlined.ViewCarousel
import androidx.compose.material.icons.automirrored.outlined.ViewList
import androidx.compose.material.icons.outlined.ViewModule
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.round
import com.example.baseui.ItemPosition
import com.example.baseui.Reorder
import com.example.baseui.ReorderLayout
import com.example.baseui.ReorderMode
import com.example.baseui.ReorderSection
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.hypot

/** How an item looks. The collection draws it as a row, tile, card or banner depending on the section's layout. */
data class ReorderRow(val title: String, val subtitle: String? = null, val icon: ImageVector? = null)

/**
 * Drag items within and across sections, or switch [mode] to [ReorderMode.Sections] to reorder whole
 * sections. Each section can use its own layout (list, grid, carousel, banner, horizontal banner).
 *
 *     BaseReorderCollection(sections, onChange = { sections = it }, key = { it.id }) { task ->
 *         ReorderRow(task.name, task.note, task.icon)
 *     }
 *
 * Hold an item to lift it; it follows the finger while the others make room. Dropping into a
 * `swapsOnDrop` section swaps with the item it lands on. [onChange] gets the new order after each drop.
 *
 * @param scrollState the collection scrolls itself; pass `null` to place it inside another scrolling
 *   container (it then lays out at full height and doesn't autoscroll).
 */
@Composable
fun <T> BaseReorderCollection(
    sections: List<ReorderSection<T>>,
    onChange: (List<ReorderSection<T>>) -> Unit,
    key: (T) -> Any,
    modifier: Modifier = Modifier,
    mode: ReorderMode = ReorderMode.Items,
    defaultLayout: ReorderLayout = ReorderLayout.List,
    allowsCrossSectionMoves: Boolean = true,
    emptySectionText: String? = "Drag items here",
    canMoveItem: (T) -> Boolean = { true },
    scrollState: ScrollState? = rememberScrollState(),
    row: (T) -> ReorderRow,
) {
    val haptics = LocalHapticFeedback.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val geometry = remember { ReorderGeometry() }

    var drag by remember { mutableStateOf<DragState<T>?>(null) }
    // The gesture handler outlives recompositions: it reads the latest arguments through these.
    val latestSections by rememberUpdatedState(sections)
    val latestOnChange by rememberUpdatedState(onChange)
    val latestKey by rememberUpdatedState(key)
    val latestCanMove by rememberUpdatedState(canMoveItem)
    val latestMode by rememberUpdatedState(mode)
    val latestCross by rememberUpdatedState(allowsCrossSectionMoves)
    val latestLayout by rememberUpdatedState(defaultLayout)
    // While dragging, the collection works on its own copy; the caller gets it on drop.
    val shown = drag?.working ?: sections
    val layoutOf: (ReorderSection<T>) -> ReorderLayout = { it.layout ?: latestLayout }
    fun sectionKey(id: String) = "section:$id"

    /** Where the finger is: target section and insertion index, or a swap target. */
    fun locate(d: DragState<T>): Unit {
        val p = d.pointer
        val list = d.working
        val key = latestKey
        if (latestMode == ReorderMode.Sections) {
            val from = list.indexOfFirst { it.id == d.sourceSection }
            var to = 0
            list.forEachIndexed { i, s ->
                if (i != from) geometry.items[sectionKey(s.id)]?.let { if (p.y > it.center.y) to++ }
            }
            val moved = Reorder.moveSection(list, from, to)
            if (moved !== list) { d.working = moved; haptics.performHapticFeedback(HapticFeedbackType.SegmentTick) }
            return
        }

        // The section under the finger: the last one whose top is above it.
        val target = list.indices.lastOrNull { i -> (geometry.sections[list[i].id]?.top ?: Float.MAX_VALUE) <= p.y + 4 }
            ?: 0
        val section = list[target]
        val from = d.position
        d.swapKey = null
        if (!Reorder.canDrop(list, from, target, latestCross)) return

        val cells = section.items.mapIndexedNotNull { i, item ->
            val k = key(item)
            if (k == d.key) null else geometry.items[k]?.let { Triple(i, k, it) }
        }
        // Swap sections: highlight the nearest card; the swap happens on drop.
        if (section.swapsOnDrop && target != from.section && cells.isNotEmpty()) {
            d.swapKey = cells.minBy { (_, _, r) -> hypot(p.x - r.center.x, p.y - r.center.y) }.second
            return
        }

        val layout = layoutOf(section)
        var index = 0
        for ((_, _, r) in cells) {
            val before = when {
                layout.scrollsSideways -> p.x < r.center.x
                layout is ReorderLayout.Grid -> p.y < r.top || (p.y <= r.bottom && p.x < r.center.x)
                else -> p.y < r.center.y
            }
            if (before) break
            index++
        }
        val to = ItemPosition(target, index)
        if (to != from) {
            d.working = Reorder.moveItem(list, from, to)
            d.position = to
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }

    fun startDrag(at: Offset) {
        val sections = latestSections
        val shown = sections
        val key = latestKey
        if (latestMode == ReorderMode.Sections) {
            val section = shown.firstOrNull { s -> geometry.items[sectionKey(s.id)]?.contains(at) == true } ?: return
            if (section.pinned) return
            val bounds = geometry.items[sectionKey(section.id)] ?: return
            drag = DragState(sections, sectionKey(section.id), ItemPosition(-1, -1), section.id, at, at - bounds.topLeft, bounds)
        } else {
            shown.forEachIndexed { s, section ->
                section.items.forEachIndexed { i, item ->
                    val k = key(item)
                    val bounds = geometry.items[k]
                    if (bounds != null && bounds.contains(at)) {
                        if (!section.allowsItemMoves || !latestCanMove(item)) return
                        drag = DragState(sections, k, ItemPosition(s, i), section.id, at, at - bounds.topLeft, bounds)
                    }
                }
            }
        }
        if (drag != null) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
    }

    fun endDrag() {
        val d = drag ?: return
        val key = latestKey
        var result = d.working
        d.swapKey?.let { swapKey ->
            val target = result.indexOfFirst { s -> s.items.any { key(it) == swapKey } }
            val index = result[target].items.indexOfFirst { key(it) == swapKey }
            result = Reorder.swapItem(result, d.position, ItemPosition(target, index))
            d.working = result
            d.swapKey = null
        }
        d.settling = true
        // Let the drop land (one layout pass), then glide the lifted copy into its slot.
        scope.launch {
            delay(16)
            val slot = geometry.items[d.key]
            if (slot != null) {
                d.ghost.animateTo(slot.topLeft - (d.pointer - d.grab), spring(stiffness = Spring.StiffnessMediumLow))
            }
            if (result !== latestSections) latestOnChange(result)
            drag = null
        }
    }

    // Autoscroll while the finger is near the top or bottom edge.
    LaunchedEffect(drag != null, scrollState) {
        val state = scrollState ?: return@LaunchedEffect
        while (drag != null) {
            val d = drag ?: break
            val view = geometry.viewport
            if (!d.settling && view != null) {
                val edge = with(density) { 72.dp.toPx() }
                val speed = when {
                    d.pointer.y < view.top + edge -> -((view.top + edge - d.pointer.y) / edge).coerceAtMost(1f)
                    d.pointer.y > view.bottom - edge -> ((d.pointer.y - (view.bottom - edge)) / edge).coerceAtMost(1f)
                    else -> 0f
                }
                if (speed != 0f && state.dispatchRawDelta(speed * with(density) { 14.dp.toPx() }) != 0f) {
                    delay(16)
                    locate(d)
                    continue
                }
            }
            delay(16)
        }
    }

    Box(
        modifier
            .onGloballyPositioned { geometry.viewport = it.boundsInRoot(); geometry.origin = it.positionInRoot() }
            .pointerInput(mode, allowsCrossSectionMoves) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val press = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                    startDrag(geometry.origin + press.position)
                    val d = drag ?: return@awaitEachGesture
                    // Initial pass: take the movement before the scroll containers see it.
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == press.id } ?: break
                        if (!change.pressed) { change.consume(); break }
                        d.pointer = geometry.origin + change.position
                        change.consume()
                        locate(d)
                    }
                    endDrag()
                }
            },
    ) {
        val content = Modifier
            .fillMaxWidth()
            .then(if (scrollState != null) Modifier.verticalScroll(scrollState, enabled = drag == null) else Modifier)
            .onPlaced { geometry.content = it }
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp)

        Column(content) {
            if (mode == ReorderMode.Sections) {
                SectionHeader("Sections", pinned = false, draggable = false)
                Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    Column {
                        shown.forEachIndexed { i, section ->
                            val k = sectionKey(section.id)
                            Box(Modifier.reorderSlot(k, geometry, drag, animate = drag != null)) {
                                SectionSummaryRow(section, layoutOf(section))
                            }
                            if (i < shown.lastIndex) HorizontalDivider(Modifier.padding(start = 56.dp))
                        }
                    }
                }
            } else {
                shown.forEach { section ->
                    Column(Modifier.onGloballyPositioned { geometry.sections[section.id] = it.unclippedBounds() }) {
                        SectionHeader(section.title, section.pinned, draggable = false)
                        SectionBody(section, layoutOf(section), key, row, geometry, drag, emptySectionText)
                    }
                }
            }
        }

        // The lifted item, following the finger.
        drag?.let { d ->
            val k = d.key
            val liftedSection = if (mode == ReorderMode.Sections) d.working.firstOrNull { sectionKey(it.id) == k } else null
            val liftedItem = if (mode == ReorderMode.Items) {
                sections.firstOrNull { it.id == d.sourceSection }
                    ?.let { s -> s.items.firstOrNull { key(it) == k }?.let { item -> s to item } }
            } else null
            if (liftedSection != null || liftedItem != null) {
                val size = d.size
                Box(
                    Modifier
                        .offset { ((d.pointer - d.grab - geometry.origin) + d.ghost.value).round() }
                        .size(with(density) { size.width.toDp() }, with(density) { size.height.toDp() })
                        .graphicsLayer {
                            scaleX = if (d.settling) 1f else 1.04f
                            scaleY = scaleX
                            shadowElevation = 12.dp.toPx()
                            shape = RoundedCornerShape(16.dp)
                            clip = true
                        }
                        .background(MaterialTheme.colorScheme.surfaceContainerHigh),
                ) {
                    when {
                        liftedSection != null -> SectionSummaryRow(liftedSection, layoutOf(liftedSection))
                        liftedItem != null -> Cell(row(liftedItem.second), layoutOf(liftedItem.first), locked = false, width = null)
                    }
                }
            }
        }
    }
}

/** Bounds in root coordinates, not clipped by scrolling containers (off-screen items keep real positions). */
private fun LayoutCoordinates.unclippedBounds(): Rect = Rect(positionInRoot(), androidx.compose.ui.geometry.Size(size.width.toFloat(), size.height.toFloat()))

// MARK: - State

private class DragState<T>(
    original: List<ReorderSection<T>>,
    val key: Any,
    var position: ItemPosition,
    val sourceSection: String,
    start: Offset,
    val grab: Offset,
    startBounds: Rect,
) {
    var working by mutableStateOf(original)
    var pointer by mutableStateOf(start)
    var swapKey by mutableStateOf<Any?>(null)
    var settling by mutableStateOf(false)
    val size = startBounds.size
    /** Extra offset used to glide the lifted copy into its slot on drop. */
    val ghost = Animatable(Offset.Zero, Offset.VectorConverter)
}

/** Bounds of everything, in root coordinates, for hit-testing the finger. */
private class ReorderGeometry {
    val items = HashMap<Any, Rect>()
    val sections = HashMap<String, Rect>()
    var viewport: Rect? = null
    var origin: Offset = Offset.Zero
    var content: LayoutCoordinates? = null
    /** Last position of each item inside the content, for the "make room" animation. */
    val placed = HashMap<Any, Offset>()
    val motion = HashMap<Any, Animatable<Offset, AnimationVector2D>>()
}

/**
 * Records the slot's bounds and, while dragging, animates it from its previous place to its new one,
 * so the other items glide out of the way (the lifted one stays invisible in its slot).
 */
@Composable
private fun Modifier.reorderSlot(key: Any, geometry: ReorderGeometry, drag: DragState<*>?, animate: Boolean): Modifier {
    val scope = rememberCoroutineScope()
    val anim = remember(key) { geometry.motion.getOrPut(key) { Animatable(Offset.Zero, Offset.VectorConverter) } }
    return this
        .onPlaced { coords ->
            geometry.items[key] = coords.unclippedBounds()
            val content = geometry.content ?: return@onPlaced
            if (!content.isAttached || !coords.isAttached) return@onPlaced
            val now = content.localPositionOf(coords, Offset.Zero)
            val before = geometry.placed.put(key, now)
            if (animate && before != null && before != now && (abs(before.x - now.x) > 0.5f || abs(before.y - now.y) > 0.5f)) {
                scope.launch {
                    anim.snapTo(anim.value + (before - now))
                    anim.animateTo(Offset.Zero, spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow))
                }
            }
        }
        .offset { anim.value.round() }
        .alpha(if (drag?.key == key) 0f else 1f)
        .then(
            if (drag?.swapKey == key) Modifier.border(BorderStroke(2.dp, MaterialTheme.colorScheme.primary), RoundedCornerShape(16.dp))
            else Modifier,
        )
}

// MARK: - Sections and cells

@Composable
private fun SectionHeader(title: String, pinned: Boolean, draggable: Boolean) {
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        if (pinned) Icon(Icons.Outlined.PushPin, null, Modifier.size(14.dp).padding(end = 2.dp), tint = MaterialTheme.colorScheme.primary)
        Text(title, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.weight(1f))
        if (draggable) Icon(Icons.Outlined.DragIndicator, null, tint = MaterialTheme.colorScheme.outline)
    }
}

@Composable
private fun <T> SectionBody(
    section: ReorderSection<T>,
    layout: ReorderLayout,
    key: (T) -> Any,
    row: (T) -> ReorderRow,
    geometry: ReorderGeometry,
    drag: DragState<*>?,
    emptyText: String?,
) {
    val animate = drag != null
    if (section.items.isEmpty()) {
        if (emptyText != null && section.allowsItemMoves) EmptyDropZone(emptyText)
        return
    }
    val locked = !section.allowsItemMoves
    when (layout) {
        ReorderLayout.List -> Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column {
                section.items.forEachIndexed { i, item ->
                    Box(Modifier.reorderSlot(key(item), geometry, drag, animate)) { Cell(row(item), layout, locked, null) }
                    if (i < section.items.lastIndex) HorizontalDivider(Modifier.padding(start = 56.dp))
                }
            }
        }

        is ReorderLayout.Grid -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            section.items.chunked(layout.columns.coerceAtLeast(1)).forEach { chunk ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    chunk.forEach { item ->
                        Box(Modifier.weight(1f).reorderSlot(key(item), geometry, drag, animate)) { Cell(row(item), layout, locked, null) }
                    }
                    repeat(layout.columns - chunk.size) { Spacer(Modifier.weight(1f)) }
                }
            }
        }

        ReorderLayout.Banner -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            section.items.forEach { item ->
                Box(Modifier.reorderSlot(key(item), geometry, drag, animate)) { Cell(row(item), layout, locked, null) }
            }
        }

        ReorderLayout.Carousel, ReorderLayout.HorizontalBanner -> BoxWithConstraints(Modifier.fillMaxWidth()) {
            val pageWidth = maxWidth
            val scroll = rememberScrollState()
            val pagePx = with(LocalDensity.current) { (pageWidth + 10.dp).toPx() }
            // Horizontal banners page one card at a time; carousels scroll freely.
            val snap = remember(scroll, pagePx) {
                object : SnapLayoutInfoProvider {
                    override fun calculateSnapOffset(velocity: Float): Float {
                        val page = scroll.value / pagePx
                        val target = when {
                            velocity > 400 -> kotlin.math.ceil(page)
                            velocity < -400 -> kotlin.math.floor(page)
                            else -> kotlin.math.round(page)
                        }
                        return target * pagePx - scroll.value
                    }
                }
            }
            val fling = rememberSnapFlingBehavior(snap)
            Row(
                Modifier.horizontalScroll(
                    scroll,
                    enabled = drag == null,
                    flingBehavior = if (layout == ReorderLayout.HorizontalBanner) fling else null,
                ),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                section.items.forEach { item ->
                    Box(Modifier.reorderSlot(key(item), geometry, drag, animate)) {
                        Cell(row(item), layout, locked, if (layout == ReorderLayout.HorizontalBanner) pageWidth else null)
                    }
                }
            }
        }
    }
}

@Composable
private fun Cell(row: ReorderRow, layout: ReorderLayout, locked: Boolean, width: androidx.compose.ui.unit.Dp?) {
    val colors = MaterialTheme.colorScheme
    when (layout) {
        ReorderLayout.List -> Row(
            Modifier.fillMaxWidth().heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            row.icon?.let { Icon(it, null, tint = colors.primary) }
            Column(Modifier.weight(1f)) {
                Text(row.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                row.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            }
            Icon(if (locked) Icons.Outlined.PushPin else Icons.Outlined.DragIndicator, null, tint = colors.outline)
        }

        is ReorderLayout.Grid -> Surface(shape = RoundedCornerShape(14.dp), color = colors.surfaceContainerLow) {
            Column(
                Modifier.fillMaxWidth().heightIn(min = 104.dp).padding(horizontal = 6.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                row.icon?.let { Icon(it, null, tint = colors.primary) }
                Text(row.title, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center,
                    maxLines = 2, modifier = Modifier.padding(top = 6.dp))
                row.subtitle?.let { Text(it, style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant) }
            }
        }

        ReorderLayout.Carousel -> Surface(shape = RoundedCornerShape(16.dp), color = colors.surfaceContainerLow) {
            Column(Modifier.width(150.dp).heightIn(min = 128.dp).padding(12.dp)) {
                Box(Modifier.size(36.dp).background(colors.primaryContainer, CircleShape), contentAlignment = Alignment.Center) {
                    row.icon?.let { Icon(it, null, Modifier.size(20.dp), tint = colors.onPrimaryContainer) }
                }
                Spacer(Modifier.weight(1f).heightIn(min = 16.dp))
                Text(row.title, style = MaterialTheme.typography.titleSmall, maxLines = 2)
                row.subtitle?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant) }
            }
        }

        ReorderLayout.Banner, ReorderLayout.HorizontalBanner -> Surface(shape = RoundedCornerShape(20.dp), color = colors.primary) {
            Row(
                (if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()).heightIn(min = 100.dp).padding(18.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(row.title, style = MaterialTheme.typography.titleLarge, color = colors.onPrimary)
                    row.subtitle?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = colors.onPrimary.copy(alpha = 0.85f)) }
                }
                row.icon?.let { Icon(it, null, Modifier.size(40.dp), tint = colors.onPrimary.copy(alpha = 0.6f)) }
                if (locked) Icon(Icons.Outlined.PushPin, null, Modifier.align(Alignment.Top).size(16.dp), tint = colors.onPrimary)
            }
        }
    }
}

@Composable
private fun <T> SectionSummaryRow(section: ReorderSection<T>, layout: ReorderLayout) {
    val colors = MaterialTheme.colorScheme
    val icon = when (layout) {
        ReorderLayout.List -> Icons.AutoMirrored.Outlined.ViewList
        is ReorderLayout.Grid -> Icons.Outlined.ViewModule
        ReorderLayout.Carousel, ReorderLayout.HorizontalBanner -> Icons.Outlined.ViewCarousel
        ReorderLayout.Banner -> Icons.Outlined.ViewAgenda
    }
    val count = if (section.items.size == 1) "1 item" else "${section.items.size} items"
    Row(
        Modifier.fillMaxWidth().background(colors.surfaceContainerLow).heightIn(min = 60.dp).padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Icon(icon, null, tint = colors.primary)
        Column(Modifier.weight(1f)) {
            Text(section.title, style = MaterialTheme.typography.bodyLarge)
            Text(if (section.pinned) "Pinned · $count" else count, style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
        }
        Icon(if (section.pinned) Icons.Outlined.PushPin else Icons.Outlined.DragIndicator, null, tint = colors.outline)
    }
}

@Composable
private fun EmptyDropZone(text: String) {
    Row(
        Modifier
            .fillMaxWidth()
            .border(1.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(16.dp))
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(Icons.Outlined.Inbox, null, tint = MaterialTheme.colorScheme.outline)
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

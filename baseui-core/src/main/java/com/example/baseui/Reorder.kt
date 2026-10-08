package com.example.baseui

/** Layout and cell type of a reorder collection, or of one of its sections. */
sealed interface ReorderLayout {
    /** Rows in a rounded group, like a settings list. */
    data object List : ReorderLayout
    /** Tiles in a fixed number of columns. */
    data class Grid(val columns: Int = 3) : ReorderLayout
    /** Cards in one horizontally scrolling row. */
    data object Carousel : ReorderLayout
    /** Large full-width cards, stacked vertically. */
    data object Banner : ReorderLayout
    /** Large full-width cards side by side; swipe to page. */
    data object HorizontalBanner : ReorderLayout

    /** Cells sit in one row that scrolls sideways. */
    val scrollsSideways: Boolean get() = this == Carousel || this == HorizontalBanner
}

/** What the user drags. */
enum class ReorderMode {
    /** Drag items within and across sections. */
    Items,
    /** Each section collapses to one row; drag to reorder sections. */
    Sections,
}

/**
 * A titled group of items.
 *
 *     ReorderSection("Favorites", favorites, layout = ReorderLayout.Carousel, pinned = true, swapsOnDrop = true)
 */
data class ReorderSection<T>(
    val id: String,
    val title: String,
    val items: List<T>,
    /** `null` uses the collection's default layout. */
    val layout: ReorderLayout? = null,
    /** Keeps the section at its position: it can't be dragged, and others can't be dragged past it. */
    val pinned: Boolean = false,
    /** `false` locks the items: they can't be dragged out and nothing can be dropped in. */
    val allowsItemMoves: Boolean = true,
    /**
     * An item dropped here from another section swaps places with the item it lands on, which moves to
     * the dragged item's old spot, so the section keeps its item count.
     */
    val swapsOnDrop: Boolean = false,
) {
    /** Uses the title as the id. */
    constructor(
        title: String,
        items: List<T>,
        layout: ReorderLayout? = null,
        pinned: Boolean = false,
        allowsItemMoves: Boolean = true,
        swapsOnDrop: Boolean = false,
    ) : this(title, title, items, layout, pinned, allowsItemMoves, swapsOnDrop)
}

/** Position of an item: section index and item index. */
data class ItemPosition(val section: Int, val item: Int)

/** Pure move operations shared by the Compose and View implementations. */
object Reorder {

    /** Removes the item at [from] and inserts it at [to] (index counted after the removal). */
    fun <T> moveItem(sections: List<ReorderSection<T>>, from: ItemPosition, to: ItemPosition): List<ReorderSection<T>> {
        if (from == to || from.section !in sections.indices || to.section !in sections.indices) return sections
        val source = sections[from.section].items
        if (from.item !in source.indices) return sections
        val result = sections.map { it.items.toMutableList() }
        val item = result[from.section].removeAt(from.item)
        val target = result[to.section]
        target.add(to.item.coerceIn(0, target.size), item)
        return sections.mapIndexed { i, s -> s.copy(items = result[i]) }
    }

    /** Keeps a section move from crossing pinned sections; returns [from] when that section is pinned. */
    fun clampedSectionDestination(from: Int, to: Int, pinned: List<Boolean>): Int {
        if (from !in pinned.indices || pinned[from]) return from
        var target = to.coerceIn(0, pinned.size - 1)
        if (target < from) {
            (target until from).lastOrNull { pinned[it] }?.let { target = it + 1 }
        } else if (target > from) {
            ((from + 1)..target).firstOrNull { pinned[it] }?.let { target = it - 1 }
        }
        return target
    }

    fun <T> moveSection(sections: List<ReorderSection<T>>, from: Int, to: Int): List<ReorderSection<T>> {
        val target = clampedSectionDestination(from, to, sections.map { it.pinned })
        if (target == from) return sections
        return sections.toMutableList().apply { add(target, removeAt(from)) }
    }

    /** Whether moving [from] to [to] swaps instead: it crosses into a `swapsOnDrop` section with items. */
    fun <T> swaps(sections: List<ReorderSection<T>>, from: ItemPosition, to: ItemPosition): Boolean =
        from.section != to.section &&
            to.section in sections.indices &&
            sections[to.section].swapsOnDrop &&
            sections[to.section].items.isNotEmpty()

    /** Swaps two items across sections. */
    fun <T> swapItem(sections: List<ReorderSection<T>>, from: ItemPosition, with: ItemPosition): List<ReorderSection<T>> {
        if (from.section == with.section) return sections
        val a = sections.getOrNull(from.section)?.items?.getOrNull(from.item) ?: return sections
        val b = sections.getOrNull(with.section)?.items?.getOrNull(with.item) ?: return sections
        return sections.mapIndexed { i, s ->
            when (i) {
                from.section -> s.copy(items = s.items.toMutableList().also { it[from.item] = b })
                with.section -> s.copy(items = s.items.toMutableList().also { it[with.item] = a })
                else -> s
            }
        }
    }

    /** Whether the user may drop an item from [from] into [to]. */
    fun <T> canDrop(sections: List<ReorderSection<T>>, from: ItemPosition, to: Int, allowsCrossSection: Boolean): Boolean =
        sections.getOrNull(to)?.allowsItemMoves == true && (allowsCrossSection || from.section == to)
}

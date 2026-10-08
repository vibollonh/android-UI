package com.example.baseui.sample

import android.view.View
import android.widget.TextView
import androidx.compose.runtime.Composable
import com.example.baseui.ReorderLayout
import com.example.baseui.ReorderMode
import com.example.baseui.ReorderSection
import com.example.baseui.views.BaseReorderView
import com.example.baseui.views.ReorderRow
import com.google.android.material.button.MaterialButtonToggleGroup

/** Same data as the Compose demo, with drawable icons for the View version. */
data class XmlTask(val id: String, val name: String, val note: String, val icon: Int)

private fun xmlSample() = listOf(
    ReorderSection("To do", listOf(
        XmlTask("a", "Design login screen", "Figma · 2 pts", R.drawable.ic_edit),
        XmlTask("b", "Add Khmer localization", "Strings · 3 pts", R.drawable.ic_star),
        XmlTask("c", "QR payment flow", "Feature · 5 pts", R.drawable.ic_qr),
    )),
    ReorderSection("In progress", listOf(XmlTask("d", "Cutout toast", "BaseUI · 3 pts", R.drawable.ic_star))),
    ReorderSection("Done", listOf(
        XmlTask("e", "Base button", "BaseUI · 2 pts", R.drawable.ic_check),
        XmlTask("f", "Base text field", "BaseUI · 3 pts", R.drawable.ic_check),
    )),
    ReorderSection<XmlTask>("Blocked", emptyList()),
)

private fun xmlMixed() = listOf(
    ReorderSection("Sprint goal", listOf(
        XmlTask("g", "Ship BaseUI 0.2", "Buttons, fields, dropdowns and reorder lists", R.drawable.ic_flag),
        XmlTask("g2", "Ship BaseUI 0.3", "Zoom transitions and Android", R.drawable.ic_flag),
    ), layout = ReorderLayout.HorizontalBanner, pinned = true, allowsItemMoves = false),
    ReorderSection("Favorites", listOf(
        XmlTask("h", "QR payment flow", "5 pts", R.drawable.ic_qr),
        XmlTask("i", "Cutout toast", "3 pts", R.drawable.ic_star),
        XmlTask("j", "Dark mode", "2 pts", R.drawable.ic_moon),
        XmlTask("k", "Haptics", "1 pt", R.drawable.ic_star),
    ), layout = ReorderLayout.Carousel, pinned = true, swapsOnDrop = true),
    ReorderSection("To do", listOf(
        XmlTask("a", "Design login screen", "Figma · 2 pts", R.drawable.ic_edit),
        XmlTask("b", "Add Khmer localization", "Strings · 3 pts", R.drawable.ic_star),
    ), layout = ReorderLayout.List),
    ReorderSection("Done", listOf(
        XmlTask("e", "Base button", "2 pts", R.drawable.ic_check),
        XmlTask("f", "Base text field", "3 pts", R.drawable.ic_check),
        XmlTask("l", "Dropdown", "2 pts", R.drawable.ic_check),
    ), layout = ReorderLayout.Grid(3)),
    ReorderSection<XmlTask>("Blocked", emptyList(), layout = ReorderLayout.List),
)

@Composable
fun ReorderXmlDemo() = XmlLayout(R.layout.demo_reorder_xml, ::bindReorder)

private fun bindReorder(root: View) {
    @Suppress("UNCHECKED_CAST")
    val board = root.findViewById<BaseReorderView<XmlTask>>(R.id.board)
    val order = root.findViewById<TextView>(R.id.order)
    var list = xmlSample()
    var mixed = xmlMixed()
    var style = R.id.style_mixed

    fun show() {
        val sections = if (style == R.id.style_mixed) mixed else list
        order.text = "Order: " + sections.joinToString(" → ") { "${it.title} (${it.items.size})" }
        board.defaultLayout = if (style == R.id.style_grid) ReorderLayout.Grid(3) else ReorderLayout.List
        board.sections = sections
    }

    board.setup(key = { it.id }, row = { ReorderRow(it.name, it.note, it.icon) })
    board.onChange = { sections ->
        if (style == R.id.style_mixed) mixed = sections else list = sections
        show()
    }
    root.findViewById<MaterialButtonToggleGroup>(R.id.mode).addOnButtonCheckedListener { _, id, checked ->
        if (checked) board.mode = if (id == R.id.mode_sections) ReorderMode.Sections else ReorderMode.Items
    }
    root.findViewById<MaterialButtonToggleGroup>(R.id.style).addOnButtonCheckedListener { _, id, checked ->
        if (checked) { style = id; show() }
    }
    show()
}

package com.example.baseui.sample

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Crop54
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.GraphicEq
import androidx.compose.material.icons.outlined.QrCode
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.baseui.ReorderLayout
import com.example.baseui.ReorderMode
import com.example.baseui.ReorderSection
import com.example.baseui.compose.BaseReorderCollection
import com.example.baseui.compose.ReorderRow

data class DemoTask(val id: String, val name: String, val note: String, val icon: ImageVector)

enum class ReorderStyle { List, Grid, Mixed }

fun sampleSections() = listOf(
    ReorderSection("To do", listOf(
        DemoTask("a", "Design login screen", "Figma · 2 pts", Icons.Outlined.Brush),
        DemoTask("b", "Add Khmer localization", "Strings · 3 pts", Icons.Outlined.Translate),
        DemoTask("c", "QR payment flow", "Feature · 5 pts", Icons.Outlined.QrCode),
    )),
    ReorderSection("In progress", listOf(DemoTask("d", "Cutout toast", "BaseUI · 3 pts", Icons.Outlined.Crop54))),
    ReorderSection("Done", listOf(
        DemoTask("e", "Base button", "BaseUI · 2 pts", Icons.Outlined.CheckCircle),
        DemoTask("f", "Base text field", "BaseUI · 3 pts", Icons.Outlined.CheckCircle),
    )),
    ReorderSection<DemoTask>("Blocked", emptyList()),
)

/** Each section has its own layout; Sprint goal is pinned and locked, Favorites is pinned and swaps on drop. */
fun mixedSections() = listOf(
    ReorderSection("Sprint goal", listOf(
        DemoTask("g", "Ship BaseUI 0.2", "Buttons, fields, dropdowns and reorder lists", Icons.Outlined.Flag),
        DemoTask("g2", "Ship BaseUI 0.3", "Zoom transitions and Android", Icons.Outlined.Flag),
    ), layout = ReorderLayout.HorizontalBanner, pinned = true, allowsItemMoves = false),
    ReorderSection("Favorites", listOf(
        DemoTask("h", "QR payment flow", "5 pts", Icons.Outlined.QrCode),
        DemoTask("i", "Cutout toast", "3 pts", Icons.Outlined.Crop54),
        DemoTask("j", "Dark mode", "2 pts", Icons.Outlined.DarkMode),
        DemoTask("k", "Haptics", "1 pt", Icons.Outlined.GraphicEq),
    ), layout = ReorderLayout.Carousel, pinned = true, swapsOnDrop = true),
    ReorderSection("To do", listOf(
        DemoTask("a", "Design login screen", "Figma · 2 pts", Icons.Outlined.Brush),
        DemoTask("b", "Add Khmer localization", "Strings · 3 pts", Icons.Outlined.Translate),
    ), layout = ReorderLayout.List),
    ReorderSection("Done", listOf(
        DemoTask("e", "Base button", "2 pts", Icons.Outlined.CheckCircle),
        DemoTask("f", "Base text field", "3 pts", Icons.Outlined.CheckCircle),
        DemoTask("l", "Dropdown", "2 pts", Icons.Outlined.CheckCircle),
    ), layout = ReorderLayout.Grid(3)),
    ReorderSection<DemoTask>("Blocked", emptyList(), layout = ReorderLayout.List),
)

@Composable
fun ReorderComposeDemo() {
    var style by remember { mutableStateOf(ReorderStyle.Mixed) }
    var mode by remember { mutableStateOf(ReorderMode.Items) }
    var list by remember { mutableStateOf(sampleSections()) }
    var mixed by remember { mutableStateOf(mixedSections()) }

    Column(Modifier.fillMaxSize()) {
        ReorderControls(style, { style = it }, mode, { mode = it })
        val sections = if (style == ReorderStyle.Mixed) mixed else list
        Text(
            "Order: " + sections.joinToString(" → ") { "${it.title} (${it.items.size})" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
        )
        BaseReorderCollection(
            sections = sections,
            onChange = { if (style == ReorderStyle.Mixed) mixed = it else list = it },
            key = { it.id },
            modifier = Modifier.fillMaxSize(),
            mode = mode,
            defaultLayout = if (style == ReorderStyle.Grid) ReorderLayout.Grid(3) else ReorderLayout.List,
        ) { task -> ReorderRow(task.name, task.note, task.icon) }
    }
}

@Composable
fun ReorderControls(style: ReorderStyle, onStyle: (ReorderStyle) -> Unit, mode: ReorderMode, onMode: (ReorderMode) -> Unit) {
    Column(Modifier.padding(horizontal = 16.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            ReorderMode.entries.forEachIndexed { i, m ->
                SegmentedButton(mode == m, { onMode(m) }, SegmentedButtonDefaults.itemShape(i, 2)) {
                    Text(if (m == ReorderMode.Items) "Reorder items" else "Reorder sections")
                }
            }
        }
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            ReorderStyle.entries.forEachIndexed { i, s ->
                SegmentedButton(style == s, { onStyle(s) }, SegmentedButtonDefaults.itemShape(i, 3)) { Text(s.name) }
            }
        }
        Text(
            if (mode == ReorderMode.Items) "Hold an item, then drag. Favorites swaps on drop." else "Hold a section, then drag. Pinned ones stay.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 8.dp),
        )
    }
}

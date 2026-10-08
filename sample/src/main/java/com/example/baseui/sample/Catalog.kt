package com.example.baseui.sample

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Crop54
import androidx.compose.material.icons.outlined.DragHandle
import androidx.compose.material.icons.automirrored.outlined.Input
import androidx.compose.material.icons.outlined.OpenInFull
import androidx.compose.material.icons.outlined.PhoneAndroid
import androidx.compose.material.icons.outlined.SmartButton
import androidx.compose.material.icons.outlined.UnfoldMore
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp

/** Every BaseUI feature gets one entry here, a Compose demo and an XML demo. */
enum class Feature(val title: String, val subtitle: String, val icon: ImageVector) {
    Toast("Cutout toast", "Grows out of the camera hole", Icons.Outlined.Crop54),
    Button("Base button", "Variants, sizes, loading", Icons.Outlined.SmartButton),
    TextField("Base text field", "Title, icon, password, helper / error", Icons.AutoMirrored.Outlined.Input),
    Dropdown("Dropdown field", "Text-field look with a menu", Icons.Outlined.UnfoldMore),
    Reorder("Reorder list", "Drag items across sections, reorder sections", Icons.Outlined.DragHandle),
    Zoom("Zoom transition", "Card to full screen; drag down or right to close", Icons.Outlined.OpenInFull),
    Device("Device screen", "Cutout type and screen corner radius", Icons.Outlined.PhoneAndroid),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatalogScreen(onOpen: (Feature) -> Unit) {
    Scaffold(topBar = { TopAppBar(title = { Text("BaseUI") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(Feature.entries) { feature ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(feature) }.padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier.size(44.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center,
                    ) { Icon(feature.icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer) }
                    Column {
                        Text(feature.title, style = MaterialTheme.typography.titleMedium)
                        Text(feature.subtitle, style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

/** One feature: Compose and XML versions behind a segmented switch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FeatureScreen(feature: Feature, onBack: () -> Unit) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(feature.title) },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                listOf("Jetpack Compose", "XML views").forEachIndexed { index, label ->
                    SegmentedButton(
                        selected = tab == index,
                        onClick = { tab = index },
                        shape = SegmentedButtonDefaults.itemShape(index, 2),
                    ) { Text(label) }
                }
            }
            Box(Modifier.fillMaxSize()) {
                if (tab == 0) ComposeDemo(feature) else XmlDemo(feature)
            }
        }
    }
}

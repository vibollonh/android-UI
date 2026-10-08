package com.example.baseui.sample

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Favorite
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.automirrored.outlined.ListAlt
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.ShoppingCart
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.example.baseui.ZoomConfig
import com.example.baseui.ZoomDismissGesture
import com.example.baseui.compose.LocalZoom
import com.example.baseui.compose.ZoomScope
import com.example.baseui.compose.zoomSource

data class WalletCard(val name: String, val amount: String, val icon: ImageVector, val color: Color)

val walletCards = listOf(
    WalletCard("Savings", "$12,480", Icons.Outlined.Payments, Color(0xFF00897B)),
    WalletCard("Travel", "$3,210", Icons.Outlined.Flight, Color(0xFF5E35B1)),
    WalletCard("Bills", "$860", Icons.Outlined.Description, Color(0xFFEF6C00)),
    WalletCard("Groceries", "$420", Icons.Outlined.ShoppingCart, Color(0xFF8D6E63)),
    WalletCard("Gifts", "$150", Icons.Outlined.CardGiftcard, Color(0xFFD81B60)),
    WalletCard("Health", "$95", Icons.Outlined.Favorite, Color(0xFF1E88E5)),
)

val walletDetails = listOf(
    "Monthly goal" to "$500", "Last deposit" to "2 days ago", "Auto-save" to "On", "Interest" to "2.1%",
    "Opened" to "Jan 2024", "Account" to "•••• 4821", "Owner" to "You", "Currency" to "USD",
)

@Composable
fun ZoomComposeDemo() {
    val zoom = LocalZoom.current
    var down by remember { mutableStateOf(true) }
    var right by remember { mutableStateOf(true) }
    val config = ZoomConfig(
        dismissGestures = buildSet {
            if (down) add(ZoomDismissGesture.Down)
            if (right) add(ZoomDismissGesture.Right)
        },
    )

    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        modifier = Modifier.fillMaxSize(),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(walletCards, key = { it.name }) { card ->
            Column(
                Modifier
                    .zoomSource(card.name, cornerRadius = 20.dp)
                    .background(card.color, RoundedCornerShape(20.dp))
                    .clickable { zoom.present(card.name, config) { WalletDetail(card, this) } }
                    .height(150.dp)
                    .padding(14.dp),
            ) {
                Icon(card.icon, null, tint = Color.White)
                Spacer(Modifier.weight(1f))
                Text(card.name, color = Color.White, style = MaterialTheme.typography.bodySmall)
                Text(card.amount, color = Color.White, style = MaterialTheme.typography.titleLarge)
            }
        }
        item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(2) }) {
            Column(Modifier.padding(top = 8.dp)) {
                Text("Dismiss gestures", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                ToggleRow("Drag down (shrinks and follows)", down) { down = it }
                ToggleRow("Drag right (slides and zooms out)", right) { right = it }
                Text("The back gesture zooms out too. Pushed screens inside the detail don't close by dragging.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f))
        Switch(checked, onChange)
    }
}

private sealed interface WalletRoute {
    data object Home : WalletRoute
    data object Transactions : WalletRoute
    data class Transaction(val title: String, val amount: String) : WalletRoute
    data object Statements : WalletRoute
}

/** The zoomed detail with its own navigation stack. Drags only close it from the first screen. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletDetail(card: WalletCard, zoom: ZoomScope) {
    val stack = remember { mutableStateListOf<WalletRoute>(WalletRoute.Home) }
    LaunchedEffect(stack.size) { zoom.isShowingPushedScreen = stack.size > 1 }
    BackHandler(enabled = stack.size > 1) { stack.removeAt(stack.lastIndex) }

    AnimatedContent(
        targetState = stack.toList(),
        transitionSpec = {
            val forward = targetState.size >= initialState.size
            (slideInHorizontally { if (forward) it else -it / 3 }) togetherWith (slideOutHorizontally { if (forward) -it / 3 else it })
        },
        label = "wallet-navigation",
    ) { screens ->
        when (val route = screens.last()) {
            WalletRoute.Home -> WalletHome(card, push = { stack.add(it) }, close = zoom::dismiss)
            WalletRoute.Transactions -> PushedList("Transactions", { stack.removeAt(stack.lastIndex) },
                (1..8).map { "${listOf("Coffee", "Taxi", "Hotel", "Market", "Flight", "Pharmacy", "Books", "Dinner")[it - 1]} · ${card.name}" to "-$${12 + it * 17}.00" },
                onRow = { (title, amount) -> stack.add(WalletRoute.Transaction(title, amount)) })
            is WalletRoute.Transaction -> PushedList(route.title, { stack.removeAt(stack.lastIndex) },
                listOf("Amount" to route.amount, "Date" to "Oct 8, 2026", "Status" to "Completed", "Reference" to "TX-4810"))
            WalletRoute.Statements -> PushedList("Statements", { stack.removeAt(stack.lastIndex) },
                listOf("September 2026" to "$1,240.00", "August 2026" to "$980.50", "July 2026" to "$1,105.20", "June 2026" to "$860.00"))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun WalletHome(card: WalletCard, push: (WalletRoute) -> Unit, close: () -> Unit) {
    Scaffold(
        containerColor = card.color,
        topBar = {
            TopAppBar(
                title = { Text(card.name) },
                actions = { IconButton(onClick = close) { Icon(Icons.Outlined.Close, "Close") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = card.color, titleContentColor = Color.White,
                    actionIconContentColor = Color.White),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding).padding(horizontal = 20.dp)) {
            item {
                Icon(card.icon, null, Modifier.size(40.dp).padding(top = 4.dp), tint = Color.White)
                Text(card.amount, color = Color.White, style = MaterialTheme.typography.displayMedium, modifier = Modifier.padding(vertical = 16.dp))
            }
            items(listOf(Triple("Transactions", Icons.AutoMirrored.Outlined.ListAlt, WalletRoute.Transactions),
                Triple("Statements", Icons.AutoMirrored.Outlined.ReceiptLong, WalletRoute.Statements))) { (title, icon, route) ->
                HorizontalDivider(color = Color.White.copy(alpha = 0.4f))
                Row(
                    Modifier.fillMaxWidth().clickable { push(route) }.padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Icon(icon, null, tint = Color.White)
                    Text(title, color = Color.White, modifier = Modifier.weight(1f))
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Color.White.copy(alpha = 0.7f))
                }
            }
            items(walletDetails) { (label, value) ->
                HorizontalDivider(color = Color.White.copy(alpha = 0.4f))
                Row(Modifier.fillMaxWidth().padding(vertical = 16.dp)) {
                    Text(label, color = Color.White, modifier = Modifier.weight(1f))
                    Text(value, color = Color.White)
                }
            }
            item {
                Text("Drag down or right, or use the back gesture, to close. Pushed screens don't close by dragging: go back first.",
                    color = Color.White.copy(alpha = 0.8f), style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = 24.dp))
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PushedList(title: String, back: () -> Unit, rows: List<Pair<String, String>>, onRow: ((Pair<String, String>) -> Unit)? = null) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = { IconButton(onClick = back) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding)) {
            items(rows) { row ->
                ListItem(
                    headlineContent = { Text(row.first) },
                    trailingContent = { Text(row.second) },
                    modifier = if (onRow != null) Modifier.clickable { onRow(row) } else Modifier,
                )
            }
        }
    }
}

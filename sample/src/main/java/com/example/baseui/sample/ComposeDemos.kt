package com.example.baseui.sample

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AccountBalanceWallet
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Mail
import androidx.compose.material.icons.outlined.Phone
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import com.example.baseui.DeviceScreen
import com.example.baseui.ToastStyle
import com.example.baseui.compose.BaseButton
import com.example.baseui.compose.BaseDropdownField
import com.example.baseui.compose.BaseTextField
import com.example.baseui.compose.ButtonSize
import com.example.baseui.compose.ButtonVariant
import kotlinx.coroutines.delay

@Composable
fun ComposeDemo(feature: Feature) {
    when (feature) {
        Feature.Toast -> ToastDemo()
        Feature.Button -> ButtonDemo()
        Feature.TextField -> TextFieldDemo()
        Feature.Dropdown -> DropdownDemo()
        Feature.Reorder -> ReorderComposeDemo()
        Feature.Zoom -> ZoomComposeDemo()
        Feature.Device -> DeviceDemo()
    }
}

@Composable
private fun DemoColumn(content: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) { content() }
}

@Composable
private fun ToastDemo() {
    val toast = LocalToast.current
    DemoColumn {
        Text("toast.show(message, style)", style = MaterialTheme.typography.bodyMedium)
        listOf(
            Triple("Success", "Transfer completed", ToastStyle.Success),
            Triple("Error", "Insufficient balance", ToastStyle.Error),
            Triple("Warning", "Session expires in 1 min", ToastStyle.Warning),
            Triple("Info", "QR code copied", ToastStyle.Info),
        ).forEach { (title, message, style) ->
            BaseButton(title, { toast.show(message, style) }, Modifier.fillMaxWidth(), variant = ButtonVariant.Tonal)
        }
        BaseButton("Queue 3 toasts", {
            toast.show("Step 1: Verifying")
            toast.show("Step 2: Sending")
            toast.show("Step 3: Done", ToastStyle.Success)
        }, Modifier.fillMaxWidth(), variant = ButtonVariant.Outlined)
        BaseButton("Long message (2 lines)", {
            toast.show("Your transfer of $1,250.00 to Sokha Chan was completed successfully.", ToastStyle.Success)
        }, Modifier.fillMaxWidth(), variant = ButtonVariant.Outlined)
    }
}

@Composable
private fun ButtonDemo() {
    var paying by remember { mutableStateOf(false) }
    LaunchedEffect(paying) { if (paying) { delay(1600); paying = false } }
    DemoColumn {
        ButtonVariant.entries.forEach { BaseButton(it.name, {}, Modifier.fillMaxWidth(), variant = it) }
        Text("Sizes", style = MaterialTheme.typography.labelMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            ButtonSize.entries.forEach { BaseButton(it.name, {}, size = it) }
        }
        Text("Loading (tap)", style = MaterialTheme.typography.labelMedium)
        BaseButton("Pay now", { paying = true }, Modifier.fillMaxWidth(), size = ButtonSize.Large, loading = paying)
    }
}

@Composable
private fun TextFieldDemo() {
    var email by remember { mutableStateOf("sokha@example.com") }
    var password by remember { mutableStateOf("secret123") }
    var phone by remember { mutableStateOf("012 34") }
    DemoColumn {
        BaseTextField(email, { email = it }, Modifier.fillMaxWidth(), title = "Email",
            leadingIcon = Icons.Outlined.Mail, helperText = "We never share your email")
        BaseTextField(password, { password = it }, Modifier.fillMaxWidth(), title = "Password",
            leadingIcon = Icons.Outlined.Lock, helperText = "At least 8 characters", isPassword = true)
        BaseTextField(phone, { phone = it }, Modifier.fillMaxWidth(), title = "Phone", leadingIcon = Icons.Outlined.Phone,
            error = if (phone.filter(Char::isDigit).length < 9) "Enter a valid phone number" else null)
    }
}

@Composable
private fun DropdownDemo() {
    val accounts = listOf("Savings •••• 4821", "Travel •••• 1902", "Bills •••• 7730")
    var account by remember { mutableStateOf(accounts.first()) }
    DemoColumn {
        BaseDropdownField(accounts, account, { account = it }, Modifier.fillMaxWidth(), title = "Account",
            leadingIcon = Icons.Outlined.AccountBalanceWallet, helperText = "Money comes out of this account")
        Text("Chosen: $account", style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun DeviceDemo() {
    val view = LocalView.current
    val screen = remember(view) { DeviceScreen.from(view) }
    val density = view.resources.displayMetrics.density
    val toast = LocalToast.current
    DemoColumn {
        listOf(
            "Cutout" to screen.cutout.name,
            "Cutout frame (px)" to (screen.cutoutRect?.toShortString() ?: "—"),
            "Display corner radius" to "${(screen.cornerRadiusPx / density).toInt()} dp",
            "Status bar / cutout inset" to "${(screen.safeTopPx / density).toInt()} dp",
            "Screen" to "${screen.widthPx} × ${screen.heightPx} px",
        ).forEach { (label, value) ->
            ListItem(headlineContent = { Text(label) }, trailingContent = { Text(value) })
        }
        BaseButton("Test cutout toast", { toast.show("Detected: ${screen.cutout}") }, Modifier.fillMaxWidth(),
            variant = ButtonVariant.Tonal)
    }
}

@Composable
fun ComingNext(name: String) {
    DemoColumn { Text("$name: coming next", style = MaterialTheme.typography.bodyLarge) }
}


package com.example.baseui.sample

import android.app.Activity
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.foundation.layout.fillMaxSize
import com.example.baseui.ToastStyle
import com.example.baseui.views.BaseButton
import com.example.baseui.views.BaseDropdownField
import com.example.baseui.views.BaseTextField
import com.example.baseui.views.CutoutToast

/** XML versions: each inflates a layout from res/layout and wires it like an Activity / Fragment would. */
@Composable
fun XmlDemo(feature: Feature) {
    when (feature) {
        Feature.Toast -> XmlLayout(R.layout.demo_toast_xml, ::bindToast)
        Feature.Button -> XmlLayout(R.layout.demo_button_xml, ::bindButton)
        Feature.TextField -> XmlLayout(R.layout.demo_field_xml, ::bindField)
        Feature.Dropdown -> XmlLayout(R.layout.demo_dropdown_xml, ::bindDropdown)
        Feature.Reorder -> ReorderXmlDemo()
        Feature.Zoom -> ZoomXmlDemo()
        Feature.Device -> ComposeDemo(Feature.Device)
    }
}

@Composable
fun XmlLayout(layout: Int, bind: (View) -> Unit) {
    AndroidView(
        factory = { context -> LayoutInflater.from(context).inflate(layout, null, false).also(bind) },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun bindToast(root: View) {
    val activity = root.context as Activity
    fun on(id: Int, action: () -> Unit) = root.findViewById<View>(id).setOnClickListener { action() }
    on(R.id.success) { CutoutToast.show(activity, "Transfer completed", ToastStyle.Success) }
    on(R.id.error) { CutoutToast.show(activity, "Insufficient balance", ToastStyle.Error) }
    on(R.id.warning) { CutoutToast.show(activity, "Session expires in 1 min", ToastStyle.Warning) }
    on(R.id.info) { CutoutToast.show(activity, "QR code copied", ToastStyle.Info) }
    on(R.id.queue) {
        CutoutToast.show(activity, "Step 1: Verifying")
        CutoutToast.show(activity, "Step 2: Sending")
        CutoutToast.show(activity, "Step 3: Done", ToastStyle.Success)
    }
    on(R.id.long_message) {
        CutoutToast.show(activity, "Your transfer of $1,250.00 to Sokha Chan was completed successfully.", ToastStyle.Success)
    }
}

private fun bindButton(root: View) {
    val pay = root.findViewById<BaseButton>(R.id.pay)
    pay.setOnClickListener {
        pay.loading = true
        pay.postDelayed({ pay.loading = false }, 1600)
    }
}

private fun bindField(root: View) {
    val phone = root.findViewById<BaseTextField>(R.id.phone)
    fun validate(text: String) {
        phone.error = if (text.count(Char::isDigit) < 9) "Enter a valid phone number" else null
    }
    phone.onTextChanged = ::validate
    validate(phone.text)
}

private fun bindDropdown(root: View) {
    val dropdown = root.findViewById<BaseDropdownField>(R.id.account)
    val chosen = root.findViewById<TextView>(R.id.chosen)
    val accounts = root.resources.getStringArray(R.array.accounts).toList()
    dropdown.setOptions(accounts) { chosen.text = "Chosen: $it" }
    dropdown.selectedIndex = 0
    chosen.text = "Chosen: ${accounts.first()}"
}


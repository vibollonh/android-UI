package com.example.baseui.compose

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class ButtonVariant { Filled, Tonal, Outlined, Text }

enum class ButtonSize(internal val height: Dp, internal val horizontal: Dp, internal val text: TextUnit, internal val icon: Dp) {
    Small(32.dp, 14.dp, 13.sp, 16.dp),
    Medium(40.dp, 20.dp, 14.sp, 18.dp),
    Large(52.dp, 24.dp, 16.sp, 20.dp),
}

/**
 * Themed Material 3 button with variants, sizes and a loading state.
 *
 *     BaseButton("Pay now", onClick = ::pay, loading = isPaying)
 *
 * While [loading], it shows a spinner, ignores taps and keeps its width.
 */
@Composable
fun BaseButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: ButtonVariant = ButtonVariant.Filled,
    size: ButtonSize = ButtonSize.Medium,
    leadingIcon: ImageVector? = null,
    loading: Boolean = false,
    enabled: Boolean = true,
) {
    val sized = modifier
        .heightIn(min = size.height)
        .semantics { if (loading) stateDescription = "Loading" }
    val padding = PaddingValues(horizontal = size.horizontal)
    val click = { if (!loading) onClick() }
    val content: @Composable () -> Unit = { ButtonContent(text, size, leadingIcon, loading) }

    when (variant) {
        ButtonVariant.Filled -> Button(click, sized, enabled, contentPadding = padding) { content() }
        ButtonVariant.Tonal -> FilledTonalButton(click, sized, enabled, contentPadding = padding) { content() }
        ButtonVariant.Outlined -> OutlinedButton(click, sized, enabled, contentPadding = padding) { content() }
        ButtonVariant.Text -> TextButton(click, sized, enabled, contentPadding = padding) { content() }
    }
}

@Composable
private fun ButtonContent(text: String, size: ButtonSize, icon: ImageVector?, loading: Boolean) {
    Row(horizontalArrangement = Arrangement.spacedBy(ButtonDefaults.IconSpacing), verticalAlignment = Alignment.CenterVertically) {
        when {
            loading -> CircularProgressIndicator(
                modifier = Modifier.size(size.icon),
                color = LocalContentColor.current,
                strokeWidth = 2.dp,
            )
            icon != null -> Icon(icon, contentDescription = null, modifier = Modifier.size(size.icon))
        }
        Text(text, fontSize = size.text)
    }
}

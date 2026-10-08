package com.example.baseui.views

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.util.AttributeSet
import android.util.TypedValue
import androidx.core.content.withStyledAttributes
import com.google.android.material.button.MaterialButton
import com.google.android.material.color.MaterialColors
import com.google.android.material.progressindicator.CircularProgressIndicatorSpec
import com.google.android.material.progressindicator.IndeterminateDrawable

/**
 * Themed Material 3 button with variants, sizes and a loading state.
 *
 *     <com.example.baseui.views.BaseButton
 *         android:text="Pay now"
 *         app:baseVariant="filled"
 *         app:baseSize="large"
 *         app:loading="false" />
 *
 * While [loading], it shows a spinner in place of the icon and ignores taps.
 */
class BaseButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.materialButtonStyle,
) : MaterialButton(context, attrs, defStyleAttr) {

    enum class Variant { Filled, Tonal, Outlined, Text }
    enum class Size(val heightDp: Int, val paddingDp: Int, val textSp: Float) {
        Small(32, 14, 13f), Medium(40, 20, 14f), Large(52, 24, 16f)
    }

    var variant: Variant = Variant.Filled
        set(value) { field = value; applyStyle() }

    var size: Size = Size.Medium
        set(value) { field = value; applyStyle() }

    var loading: Boolean = false
        set(value) {
            if (field == value) return
            field = value
            if (value) {
                savedIcon = icon
                icon = spinner
            } else {
                icon = savedIcon
            }
            isClickable = !value
            stateDescription = if (value) "Loading" else null
        }

    private var savedIcon = icon
    private val spinner by lazy {
        val spec = CircularProgressIndicatorSpec(context, null).apply {
            indicatorSize = dp(18)
            trackThickness = dp(2)
            indicatorColors = intArrayOf(currentTextColor)
            trackColor = Color.TRANSPARENT
        }
        IndeterminateDrawable.createCircularDrawable(context, spec)
    }

    init {
        context.withStyledAttributes(attrs, R.styleable.BaseButton) {
            variant = Variant.entries[getInt(R.styleable.BaseButton_baseVariant, 0)]
            size = Size.entries[getInt(R.styleable.BaseButton_baseSize, 1)]
            loading = getBoolean(R.styleable.BaseButton_loading, false)
        }
        applyStyle()
    }

    private fun applyStyle() {
        fun color(attr: Int) = MaterialColors.getColor(this, attr)
        val primary = color(androidx.appcompat.R.attr.colorPrimary)
        val (background, foreground) = when (variant) {
            Variant.Filled -> primary to color(com.google.android.material.R.attr.colorOnPrimary)
            Variant.Tonal -> color(com.google.android.material.R.attr.colorSecondaryContainer) to
                color(com.google.android.material.R.attr.colorOnSecondaryContainer)
            Variant.Outlined, Variant.Text -> Color.TRANSPARENT to primary
        }
        backgroundTintList = ColorStateList.valueOf(background)
        setTextColor(foreground)
        iconTint = ColorStateList.valueOf(foreground)
        strokeColor = ColorStateList.valueOf(color(com.google.android.material.R.attr.colorOutline))
        strokeWidth = if (variant == Variant.Outlined) dp(1) else 0
        elevation = 0f
        stateListAnimator = null

        // Keep Material's top / bottom insets: they give the 48dp touch target and space stacked buttons.
        minHeight = dp(size.heightDp) + insetTop + insetBottom
        minimumHeight = minHeight
        setPadding(dp(size.paddingDp), 0, dp(size.paddingDp), 0)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size.textSp)
        cornerRadius = dp(size.heightDp) / 2
        rippleColor = ColorStateList.valueOf(foreground and 0x00FFFFFF or 0x33000000)
    }

    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()
}

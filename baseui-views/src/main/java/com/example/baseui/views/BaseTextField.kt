package com.example.baseui.views

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import androidx.core.content.withStyledAttributes
import androidx.core.widget.doAfterTextChanged
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout

/**
 * Outlined text field with a title, optional icon, helper / error text and a password mode with a
 * show / hide toggle. Builds its own edit text.
 *
 *     <com.example.baseui.views.BaseTextField
 *         app:title="Email"
 *         app:startIconDrawable="@drawable/ic_mail"
 *         app:helperText="We never share it"
 *         app:isPassword="false" />
 *
 *     field.error = "Enter a valid email"   // null clears it
 */
class BaseTextField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.textInputOutlinedStyle,
) : TextInputLayout(context, attrs, defStyleAttr) {

    /** The edit text this field builds (also returned by `getEditText()`). */
    val input: TextInputEditText = TextInputEditText(getContext())

    var text: String
        get() = input.text?.toString().orEmpty()
        set(value) { input.setText(value) }

    var title: CharSequence?
        get() = hint
        set(value) { hint = value }

    var isPassword: Boolean = false
        set(value) {
            field = value
            input.inputType = if (value) {
                InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
            } else {
                InputType.TYPE_CLASS_TEXT
            }
            endIconMode = if (value) END_ICON_PASSWORD_TOGGLE else END_ICON_NONE
        }

    /** Called after every edit. */
    var onTextChanged: ((String) -> Unit)? = null

    init {
        addView(input, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        input.isSingleLine = true
        input.doAfterTextChanged { onTextChanged?.invoke(it?.toString().orEmpty()) }
        context.withStyledAttributes(attrs, R.styleable.BaseTextField) {
            getString(R.styleable.BaseTextField_title)?.let { title = it }
            isPassword = getBoolean(R.styleable.BaseTextField_isPassword, false)
            getString(R.styleable.BaseTextField_android_text)?.let { text = it }
        }
    }
}

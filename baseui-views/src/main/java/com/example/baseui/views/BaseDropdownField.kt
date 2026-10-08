package com.example.baseui.views

import android.content.Context
import android.text.InputType
import android.util.AttributeSet
import android.widget.ArrayAdapter
import androidx.core.content.withStyledAttributes
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.android.material.textfield.TextInputLayout

/**
 * A field with the [BaseTextField] look that opens a menu of options (Material exposed dropdown).
 *
 *     <com.example.baseui.views.BaseDropdownField
 *         app:title="Account"
 *         android:entries="@array/accounts" />
 *
 *     dropdown.setOptions(accounts, label = { it.name }) { account = it }
 */
class BaseDropdownField @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.textInputOutlinedExposedDropdownMenuStyle,
) : TextInputLayout(context, attrs, defStyleAttr) {

    private val field = MaterialAutoCompleteTextView(getContext())
    private var labels: List<String> = emptyList()
    private var select: (Int) -> Unit = {}

    /** Index of the chosen option, or -1. */
    var selectedIndex: Int = -1
        set(value) {
            field = value
            this.field.setText(labels.getOrNull(value).orEmpty(), false)
        }

    init {
        endIconMode = END_ICON_DROPDOWN_MENU
        field.inputType = InputType.TYPE_NULL
        field.isSingleLine = true
        addView(field, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT))
        field.setOnItemClickListener { _, _, position, _ ->
            selectedIndex = position
            select(position)
        }
        context.withStyledAttributes(attrs, R.styleable.BaseDropdownField) {
            getString(R.styleable.BaseDropdownField_title)?.let { hint = it }
            getTextArray(R.styleable.BaseDropdownField_android_entries)?.let { entries ->
                setOptions(entries.map(CharSequence::toString)) {}
            }
        }
    }

    /** Sets the options; [onSelect] gets the chosen one. */
    fun <T> setOptions(options: List<T>, label: (T) -> String = { it.toString() }, onSelect: (T) -> Unit) {
        labels = options.map(label)
        field.setAdapter(ArrayAdapter(context, com.google.android.material.R.layout.m3_auto_complete_simple_item, labels))
        select = { onSelect(options[it]) }
        if (selectedIndex >= labels.size) selectedIndex = -1
    }
}
